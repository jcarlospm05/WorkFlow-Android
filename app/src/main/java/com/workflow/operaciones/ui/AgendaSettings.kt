package com.workflow.operaciones.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.workflow.operaciones.reminders.*

@Composable internal fun AgendaSettingsContent(vm: WorkflowViewModel) {
    val context=LocalContext.current
    val access=remember(context){AndroidAgendaAccess(context)}
    var revision by remember { mutableIntStateOf(0) }
    var hour by rememberSaveable { mutableStateOf(access.defaultTime()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var permissionAsked by rememberSaveable { mutableStateOf(context.getSharedPreferences("agenda_alarms",android.content.Context.MODE_PRIVATE).getBoolean("notification_asked",false)) }
    val owner=LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME)revision++}
        owner.lifecycle.addObserver(observer);onDispose{owner.lifecycle.removeObserver(observer)}
    }
    val notifications=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){revision++}
    val issues=remember(revision){access.issues()}
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Cada tarea pendiente necesita una hora. La comprobación del cierre será a las 7:00 p. m.")
        OutlinedTextField(hour,{hour=it;error=null},label={Text("Hora para tareas automáticas · HH:mm")},modifier=Modifier.fillMaxWidth().testTag("alarm-default-time"),singleLine=true)
        Button(onClick={try{access.setDefaultTime(hour.trim());revision++;vm.refresh();error=null}catch(e:IllegalArgumentException){error=e.message}},modifier=Modifier.fillMaxWidth().testTag("alarm-save-time")){Text("Guardar hora")}
        if(issues.isEmpty())WFStatus("Alarmas activadas") else issues.forEach{Text("• $it",color=Brand.Red,style=MaterialTheme.typography.bodyMedium)}
        OutlinedButton(onClick={
            if(Build.VERSION.SDK_INT>=33&&!access.notificationsAllowed()&&!permissionAsked) {
                permissionAsked=true
                context.getSharedPreferences("agenda_alarms",android.content.Context.MODE_PRIVATE).edit().putBoolean("notification_asked",true).apply()
                notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))
        },modifier=Modifier.fillMaxWidth()){Text("Activar notificaciones")}
        if(Build.VERSION.SDK_INT>=31)OutlinedButton(onClick={context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:${context.packageName}")))},modifier=Modifier.fillMaxWidth()){Text("Autorizar alarmas y recordatorios")}
        OutlinedButton(onClick={context.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName).putExtra(Settings.EXTRA_CHANNEL_ID,AgendaScheduler.CHANNEL))},modifier=Modifier.fillMaxWidth()){Text("Sonido y vibración")}
        OutlinedButton(onClick={context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS))},modifier=Modifier.fillMaxWidth()){Text("Volumen del teléfono")}
        Text("Los avisos respetan el volumen de alarmas y No molestar de Android. Detener una alarma no completa la tarea.",style=MaterialTheme.typography.bodySmall,color=Brand.SecondaryText)
        error?.let{Text(it,color=Brand.Red)}
        vm.repository.fileWarning?.let{Text(it,color=Brand.Red)}
    }
}
@Composable internal fun AgendaSettingsDialog(vm: WorkflowViewModel, close: () -> Unit) {
    SafeAlertDialog(onDismissRequest=close,title={Text("Alarmas de la agenda")},text={AgendaSettingsContent(vm)},confirmButton={TextButton(onClick={vm.refresh();close()}){Text("Cerrar")}})
}
