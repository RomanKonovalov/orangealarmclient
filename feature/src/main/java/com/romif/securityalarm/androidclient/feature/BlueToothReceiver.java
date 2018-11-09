package com.romif.securityalarm.androidclient.feature;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.romif.securityalarm.androidclient.feature.service.NotificationService;
import com.romif.securityalarm.androidclient.feature.service.SecurityService;
import com.romif.securityalarm.androidclient.feature.service.WialonService;

import java.util.Map;

import java9.util.concurrent.CompletableFuture;
import java9.util.stream.StreamSupport;

public class BlueToothReceiver extends BroadcastReceiver {

    private static final String TAG = "BlueToothReceiver";

    private FirebaseAnalytics mFirebaseAnalytics;

    @Override
    public void onReceive(Context context, Intent intent) {

        mFirebaseAnalytics = FirebaseAnalytics.getInstance(context);

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

        Log.d(TAG, "device " + device.getName());

        log(Log.INFO, "on_bluetooth_receive", "begin");

        boolean useSmartLock = sharedPref.getBoolean(SettingsConstants.USE_SMART_LOCK_PREFERENCE, true);
        CompletableFuture<String> tokenFuture = useSmartLock ? SecurityService.getCredential(context)
                .thenCompose(credential -> WialonService.getToken(wialonHost, credential.getId(), credential.getPassword(), true)) :
                CompletableFuture.completedFuture(sharedPref.getString(SettingsConstants.TOKEN, ""));

        CompletableFuture<String> loginFuture = tokenFuture
                .thenCompose(token -> WialonService.login(wialonHost, token))
                .handle((aVoid, throwable) -> {
                    if (throwable == null || throwable.getCause() == null) {
                        return aVoid;
                    }
                    Throwable e = throwable.getCause();
                    if (e instanceof ResolvableApiException) {
                        NotificationService.notify(context, AlarmState.INCORRECT_CREDENTIALS, 333);
                        log(Log.ERROR, "on_bluetooth_receive", "credentials_resolve_required");
                    }
                    return aVoid;
                });

        String notificationName = sharedPref.getString(SettingsConstants.NOTIFICATION_NAME_PREFERENCE, context.getString(R.string.notification_name));
        if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {

            log(Log.INFO, "on_bluetooth_receive", "action_acl_connected");

            loginFuture
                    .thenCompose(result -> WialonService.getNotification())
                    .thenCompose(notification -> WialonService.updateNotification(notification, true, notificationName))
                    .handle((o, throwable) -> {
                        WialonService.logout();

                        if (throwable != null && throwable.getCause() instanceof WialonService.InvalidCredentialsException) {
                            NotificationService.notify(context, AlarmState.INCORRECT_CREDENTIALS, 333);
                            log(Log.ERROR, "on_bluetooth_receive", "incorrect_credentials");
                        } else if (throwable != null) {
                            Log.e(TAG, "Error while pausing", throwable.getCause());
                            log("on_bluetooth_receive", throwable.getCause() != null ? throwable.getCause() : throwable);
                            NotificationService.notify(context, AlarmState.PAUSE_EXCEPTION, 333, throwable.getCause());
                        } else {
                            NotificationService.notify(context, AlarmState.PAUSED, 333);
                            log(Log.INFO, "on_bluetooth_receive", "alarm_paused");
                        }

                        return null;
                    });
        } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
            log(Log.INFO, "on_bluetooth_receive", "action_acl_disconnected");
            String geozoneName = sharedPref.getString(SettingsConstants.GEOZONE_NAME_PREFERENCE, context.getString(R.string.geozone_name));
            loginFuture
                    .thenCompose(result -> WialonService.getGeozone(false))
                    .thenCompose(geozone -> {
                        String zoneId = StreamSupport.stream(geozone.getZl().entrySet())
                                .filter(e -> geozoneName.equals(e.getValue().getN()))
                                .findFirst()
                                .map(Map.Entry::getKey)
                                .orElse(null);

                        int geozoneRadius = Integer.parseInt(sharedPref.getString(SettingsConstants.GEOZONE_RADIUS_PREFERENCE, String.valueOf(context.getResources().getInteger(R.integer.geozone_radius))));
                        int geozoneColor = -0x80000000 + sharedPref.getInt(SettingsConstants.GEOZONE_COLOR_PREFERENCE, context.getColor(R.color.geozone_color));
                        if (zoneId == null) {
                            return WialonService.getLocation(unitId).thenCompose(position -> WialonService.createGeozone(geozone, position, geozoneName, geozoneRadius, geozoneColor));
                        } else {
                            return WialonService.getLocation(unitId).thenCompose(position -> WialonService.updateGeozone(geozone, position, geozoneName, geozoneRadius, geozoneColor));
                        }
                    })
                    .thenCompose(result -> WialonService.getNotification())
                    .thenCompose(notification -> {
                        String notificationId = StreamSupport.stream(notification.getUnf().entrySet())
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
                            log(Log.ERROR, "on_bluetooth_receive", "incorrect_credentials");
                        } else if (throwable != null) {
                            Log.e(TAG, "Error while resuming", throwable.getCause());
                            log("on_bluetooth_receive", throwable.getCause() != null ? throwable.getCause() : throwable);
                            NotificationService.notify(context, AlarmState.RESUME_EXCEPTION, 333, throwable.getCause());
                        } else {
                            NotificationService.notify(context, AlarmState.RESUMED, 333);
                            log(Log.INFO, "on_bluetooth_receive", "alarm_resumed");
                        }

                        return null;
                    });
        }


    }

    private void log(int priority, String event, String status) {
        Bundle params = new Bundle();
        params.putString("activity", TAG);
        params.putString("status", status);
        mFirebaseAnalytics.logEvent(event, params);
        Crashlytics.log(priority, TAG, event + ": status " + status);
    }

    private void log(String event, Throwable throwable) {
        Bundle params = new Bundle();
        params.putString("error", throwable.getLocalizedMessage());
        mFirebaseAnalytics.logEvent(event, params);
        Crashlytics.logException(throwable);
    }

}
