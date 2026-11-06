package com.workflow.operaciones.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.workflow.core.ProtectedReport

@Composable internal fun ReportKeyDialog(setup: Boolean, busy: Boolean, error: String?, onDismiss: () -> Unit, onSubmit: (String, Boolean) -> Unit) {
    var phrase by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var rememberKey by remember { mutableStateOf(true) }
    var localError by remember { mutableStateOf<String?>(null) }
    SafeAlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(if (setup) "Protege tus informes" else "Abrir informe protegido") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (setup) "Acuerda una clave con tu equipo y úsala en los teléfonos que compartirán informes. Puede ser una frase de 12 caracteres o más." else "Escribe la clave que acordaste con quien te envió este informe.")
            OutlinedTextField(phrase, { phrase = it; localError = null }, label = { Text("Clave del equipo") }, enabled = !busy, singleLine = true,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth().testTag("report-key"), trailingIcon = { IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (visible) "Ocultar clave" else "Mostrar clave") } })
            if (setup) OutlinedTextField(confirmation, { confirmation = it; localError = null }, label = { Text("Repite la clave") }, enabled = !busy, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth().testTag("report-key-confirmation"))
            else Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(rememberKey, { rememberKey = it }, enabled = !busy); Text("Recordar en este teléfono", Modifier.weight(1f)) }
            Text(if (setup) "La clave se guardará protegida en este teléfono para los próximos envíos." else "Al recordarla, los próximos informes del equipo se abrirán sin volver a pedirla.", style = MaterialTheme.typography.bodyMedium, color = Brand.SecondaryText)
            (localError ?: error)?.let { Text(it, color = Brand.Red, modifier = Modifier.testTag("report-key-error")) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }, confirmButton = { Button(onClick = {
        try {
            val secret = phrase.toCharArray()
            try { ProtectedReport.validatePassword(secret) } finally { secret.fill('\u0000') }
            require(!setup || phrase == confirmation) { "Las dos claves deben coincidir" }
            localError = null; onSubmit(phrase, rememberKey)
        } catch (e: IllegalArgumentException) { localError = e.message }
    }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp).testTag("report-key-submit")) { Text(if (busy) "Un momento…" else if (setup) "Guardar clave" else "Abrir informe") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancelar") } })
}
