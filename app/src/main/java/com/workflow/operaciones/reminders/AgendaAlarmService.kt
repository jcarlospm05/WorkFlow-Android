package com.workflow.operaciones.reminders

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.*
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.workflow.core.AgendaAlarms
import com.workflow.operaciones.WorkflowApp
import kotlinx.coroutines.*
import java.time.*

class AgendaAlarmService : Service() {
    private var player:MediaPlayer?=null
    private var wake:PowerManager.WakeLock?=null
    private val handler=Handler(Looper.getMainLooper())
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
    private var activeKey=""
    private val stop=Runnable { stopAlarm() }
    companion object {
        const val RING="ring";const val STOP="stop";const val RECHECK="recheck"
        @Volatile var running=false;private set
        const val NOTICE=1900
    }
    override fun onBind(intent:Intent?)=null
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        when(intent?.action) {
            STOP -> stopAlarm()
            RECHECK -> if(running)scope.launch {
                val docs=withContext(Dispatchers.IO){(application as WorkflowApp).repository.history()}
                val access=AndroidAgendaAccess(this@AgendaAlarmService)
                if(!access.enabled() || !access.notificationsAllowed() || !access.channelAllowed() || AgendaAlarms.plan(docs,Instant.now(),ZoneId.systemDefault()).none{it.key==activeKey})stopAlarm()
            } else stopSelf()
            RING -> {
                if(!AndroidAgendaAccess(this).enabled()) { stopAlarm();return START_NOT_STICKY }
                val day=intent.getStringExtra("day") ?: return START_NOT_STICKY
                val title=intent.getStringExtra("title") ?: "Agenda pendiente"
                activeKey=intent.getStringExtra("key").orEmpty()
                AgendaScheduler.createChannel(this)
                val open=PendingIntent.getActivity(this,1,AgendaScheduler.agendaIntent(this,day),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val dismiss=PendingIntent.getService(this,2,Intent(this,AgendaAlarmService::class.java).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val public=NotificationCompat.Builder(this,AgendaScheduler.CHANNEL).setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("WorkFlow").setContentText("Tienes una actividad pendiente en la agenda").build()
                val notice=NotificationCompat.Builder(this,AgendaScheduler.CHANNEL).setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle(title).setContentText("$day · Abre la agenda para completar el trabajo")
                    .setContentIntent(open).setCategory(NotificationCompat.CATEGORY_ALARM).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setPublicVersion(public).setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
                    .addAction(0,"Abrir agenda",open).addAction(0,"Detener alarma",dismiss).build()
                ServiceCompat.startForeground(this,NOTICE,notice,if(Build.VERSION.SDK_INT>=34)ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE else 0)
                running=true
                releaseAudio()
                val channel=getSystemService(NotificationManager::class.java).getNotificationChannel(AgendaScheduler.CHANNEL)
                val audio=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                channel?.sound?.let { sound -> try {
                    player=MediaPlayer().apply { setAudioAttributes(audio);setDataSource(this@AgendaAlarmService,sound);isLooping=true;prepare();start() }
                } catch (_:Exception) { player?.release();player=null } }
                if(channel?.shouldVibrate()==true) {
                    @Suppress("DEPRECATION") val vibrator=getSystemService(VIBRATOR_SERVICE) as Vibrator
                    if(vibrator.hasVibrator())vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0,500,250,500,1000),0))
                }
                wake=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"WorkFlow:AgendaAlarm").apply { acquire(65_000) }
                handler.removeCallbacks(stop);handler.postDelayed(stop,60_000)
            }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }
    private fun releaseAudio() {
        player?.let { runCatching { it.stop() };it.release() };player=null
        @Suppress("DEPRECATION") val vibrator=getSystemService(VIBRATOR_SERVICE) as Vibrator
        vibrator.cancel()
        wake?.let { if(it.isHeld)it.release() };wake=null
    }
    private fun stopAlarm() { running=false;handler.removeCallbacks(stop);releaseAudio();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf() }
    override fun onDestroy() { running=false;handler.removeCallbacks(stop);releaseAudio();scope.cancel();super.onDestroy() }
}
