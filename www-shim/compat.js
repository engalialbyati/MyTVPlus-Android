'use strict';
/* ES5-only guard script, loaded FIRST. Old TV-box WebViews may fail to parse
   the modern app scripts; without this, the only symptom is an eternal
   "Connecting…" spinner. It surfaces any script error or rejected promise on
   the boot screen and detects WebViews too old for the app (Chrome 60+). */

(function () {
  function bootMsg(text) {
    try {
      var m = document.getElementById('boot-msg');
      if (m) m.textContent = text;
    } catch (e) {}
  }

  function describe(err) {
    if (!err) return 'unknown error';
    if (typeof err === 'string') return err;
    return (err.message || String(err)) + (err.stack ? '' : '');
  }

  var prevOnError = window.onerror;
  window.onerror = function (msg, src, line, col) {
    var f = (src || '').split('/');
    bootMsg('Startup error: ' + msg + ' (' + f[f.length - 1] + ':' + line + ')');
    if (typeof prevOnError === 'function') return prevOnError.apply(this, arguments);
    return false;
  };

  window.addEventListener('error', function (ev) {
    bootMsg('Startup error: ' + describe(ev && (ev.error || ev.message)));
  });

  window.addEventListener('unhandledrejection', function (ev) {
    var r = ev && ev.reason;
    bootMsg('Startup failure: ' + describe(r && (r.message || r)));
  });

  // Feature floor for the app scripts: ES2019 syntax + Web Crypto.
  // padStart (Chrome 57) is used as the sentinel; anything below cannot run.
  var missing = [];
  if (!window.Promise) missing.push('Promise');
  if (!window.crypto || !window.crypto.getRandomValues) missing.push('WebCrypto');
  if (!(Object.entries && Object.values)) missing.push('Object.entries');
  if (!Array.prototype.includes) missing.push('Array.includes');
  if (!String.prototype.padStart) missing.push('String.padStart');
  if (missing.length) {
    bootMsg('This Android WebView is too old for the app (missing: ' +
      missing.join(', ') + '). Update "Android System WebView" in the Play Store, or use a newer firmware.');
  }
})();
