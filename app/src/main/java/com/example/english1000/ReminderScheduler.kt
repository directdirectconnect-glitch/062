package com.example.english1000

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.util.concurrent.TimeUnit

/**
 * On-device, opt-in reminders. Deliberately uses inexact, idle-compatible alarms:
 * Android can defer notifications for battery protection, so hours are approximate.
 * A new one-shot alarm is registered after each reminder and after device reboot.
 */
object ReminderScheduler {
    const val PREF_INTERVAL_HOURS = "reminder_interval_hours"
    const val EXTRA_START_REVIEW = "start_reminder_review"
    const val EXTRA_REVIEW_MODE = "reminder_review_mode"
    private const val ACTION_REMIND = "com.example.english1000.ACTION_REMIND"
    private const val CHANNEL_ID = "english_words_practice_v1"

    fun hours(context: Context): Int = context.getSharedPreferences("progress_v1", Context.MODE_PRIVATE)
        .getInt(PREF_INTERVAL_HOURS, 0)

    fun notificationsAllowed(context: Context): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED)
    }

    fun schedule(context: Context, intervalHours: Int = hours(context)) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarm = PendingIntent.getBroadcast(
            context, 101, Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_REMIND
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.cancel(alarm)
        if (intervalHours > 0 && notificationsAllowed(context)) {
            val next = System.currentTimeMillis() +
                TimeUnit.HOURS.toMillis(intervalHours.toLong())
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, alarm)
        }
    }

    internal fun showNotification(context: Context) {
        if (hours(context) <= 0 || !notificationsAllowed(context)) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val english = context.getSharedPreferences("progress_v1", Context.MODE_PRIVATE)
            .getString("ui_language", "ru") == "en"
        val channel = NotificationChannel(
            CHANNEL_ID,
            if (english) "Word practice reminders" else "Напоминания о словах",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = if (english) "Periodic reminders to review English words"
                else "Периодические напоминания повторить английские слова"
        }
        manager.createNotificationChannel(channel)

        fun launchAction(requestCode: Int, mode: String): PendingIntent =
            PendingIntent.getActivity(
                context, requestCode,
                Intent(context, MainActivity::class.java).apply {
                    action = "com.example.english1000.OPEN_" + mode.uppercase()
                    putExtra(EXTRA_START_REVIEW, true)
                    putExtra(EXTRA_REVIEW_MODE, mode)
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        val openLearn = launchAction(102, "learn")
        val openCheck = launchAction(103, "check")
        val openChoose = launchAction(104, "choose")
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(if (english) "Time for English words!" else "Пора повторить английские слова!")
            .setContentText(if (english) "Learn new words or check what you remember."
                else "Можно изучать новые слова или проверить знакомые.")
            .setContentIntent(openChoose)
            .addAction(Notification.Action.Builder(
                android.R.drawable.ic_media_play,
                if (english) "Learn words" else "Изучать слова", openLearn
            ).build())
            .addAction(Notification.Action.Builder(
                android.R.drawable.ic_menu_search,
                if (english) "Check words" else "Проверить слова", openCheck
            ).build())
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .build()
        manager.notify(101, notification)
    }

    internal const val REMIND_ACTION = ACTION_REMIND
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.REMIND_ACTION) return
        val hours = ReminderScheduler.hours(context)
        if (hours <= 0) return
        // Continue reminders even when the app is not open.
        ReminderScheduler.schedule(context, hours)
        ReminderScheduler.showNotification(context)
    }
}

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) ReminderScheduler.schedule(context)
    }
}
