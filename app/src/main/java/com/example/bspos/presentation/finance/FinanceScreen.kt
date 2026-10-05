package com.example.bspos.presentation.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.bspos.core.money.LocalCurrency
import com.example.bspos.core.money.MoneyUtils
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.data.micatalogo.dto.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceScreen(
    onNavigateBack: (() -> Unit)? = null,
    onNavigateToCash: (() -> Unit)? = null,
    viewModel: FinanceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showExpenseDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessages()
        }
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessages()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().background(BSPOSTheme.colors.surface).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onNavigateBack != null) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Regresar")
                    }
                }
                Text(
                    text = uiState.shopName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = BSPOSTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = { viewModel.refresh() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
                }
            }
        },
        floatingActionButton = {
            if (selectedTab == 3) {
                FloatingActionButton(
                    onClick = { showExpenseDialog = true },
                    containerColor = BSPOSTheme.colors.primary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Registrar Gasto")
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BSPOSTheme.colors.background)
                .padding(paddingValues)
        ) {
            if (uiState.isLoading && uiState.summary == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BSPOSTheme.colors.primary)
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Selector de Períodos
                    PeriodSelectorRow(
                        currentFilter = uiState.periodFilter,
                        onFilterSelected = { viewModel.selectPeriod(it) }
                    )

                    // Pestañas Principales
                    PrimaryScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        edgePadding = 16.dp,
                        containerColor = BSPOSTheme.colors.surface
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Resumen y KPIs", fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Estado de Resultados", fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = { Text("Flujo de Efectivo", fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = selectedTab == 3,
                            onClick = { selectedTab = 3 },
                            text = { Text("Gastos", fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = selectedTab == 4,
                            onClick = { selectedTab = 4 },
                            text = { Text("Caja en línea", fontWeight = FontWeight.Bold) }
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        when (selectedTab) {
                            0 -> FinanceSummaryTab(
                                summary = uiState.summary,
                                onNavigateToCash = onNavigateToCash
                            )
                            1 -> IncomeStatementTab(
                                incomeStatement = uiState.summary?.incomeStatement,
                                period = uiState.summary?.period
                            )
                            2 -> CashFlowTab(
                                cashFlow = uiState.summary?.cashFlow
                            )
                            3 -> ExpensesTab(
                                expenses = uiState.expenses,
                                onAddExpense = { showExpenseDialog = true }
                            )
                            4 -> RemoteCashTab(
                                cash = uiState.cashSession,
                                busy = uiState.isCashOperationBusy,
                                onOpen = viewModel::openRemoteCash,
                                onClose = viewModel::closeRemoteCash,
                                onMovement = viewModel::recordRemoteCashMovement
                            )
                        }
                    }
                }
            }
        }
    }

    if (showExpenseDialog) {
        ExpenseCreateDialog(
            categories = uiState.expenseCategories,
            isSaving = uiState.isRegisteringExpense,
            onDismiss = { showExpenseDialog = false },
            onConfirm = { desc, amt, catId, method, notes ->
                viewModel.registerExpense(desc, amt, catId, method, notes)
                showExpenseDialog = false
            }
        )
    }
}

@Composable
private fun PeriodSelectorRow(
    currentFilter: FinancePeriodFilter,
    onFilterSelected: (FinancePeriodFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FinancePeriodFilter.entries.forEach { filter ->
            FilterChip(
                selected = currentFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = { Text(filter.label, fontWeight = FontWeight.SemiBold) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = BSPOSTheme.colors.primary,
                    selectedLabelColor = Color.White
                )
            )
        }
    }
}

