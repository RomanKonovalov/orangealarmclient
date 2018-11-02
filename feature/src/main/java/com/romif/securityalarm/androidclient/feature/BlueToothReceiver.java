package com.romif.securityalarm.androidclient.feature;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.android.gms.common.api.ResolvableApiException;
import com.romif.securityalarm.androidclient.feature.service.NotificationService;
import com.romif.securityalarm.androidclient.feature.service.SecurityService;
import com.romif.securityalarm.androidclient.feature.service.WialonService;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class BlueToothReceiver extends BroadcastReceiver {

    private static final String TAG = "BlueToothReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);

        boolean autoSwitching = sharedPref.getBoolean(SettingsConstants.BT_AUTO_SWITCHING_PREFERENCE, true);
        String address = sharedPref.getString(SettingsConstants.DEVICE_PREFERENCE, "");
        String email = sharedPref.getString(SettingsConstants.EMAIL_PREFERENCE, "");
        long unitId = Long.parseLong(sharedPref.getString(SettingsConstants.UNIT_PREFERENCE, "0"));
        String wialonHost = sharedPref.getString(SettingsConstants.WIALON_HOST_PREFERENCE, context.getString(R.string.wialon_host));
        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        if (!autoSwitching || "".equals(email) || "".equals(wialonHost) || unitId == 0 || device == null || !address.equalsIgnoreCase(device.getAddress())) {
            return;
        }

        String action = intent.getAction();

        Log.d("BlueToothReceiver", "device " + device.getName());

        CompletableFuture<String> loginFuture = SecurityService.getCredential(context)
                .thenCompose(credential -> WialonService.login(wialonHost, credential.getId(), credential.getPassword()))
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

        String notificationName = sharedPref.getString(SettingsConstants.NOTIFICATION_NAME_PREFERENCE, context.getString(R.string.notification_name));
        if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {

            loginFuture
                    .thenCompose(result -> WialonService.getNotification())
                    .thenCompose(notification -> WialonService.updateNotification(notification, true, notificationName))
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
            String geozoneName = sharedPref.getString(SettingsConstants.GEOZONE_NAME_PREFERENCE, context.getString(R.string.geozone_name));
            loginFuture
                    .thenCompose(result -> WialonService.getGeozone(false))
                    .thenCompose(geozone -> {
                        String zoneId = geozone.getZl().entrySet().stream()
                                .filter(e -> geozoneName.equals(e.getValue().getN()))
                                .findFirst()
                                .map(Map.Entry::getKey)
                                .orElse(null);

                        int geozoneRadius = Integer.parseInt(sharedPref.getString(SettingsConstants.GEOZONE_RADIUS_PREFERENCE, String.valueOf(context.getResources().getInteger(R.integer.geozone_radius))));
                        int geozoneColor = - 0xFF000000 + sharedPref.getInt(SettingsConstants.GEOZONE_COLOR_PREFERENCE, context.getColor(R.color.geozone_color));
                        if (zoneId == null) {
                            return WialonService.getLocation(unitId).thenCompose(position -> WialonService.createGeozone(geozone, position, geozoneName, geozoneRadius, geozoneColor));
                        } else {
                            return WialonService.getLocation(unitId).thenCompose(position -> WialonService.updateGeozone(geozone, position, geozoneName, geozoneRadius, geozoneColor));
                        }
                    })
                    .thenCompose(result -> WialonService.getNotification())
                    .thenCompose(notification -> {
                        String notificationId = notification.getUnf().entrySet().stream()
                                .filter(e -> notificationName.equals(e.getValue().getN()))
                                .findFirst()
                                .map(Map.Entry::getKey)
                                .orElse(null);
                        if (notificationId == null) {
                            String notificationEmailSubject = sharedPref.getString(SettingsConstants.NOTIFICATION_EMAIL_SUBJECT_PREFERENCE, context.getString(R.string.notification_email_subject));
                            String notificationPatternText = sharedPref.getString(SettingsConstants.NOTIFICATION_PATTERN_PREFERENCE, context.getString(R.string.notification_pattern));
                            return WialonService.getGeozone(true).thenCompose(resource -> WialonService.createNotification(resource, unitId, email, geozoneName, notificationName, notificationEmailSubject, notificationPatternText));
                        } else {
                            return WialonService.updateNotification(notification, false, notificationName);
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
