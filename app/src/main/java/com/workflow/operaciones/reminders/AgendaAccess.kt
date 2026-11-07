package com.workflow.operaciones.reminders

import android.Manifest
import android.app.*
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.workflow.core.AgendaTime

/** Alarm preferences never gate business operations. Android permissions only control delivery. */
interface AgendaAccess {
    fun defaultTime(): String
}
class AndroidAgendaAccess(private val context: Context) : AgendaAccess {
    private val preferences get() = context.getSharedPreferences("agenda_alarms", Context.MODE_PRIVATE)
    fun enabled(): Boolean = preferences.getBoolean("enabled", false)
    fun setEnabled(value: Boolean) { check(preferences.edit().putBoolean("enabled", value).commit()) }
    override fun defaultTime(): String = context.getSharedPreferences("agenda_alarms", Context.MODE_PRIVATE).getString("default_time", "").orEmpty()
    fun setDefaultTime(value: String) { if(value.isNotEmpty()) AgendaTime.requireTime(value); check(preferences.edit().putString("default_time", value).commit()) }
    fun notificationsAllowed(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    fun exactAllowed(): Boolean = Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    fun channelAllowed(): Boolean {
        val channel = context.getSystemService(NotificationManager::class.java).getNotificationChannel(AgendaScheduler.CHANNEL)
        return channel != null && channel.importance >= NotificationManager.IMPORTANCE_DEFAULT && channel.sound != null && channel.shouldVibrate()
    }
    fun issues(): List<String> = buildList {
        if (!notificationsAllowed()) add("Activa las notificaciones de WorkFlow.")
        if (!exactAllowed()) add("Autoriza las alarmas y recordatorios de WorkFlow.")
        if (!channelAllowed()) add("Activa sonido y vibración en el canal Agenda y cierre del día.")
        if (context.getSystemService(AudioManager::class.java).getStreamVolume(AudioManager.STREAM_ALARM) == 0) add("Sube el volumen de las alarmas del teléfono.")
    }
}
