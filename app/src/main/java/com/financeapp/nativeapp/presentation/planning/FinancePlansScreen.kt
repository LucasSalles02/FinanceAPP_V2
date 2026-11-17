package com.financeapp.nativeapp.presentation.planning

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.financeapp.nativeapp.data.local.entity.FinancePlanEntity
import com.financeapp.nativeapp.planning.DueEntry
import com.financeapp.nativeapp.planning.FinancialPlanRepository
import com.financeapp.nativeapp.planning.FinancialPlans
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private enum class PlanFilter(val label: String) {
    ALL("Todas"), INSTALLMENTS("Parcelas"), MONTHLY("Mensais"), UNPAID("Não pagas"), PAID("Pagas")
}

@Composable
fun FinancePlansScreen(repository: FinancialPlanRepository) {
    val plans by repository.plans().collectAsState(initial = emptyList())
    val payments by repository.payments().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(PlanFilter.ALL) }
    var pendingDelete by remember { mutableStateOf<FinancePlanEntity?>(null) }
    var feedback by remember { mutableStateOf("") }
    var busyId by remember { mutableStateOf("") }
    val currency = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")) }
    val today = System.currentTimeMillis()
    val paymentByPlan = payments.groupBy { it.planId }
    val entries = plans.flatMap { plan ->
        FinancialPlans.occurrences(plan, paymentByPlan[plan.id].orEmpty().associateBy { it.sequence }, today)
    }.filter { entry ->
        when (selected) {
            PlanFilter.ALL -> true
            PlanFilter.INSTALLMENTS -> entry.plan.mode == "INSTALLMENTS"
            PlanFilter.MONTHLY -> entry.plan.mode == "MONTHLY"
            PlanFilter.UNPAID -> entry.payment == null
            PlanFilter.PAID -> entry.payment != null
        }
    }.sortedWith(compareBy<DueEntry> { it.payment != null }.thenBy { it.dueAt })

    pendingDelete?.let { plan ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Excluir programação?") },
            text = { Text("Excluir '${plan.title}' removerá também os lançamentos vinculados já pagos ou recebidos, atualizando o extrato e o saldo. Não é possível desfazer.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        val r = repository.deletePlan(plan.id)
                        feedback = if (r.isSuccess) "Programação excluída." else (r.exceptionOrNull()?.message ?: "Erro ao excluir.")
                    }
                }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Voltar") } }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Payments, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Contas e parcelas", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Contas programadas não alteram o saldo até você marcar como pago ou recebido. Cadastre novas contas em Nova Transação.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlanFilter.entries.forEach { choice ->
                    FilterChip(selected = selected == choice, onClick = { selected = choice }, label = { Text(choice.label) })
                }
            }
            if (feedback.isNotEmpty()) {
                Text(feedback, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (entries.isEmpty()) {
            item {
                Card { Text("Nenhuma conta nesta categoria.", Modifier.padding(18.dp)) }
            }
        } else {
            items(entries, key = { "${it.plan.id}_${it.sequence}" }) { due ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text(due.plan.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                                val mode = if (due.plan.mode == "INSTALLMENTS")
                                    "Parcela ${due.sequence + 1}/${due.plan.installmentCount}" else "Mensal · ${if (due.plan.type == "DESPESA") "Despesa" else "Receita"}"
                                Text(mode, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(currency.format(due.amount), fontWeight = FontWeight.Bold)
                        }
                        val endOfDay = Calendar.getInstance().apply {
                            timeInMillis = due.dueAt
                            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
                            set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
                        }.timeInMillis
                        val desc = when {
                            due.payment != null -> "${if (due.plan.type == "DESPESA") "Pago" else "Recebido"} em ${dateFormat.format(due.payment.paidAt)}"
                            endOfDay < today -> "Vencido · ${dateFormat.format(due.dueAt)}"
                            dateFormat.format(today) == dateFormat.format(due.dueAt) -> "Vence hoje"
                            else -> "Vencimento: ${dateFormat.format(due.dueAt)}"
                        }
                        Text(
                            if (due.payment != null) "Situação: ${if (due.plan.type == "DESPESA") "Pago" else "Recebido"}"
                            else "Situação: ${if (due.plan.type == "DESPESA") "Não pago" else "Não recebido"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (due.payment == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        Text(desc, style = MaterialTheme.typography.bodySmall,
                            color = if (due.payment == null && endOfDay < today) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val key = "${due.plan.id}_${due.sequence}"
                            Button(
                                modifier = Modifier.weight(1f),
                                enabled = busyId != key,
                                onClick = {
                                    busyId = key
                                    scope.launch {
                                        val result = repository.setPaid(due, due.payment == null)
                                        feedback = if (result.isSuccess) "Situação atualizada." else (result.exceptionOrNull()?.message ?: "Erro ao atualizar.")
                                        busyId = ""
                                    }
                                }
                            ) {
                                Text(
                                    if (due.payment != null) "Marcar não ${if (due.plan.type == "DESPESA") "pago" else "recebido"}"
                                    else if (due.plan.type == "DESPESA") "Marcar pago" else "Marcar recebido"
                                )
                            }
                            if (paymentByPlan[due.plan.id].isNullOrEmpty()) {
                                IconButton(onClick = { pendingDelete = due.plan }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Excluir programação")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
