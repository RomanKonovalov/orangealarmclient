package com.romif.securityalarm.androidclient.feature.service;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import com.romif.securityalarm.androidclient.feature.AlarmState;
import com.romif.securityalarm.androidclient.feature.activities.MainActivity;
import com.romif.securityalarm.androidclient.feature.R;

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
    private static final String CHANNEL_ID = "alarm_notification";

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

        String text = "";
        switch (alarmState) {
            case PAUSED:
                text = res.getString(R.string.alarm_notification_text_alarm_paused);
                break;
            case RESUMED:
                text = res.getString(R.string.alarm_notification_text_alarm_resumed);
                break;
            case INCORRECT_CREDENTIALS:
                text = res.getString(R.string.alarm_notification_text_credential_incorrect);
                break;
            case PAUSE_EXCEPTION:
                text = res.getString(R.string.alarm_notification_text_pause_exception, exception != null ? exception.getLocalizedMessage() : "");
                break;

            case RESUME_EXCEPTION:
                text = res.getString(R.string.alarm_notification_text_resume_exception, exception != null ? exception.getLocalizedMessage() : "");
                break;

            case ZONE_ESCAPE:
                text = res.getString(R.string.alarm_notification_text_alarm_paused);
                break;
        }


        final NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)

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
                .setAutoCancel(true);

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
                builder
                        .setSmallIcon(R.drawable.ic_alarm_error)
                        .setContentIntent(
                                PendingIntent.getActivity(
                                        context,
                                        0,
                                        new Intent(context, MainActivity.class),
                                        PendingIntent.FLAG_UPDATE_CURRENT));

                break;
        }

        if (AlarmState.ZONE_ESCAPE == alarmState) {
            builder.setVibrate(new long[]{1000, 1000, 1000, 1000, 1000});
            builder.setLights(Color.RED, 3000, 3000);
        }

        createNotificationChannel(context);

        notify(context, builder);
    }

    private static void createNotificationChannel(Context context) {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = context.getString(R.string.channel_name);
            String description = context.getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
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
        final NotificationManager nm = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR) {
            nm.cancel(NOTIFICATION_TAG, 0);
        } else {
            nm.cancel(NOTIFICATION_TAG.hashCode());
        }
    }
}
