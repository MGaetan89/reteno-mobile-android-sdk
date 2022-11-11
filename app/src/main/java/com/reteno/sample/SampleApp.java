package com.reteno.sample;


import android.app.Application;

import androidx.annotation.NonNull;

import com.reteno.core.Reteno;
import com.reteno.core.RetenoApplication;
import com.reteno.core.RetenoImpl;
import com.reteno.core.data.local.config.DeviceIdMode;
import com.reteno.sample.util.AppSharedPreferencesManager;

import kotlin.Unit;

public class SampleApp extends Application implements RetenoApplication {

    private Reteno retenoInstance;

    @Override
    public void onCreate() {
        super.onCreate();
        retenoInstance = new RetenoImpl(this, BuildConfig.API_ACCESS_KEY);
        DeviceIdMode deviceIdMode = AppSharedPreferencesManager.getDeviceIdMode(this);
        retenoInstance.setDeviceIdMode(deviceIdMode, () -> {
            return Unit.INSTANCE;
        });
    }


    @NonNull
    @Override
    public Reteno getRetenoInstance() {
        return retenoInstance;
    }
}
