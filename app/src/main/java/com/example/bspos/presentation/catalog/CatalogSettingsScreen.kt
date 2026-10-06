package com.example.bspos.presentation.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.presentation.common.DialogScrollableColumn

@Composable fun CatalogSettingsScreen(modifier: Modifier = Modifier, viewModel: CatalogSettingsViewModel = hiltViewModel()) {
    var units by remember { mutableStateOf(false) }; var showForm by remember { mutableStateOf(false) }; var editCategory by remember { mutableStateOf<com.example.bspos.domain.model.Category?>(null) }; var editUnit by remember { mutableStateOf<com.example.bspos.domain.model.UnitOfMeasure?>(null) }
    val categories by viewModel.categoryList.collectAsState(); val unitList by viewModel.unitList.collectAsState()
    Column(modifier.fillMaxSize().background(BSPOSTheme.colors.background).padding(20.dp)) {
        Text("Catálogo base", style = MaterialTheme.typography.headlineMedium, color = BSPOSTheme.colors.textPrimary)
        Text("Define cómo se organizan y miden tus productos", color = BSPOSTheme.colors.textSecondary)
        Spacer(Modifier.height(16.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(!units, { units = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Categorías") }
            SegmentedButton(units, { units = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Unidades") }
        }
        Spacer(Modifier.height(12.dp))
        if (if (units) unitList.isEmpty() else categories.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) { Text(if (units) "Aún no hay unidades" else "Aún no hay categorías", color = BSPOSTheme.colors.textSecondary) }
        else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (units) items(unitList) { unit -> ListItem({ Text(unit.name) }, supportingContent = { Text(unit.abbreviation) }, trailingContent = { Row { Switch(unit.isActive, { viewModel.toggle(unit) }); IconButton({ editUnit = unit }) { Icon(Icons.Default.Edit, "Editar unidad") }; IconButton({ viewModel.delete(unit) }) { Icon(Icons.Default.DeleteOutline, "Eliminar unidad") } } }, colors = ListItemDefaults.colors(containerColor = BSPOSTheme.colors.surface)) }
            else items(categories) { category -> ListItem({ Text(category.name) }, supportingContent = category.description?.let { { Text(it) } }, trailingContent = { Row { Switch(category.isActive, { viewModel.toggle(category) }); IconButton({ editCategory = category }) { Icon(Icons.Default.Edit, "Editar categoría") }; IconButton({ viewModel.delete(category) }) { Icon(Icons.Default.DeleteOutline, "Eliminar categoría") } } }, colors = ListItemDefaults.colors(containerColor = BSPOSTheme.colors.surface)) }
        }
        Button(onClick = { showForm = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(if (units) "Nueva unidad" else "Nueva categoría") }
    }
    if (showForm) CatalogForm(units, "", "", { name, detail -> if (units) viewModel.addUnit(name, detail) else viewModel.addCategory(name, detail); showForm = false }, { showForm = false })
    editCategory?.let { category -> CatalogForm(false, category.name, category.description.orEmpty(), { name, detail -> viewModel.update(category, name, detail); editCategory = null }, { editCategory = null }) }
    editUnit?.let { unit -> CatalogForm(true, unit.name, unit.abbreviation, { name, detail -> viewModel.update(unit, name, detail); editUnit = null }, { editUnit = null }) }
}
@Composable private fun CatalogForm(units:Boolean,initialName:String,initialDetail:String,onSave:(String,String)->Unit,onDismiss:()->Unit){var name by remember(initialName){mutableStateOf(initialName)};var detail by remember(initialDetail){mutableStateOf(initialDetail)};AlertDialog(onDismissRequest=onDismiss,title={Text(if(initialName.isEmpty())if(units)"Nueva unidad" else "Nueva categoría" else if(units)"Editar unidad" else "Editar categoría")},text={DialogScrollableColumn{OutlinedTextField(name,{name=it},label={Text("Nombre")},singleLine=true);Spacer(Modifier.height(8.dp));OutlinedTextField(detail,{detail=it},label={Text(if(units)"Abreviatura" else "Descripción")},singleLine=true)}},confirmButton={TextButton(onClick={onSave(name,detail)}){Text("Guardar")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancelar")}})}
