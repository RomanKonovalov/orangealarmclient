package com.romif.securityalarm.androidclient.feature.service;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import com.romif.securityalarm.androidclient.feature.AlarmState;
import com.romif.securityalarm.androidclient.feature.R;
import com.romif.securityalarm.androidclient.feature.activities.MainActivity;

/**
 * Helper class for showing and canceling alarm
 * notifications.
 * <p>
 * This class makes heavy use of the {@link NotificationCompat.Builder} helper
 * class to create notifications in a backward-compatible way.
 */
public class NotificationService {
    public static final int NOTIFICATION_ID = 888;
    /**
     * The unique identifier for this type of notification.
     */
    private static final String NOTIFICATION_TAG = "Alarm";
    public static final String CHANNEL_ID_SUCCESS_ACTION = "notification_success_action";
    public static final String CHANNEL_ID_FAIL_ACTION = "alarm_notification_fail_action";
    public static final String CHANNEL_ID_ALARM = "notification_alarm";

    public static void init(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            if (notificationManager.getNotificationChannel(CHANNEL_ID_SUCCESS_ACTION) == null) {
                createNotificationChannel(context, CHANNEL_ID_SUCCESS_ACTION, AlarmState.PAUSED);
            }
            if (notificationManager.getNotificationChannel(CHANNEL_ID_FAIL_ACTION) == null) {
                createNotificationChannel(context, CHANNEL_ID_FAIL_ACTION, AlarmState.PAUSE_EXCEPTION);
            }
            if (notificationManager.getNotificationChannel(CHANNEL_ID_ALARM) == null) {
                createNotificationChannel(context, CHANNEL_ID_ALARM, AlarmState.ZONE_ESCAPE);
            }
        }
    }

    public static void notify(final Context context, AlarmState alarmState, final int number) {
        notify(context, alarmState, number, null);
    }

    /**
     * Shows the notification, or updates a previously shown notification of
     * this type, with the given parameters.
     * <p>
     * TODO: Customize this method's arguments to present relevant content in
     * the notification.
     * <p>
     * TODO: Customize the contents of this method to tweak the behavior and
     * presentation of alarm notifications. Make
     * sure to follow the
     * <a href="https://developer.android.com/design/patterns/notifications.html">
     * Notification design guidelines</a> when doing so.
     *
     * @see #cancel(Context)
     */
    public static void notify(final Context context, AlarmState alarmState, final int number, Throwable exception) {
        final Resources res = context.getResources();

        // This image is used as the notification's large icon (thumbnail).
        // TODO: Remove this if your notification has no relevant thumbnail.

        final String title = res.getString(R.string.alarm_notification_title_template);

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);

        String text = "";
        String channelId = CHANNEL_ID_SUCCESS_ACTION;
        switch (alarmState) {
            case PAUSED:
                text = res.getString(R.string.alarm_notification_text_alarm_paused);
                channelId = CHANNEL_ID_SUCCESS_ACTION;
                break;
            case RESUMED:
                text = res.getString(R.string.alarm_notification_text_alarm_resumed);
                channelId = CHANNEL_ID_SUCCESS_ACTION;
                break;
            case INCORRECT_CREDENTIALS:
                text = res.getString(R.string.alarm_notification_text_credential_incorrect);
                channelId = CHANNEL_ID_FAIL_ACTION;
                break;
            case PAUSE_EXCEPTION:
                text = res.getString(R.string.alarm_notification_text_pause_exception, exception != null ? exception.getLocalizedMessage() : "");
                channelId = CHANNEL_ID_FAIL_ACTION;
                break;

            case RESUME_EXCEPTION:
                text = res.getString(R.string.alarm_notification_text_resume_exception, exception != null ? exception.getLocalizedMessage() : "");
                channelId = CHANNEL_ID_FAIL_ACTION;
                break;

            case ZONE_ESCAPE:
                text = res.getString(R.string.alarm_notification_text_unit_escape);
                channelId = CHANNEL_ID_ALARM;
                break;
        }


        final NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)

                // Set appropriate defaults for the notification light, sound,
                // and vibration.
                .setDefaults(Notification.DEFAULT_ALL)

                // Set required fields, including the small icon, the
                // notification title, and text.
                .setContentTitle(title)
                .setContentText(text)

                // All fields below this line are optional.

                // Use a default priority (recognized on devices running Android
                // 4.1 or later)
                .setPriority(AlarmState.ZONE_ESCAPE == alarmState ? NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_DEFAULT)

                // Provide a large icon, shown with the notification in the
                // notification drawer on devices running Android 3.0 or later.
                //.setLargeIcon(picture)

                // Set ticker text (preview) information for this notification.
                //.setTicker(ticker)

                // Show a number. This is useful when stacking notifications of
                // a single type.
                //.setNumber(number)

                // If this notification relates to a past or upcoming event, you
                // should set the relevant time information using the setWhen
                // method below. If this call is omitted, the notification's
                // timestamp will by set to the time at which it was shown.
                // TODO: Call setWhen if this notification relates to a past or
                // upcoming event. The sole argument to this method should be
                // the notification timestamp in milliseconds.
                //.setWhen(...)

                // Set the pending intent to be initiated when the user touches
                // the notification.


                // Show expanded text content on devices running Android 4.1 or
                // later.
                .setStyle(new NotificationCompat.BigTextStyle()
                                .bigText(text)
                                .setBigContentTitle(title)
                        /*.setSummaryText("Dummy summary text")*/)

                // Example additional actions for this notification. These will
                // only show on devices running Android 4.1 or later, so you
                // should ensure that the activity in this notification's
                // content intent provides access to the same actions in
                // another way.
                /*.addAction(
                        R.drawable.ic_action_stat_share,
                        res.getString(R.string.action_share),
                        PendingIntent.getActivity(
                                context,
                                0,
                                Intent.createChooser(new Intent(Intent.ACTION_SEND)
                                        .setType("text/plain")
                                        .putExtra(Intent.EXTRA_TEXT, "Dummy text"), "Dummy title"),
                                PendingIntent.FLAG_UPDATE_CURRENT))
                .addAction(
                        R.drawable.ic_action_stat_reply,
                        res.getString(R.string.action_reply),
                        null)*/

                // Automatically dismiss the notification when it is touched.
                .setAutoCancel(true)
                .setContentIntent(
                        PendingIntent.getActivity(
                                context,
                                0,
                                new Intent(context, MainActivity.class),
                                PendingIntent.FLAG_UPDATE_CURRENT));
        switch (alarmState) {
            case PAUSED:
                builder.setSmallIcon(R.drawable.ic_alarm_paused);
                break;
            case RESUMED:
                builder.setSmallIcon(R.drawable.ic_alarm_resumed);
                break;
            case PAUSE_EXCEPTION:
            case RESUME_EXCEPTION:
            case INCORRECT_CREDENTIALS:
            case ZONE_ESCAPE:
                builder.setSmallIcon(R.drawable.ic_alarm_error);
                break;
        }

        createNotificationChannel(context, channelId, alarmState);

        notify(context, builder);
    }

    private static void createNotificationChannel(Context context, String channelId, AlarmState alarmState) {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = null;
            String description = null;
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            Uri sound = null;
            long[] vibrationPattern = null;
            switch (alarmState) {
                case PAUSED:
                case RESUMED:
                    name = context.getString(R.string.channel_name_success_action);
                    description = context.getString(R.string.channel_name_success_action);
                    sound = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + context.getPackageName() + "/" + R.raw.job_done);
                    break;

                case INCORRECT_CREDENTIALS:
                case PAUSE_EXCEPTION:
                case RESUME_EXCEPTION:
                    name = context.getString(R.string.channel_name_fail_action);
                    description = context.getString(R.string.channel_name_fail_action);
                    importance = NotificationManager.IMPORTANCE_HIGH;
                    sound = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + context.getPackageName() + "/" + R.raw.job_done);
                    vibrationPattern = new long[]{500, 1000, 500, 1000, 500, 1000};
                    break;

                case ZONE_ESCAPE:
                    name = context.getString(R.string.channel_name_alarm);
                    description = context.getString(R.string.channel_name_alarm);
                    importance = NotificationManager.IMPORTANCE_MAX;
                    sound = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + context.getPackageName() + "/" + R.raw.job_done);
                    vibrationPattern = new long[]{500, 1000, 500, 1000, 500, 1000};
                    break;
            }


            NotificationChannel channel = new NotificationChannel(channelId, name, importance);
            channel.setDescription(description);

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build();

            if (sound != null) {
                channel.setSound(sound, audioAttributes);
            }

            if (vibrationPattern != null) {
                channel.setVibrationPattern(vibrationPattern);
            }

            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private static void notify(final Context context, final NotificationCompat.Builder builder) {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

        // notificationId is a unique int for each notification that you must define
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    /**
     * Cancels any notifications of this type previously shown using
     * {@link #notify(Context, AlarmState, int)}.
     */
    @TargetApi(Build.VERSION_CODES.ECLAIR)
    public static void cancel(final Context context) {
        final NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(NOTIFICATION_TAG, 0);
    }
}
