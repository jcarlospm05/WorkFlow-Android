package com.workflow.operaciones

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import com.workflow.operaciones.data.WorkflowDatabase
import com.workflow.operaciones.data.WorkflowRepository
import com.workflow.operaciones.reminders.AgendaScheduler

class WorkflowApp : Application() {
    val agendaScheduler: AgendaScheduler by lazy { AgendaScheduler(this) { repository.history() } }
    val repository: WorkflowRepository by lazy { WorkflowRepository(this, WorkflowDatabase.open(this)).also { it.onChanged = { agendaScheduler.requestReconcile() } } }
    override fun onCreate() {
        super.onCreate()
        AgendaScheduler.createChannel(this)
        agendaScheduler.requestReconcile()
        ContextCompat.registerReceiver(this, object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> { PhoneSession.lockGeneration++; PhoneSession.unlocked = false }
                    Intent.ACTION_USER_PRESENT -> PhoneSession.presentEvents++
                }
            }
        }, IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(Intent.ACTION_USER_PRESENT) }, ContextCompat.RECEIVER_NOT_EXPORTED)
    }
}

/** Memory only: app switching keeps the session; screen-off and process death revoke it. */
object PhoneSession {
    var unlocked by mutableStateOf(false)
    var lockGeneration by mutableStateOf(0)
    var presentEvents by mutableStateOf(0)
}