@Composable
private fun FinanceSummaryTab(
    summary: FinanceSummaryDto?,
    onNavigateToCash: (() -> Unit)? = null
) {
    if (summary == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No hay datos disponibles para el período.", color = BSPOSTheme.colors.textSecondary)
        }
        return
    }

    val period = summary.period
    val current = summary.currentState
    var showInvoiceDetails by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Bloque 1: KPIs de Ganancia y Resultado Operativo
        Text(
            text = "Resultado del período",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FinanceKpiCard(
                title = "Ventas netas",
                amount = formatPesos(period.netSales),
                subtitle = "${period.salesCount} ventas (${period.unitsSold} u.)",
                icon = Icons.Default.ShoppingCart,
                accentColor = BSPOSTheme.colors.primary,
                modifier = Modifier.weight(1f)
            )
            FinanceKpiCard(
                title = "Ganancia bruta",
                amount = period.grossProfit?.let { formatPesos(it) } ?: "N/D",
                subtitle = "Margen: ${period.grossMarginPercent?.let { "%.1f%%".format(it) } ?: "N/D"}",
                icon = Icons.Default.TrendingUp,
                accentColor = Color(0xFF2E7D32),
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FinanceKpiCard(
                title = "Gastos operativos",
                amount = formatPesos(period.operatingExpenses),
                subtitle = "Comisiones: ${formatPesos(period.commissionsGenerated)}",
                icon = Icons.Default.ReceiptLong,
                accentColor = Color(0xFFD32F2F),
                modifier = Modifier.weight(1f)
            )
            FinanceKpiCard(
                title = "Ganancia operativa",
                amount = period.operatingProfit?.let { formatPesos(it) } ?: "N/D",
                subtitle = "Margen op: ${period.operatingMarginPercent?.let { "%.1f%%".format(it) } ?: "N/D"}",
                icon = Icons.Default.AccountBalance,
                accentColor = Color(0xFF1565C0),
                modifier = Modifier.weight(1f)
            )
        }

        // Flujo Neto
        summary.cashFlow?.let { cf ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Flujo de Efectivo Neto",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Cobrado: ${formatPesos(cf.inflows.total)} · Pagado: ${formatPesos(cf.outflows.total)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = BSPOSTheme.colors.textSecondary
                        )
                    }
                    Text(
                        text = formatPesos(cf.netCashFlow),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (cf.netCashFlow >= 0) Color(0xFF2E7D32) else Color(0xFFD32F2F),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        // Bloque 2: Estado Actual del Negocio
        Text(
            text = "Estado actual del negocio",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Cuentas por Cobrar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Cuentas por cobrar", fontWeight = FontWeight.Bold)
                        Text(
                            "Cartera total pendiente",
                            style = MaterialTheme.typography.bodySmall,
                            color = BSPOSTheme.colors.textSecondary
                        )
                    }
                    Text(
                        text = formatPesos(current.receivableTotal),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFE65100),
                        maxLines = 1,
                        softWrap = false
                    )
                }

                HorizontalDivider()

                // Desglose de Antigüedad (Aging)
                Text("Antigüedad de saldos por cobrar:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    AgingPill("0-30 d", formatPesos(current.aging.days0To30), Color(0xFF2E7D32))
                    AgingPill("31-60 d", formatPesos(current.aging.days31To60), Color(0xFFF57C00))
                    AgingPill("61-90 d", formatPesos(current.aging.days61To90), Color(0xFFE65100))
                    AgingPill(">90 d", formatPesos(current.aging.daysOver90), Color(0xFFD32F2F))
                }

                HorizontalDivider()

                Text("Conciliación de cuentas por cobrar", fontWeight = FontWeight.Bold)
                FinanceValueRow("Deuda en facturas", formatPesos(current.aging.invoicesTotal))
                FinanceValueRow("Saldo no asociado a facturas", formatPesos(current.aging.unallocatedReceivables))
                FinanceValueRow("Total por cobrar (cuentas)", formatPesos(current.aging.totalReceivable))
                FinanceValueRow(
                    "Diferencia de conciliación",
                    formatPesos(current.aging.reconciliationDifference),
                    if (kotlin.math.abs(current.aging.reconciliationDifference) < 0.005) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                )
                if (current.aging.invoiceDetails.isNotEmpty()) {
                    TextButton(onClick = { showInvoiceDetails = !showInvoiceDetails }) {
                        Text(if (showInvoiceDetails) "Ocultar facturas" else "Ver facturas pendientes (${current.aging.invoiceDetails.size})")
                    }
                    if (showInvoiceDetails) {
                        current.aging.invoiceDetails.forEach { invoice ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.background),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(invoice.invoiceNumber, fontWeight = FontWeight.Bold)
                                        Text(formatPesos(invoice.outstandingAmount), fontWeight = FontWeight.Bold, color = BSPOSTheme.colors.primary)
                                    }
                                    Text(invoice.customerName, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "Vence ${invoice.referenceDate} · ${invoice.overdueDays} días · ${invoice.agingBucket} días",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = BSPOSTheme.colors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Valor de Inventario al Costo FIFO
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Valor de inventario (FIFO)", fontWeight = FontWeight.Bold)
                        Text(
                            "${current.activeProductsCount} productos activos",
                            style = MaterialTheme.typography.bodySmall,
                            color = BSPOSTheme.colors.textSecondary
                        )
                    }
                    Text(
                        text = formatPesos(current.inventoryCostValue),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = BSPOSTheme.colors.primary,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                HorizontalDivider()

                // Estado de Caja
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (current.hasOpenCashSession) Color(0xFF2E7D32) else Color(0xFF757575))
                        )
                        Text(
                            if (current.hasOpenCashSession) "Caja abierta" else "Caja cerrada",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (onNavigateToCash != null) {
                        TextButton(onClick = onNavigateToCash) {
                            Text("Ir a Caja", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Bloque 3: Requiere tu Atención (Alertas)
        if (summary.requiresAttention.isNotEmpty()) {
            Text(
                text = "Requiere tu atención",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                summary.requiresAttention.forEach { alert ->
                    FinanceAlertRow(alert)
                }
            }
        }

        // Bloque 4: Top Productos Más Rentables
        if (summary.profitability.isNotEmpty()) {
            Text(
                text = "Top productos más rentables",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    summary.profitability.take(5).forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${index + 1}. ${item.productName}",
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${item.units} vendidas · Ingresos: ${formatPesos(item.revenue)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BSPOSTheme.colors.textSecondary
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = item.grossProfit?.let { "+${formatPesos(it)}" } ?: "N/D",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF2E7D32),
                                    maxLines = 1,
                                    softWrap = false
                                )
                                item.marginPercent?.let {
                                    Text(
                                        text = "%.1f%% margen".format(it),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = BSPOSTheme.colors.textSecondary,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                        if (index < 4 && index < summary.profitability.size - 1) {
                            HorizontalDivider()
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun IncomeStatementTab(
    incomeStatement: FinanceIncomeStatementDto?,
    period: FinancePeriodDto?
) {
    if (incomeStatement == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No se cuenta con permisos para ver el Estado de Resultados.", color = BSPOSTheme.colors.textSecondary)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Estado de Resultados (P&L)",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )

                HorizontalDivider()

                PAndLRow("Ventas Brutas", period?.grossSales?.let { formatPesos(it) } ?: "0.00", isHeader = false)
                PAndLRow("(-) Descuentos", period?.discounts?.let { "- ${formatPesos(it)}" } ?: "0.00", isHeader = false, textColor = Color(0xFFD32F2F))
                PAndLRow("(-) Devoluciones", period?.returns?.let { "- ${formatPesos(it)}" } ?: "0.00", isHeader = false, textColor = Color(0xFFD32F2F))

                HorizontalDivider()

                PAndLRow("(=) Ventas Netas", formatPesos(incomeStatement.netSales), isHeader = true)
                PAndLRow("(-) Costo de Ventas (FIFO)", incomeStatement.fifoCogs?.let { "- ${formatPesos(it)}" } ?: "N/D", isHeader = false, textColor = Color(0xFFD32F2F))

                HorizontalDivider()

                PAndLRow(
                    "(=) Ganancia Bruta",
                    incomeStatement.grossProfit?.let { formatPesos(it) } ?: "N/D",
                    isHeader = true,
                    textColor = Color(0xFF2E7D32),
                    badge = incomeStatement.grossMarginPercent?.let { "%.1f%%".format(it) }
                )

                HorizontalDivider()

                PAndLRow("(-) Gastos Operativos", "- ${formatPesos(incomeStatement.operatingExpensesTotal)}", isHeader = false, textColor = Color(0xFFD32F2F))
                PAndLRow("(-) Comisiones Vendedores", "- ${formatPesos(incomeStatement.commissions)}", isHeader = false, textColor = Color(0xFFD32F2F))

                HorizontalDivider()

                PAndLRow(
                    "(=) Ganancia Operativa",
                    incomeStatement.operatingProfit?.let { formatPesos(it) } ?: "N/D",
                    isHeader = true,
                    textColor = if ((incomeStatement.operatingProfit ?: 0.0) >= 0) Color(0xFF1565C0) else Color(0xFFD32F2F),
                    badge = incomeStatement.operatingMarginPercent?.let { "%.1f%%".format(it) }
                )
            }
        }
    }
}

@Composable
private fun CashFlowTab(
    cashFlow: FinanceCashFlowDto?
) {
    if (cashFlow == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No hay datos de flujo de efectivo disponibles.", color = BSPOSTheme.colors.textSecondary)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Entradas de dinero
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Entradas de Dinero (Cobros Reales)", fontWeight = FontWeight.Bold)
                    Text(
                        formatPesos(cashFlow.inflows.total),
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF2E7D32),
                        maxLines = 1,
                        softWrap = false
                    )
                }

                HorizontalDivider()

                CashFlowItem("Ventas en Efectivo", formatPesos(cashFlow.inflows.salesCash))
                CashFlowItem("Ventas con Tarjeta", formatPesos(cashFlow.inflows.salesCard))
                CashFlowItem("Ventas por Transferencia", formatPesos(cashFlow.inflows.salesTransfer))
                CashFlowItem("Otros métodos de venta", formatPesos(cashFlow.inflows.salesOther))
                CashFlowItem("Cobros de deudas (Cartera)", formatPesos(cashFlow.inflows.debtCollections))
            }
        }

        // Salidas de dinero
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Salidas de Dinero (Pagos Reales)", fontWeight = FontWeight.Bold)
                    Text(
                        formatPesos(cashFlow.outflows.total),
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFD32F2F),
                        maxLines = 1,
                        softWrap = false
                    )
                }

                HorizontalDivider()

                CashFlowItem("Gastos Operativos Pagados", formatPesos(cashFlow.outflows.expensesPaid))
                CashFlowItem("Salidas / Retiros de Caja", formatPesos(cashFlow.outflows.cashOut))
            }
        }

        // Resultado Neto
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.secondaryNavy),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Flujo Neto de Efectivo", color = BSPOSTheme.colors.primary, fontWeight = FontWeight.Bold)
                    Text("Dinero real disponible generado", color = BSPOSTheme.colors.textOnNavy, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    text = formatPesos(cashFlow.netCashFlow),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (cashFlow.netCashFlow >= 0) BSPOSTheme.colors.success else BSPOSTheme.colors.error,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun ExpensesTab(
    expenses: List<ExpenseDto>,
    onAddExpense: () -> Unit
) {
    if (expenses.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(56.dp), tint = BSPOSTheme.colors.textSecondary)
                Text("No hay gastos registrados en este período.", color = BSPOSTheme.colors.textSecondary, textAlign = TextAlign.Center)
                Button(onClick = onAddExpense, colors = ButtonDefaults.buttonColors(containerColor = BSPOSTheme.colors.primary)) {
                    Text("Registrar Primer Gasto")
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(expenses) { expense ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = expense.description,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            expense.categoryName?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BSPOSTheme.colors.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = "· ${expense.occurredAt?.take(10) ?: ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = BSPOSTheme.colors.textSecondary
                            )
                        }
                    }
                    Text(
                        text = "- ${formatPesos(expense.amount)}",
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFD32F2F),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun FinanceKpiCard(
    title: String,
    amount: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = BSPOSTheme.colors.textSecondary,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = amount,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor,
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = BSPOSTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FinanceValueRow(label: String, value: String, color: Color = BSPOSTheme.colors.textPrimary) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = BSPOSTheme.colors.textSecondary)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun RemoteCashTab(
    cash: CashCurrentSessionResponseDto?,
    busy: Boolean,
    onOpen: (String, String?) -> Unit,
    onClose: (String, String?) -> Unit,
    onMovement: (String, String, String) -> Unit
) {
    var openingAmount by remember { mutableStateOf("") }
    var openingNotes by remember { mutableStateOf("") }
    var countedAmount by remember { mutableStateOf("") }
    var closingNotes by remember { mutableStateOf("") }
    var movementAmount by remember { mutableStateOf("") }
    var movementNotes by remember { mutableStateOf("") }
    var movementType by remember { mutableStateOf("cash_in") }
    val session = cash?.session

    LaunchedEffect(session?.id, session?.summary?.expectedClosingAmount) {
        session?.summary?.expectedClosingAmount?.let { countedAmount = String.format(Locale.US, "%.2f", it) }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Caja de MiCatalogo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Esta caja se sincroniza con el panel web. La caja local del POS sigue disponible para ventas sin conexión.",
            style = MaterialTheme.typography.bodySmall, color = BSPOSTheme.colors.textSecondary)

        if (session == null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("No hay una sesión de caja abierta", fontWeight = FontWeight.Bold)
                    OutlinedTextField(openingAmount, { openingAmount = it }, Modifier.fillMaxWidth(),
                        label = { Text("Fondo inicial") }, prefix = { Text("RD$ ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    OutlinedTextField(openingNotes, { openingNotes = it }, Modifier.fillMaxWidth(),
                        label = { Text("Nota (opcional)") }, singleLine = true)
                    Button(onClick = { onOpen(openingAmount, openingNotes) }, enabled = !busy && openingAmount.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                        Text(if (busy) "Procesando…" else "Abrir caja")
                    }
                }
            }
        } else {
            val summary = session.summary
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Caja abierta", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                    Text("Desde ${session.openedAt}", style = MaterialTheme.typography.bodySmall, color = BSPOSTheme.colors.textSecondary)
                    HorizontalDivider()
                    FinanceValueRow("Fondo inicial", formatPesos(summary?.openingAmount ?: session.openingAmount))
                    FinanceValueRow("Ventas en efectivo", formatPesos(summary?.salesCash ?: 0.0))
                    FinanceValueRow("Cobros de deuda en efectivo", formatPesos(summary?.debtCollectionsCash ?: 0.0))
                    FinanceValueRow("Entradas de efectivo", formatPesos(summary?.cashIn ?: 0.0))
                    FinanceValueRow("Aportes del propietario", formatPesos(summary?.ownerContribution ?: 0.0))
                    FinanceValueRow("Salidas de efectivo", formatPesos(summary?.cashOut ?: 0.0))
                    FinanceValueRow("Retiros del propietario", formatPesos(summary?.ownerWithdrawal ?: 0.0))
                    HorizontalDivider()
                    FinanceValueRow("Esperado en caja", formatPesos(summary?.expectedClosingAmount ?: 0.0), BSPOSTheme.colors.primary)
                    FinanceValueRow("Movimientos", (summary?.movementsCount ?: 0).toString())
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Registrar movimiento", fontWeight = FontWeight.Bold)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("cash_in" to "Entrada", "cash_out" to "Salida", "owner_contribution" to "Aporte", "owner_withdrawal" to "Retiro", "adjustment" to "Ajuste").forEach { (type, label) ->
                            FilterChip(selected = movementType == type, onClick = { movementType = type }, label = { Text(label) })
                        }
                    }
                    OutlinedTextField(movementAmount, { movementAmount = it }, Modifier.fillMaxWidth(),
                        label = { Text("Monto") }, prefix = { Text("RD$ ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    OutlinedTextField(movementNotes, { movementNotes = it }, Modifier.fillMaxWidth(),
                        label = { Text("Motivo (requerido)") }, singleLine = true)
                    OutlinedButton(onClick = { onMovement(movementType, movementAmount, movementNotes) },
                        enabled = !busy && movementAmount.isNotBlank() && movementNotes.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                        Text("Guardar movimiento")
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Arqueo y cierre", fontWeight = FontWeight.Bold)
                    OutlinedTextField(countedAmount, { countedAmount = it }, Modifier.fillMaxWidth(),
                        label = { Text("Efectivo contado") }, prefix = { Text("RD$ ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    OutlinedTextField(closingNotes, { closingNotes = it }, Modifier.fillMaxWidth(),
                        label = { Text("Nota de cierre (opcional)") }, singleLine = true)
                    Button(onClick = { onClose(countedAmount, closingNotes) },
                        enabled = !busy && countedAmount.isNotBlank(), modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))) {
                        Text(if (busy) "Procesando…" else "Cerrar caja")
                    }
                }
            }
        }
    }
}

