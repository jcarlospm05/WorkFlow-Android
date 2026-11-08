package com.workflow.operaciones.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.workflow.operaciones.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Native calendar: selecting a date does not require typing a format or granting permissions. */
@Composable internal fun WorkDatePicker(value: String, close: () -> Unit, choose: (String) -> Unit) {
    val context=LocalContext.current
    val latestClose by rememberUpdatedState(close)
    val latestChoose by rememberUpdatedState(choose)
    DisposableEffect(context,value) {
        val day=runCatching{LocalDate.parse(value)}.getOrDefault(LocalDate.now())
        val dialog=DatePickerDialog(context,R.style.Theme_WorkFlow_Calendar,{_,year,month,date->
            latestChoose(LocalDate.of(year,month+1,date).toString())
        },day.year,day.monthValue-1,day.dayOfMonth)
        dialog.setTitle("Elegir fecha")
        dialog.setOnDismissListener{latestClose()}
        dialog.show()
        onDispose { dialog.setOnDismissListener(null);dialog.dismiss() }
    }
}

@Composable internal fun WorkDateField(label:String,value:String,tag:String,choose:(String)->Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val text=runCatching { LocalDate.parse(value).format(DateTimeFormatter.ofPattern("d MMM yyyy",Locale.forLanguageTag("es"))) }.getOrDefault(value)
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(label,style=MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick={open=true},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag(tag)) {
            Icon(Icons.Outlined.CalendarMonth,null);Spacer(Modifier.width(10.dp));Text(text,Modifier.weight(1f));Text("Cambiar",style=MaterialTheme.typography.labelMedium)
        }
    }
    if(open)WorkDatePicker(value,{open=false}){choose(it);open=false}
}
