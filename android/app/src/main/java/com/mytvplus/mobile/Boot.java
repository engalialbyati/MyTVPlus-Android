package com.mytvplus.mobile;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/** Settings surface for start-on-boot. The flag lives in SharedPreferences
 *  ("boot" / "enabled", default true) so BootReceiver can read it natively. */
@CapacitorPlugin(name = "Boot")
public class Boot extends Plugin {

    static final String PREFS = "mytv_boot";

    static boolean tvEnabled(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return p.getBoolean("tv_controls", false);   // default off, user opts in
    }

    static boolean isEnabled(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return p.getBoolean("enabled", true);   // default ON: user asked for boot start
    }

    @PluginMethod
    public void isEnabled(PluginCall call) {
        JSObject r = new JSObject();
        r.put("value", isEnabled(getContext()));
        call.resolve(r);
    }

    @PluginMethod
    public void setEnabled(PluginCall call) {
        boolean v = call.getBoolean("value", true);
        getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean("enabled", v).apply();
        call.resolve();
    }

    @PluginMethod
    public void tvEnabled(PluginCall call) {
        JSObject r = new JSObject();
        r.put("value", tvEnabled(getContext()));
        call.resolve(r);
    }

    @PluginMethod
    public void tvSetEnabled(PluginCall call) {
        boolean v = call.getBoolean("value", false);
        getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean("tv_controls", v).apply();
        call.resolve();
    }

    @PluginMethod
    public void proxyUrl(PluginCall call) {
        String url = call.getString("url", "");
        JSObject r = new JSObject();
        int port = MediaProxy.getPort();
        if (port > 0 && (url.startsWith("http://") || url.startsWith("https://"))) {
            r.put("value", "http://127.0.0.1:" + port + "/m?u=" + java.net.URLEncoder.encode(url));
        } else {
            r.put("value", url);
        }
        call.resolve(r);
    }

    @PluginMethod
    public void canDrawOverlays(PluginCall call) {
        JSObject r = new JSObject();
        r.put("value", Settings.canDrawOverlays(getContext()));
        call.resolve(r);
    }

    @PluginMethod
    public void requestOverlayPermission(PluginCall call) {
        if (!Settings.canDrawOverlays(getContext())) {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getContext().getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
        }
        call.resolve();
    }
}
