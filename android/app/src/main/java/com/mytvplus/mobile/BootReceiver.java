package com.mytvplus.mobile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Starts the app when the box finishes booting, if the setting is on. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction() != null ? intent.getAction() : "";
        if (!action.equals(Intent.ACTION_BOOT_COMPLETED)
                && !action.equals("android.intent.action.QUICKBOOT_POWERON")
                && !action.equals("android.intent.action.REBOOT")) {
            return;
        }
        if (!Boot.isEnabled(context)) return;
        Intent launch = new Intent(context, MainActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            context.startActivity(launch);
        } catch (Exception e) {
            // Some Android 10+ builds block background activity starts without
            // the overlay permission; the Settings screen guides users to grant it.
        }
    }
}
