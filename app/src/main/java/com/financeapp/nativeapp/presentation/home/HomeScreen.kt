package com.financeapp.nativeapp.presentation.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.financeapp.nativeapp.presentation.components.*
import com.financeapp.nativeapp.planning.FinancialPlanRepository
import com.financeapp.nativeapp.data.local.entity.FinancePlanEntity
import com.financeapp.nativeapp.domain.model.Transaction
import com.financeapp.nativeapp.presentation.theme.*
import kotlinx.coroutines.flow.collect
import java.text.NumberFormat
import java.util.*

private sealed class LedgerRow(val sortedAt: Long) {
    class TransactionRow(val transaction: Transaction) : LedgerRow(transaction.date)
    class PlannedRow(val plan: FinancePlanEntity) : LedgerRow(plan.createdAt)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    financialPlans: FinancialPlanRepository,
    isOnline: Boolean,
    onNavigateToAdd: () -> Unit,
    userName: String = "Usuário",
    profilePhoto: ImageBitmap? = null,
    onNavigateToProfile: () -> Unit = {}
) {
    val transactions by viewModel.transactions.collectAsState()
    val planned by financialPlans.plans().collectAsState(initial = emptyList())
    val payments by financialPlans.payments().collectAsState(initial = emptyList())
    var selectedPlanId by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val paymentMap = payments.groupBy { it.planId }
    val linkedTransactionIds = payments.map { it.transactionId }.toSet()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.paymentErrors.collect { snackbarHostState.showSnackbar(it) }
    }
    // Uma única lista na aba Extrato: transações e contas programadas, sem aba separada.
    val ledger = (transactions.map { LedgerRow.TransactionRow(it) } +
        planned.map { LedgerRow.PlannedRow(it) }).sortedByDescending { it.sortedAt }
    val pendingCount by viewModel.pendingSyncCount.collectAsState()
    val totalReceitas by viewModel.totalReceitas.collectAsState()
    val totalDespesas by viewModel.totalDespesas.collectAsState()
    val saldo by viewModel.saldo.collectAsState()
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()
    val selectedTransaction by viewModel.selectedTransaction.collectAsState()
    val showEditDialog by viewModel.showEditDialog.collectAsState()
    val showDeleteDialog by viewModel.showDeleteDialog.collectAsState()

    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAdd,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nova Transação")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // ═══════════════════════════════════════════════════════
            // HEADER — Saudação
            // ═══════════════════════════════════════════════════════
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Olá, ${userName.substringBefore(' ')} 👋",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Suas finanças",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // A foto do Perfil é compartilhada com a tela inicial.
                    // O conjunto inteiro (avatar + seta) abre a aba Perfil.
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                            .clickable(onClickLabel = "Abrir perfil", onClick = onNavigateToProfile)
                            .padding(start = 4.dp, end = 5.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(GradientStart, GradientEnd))),
                            contentAlignment = Alignment.Center
                        ) {
                            if (profilePhoto != null) {
                                Image(
                                    bitmap = profilePhoto,
                                    contentDescription = "Foto de perfil",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = userName.take(1).uppercase(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // ═══════════════════════════════════════════════════════
            // BANNER DE SINCRONIZAÇÃO
            // ═══════════════════════════════════════════════════════
            item {
                SyncStatusBanner(
                    pendingCount = pendingCount,
                    isOnline = isOnline
                )
            }

            // ═══════════════════════════════════════════════════════
            // CARDS RECEITA / DESPESA
            // ═══════════════════════════════════════════════════════
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Card de Receita
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            if (MaterialTheme.colorScheme.background == Color.White) Color(0xFFDCF5E7) else Color(0xFF21382C)
                                        ),
                                        start = Offset.Zero,
                                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                                    )
                                )
                                .padding(16.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.TrendingUp,
                                        contentDescription = null,
                                        tint = if (MaterialTheme.colorScheme.background == Color.White) MaterialTheme.colorScheme.onSurface else Color.White.copy(alpha = 0.9f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Receitas",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (MaterialTheme.colorScheme.background == Color.White) MaterialTheme.colorScheme.onSurface else Color.White.copy(alpha = 0.9f),
                                        letterSpacing = 0.5.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = currencyFormat.format(totalReceitas),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (MaterialTheme.colorScheme.background == Color.White) MaterialTheme.colorScheme.onSurface else Color.White
                                )
                            }
                        }
                    }

                    // Card de Despesa
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            if (MaterialTheme.colorScheme.background == Color.White) Color(0xFFFFDDE2) else Color(0xFF3A1720)
                                        ),
                                        start = Offset.Zero,
                                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                                    )
                                )
                                .padding(16.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.TrendingDown,
                                        contentDescription = null,
                                        tint = if (MaterialTheme.colorScheme.background == Color.White) MaterialTheme.colorScheme.onSurface else Color.White.copy(alpha = 0.9f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Despesas",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (MaterialTheme.colorScheme.background == Color.White) MaterialTheme.colorScheme.onSurface else Color.White.copy(alpha = 0.9f),
                                        letterSpacing = 0.5.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = currencyFormat.format(totalDespesas),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (MaterialTheme.colorScheme.background == Color.White) MaterialTheme.colorScheme.onSurface else Color.White
                                )
                            }
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════════════════
            // CARD DE SALDO — Gradiente Premium
            // ═══════════════════════════════════════════════════════
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(GradientStart, GradientEnd),
                                    start = Offset.Zero,
                                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                                )
                            )
                            .padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "SALDO TOTAL",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.85f),
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = currencyFormat.format(saldo),
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "💰 Balanço geral de todas as transações",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════════════════
            // SEÇÃO HEADER + FILTROS
            // ═══════════════════════════════════════════════════════
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Transações",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${ledger.size} itens",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Filtros de período
            item {
                LazyRow(
                    modifier = Modifier.padding(vertical = 8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(Period.entries.toList()) { period ->
                        FilterChip(
                            selected = selectedPeriod == period,
                            onClick = { viewModel.selectPeriod(period) },
                            label = {
                                Text(
                                    text = period.label,
                                    fontWeight = if (selectedPeriod == period) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            shape = RoundedCornerShape(20.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = Color.Transparent,
                                selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                enabled = true,
                                selected = selectedPeriod == period
                            )
                        )
                    }
                }
            }

            // ═══════════════════════════════════════════════════════
            // LISTA DE TRANSAÇÕES ou ESTADO VAZIO
            // ═══════════════════════════════════════════════════════
            if (ledger.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 40.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Nenhuma transação encontrada",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Comece adicionando sua primeira receita ou despesa!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(
                    items = ledger,
                    key = {
                        when (it) {
                            is LedgerRow.TransactionRow -> "tx_${it.transaction.id}"
                            is LedgerRow.PlannedRow -> "schedule_${it.plan.id}"
                        }
                    }
                ) { row ->
                    when (row) {
                        is LedgerRow.TransactionRow -> TransactionCard(
                            transaction = row.transaction,
                            onClick = { viewModel.onTransactionClick(row.transaction) },
                            onDelete = { viewModel.onTransactionLongClick(row.transaction) },
                            // Parcelas já são pagas/desfeitas em seu painel próprio.
                            onTogglePaid = if (row.transaction.type == com.financeapp.nativeapp.domain.model.TransactionType.DESPESA &&
                                row.transaction.id !in linkedTransactionIds &&
                                !row.transaction.id.startsWith("plan_")) {
                                { viewModel.toggleExpensePaid(row.transaction) }
                            } else null
                        )
                        is LedgerRow.PlannedRow -> PlannedTransactionCard(
                            plan = row.plan,
                            payments = paymentMap[row.plan.id].orEmpty(),
                            onOpen = { selectedPlanId = row.plan.id }
                        )
                    }
                }
            }
        }
    }

    selectedPlanId?.let { id ->
        planned.firstOrNull { it.id == id }?.let { item ->
            PlannedTransactionDetails(
                plan = item,
                payments = paymentMap[item.id].orEmpty(),
                repository = financialPlans,
                onDismiss = { selectedPlanId = null }
            )
        }
    }

    // ═══════════════════════════════════════════════════════════
    // DIALOGS
    // ═══════════════════════════════════════════════════════════

    if (showEditDialog && selectedTransaction != null) {
        EditTransactionDialog(
            transaction = selectedTransaction!!,
            onDismiss = { viewModel.dismissEditDialog() },
            onSave = { title, amount, category, type ->
                viewModel.updateTransaction(title, amount, category, type)
            }
        )
    }

    if (showDeleteDialog && selectedTransaction != null) {
        DeleteConfirmationDialog(
            transaction = selectedTransaction!!,
            onDismiss = { viewModel.dismissDeleteDialog() },
            onConfirm = { viewModel.deleteSelectedTransaction() }
        )
    }
}
