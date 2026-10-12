package com.mytvplus.mobile;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(Boot.class);
        MediaProxy.start();
        super.onCreate(savedInstanceState);
    }
}
