package com.workflow.operaciones.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.workflow.core.*
import com.workflow.core.Module

@Composable internal fun CompactNameRow(name: String) {
    Column(Modifier.fillMaxWidth().background(Color.White)) {
        Text(name,Modifier.padding(horizontal=12.dp,vertical=10.dp),style=MaterialTheme.typography.bodyMedium)
        HorizontalDivider(color=Brand.Border)
    }
}
@Composable internal fun CompactAttendanceRow(person: Operator, record: Operation?, enabled: Boolean, save:(String,Boolean)->Unit) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(Color.White).testTag("attendance-${if(record==null)"unmarked"else "marked"}-${person.id}")) {
        Row(Modifier.fillMaxWidth().heightIn(min=48.dp).padding(start=10.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(person.name,style=MaterialTheme.typography.bodyMedium)
                if(record!=null)Text(record.status+if(record.extended)" · EXT"else "",style=MaterialTheme.typography.labelMedium,color=if(record.status.startsWith("Ausencia"))Brand.Red else Brand.SecondaryText)
            }
            if(record==null)TextButton(onClick={save("Presente",false)},enabled=enabled,modifier=Modifier.testTag("mark-present-${person.id}"),contentPadding=PaddingValues(horizontal=8.dp)){Text("Presente")}
            Box {
                IconButton(onClick={menu=true},enabled=enabled){Icon(Icons.Outlined.MoreVert,if(record==null)"Ausencia u otro estado"else "Cambiar asistencia")}
                DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                    Rules.statuses.getValue(Module.ATTENDANCE).forEach { status -> DropdownMenuItem(text={Text(status)},onClick={menu=false;save(status,record?.extended?:false)}) }
                    if(record!=null)DropdownMenuItem(text={Text(if(record.extended)"Quitar jornada extendida"else "Marcar jornada extendida")},onClick={menu=false;save(record.status,!record.extended)})
                }
            }
        }
        HorizontalDivider(color=Brand.Border)
    }
}
