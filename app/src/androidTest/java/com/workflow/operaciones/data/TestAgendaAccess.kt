package com.workflow.operaciones.data

import com.workflow.operaciones.reminders.AgendaAccess

/** Database/UI fixtures isolate Android permissions; AlarmIntegrationTest uses real services. */
class TestAgendaAccess(private val allowed: Boolean = true) : AgendaAccess {
    override fun defaultTime() = "09:00"
    override fun requireReady() { require(allowed) { "Activa las alarmas antes de iniciar la jornada" } }
}
