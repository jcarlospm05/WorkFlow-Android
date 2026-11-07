package com.workflow.operaciones.data

import com.workflow.operaciones.reminders.AgendaAccess

/** Database/UI fixtures isolate Android permissions; AlarmIntegrationTest uses real services. */
class TestAgendaAccess(private val hour: String = "09:00") : AgendaAccess {
    override fun defaultTime() = hour
}
