package com.example.bspos.presentation.reports

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bspos.R
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.data.micatalogo.dto.FeatureModuleDto
import com.example.bspos.data.micatalogo.dto.FeatureSectionDto
import com.example.bspos.presentation.common.UiErrorBus
import kotlinx.coroutines.*

@Composable
fun OperationalReportsScreen(
    module: FeatureModuleDto,
    period: String,
    onPeriodChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onExport: (String) -> Unit
) {
    val colors = BSPOSTheme.colors
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selected by rememberSaveable { mutableStateOf("sales") }
    var query by rememberSaveable(selected, period) { mutableStateOf("") }
    var showInfo by rememberSaveable(selected) { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var exportJob by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(module) { onDispose { exportJob?.cancel() } }
    val sections = module.sections.ifEmpty {
        listOf(FeatureSectionDto(key = "sales", label = stringResource(R.string.reports_detail), kpis = module.kpis, rows = module.rows, note = module.note))
    }
    val section = sections.firstOrNull { it.key == selected } ?: sections.first()
    val rows = remember(section, query) { section.rows.filter { row ->
        query.isBlank() || listOf(row.primary, row.secondary, row.value, row.status).any { it.contains(query.trim(), ignoreCase = true) }
    } }
    val scope = section.scope.ifBlank {
        module.reportContext?.let { "${it.from} - ${it.to}" } ?: stringResource(R.string.reports_default_scope)
    }
    val title = stringResource(R.string.reports_title)
    fun export(action: String) {
        if (exporting) return
        val snapshot = OperationalReportSnapshot(title, module.reportContext, section.copy(rows = rows, scope = scope), query)
        exporting = true
        exportJob = coroutineScope.launch {
            try {
                val file = withContext(Dispatchers.IO) { OperationalReportPdf.create(context, snapshot) }
                ensureActive()
                if (action == "print") {
                    val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                    manager.print("$title - ${section.label}", OperationalReportPrintAdapter(context, file),
                        PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
                } else {
                    val intent = reportPdfSendIntent(context, file, "$title - ${section.label}")
                    if (action == "whatsapp") {
                        try { context.startActivity(Intent(intent).setPackage("com.whatsapp")) }
                        catch (_: ActivityNotFoundException) {
                            try { context.startActivity(Intent(intent).setPackage("com.whatsapp.w4b")) }
                            catch (_: ActivityNotFoundException) { context.startActivity(Intent.createChooser(intent, context.getString(R.string.reports_share))) }
                        }
                    } else context.startActivity(Intent.createChooser(intent, context.getString(R.string.reports_share)))
                }
            } catch (error: CancellationException) { throw error }
            catch (_: ActivityNotFoundException) { UiErrorBus.show(context.getString(R.string.reports_share_error)) }
            catch (_: Exception) { UiErrorBus.show(context.getString(R.string.reports_pdf_error)) }
            finally { exporting = false }
        }
    }
    LazyColumn(
        Modifier.fillMaxSize().testTag("operational_reports"),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.reports_eyebrow), style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                    Text(module.reportContext?.shopName?.ifBlank { title } ?: title, style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.reports_intro), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                }
                IconButton(onClick = onRefresh, enabled = !exporting) { Icon(Icons.Default.Refresh, stringResource(R.string.reports_refresh), tint = colors.primary) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sections.forEach { tab -> FilterChip(selected = section.key == tab.key, onClick = { selected = tab.key }, label = { Text(tab.label) }) }
            }
            if (section.key == "sales") Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("today" to R.string.reports_today, "last_7" to R.string.reports_week, "month" to R.string.reports_month).forEach { (key, label) ->
                    FilterChip(selected = period == key, enabled = !exporting, onClick = { onPeriodChange(key) }, label = { Text(stringResource(label)) })
                }
            }
            Text(scope, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
        if (section.kpis.isNotEmpty()) item {
            Surface(shape = RoundedCornerShape(16.dp), color = colors.surface, border = BorderStroke(1.dp, colors.outline)) {
                Column {
                    section.kpis.chunked(2).forEachIndexed { index, pair ->
                        if (index > 0) HorizontalDivider(color = colors.outline)
                        Row(Modifier.fillMaxWidth()) {
                            pair.forEach { metric ->
                                Column(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(metric.label, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                                    Text(metric.value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(16.dp), color = colors.surface, border = BorderStroke(1.dp, colors.outline)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { export("whatsapp") }, enabled = !exporting, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) { Text(stringResource(R.string.reports_whatsapp)) }
                        OutlinedButton(onClick = { export("print") }, enabled = !exporting, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) { Text(stringResource(R.string.reports_print)) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { export("share") }, enabled = !exporting) { Text(stringResource(R.string.reports_share)) }
                        if (section.key == "sales") {
                            var expanded by remember { mutableStateOf(false) }
                            Box {
                                TextButton(onClick = { expanded = true }, enabled = !exporting) { Text(stringResource(R.string.reports_export)) }
                                DropdownMenu(expanded, { expanded = false }) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.reports_csv)) }, onClick = { expanded = false; onExport("csv") })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.reports_excel)) }, onClick = { expanded = false; onExport("xlsx") })
                                }
                            }
                        }
                    }
                    if (exporting) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.reports_preparing), style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.reports_detail), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                Text(pluralStringResource(R.plurals.reports_records, rows.size, rows.size), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(top = 12.dp), label = { Text(stringResource(R.string.reports_search)) }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, shape = RoundedCornerShape(10.dp))
            if (query.isNotBlank()) Text(stringResource(R.string.reports_total_note), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
        if (rows.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Assessment, null, tint = colors.textSecondary)
                Text(stringResource(R.string.reports_empty), fontWeight = FontWeight.Bold, color = colors.textPrimary)
                Text(stringResource(R.string.reports_empty_hint), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
        }
        itemsIndexed(rows) { _, row ->
            Surface(shape = RoundedCornerShape(12.dp), color = colors.surface, border = BorderStroke(1.dp, colors.outline)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(row.primary, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    if (row.secondary.isNotBlank()) Text(row.secondary, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(row.value, Modifier.weight(1f), color = colors.primary, fontWeight = FontWeight.SemiBold)
                        if (row.status.isNotBlank()) Text(row.status, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                    }
                }
            }
        }
        section.note?.takeIf { it.isNotBlank() }?.let { note -> item {
            TextButton(onClick = { showInfo = !showInfo }) { Icon(Icons.Default.Info, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.reports_info)) }
            if (showInfo) Text(note, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        } }
    }
}

internal fun reportPdfSendIntent(context: Context, file: java.io.File, title: String): Intent {
    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    return Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, title)
        clipData = ClipData.newRawUri(title, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
