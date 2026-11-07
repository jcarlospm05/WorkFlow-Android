package com.workflow.operaciones.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.workflow.core.*

internal fun LazyListScope.catalogRows(
    kind: String, vm: WorkflowViewModel, search: String, onSearch: (String) -> Unit,
    onBack: () -> Unit, onAdd: () -> Unit, editOperator: (Operator) -> Unit,
    editAsset: (Asset) -> Unit, editItem: (StockItem) -> Unit
) {
    val title = when (kind) { "operator" -> "Personal"; "asset" -> "Equipos"; else -> "Artículos" }
    val addLabel = when (kind) { "operator" -> "Añadir operador"; "asset" -> "Añadir equipo"; else -> "Añadir artículo" }
    item {
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("catalog-title"))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, null); Text("Volver") }
            Button(onClick = onAdd, enabled = !vm.busy, modifier = Modifier.testTag("catalog-add")) { Icon(Icons.Outlined.Add, null); Text(addLabel) }
        }
    }
    item { OutlinedTextField(search, onSearch, label = { Text("Buscar en ${title.lowercase()}") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, modifier = Modifier.fillMaxWidth().testTag("catalog-search"), singleLine = true) }
    when (kind) {
        "operator" -> {
            val rows = vm.operators.filter { search.isBlank() || it.displayLabel.contains(search, true) }.sortedBy { it.name.lowercase() }
            if (rows.isEmpty()) item { Text(if (search.isBlank()) "Añade al primer operador para empezar." else "No encontramos operadores con esa búsqueda.") }
            items(rows, key = { it.id }) { operator ->
                WFCard(Modifier.fillMaxWidth().clickable(role = Role.Button) { editOperator(operator) }.testTag("catalog-record-${operator.id}")) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        WFIcon(Icons.Outlined.Person, Brand.Sage)
                        Column(Modifier.weight(1f)) { Text(operator.name, style = MaterialTheme.typography.titleSmall); if (operator.code.isNotBlank()) Text(operator.code, style = MaterialTheme.typography.bodyMedium); Text(if (operator.active) operator.shift else "Inactivo", color = Brand.SecondaryText) }
                        Icon(Icons.Outlined.ChevronRight, "Editar operador")
                    }
                }
            }
        }
        "asset" -> {
            val rows = vm.assets.filter { search.isBlank() || "${it.code} ${it.type}".contains(search, true) }
            if (rows.isEmpty()) item { Text(if (search.isBlank()) "Añade el primer equipo para empezar." else "No encontramos equipos con esa búsqueda.") }
            items(rows, key = { it.id }) { asset ->
                WFCard(Modifier.fillMaxWidth().clickable(role = Role.Button) { editAsset(asset) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        WFIcon(Icons.Outlined.LocalShipping)
                        Column(Modifier.weight(1f)) { Text(asset.code, style = MaterialTheme.typography.titleSmall); Text(asset.type, color = Brand.SecondaryText); WFStatus(asset.state) }
                        Icon(Icons.Outlined.ChevronRight, "Editar equipo")
                    }
                }
            }
        }
        else -> {
            val rows = vm.items.filter { search.isBlank() || it.name.contains(search, true) }
            if (rows.isEmpty()) item { Text(if (search.isBlank()) "Añade el primer artículo para empezar." else "No encontramos artículos con esa búsqueda.") }
            items(rows, key = { it.id }) { stock ->
                WFCard(Modifier.fillMaxWidth().clickable(role = Role.Button) { editItem(stock) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        WFIcon(Icons.Outlined.Inventory2, Brand.Sand)
                        Column(Modifier.weight(1f)) { Text(stock.name, style = MaterialTheme.typography.titleSmall); Text(stock.unit, color = Brand.SecondaryText) }
                        Icon(Icons.Outlined.ChevronRight, "Editar artículo")
                    }
                }
            }
        }
    }
}
