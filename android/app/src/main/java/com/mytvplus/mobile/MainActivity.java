package com.mytvplus.mobile;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(Boot.class);
        MediaProxy.start();
        super.onCreate(savedInstanceState);
        applyImmersive();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersive();
    }

    // TV-box UX: playback always fullscreen, system bars hidden (sticky)
    private void applyImmersive() {
        Window w = getWindow();
        w.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    // TV controls during playback — mirrors the original MyTV+ app, which is
    // stock ExoPlayer key handling: media keys work with the controller hidden,
    // D-pad shows/navigates the controller, volume stays on the system, and
    // Back leaves the player. Keys are only routed while the player is open,
    // so the remote still navigates the browse grid normally.
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN
                && Boot.tvEnabled(this) && Boot.isPlayerOpen()) {
            int kc = event.getKeyCode();
            // the original ignores auto-repeat for media keys / OK (no held-key spam)
            if (event.getRepeatCount() > 0 && !isSeekOrOsdKey(kc)) return true;
            String k = mapPlayerKey(kc);
            if (k != null) {
                evalJs("window.__tvKey && window.__tvKey('" + k + "')");
                return true;
            }
            if (kc == KeyEvent.KEYCODE_BACK) {
                evalJs("window.__tvBack && window.__tvBack()");
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private static boolean isSeekOrOsdKey(int kc) {
        return kc == KeyEvent.KEYCODE_DPAD_LEFT || kc == KeyEvent.KEYCODE_DPAD_RIGHT
                || kc == KeyEvent.KEYCODE_DPAD_UP || kc == KeyEvent.KEYCODE_DPAD_DOWN;
    }

    /** Same key set ExoPlayer's PlayerControlView handles, plus CH +/- for zapping. */
    private static String mapPlayerKey(int kc) {
        switch (kc) {
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_HEADSETHOOK:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                return "ok";
            case KeyEvent.KEYCODE_MEDIA_PLAY:      return "play";
            case KeyEvent.KEYCODE_MEDIA_PAUSE:     return "pause";
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_DPAD_RIGHT:      return "ff";   // +15s
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_DPAD_LEFT:       return "rew";  // -5s
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:       return "show"; // show the "controller" (OSD)
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_CHANNEL_UP:      return "ch+";
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:    return "ch-";
            default: return null;
        }
    }

    private void evalJs(String js) {
        if (getBridge() != null && getBridge().getWebView() != null) {
            getBridge().getWebView().post(() ->
                    getBridge().getWebView().evaluateJavascript(js, null));
        }
    }
}
