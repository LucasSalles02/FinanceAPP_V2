package com.financeapp.nativeapp.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.financeapp.nativeapp.data.local.entity.FinancePlanEntity
import com.financeapp.nativeapp.data.local.entity.FinancePlanPaymentEntity
import com.financeapp.nativeapp.planning.DueEntry
import com.financeapp.nativeapp.planning.FinancialPlanRepository
import com.financeapp.nativeapp.planning.FinancialPlans
import com.financeapp.nativeapp.presentation.components.CategoryUtils
import com.financeapp.nativeapp.presentation.theme.*
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

private val br = Locale("pt", "BR")
private fun asMoney(value: Double) = NumberFormat.getCurrencyInstance(br).format(value)
private fun asDate(value: Long) = SimpleDateFormat("dd/MM/yyyy", br).format(value)

/** A scheduled item looks like a normal expense/income card; details are deliberately hidden. */
@Composable
fun PlannedTransactionCard(
    plan: FinancePlanEntity,
    payments: List<FinancePlanPaymentEntity>,
    onOpen: () -> Unit
) {
    val category = CategoryUtils.getCategoryInfo(plan.category)
    val expense = plan.type == "DESPESA"
    val accent = if (expense) ExpenseRed else IncomeGreen
    val paid = payments.size
    // A data original da programação não muda: mostrar o último pagamento
    // confirmado evita apresentar sempre o mês da primeira parcela/receita.
    val lastPayment = payments.maxByOrNull { it.paidAt }
    val nextPending = FinancialPlans.occurrences(plan, payments.associateBy { it.sequence })
        .firstOrNull { it.payment == null }
    val subtitleDate = when {
        lastPayment != null -> "${if (expense) "Pago" else "Recebido"} em ${asDate(lastPayment.paidAt)}"
        nextPending != null -> "Vencimento ${asDate(nextPending.dueAt)}"
        else -> "Início ${asDate(plan.startAt)}"
    }
    val label = if (plan.mode == "INSTALLMENTS") {
        "${plan.installmentCount} parcelas · $paid/${plan.installmentCount} pagas"
    } else {
        "Mensal · ${if (expense) "Despesa" else "Receita"}"
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(48.dp).clip(CircleShape)
                    .background(category.color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(category.icon, contentDescription = null, tint = category.color,
                    modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(plan.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface)
                Text("${plan.category} · $subtitleDate", maxLines = 1,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$label  ›", maxLines = 1, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text("${if (expense) "−" else "+"} ${asMoney(plan.totalAmount)}",
                    style = MaterialTheme.typography.titleSmall, color = accent,
                    fontWeight = FontWeight.Bold)
                Text("previsto", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Compact installment detail sheet: no new navigation tab and no duplicated form. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannedTransactionDetails(
    plan: FinancePlanEntity,
    payments: List<FinancePlanPaymentEntity>,
    repository: FinancialPlanRepository,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var workingKey by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    var askDelete by remember { mutableStateOf(false) }
    val paymentMap = payments.associateBy { it.sequence }
    val dueItems = FinancialPlans.occurrences(plan, paymentMap)
    val isInstallments = plan.mode == "INSTALLMENTS"
    val expense = plan.type == "DESPESA"
    val accent = if (expense) ExpenseRed else IncomeGreen
    val itemsToShow = if (isInstallments) dueItems else {
        val now = System.currentTimeMillis()
        val from = java.util.Calendar.getInstance().apply {
            timeInMillis = FinancialPlans.monthAt(now, -2)
            set(java.util.Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis
        val to = java.util.Calendar.getInstance().apply {
            timeInMillis = FinancialPlans.monthAt(now, 3)
            set(java.util.Calendar.DAY_OF_MONTH, getActualMaximum(java.util.Calendar.DAY_OF_MONTH))
        }.timeInMillis
        dueItems.filter { it.dueAt in from..to || it.payment != null || it.sequence == 0 }
    }

    fun updatePaid(entry: DueEntry, markPaid: Boolean) {
        scope.launch {
            workingKey = "${entry.plan.id}_${entry.sequence}"
            val result = repository.setPaid(entry, markPaid)
            message = if (result.isSuccess) "" else
                result.exceptionOrNull()?.message ?: "Não foi possível atualizar a parcela."
            workingKey = null
        }
    }

    if (askDelete) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            title = { Text("Excluir programação?") },
            text = { Text("Esta ação excluirá a programação e todos os lançamentos vinculados a ela, inclusive parcelas pagas ou receitas recebidas. Os valores serão retirados do extrato e do saldo. Não é possível desfazer.") },
            confirmButton = {
                TextButton(onClick = {
                    askDelete = false
                    scope.launch {
                        workingKey = "delete"
                        val result = repository.deletePlan(plan.id)
                        workingKey = null
                        if (result.isSuccess) onDismiss()
                        else message = result.exceptionOrNull()?.message ?: "Não foi possível excluir."
                    }
                }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { askDelete = false }) { Text("Voltar") } }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(plan.title, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        if (isInstallments) "${plan.installmentCount} parcelas · ${payments.size} pagas"
                        else "Repetição mensal · ${if (expense) "Despesa" else "Receita"}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
                    )
                }
                IconButton(onClick = { askDelete = true }, enabled = workingKey == null) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Excluir programação",
                        tint = MaterialTheme.colorScheme.error)
                }
            }
            if (message.isNotBlank()) {
                Text(message, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 490.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(itemsToShow, key = { it.sequence }) { entry ->
                    val paid = entry.payment != null
                    val paidLabel = if (expense) "Pago" else "Recebido"
                    val unpaidLabel = if (expense) "Não pago" else "Não recebido"
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape)
                                .background((if (paid) IncomeGreen else MaterialTheme.colorScheme.primary).copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(if (isInstallments) Icons.Default.ReceiptLong else Icons.Default.EventRepeat,
                                contentDescription = null,
                                tint = if (paid) IncomeGreen else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(19.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            val competence = SimpleDateFormat("MM/yyyy", br).format(entry.dueAt)
                            Text(if (isInstallments) "Parcela ${entry.sequence + 1}/${plan.installmentCount} · $competence"
                                else "Mensal · $competence",
                                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                            val detail = if (paid) {
                                "${asMoney(entry.amount)} · $paidLabel em ${asDate(entry.payment!!.paidAt)}"
                            } else {
                                "${asMoney(entry.amount)} · $unpaidLabel · Vence ${asDate(entry.dueAt)}"
                            }
                            Text(detail, style = MaterialTheme.typography.bodySmall,
                                color = if (paid) IncomeGreen else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (paid) {
                            OutlinedButton(
                                enabled = workingKey == null,
                                onClick = { updatePaid(entry, false) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) { Text("Desfazer", style = MaterialTheme.typography.labelSmall) }
                        } else {
                            Button(
                                enabled = workingKey == null,
                                onClick = { updatePaid(entry, true) },
                                colors = ButtonDefaults.buttonColors(containerColor = accent),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) { Text(if (expense) "Pagar" else "Receber",
                                style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                    if (entry != itemsToShow.lastOrNull()) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                }
            }
        }
    }
}
