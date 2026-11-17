package com.financeapp.nativeapp.presentation.add

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.financeapp.nativeapp.domain.model.TransactionType
import com.financeapp.nativeapp.presentation.components.CategoryPickerDialog
import com.financeapp.nativeapp.presentation.components.CategoryUtils
import com.financeapp.nativeapp.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    viewModel: AddViewModel,
    onBack: () -> Unit,
    onPlanSaved: () -> Unit = onBack
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showSuccessSnackbar by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            val wasPlan = uiState.savedPlan
            viewModel.resetSuccessState()
            if (wasPlan) onPlanSaved() else onBack()
        }
    }

    val isExpense = uiState.type == TransactionType.DESPESA
    val accentColor = if (isExpense) ExpenseRed else IncomeGreen

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Nova Transação",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Voltar",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // ═══════════════════════════════════════════════════════
            // SELETOR DE TIPO — Segmented Button Style
            // ═══════════════════════════════════════════════════════
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Botão Despesa
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { viewModel.updateType(TransactionType.DESPESA) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isExpense) ExpenseRedBg else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = if (isExpense) CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(ExpenseRed.copy(alpha = 0.5f))
                    ) else null
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.TrendingDown,
                            contentDescription = null,
                            tint = if (isExpense) ExpenseRed else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Despesa",
                            fontWeight = if (isExpense) FontWeight.Bold else FontWeight.Normal,
                            color = if (isExpense) ExpenseRed else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Botão Receita
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { viewModel.updateType(TransactionType.RECEITA) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (!isExpense) IncomeGreenBg else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = if (!isExpense) CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(IncomeGreen.copy(alpha = 0.5f))
                    ) else null
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.TrendingUp,
                            contentDescription = null,
                            tint = if (!isExpense) IncomeGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Receita",
                            fontWeight = if (!isExpense) FontWeight.Bold else FontWeight.Normal,
                            color = if (!isExpense) IncomeGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ═══════════════════════════════════════════════════════
            // FORMULÁRIO
            // ═══════════════════════════════════════════════════════

            // Erro
            uiState.errorMessage?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = ExpenseRed.copy(alpha = 0.12f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = ExpenseRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            error,
                            color = ExpenseRed,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Descrição
            OutlinedTextField(
                value = uiState.title,
                onValueChange = viewModel::updateTitle,
                label = { Text("Descrição") },
                placeholder = { Text("Ex: Supermercado") },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    cursorColor = accentColor,
                    focusedLabelColor = accentColor
                )
            )

            // Valor
            OutlinedTextField(
                value = uiState.amount,
                onValueChange = viewModel::updateAmount,
                label = { Text("Valor (R\$)") },
                placeholder = { Text("0.00") },
                leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    cursorColor = accentColor,
                    focusedLabelColor = accentColor
                )
            )

            // Seletor de Categoria (Dropdown estilizado)
            val catInfo = if (uiState.category.isNotEmpty()) {
                CategoryUtils.getCategoryInfo(uiState.category)
            } else null

            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showCategoryPicker = true },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = Color.Transparent
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (catInfo != null) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(catInfo.color.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                catInfo.icon,
                                contentDescription = null,
                                tint = catInfo.color,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        Icon(
                            Icons.Default.Category,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Categoria",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            uiState.category.ifEmpty { "Selecione uma categoria" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (uiState.category.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Data
            val dateText = uiState.dateFormatted

            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = Color.Transparent
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (uiState.repeatMonthly || uiState.installmentsEnabled) "Primeiro vencimento" else "Data da transação",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            dateText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // As opções novas usam a mesma linguagem visual das transações existentes.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Frequência", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = !uiState.repeatMonthly && !uiState.installmentsEnabled,
                        onClick = { viewModel.setMonthly(false); viewModel.setInstallments(false) },
                        label = { Text("Uma vez") }
                    )
                    FilterChip(
                        selected = uiState.repeatMonthly,
                        onClick = { viewModel.setMonthly(true) },
                        label = { Text("Todo mês") }
                    )
                    if (isExpense) {
                        FilterChip(
                            selected = uiState.installmentsEnabled,
                            onClick = { viewModel.setInstallments(true) },
                            label = { Text("Parcelar") }
                        )
                    }
                }
                if (uiState.installmentsEnabled && isExpense) {
                    OutlinedTextField(
                        value = uiState.installmentCountInput,
                        onValueChange = viewModel::setInstallmentCountInput,
                        label = { Text("Número de parcelas (2 a 60)") },
                        leadingIcon = { Icon(Icons.Default.ReceiptLong, contentDescription = null) },
                        trailingIcon = { Text("x", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            cursorColor = accentColor,
                            focusedLabelColor = accentColor
                        )
                    )
                    val count = uiState.installmentCountInput.toIntOrNull()
                    val total = uiState.amount.replace(',', '.').toDoubleOrNull()
                    if (count != null && count in 2..60 && total != null && total > 0) {
                        val per = total / count
                        val exampleValue = java.text.NumberFormat.getCurrencyInstance(
                            java.util.Locale("pt", "BR")
                        ).format(per)
                        Text(
                            "${count}x de aproximadamente $exampleValue",
                            style = MaterialTheme.typography.labelMedium, color = accentColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // ═══════════════════════════════════════════════════════
            // BOTÃO SALVAR
            // ═══════════════════════════════════════════════════════
            Button(
                onClick = { viewModel.saveTransaction() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !uiState.isSaving,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor
                )
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Icon(
                        if (isExpense) Icons.Default.RemoveCircleOutline else Icons.Default.AddCircleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (uiState.repeatMonthly || uiState.installmentsEnabled) "Programar conta" else if (isExpense) "Registrar Despesa" else "Registrar Receita",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // ═══════════════════════════════════════════════════════════
    // DIALOGS
    // ═══════════════════════════════════════════════════════════

    if (showCategoryPicker) {
        CategoryPickerDialog(
            isExpense = isExpense,
            currentCategory = uiState.category,
            onSelect = {
                viewModel.updateCategory(it)
                showCategoryPicker = false
            },
            onDismiss = { showCategoryPicker = false }
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = localDateToUtcDay(uiState.date)
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        viewModel.updateDate(utcDayToLocalDate(it))
                    }
                    showDatePicker = false
                }) {
                    Text("OK", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancelar")
                }
            },
            colors = DatePickerDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    selectedDayContainerColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

/** Material DatePicker stores midnight UTC, while the app displays local civil dates. */
private fun localDateToUtcDay(millis: Long): Long {
    val local = java.util.Calendar.getInstance().apply { timeInMillis = millis }
    return java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(java.util.Calendar.YEAR), local.get(java.util.Calendar.MONTH),
            local.get(java.util.Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

private fun utcDayToLocalDate(millis: Long): Long {
    val utc = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = millis
    }
    return java.util.Calendar.getInstance().apply {
        clear()
        set(utc.get(java.util.Calendar.YEAR), utc.get(java.util.Calendar.MONTH),
            utc.get(java.util.Calendar.DAY_OF_MONTH), 12, 0, 0)
    }.timeInMillis
}