@Composable
private fun AgingPill(label: String, amount: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = BSPOSTheme.colors.textSecondary)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = amount,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun FinanceAlertRow(alert: FinanceAlertDto) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (alert.severity == "warning") Color(0xFFFFF3E0) else Color(0xFFEDE7F6)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = if (alert.severity == "warning") Color(0xFFE65100) else BSPOSTheme.colors.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alert.label,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Text(
                    text = alert.actionLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = BSPOSTheme.colors.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun PAndLRow(
    concept: String,
    amount: String,
    isHeader: Boolean = false,
    textColor: Color = Color.Unspecified,
    badge: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = concept,
                fontWeight = if (isHeader) FontWeight.ExtraBold else FontWeight.Medium,
                style = if (isHeader) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium
            )
            badge?.let {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BSPOSTheme.colors.primaryLight,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = BSPOSTheme.colors.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
        Text(
            text = amount,
            fontWeight = if (isHeader) FontWeight.ExtraBold else FontWeight.Bold,
            style = if (isHeader) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = if (textColor != Color.Unspecified) textColor else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun CashFlowItem(concept: String, amount: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(concept, style = MaterialTheme.typography.bodyMedium)
        Text(
            amount,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun ExpenseCreateDialog(
    categories: List<ExpenseCategoryDto>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int?, String, String?) -> Unit
) {
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<Int?>(categories.firstOrNull()?.id) }
    var paymentMethod by remember { mutableStateOf("cash") }
    var notes by remember { mutableStateOf("") }
    var amountError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar Gasto Operativo", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción del gasto *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        amount = it
                        amountError = false
                    },
                    label = { Text("Monto (${LocalCurrency.current.symbol}) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = amountError,
                    supportingText = {
                        if (amountError) Text("Indica un monto válido mayor a 0")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (categories.isNotEmpty()) {
                    Text("Categoría:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.forEach { cat ->
                            FilterChip(
                                selected = selectedCategoryId == cat.id,
                                onClick = { selectedCategoryId = cat.id },
                                label = { Text(cat.name) }
                            )
                        }
                    }
                }

                Text("Método de pago:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val methods = listOf("cash" to "Efectivo", "card" to "Tarjeta", "bank_transfer" to "Transferencia")
                    methods.forEach { (key, label) ->
                        FilterChip(
                            selected = paymentMethod == key,
                            onClick = { paymentMethod = key },
                            label = { Text(label) }
                        )
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas / Referencia") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanAmount = amount.trim().replace(',', '.')
                    val parsed = cleanAmount.toDoubleOrNull()
                    if (description.isBlank() || parsed == null || parsed <= 0.0) {
                        amountError = true
                    } else {
                        onConfirm(description, cleanAmount, selectedCategoryId, paymentMethod, notes)
                    }
                },
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                } else {
                    Text("Guardar Gasto")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun formatPesos(amount: Double): String {
    val currency = LocalCurrency.current
    val cents = MoneyUtils.pesosToCents(amount)
    return MoneyUtils.formatCents(cents, currency)
}
