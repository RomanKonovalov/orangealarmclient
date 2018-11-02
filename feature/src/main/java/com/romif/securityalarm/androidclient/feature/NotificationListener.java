package com.romif.securityalarm.androidclient.feature;

import android.app.Notification;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.SpannableString;

import com.romif.securityalarm.androidclient.feature.service.NotificationService;

public class NotificationListener extends NotificationListenerService {

    public static final String GMAIL_PACKAGE = "com.google.android.gm";

    public NotificationListener() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (!GMAIL_PACKAGE.equals(sbn.getPackageName())) {
            return;
        }
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        String notificationName = sharedPref.getString(SettingsConstants.NOTIFICATION_NAME_PREFERENCE, getString(R.string.notification_name));
        Notification notification = sbn.getNotification();
        SpannableString subject = (SpannableString) notification.extras.get(Notification.EXTRA_TEXT);
        if (subject != null && subject.toString().contains(notificationName)) {
            NotificationService.notify(getApplicationContext(), AlarmState.ZONE_ESCAPE, 333);
        }
    }

}
