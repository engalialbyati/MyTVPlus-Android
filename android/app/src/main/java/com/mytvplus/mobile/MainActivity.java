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

    // TV controls: when the setting is on, remote keys drive the web player
    // (volume, CH+/-, seek, OK=pause) instead of the system.
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && Boot.tvEnabled(this)) {
            String k = null;
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_VOLUME_UP: k = "vol+"; break;
                case KeyEvent.KEYCODE_VOLUME_DOWN: k = "vol-"; break;
                case KeyEvent.KEYCODE_CHANNEL_UP: k = "ch+"; break;
                case KeyEvent.KEYCODE_CHANNEL_DOWN: k = "ch-"; break;
                case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD: k = "ff"; break;
                case KeyEvent.KEYCODE_MEDIA_REWIND: k = "rew"; break;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER: k = "ok"; break;
            }
            if (k != null) {
                final String js = "window.__tvKey && window.__tvKey('" + k + "')";
                if (getBridge() != null && getBridge().getWebView() != null) {
                    getBridge().getWebView().post(() ->
                            getBridge().getWebView().evaluateJavascript(js, null));
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }
}
