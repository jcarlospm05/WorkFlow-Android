package com.workflow.operaciones.reminders

import android.app.*
import android.content.*
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import com.workflow.core.*
import com.workflow.operaciones.MainActivity
import com.workflow.operaciones.WorkflowApp
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.*

class AgendaScheduler(private val context: Context, private val documents: suspend () -> List<DailyDocument>) {
    private val scope = CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val mutex = Mutex()
    private val state = context.getSharedPreferences("agenda_alarm_state", Context.MODE_PRIVATE)
    private val manager = context.getSystemService(AlarmManager::class.java)
    private var pending: Job? = null
    @Volatile var problem: String? = null; private set
    companion object {
        const val CHANNEL = "workflow_agenda_alarm_v1"
        const val FIRE = "com.workflow.operaciones.AGENDA_ALARM"
        const val OPEN = "com.workflow.operaciones.OPEN_AGENDA"
        fun createChannel(context: Context) {
            val attributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            val channel=NotificationChannel(CHANNEL,"Agenda y cierre del día",NotificationManager.IMPORTANCE_HIGH).apply {
                description="Alarmas de tareas y comprobación del cierre a las 7:00 p. m."
                enableVibration(true);setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),attributes)
                lockscreenVisibility=Notification.VISIBILITY_PRIVATE
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        fun agendaIntent(context: Context, day: String): Intent = Intent(context,MainActivity::class.java).setAction(OPEN).putExtra("day",day).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    private fun broadcast(key: String): PendingIntent = PendingIntent.getBroadcast(context,0,
        Intent(context,AgendaReceiver::class.java).setAction(FIRE).setData(Uri.Builder().scheme("workflow-alarm").authority("event").appendPath(key).build()),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun requestReconcile() { synchronized(this) { pending?.cancel();pending=scope.launch { delay(150);reconcile() } } }
    suspend fun reconcile() = mutex.withLock {
        try {
            createChannel(context)
            val access=AndroidAgendaAccess(context)
            val plan=AgendaAlarms.plan(documents(),Instant.now(),ZoneId.systemDefault()).associateBy { it.key }
            val delivered=state.getStringSet("delivered",emptySet()).orEmpty().intersect(plan.keys)
            val old=state.getStringSet("scheduled",emptySet()).orEmpty().toSet()
            val eligible=plan.values.filter { it.key !in delivered }
            // Keep below Android's per-UID alarm quota, rearming the next tasks after each event.
            val desired=if(access.exactAllowed()&&access.notificationsAllowed()&&access.channelAllowed())
                (eligible.filter{it.taskId==null}+eligible.filter{it.taskId!=null}.sortedBy{it.at}.take(126)).associateBy{it.key}
                else emptyMap()
            (old-desired.keys).forEach { val alarm=broadcast(it);manager.cancel(alarm);alarm.cancel() }
            desired.values.forEach { alarm ->
                val at=maxOf(alarm.at.toEpochMilli(),System.currentTimeMillis()+2000)
                val show=PendingIntent.getActivity(context,0,agendaIntent(context,alarm.day).setData(Uri.parse("workflow-agenda://day/${alarm.day}")),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                manager.setAlarmClock(AlarmManager.AlarmClockInfo(at,show),broadcast(alarm.key))
            }
            state.edit().putStringSet("scheduled",desired.keys.toSet()).putStringSet("delivered",delivered).apply()
            problem=if(plan.isNotEmpty()&&desired.isEmpty()&&(!access.exactAllowed()||!access.notificationsAllowed()||!access.channelAllowed())) "Activa los permisos, sonido y vibración para recibir las alarmas de la agenda." else null
            if(AgendaAlarmService.running)context.startService(Intent(context,AgendaAlarmService::class.java).setAction(AgendaAlarmService.RECHECK))
        } catch (_:SecurityException) { problem="Autoriza las alarmas y notificaciones de WorkFlow." }
        catch (_:Exception) { problem="No se pudieron programar las alarmas. Revisa su configuración." }
    }
    fun receive(intent: Intent, finished: () -> Unit) {
        scope.launch {
            try {
                if(intent.action==FIRE) {
                    val key=intent.data?.lastPathSegment ?: return@launch
                    val access=AndroidAgendaAccess(context)
                    if(!access.notificationsAllowed()||!access.exactAllowed()||!access.channelAllowed())return@launch
                    val alarm=mutex.withLock {
                        val candidate=AgendaAlarms.plan(documents(),Instant.now(),ZoneId.systemDefault()).firstOrNull { it.key==key }
                        val delivered=state.getStringSet("delivered",emptySet()).orEmpty()
                        if(candidate==null||key in delivered||candidate.at.isAfter(Instant.now().plusSeconds(2)))null
                        else { state.edit().putStringSet("delivered",delivered+key).commit();candidate }
                    }
                    if(alarm!=null)try { ContextCompat.startForegroundService(context,Intent(context,AgendaAlarmService::class.java)
                        .setAction(AgendaAlarmService.RING).putExtra("key",alarm.key).putExtra("day",alarm.day).putExtra("title",alarm.title)) }
                    catch(e:Exception) { state.edit().putStringSet("delivered",state.getStringSet("delivered",emptySet()).orEmpty()-key).commit();throw e }
                }
                reconcile()
            } catch (_:Exception) { problem="No se pudo activar el aviso. Revisa los permisos de la agenda." }
            finally { finished() }
        }
    }
}

class AgendaReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending=goAsync()
        (context.applicationContext as WorkflowApp).agendaScheduler.receive(intent) { pending.finish() }
    }
}
