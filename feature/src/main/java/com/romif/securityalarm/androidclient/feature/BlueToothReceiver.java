package com.romif.securityalarm.androidclient.feature;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.romif.securityalarm.androidclient.feature.service.NotificationService;
import com.romif.securityalarm.androidclient.feature.service.SecurityService;
import com.romif.securityalarm.androidclient.feature.service.WialonService;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

public class BlueToothReceiver extends BroadcastReceiver {

    private static final String TAG = "BlueToothReceiver";

    private Properties properties = new Properties();

    @Override
    public void onReceive(Context context, Intent intent) {

        SharedPreferences sharedPref = context.getSharedPreferences("deviceInfo", Context.MODE_PRIVATE);
        String address = sharedPref.getString("address", "");
        Long unitId = sharedPref.getLong("unitId", 0);
        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        if (unitId == 0 || device == null || !address.equalsIgnoreCase(device.getAddress())) {
            return;
        }

        String action = intent.getAction();

        Log.d("BlueToothReceiver", "device " + device.getName());

        try {
            properties.load(context.getAssets().open("application.properties"));
        } catch (IOException e) {
            Log.e(TAG, e.getMessage(), e);
        }

        CompletableFuture<String> loginFuture = SecurityService.getCredential(context)
                .thenCompose(credential -> WialonService.login((String) properties.get("wialon.host"), credential.getId(), credential.getPassword()))
                .handle((aVoid, throwable) -> {
                    if (throwable == null || throwable.getCause() == null) {
                        return aVoid;
                    }
                    Throwable e = throwable.getCause();
                    if (e instanceof ResolvableApiException) {
                        NotificationService.notify(context, AlarmState.INCORRECT_CREDENTIALS, 333);
                    }
                    return aVoid;
                });

        if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
            loginFuture
                    .thenCompose(result -> WialonService.getNotification())
                    .thenCompose(notification -> WialonService.updateNotification(notification, true))
                    .handle((o, throwable) -> {
                        WialonService.logout();

                        if (throwable != null && throwable.getCause() instanceof WialonService.InvalidCredentialsException) {
                            NotificationService.notify(context, AlarmState.INCORRECT_CREDENTIALS, 333);
                        } else if (throwable != null) {
                            Log.e(TAG, "Error while pausing", throwable.getCause());
                            NotificationService.notify(context, AlarmState.PAUSE_EXCEPTION, 333, throwable.getCause());
                        } else {
                            NotificationService.notify(context, AlarmState.PAUSED, 333);
                        }

                        return null;
                    });
        } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
            loginFuture
                    .thenCompose(result -> WialonService.getGeozone(false))
                    .thenCompose(geozone -> {
                        String zoneId = geozone.getZl().entrySet().stream()
                                .filter(e -> WialonService.GEOZONE_NAME.equals(e.getValue().getN()))
                                .findFirst()
                                .map(Map.Entry::getKey)
                                .orElse(null);
                        if (zoneId == null) {
                            return WialonService.getLocation(context).thenCompose(position -> WialonService.createGeozone(geozone, position));
                        } else {
                            return WialonService.getLocation(context).thenCompose(position -> WialonService.updateGeozone(geozone, position));
                        }
                    })
                    .thenCompose(result -> WialonService.getNotification())
                    .thenCompose(notification -> {
                        String notificationId = notification.getUnf().entrySet().stream()
                                .filter(e -> WialonService.NOTIFICATION_NAME.equals(e.getValue().getN()))
                                .findFirst()
                                .map(Map.Entry::getKey)
                                .orElse(null);
                        if (notificationId == null) {
                            return WialonService.getGeozone(true).thenCompose(resource -> WialonService.createNotification(resource, context));
                        } else {
                            return WialonService.updateNotification(notification, false);
                        }
                    })
                    .handle((o, throwable) -> {
                        WialonService.logout();

                        if (throwable != null && throwable.getCause() instanceof WialonService.InvalidCredentialsException) {
                            NotificationService.notify(context, AlarmState.INCORRECT_CREDENTIALS, 333);
                        } else if (throwable != null) {
                            Log.e(TAG, "Error while resuming", throwable.getCause());
                            NotificationService.notify(context, AlarmState.RESUME_EXCEPTION, 333, throwable.getCause());
                        } else {
                            NotificationService.notify(context, AlarmState.RESUMED, 333);
                        }

                        return null;
                    });
        }


    }

}
