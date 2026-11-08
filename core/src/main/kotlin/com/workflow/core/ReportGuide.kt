package com.workflow.core

import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import java.text.Normalizer

data class ReportTopic(val type:ReportType,val title:String,val description:String,val frequent:Boolean=false)
enum class ReportPeriod(val label:String) { TODAY("Hoy"),YESTERDAY("Ayer"),WEEK("Semana"),MONTH("Mes"),CUSTOM("Fechas") }
object ReportGuide {
    val topics=listOf(
        ReportTopic(ReportType.GENERAL,"Resumen de operaciones","Avance, comidas, combustible y pendientes",true),
        ReportTopic(ReportType.ATTENDANCE,"Quién trabajó","Asistencia por persona y turno",true),
        ReportTopic(ReportType.DINNER,"Cena del equipo","Nombres y solicitudes de cena",true),
        ReportTopic(ReportType.FUEL,"Combustible","Abastecimientos y requerimientos pendientes",true),
        ReportTopic(ReportType.WORKSHOP,"Averías y taller","Equipos afectados y reparaciones",true),
        ReportTopic(ReportType.TICKETS,"Infracciones","Tickets, motivos y operadores",true),
        ReportTopic(ReportType.STOCK,"Existencias","Saldos y movimientos de materiales",true),
        ReportTopic(ReportType.WEEK,"Agenda y tareas","Qué se programó y qué falta completar",true),
        ReportTopic(ReportType.ABSENCE,"Quién faltó","Ausencias justificadas e injustificadas"),
        ReportTopic(ReportType.SUNDAY,"Domingos trabajados","Qué domingos trabajó cada persona"),
        ReportTopic(ReportType.DAYS_OFF,"Días libres","Descansos previstos, confirmados y disfrutados"),
        ReportTopic(ReportType.LUNCH,"Almuerzos del equipo","Nombres por fecha, incluido el fin de semana"),
        ReportTopic(ReportType.ASSET_HISTORY,"Historial de un equipo","Averías y resoluciones de una máquina"),
        ReportTopic(ReportType.OPERATOR_TICKETS,"Tickets de un operador","Infracciones por persona y período"),
        ReportTopic(ReportType.MONTH,"Agenda del mes","Calendario y seguimiento mensual"),
        ReportTopic(ReportType.CLOSE,"Cierre y pendientes","Cierres del día y excepciones documentadas"),
        ReportTopic(ReportType.PLANNING,"Programación del personal","Turnos y descansos programados"),
        ReportTopic(ReportType.POSITIONS,"Asignaciones del personal","TRH, Reefer y Taller por operador"),
        ReportTopic(ReportType.OPERATORS,"Listado de personal","Nombres, turnos y estado activo"),
        ReportTopic(ReportType.ASSETS,"Listado de equipos","Flota, tipos y disponibilidad")
    )
    fun topic(type:ReportType)=topics.firstOrNull{it.type==type}?:ReportTopic(type,type.label,"Registro individual")
    private fun normalized(s:String)=Normalizer.normalize(s,Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"").lowercase(java.util.Locale.ROOT)
    fun matches(topic:ReportTopic,search:String):Boolean {
        val haystack=normalized("${topic.title} ${topic.description} ${topic.type.label}")
        return normalized(search).trim().split(Regex("\\s+")).all{it in haystack}
    }
    fun bounds(period:ReportPeriod,reference:String,today:String):Pair<String,String> {
        val ref=LocalDate.parse(reference);val now=LocalDate.parse(today)
        val from=when(period){
            ReportPeriod.TODAY->now;ReportPeriod.YESTERDAY->now.minusDays(1)
            ReportPeriod.WEEK->ref.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            ReportPeriod.MONTH->ref.withDayOfMonth(1);ReportPeriod.CUSTOM->ref
        }
        val to=when(period){ReportPeriod.WEEK->from.plusDays(6);ReportPeriod.MONTH->ref.withDayOfMonth(ref.lengthOfMonth());else->from}
        return from.toString() to to.toString()
    }
    fun initial(type:ReportType,day:String):ReportQuery {
        val period=when(type){ReportType.WEEK->ReportPeriod.WEEK;ReportType.MONTH,ReportType.SUNDAY,ReportType.DAYS_OFF,ReportType.ASSET_HISTORY,ReportType.OPERATOR_TICKETS->ReportPeriod.MONTH;else->ReportPeriod.CUSTOM}
        val (from,to)=bounds(period,day,day)
        return ReportQuery(type,from,to)
    }
    fun selectedPeriod(query:ReportQuery,reference:String,today:String):ReportPeriod =
        ReportPeriod.entries.filter{it!=ReportPeriod.CUSTOM}.firstOrNull{period->
            val dates=bounds(period,reference,today)
            query.from==dates.first&&query.to==dates.second
        } ?: ReportPeriod.CUSTOM
}
