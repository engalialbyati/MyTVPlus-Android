'use strict';
/* Android bridge: reimplements the Electron preload surface (window.mytv)
   on top of CapacitorHttp (no CORS), CryptoJS (channel AES), localStorage
   (state + profiles) and the Filesystem plugin (big catalog cache). */

(function () {
  const Cap = window.Capacitor;
  if (!Cap) { console.error('Capacitor runtime missing'); return; }
  const Http = Cap.Plugins.CapacitorHttp;
  const FS = Cap.Plugins.Filesystem;
  const Boot = Cap.Plugins.Boot || null;

  const APP_VERSION = '3.15.1';
  const UA = 'And$MyTV';
  const HOSTS = ['https://androidapi.appmytv.com', 'https://androidapi.mtvapp.net'];
  const API_ROOT = '/android/v2';
  const CATALOG_TTL_MS = 6 * 60 * 60 * 1000;

  const SEED = '9z_z%C*F-JaNdWgU';
  const IV = CryptoJS.enc.Utf8.parse('t2w!W%u&F(J@McWf');

  let activeHostIdx = 0;
  let state = null;

  const lsGet = (k, d) => { try { return JSON.parse(localStorage.getItem(k)) ?? d; } catch { return d; } };
  const lsSet = (k, v) => localStorage.setItem(k, JSON.stringify(v));

  function decryptPayload(s) {
    const mid = Math.floor(s.length / 2);
    const rot = parseInt(s[mid], 10);
    const body = s.slice(0, mid) + s.slice(mid + 1);
    const keyStr = rot === 0 ? SEED : SEED.slice(SEED.length - rot) + SEED.slice(0, SEED.length - rot);
    const key = CryptoJS.enc.Utf8.parse(keyStr);
    const dec = CryptoJS.AES.decrypt(body, key, { iv: IV, mode: CryptoJS.mode.CBC, padding: CryptoJS.pad.Pkcs7 });
    return dec.toString(CryptoJS.enc.Utf8);
  }

  async function apiPost(pathname, body, timeoutMs = 20000) {
    let lastErr;
    for (let attempt = 0; attempt < HOSTS.length; attempt++) {
      const idx = (activeHostIdx + attempt) % HOSTS.length;
      const url = HOSTS[idx] + API_ROOT + '/' + pathname.replace(/^\/+/, '');
      try {
        const res = await Http.post({
          url,
          headers: { 'Content-Type': 'application/json', 'User-Agent': UA },
          data: body,
          connectTimeout: timeoutMs,
          readTimeout: timeoutMs,
        });
        if (res.status >= 400) throw new Error('HTTP ' + res.status);
        activeHostIdx = idx;
        return typeof res.data === 'string' ? res.data : JSON.stringify(res.data);
      } catch (e) { lastErr = e; }
    }
    throw lastErr || new Error('all API hosts failed');
  }

  function componentRequest() {
    return {
      appVersion: APP_VERSION, deviceType: 'Android',
      uuid: state.uuid, userId: state.userId || '', country: 'IQ',
    };
  }

  async function bootstrap() {
    state = lsGet('mytv_state', null);
    if (state && state.userId) return { ...state };
    state = state || { uuid: crypto.randomUUID(), userId: null, createdAt: Date.now() };
    const body = {
      deviceType: 'Android', uuid: state.uuid,
      joinDate: new Date().toISOString().slice(0, 19).replace('T', ' '),
      appVersion: APP_VERSION, osVersion: Cap.isNativePlatform() ? 'android' : 'web',
      macAddress: '02:00:00:00:00:00', serialNumber: 'N/A', fcmToken: '',
      deviceModel: 'AndroidBox', deviceManufacturer: 'Generic', imsi: '', age: '', gender: '',
    };
    const resp = JSON.parse(await apiPost('users/saveUser.php', body));
    if (!resp.state || !resp.userId) throw new Error('saveUser failed');
    state.userId = String(resp.userId);
    lsSet('mytv_state', state);
    return { ...state };
  }

  // ---- catalog cache (Filesystem; falls back to no cache) ----
  async function cacheGet(name, maxAge) {
    try {
      const r = await FS.readFile({ path: 'cache-' + name + '.json', directory: 'DATA', encoding: 'utf8' });
      const raw = JSON.parse(r.data);
      if (Date.now() - raw.fetchedAt > maxAge) return null;
      return raw.data;
    } catch { return null; }
  }
  async function cacheSet(name, data) {
    try {
      await FS.writeFile({
        path: 'cache-' + name + '.json', directory: 'DATA', encoding: 'utf8',
        data: JSON.stringify({ fetchedAt: Date.now(), data }),
      });
    } catch (e) { console.warn('cache write failed', name, e); }
  }

  async function fetchCatalog(name, pathname, opts = {}) {
    const cached = await cacheGet(name, CATALOG_TTL_MS);
    if (cached) return cached;
    let text = await apiPost(pathname, componentRequest());
    if (opts.encrypted) text = decryptPayload(text.trim());
    const data = JSON.parse(text);
    cacheSet(name, data);
    return data;
  }

  // ---- profiles (localStorage; same shape as Electron main) ----
  const PKEY = 'mytv_profiles';
  const newId = () => Date.now().toString(36) + Math.random().toString(36).slice(2, 8);
  const emptyData = () => ({
    favorites: { channel: {}, movie: {}, series: {} },
    watchlater: { channel: {}, movie: {}, series: {} },
    progress: {}, history: [],
  });
  function profilesMap() { return lsGet(PKEY, {}); }

  window.mytv = {
    bootstrap,
    channels: () => fetchCatalog('channels', 'channels/getChannelsDetail_local.php', { encrypted: true }),
    movies: () => fetchCatalog('movies', 'movies/getMoviesDetail_local.php'),
    series: () => fetchCatalog('series', 'series/getSeriesDetail_local.php'),
    movieCategories: () => fetchCatalog('movieCategories', 'movies/getMoviesCategory.php'),
    movieCategoryConnections: () => fetchCatalog('movieCategoryConnections', 'movies/getMoviesCategoryConnection.php'),
    seasons: async (seriesId) => JSON.parse(await apiPost('series/getSeriesSeasonDetail.php', { id: String(seriesId), userId: state.userId })),
    episodes: async (seasonId) => JSON.parse(await apiPost('series/getSeriesEpisodeDetail.php', { id: String(seasonId), userId: state.userId, uuid: state.userId })),
    subtitle: async (url) => {
      const r = await Http.get({ url, headers: { 'User-Agent': UA }, responseType: 'text' });
      return typeof r.data === 'string' ? r.data : JSON.stringify(r.data);
    },
    info: () => ({ version: APP_VERSION, ua: UA, platform: 'android' }),

    profilesList: async () => Object.values(profilesMap()).sort((a, b) => (a.createdAt || 0) - (b.createdAt || 0)),
    profileCreate: async (name, avatar) => {
      const m = profilesMap();
      const p = { id: newId(), name: String(name || 'User').slice(0, 24), avatar: String(avatar || '🙂'), createdAt: Date.now() };
      m[p.id] = p; lsSet(PKEY, m);
      return p;
    },
    profileDelete: async (id) => {
      const m = profilesMap(); delete m[id];
      localStorage.removeItem('mytv_pdata_' + id);
      lsSet(PKEY, m);
    },
    profileLoad: async (id) => {
      const m = profilesMap();
      if (!m[id]) throw new Error('profile not found');
      return { profile: m[id], data: lsGet('mytv_pdata_' + id, emptyData()) };
    },
    profileSave: async (id, data) => lsSet('mytv_pdata_' + id, data),
  };

  // boot-on-boot settings (native plugin, Android only)
  window.mytvBoot = Boot;
})();
