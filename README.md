# MyTV+ Android (unofficial)

Capacitor wrapper around the same renderer used by the
[desktop client](../desktop-client). One UI codebase, two platforms.

## Features

Same as the desktop client (profiles, favorites, watch later, resume,
subtitles, filters), plus:

- **Start on boot** — Settings ⚙ → *Start app on boot* (on by default).
  Works via a `BOOT_COMPLETED` receiver. On Android 10+ boxes that block
  background activity starts, also grant *Display over other apps* from the
  same Settings screen, then reboot once to test.
- Touch and D-pad (remote) friendly: corner buttons always visible on touch
  devices, focus outlines for remote navigation.

## Install on your Android box

1. Download `MyTVPlus-1.0.apk` from the [releases](../../releases) page.
2. Copy it to the box (USB or downloader app) and open it.
3. Allow "install from unknown sources" when prompted.

## Build from source

Requires JDK 17+ and an Android SDK (set `ANDROID_HOME`):

```bash
cd mobile
npm install
node scripts/build-web.js        # assembles www/ from ../desktop-client/renderer
npx cap sync android
cd android && gradlew.bat assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

Signing: release builds expect `android/mytvplus-release.keystore`
(store/key password `mytvplus`, alias `mytvplus`, override with `MYTV_KS_PASS`).
**The keystore is git-ignored — keep it; it's required to sign updates.**
Regenerate one with:

```bash
keytool -genkeypair -keystore mytvplus-release.keystore -alias mytvplus \
  -keyalg RSA -keysize 2048 -validity 10000
```

## Architecture

```
scripts/build-web.js     copies ../desktop-client/renderer + injects mobile bits
www-shim/native-shim.js  window.mytv on Android: CapacitorHttp (no CORS),
                         CryptoJS channel decryption, localStorage profiles,
                         Filesystem-backed catalog cache
android/…/Boot.java      native settings plugin (SharedPreferences toggle,
                         overlay-permission helpers)
android/…/BootReceiver.java  BOOT_COMPLETED → launches MainActivity when enabled
```

Min SDK 22 (Android 5.1+). The renderer is shared verbatim with the desktop
client — mobile-specific behavior is injected at build time, so most feature
work in `desktop-client/renderer` lands on both platforms via a rebuild.
