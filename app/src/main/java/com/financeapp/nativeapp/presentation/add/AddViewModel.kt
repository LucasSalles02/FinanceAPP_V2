package com.financeapp.nativeapp.presentation.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.financeapp.nativeapp.domain.model.SyncStatus
import com.financeapp.nativeapp.domain.model.Transaction
import com.financeapp.nativeapp.domain.model.TransactionType
import com.financeapp.nativeapp.domain.repository.TransactionRepository
import com.financeapp.nativeapp.planning.FinancialPlanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class AddTransactionUiState(
    val title: String = "",
    val amount: String = "",
    val type: TransactionType = TransactionType.DESPESA,
    val category: String = "",
    val date: Long = System.currentTimeMillis(),
    val repeatMonthly: Boolean = false,
    val installmentsEnabled: Boolean = false,
    val installmentCountInput: String = "2",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false,
    val savedPlan: Boolean = false
) {
    val dateFormatted: String
        get() = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(date))
}

class AddViewModel(
    private val repository: TransactionRepository,
    private val plans: FinancialPlanRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddTransactionUiState())
    val uiState: StateFlow<AddTransactionUiState> = _uiState.asStateFlow()

    fun updateTitle(title: String) {
        _uiState.update { it.copy(title = title, errorMessage = null) }
    }

    fun updateAmount(amount: String) {
        // Validação básica para aceitar apenas números e ponto
        if (amount.isEmpty() || amount.matches(Regex("^\\d*([.,]\\d{0,2})?$"))) {
            _uiState.update { it.copy(amount = amount, errorMessage = null) }
        }
    }

    fun updateType(type: TransactionType) {
        // Parcelamento só se aplica a despesas; as receitas mantêm a opção mensal.
        _uiState.update { it.copy(type = type, category = "", installmentsEnabled = false) }
    }

    fun updateCategory(category: String) {
        _uiState.update { it.copy(category = category, errorMessage = null) }
    }

    fun setMonthly(value: Boolean) {
        _uiState.update { it.copy(repeatMonthly = value, installmentsEnabled = if (value) false else it.installmentsEnabled) }
    }

    fun setInstallments(value: Boolean) {
        _uiState.update { it.copy(installmentsEnabled = value, repeatMonthly = if (value) false else it.repeatMonthly) }
    }

    fun setInstallmentCountInput(value: String) {
        if (value.length <= 2 && value.all { it.isDigit() }) {
            _uiState.update { it.copy(installmentCountInput = value, errorMessage = null) }
        }
    }

    fun updateDate(dateMillis: Long) {
        _uiState.update { it.copy(date = dateMillis) }
    }

    fun saveTransaction() {
        val currentState = _uiState.value
        val amountValue = currentState.amount.replace(',', '.').toDoubleOrNull()
        val installmentCount = currentState.installmentCountInput.toIntOrNull()

        if (currentState.title.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Informe a descrição da transação.") }
            return
        }
        if (amountValue == null || !amountValue.isFinite() || amountValue < 0.01) {
            _uiState.update { it.copy(errorMessage = "Informe um valor válido maior que zero.") }
            return
        }
        if (currentState.category.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Selecione uma categoria.") }
            return
        }

        if (currentState.installmentsEnabled && (installmentCount == null || installmentCount !in 2..60 || currentState.type != TransactionType.DESPESA)) {
            _uiState.update { it.copy(errorMessage = "Escolha de 2 a 60 parcelas para despesas.") }
            return
        }
        if (currentState.installmentsEnabled && amountValue != null &&
            java.math.BigDecimal.valueOf(amountValue).setScale(2, java.math.RoundingMode.HALF_UP)
                .movePointRight(2).longValueExact() < (installmentCount ?: 2)
        ) {
            _uiState.update { it.copy(errorMessage = "O valor total deve permitir pelo menos R$ 0,01 por parcela.") }
            return
        }
        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            if (currentState.repeatMonthly || currentState.installmentsEnabled) {
                val result = plans.add(
                    currentState.title, amountValue, currentState.type,
                    currentState.category, currentState.date,
                    if (currentState.installmentsEnabled) "INSTALLMENTS" else "MONTHLY",
                    installmentCount ?: 2
                )
                _uiState.update {
                    if (result.isSuccess) it.copy(isSaving = false, isSuccess = true, savedPlan = true)
                    else it.copy(isSaving = false, errorMessage = "Erro ao salvar a programação.")
                }
                return@launch
            }
            val transaction = Transaction(
                id = UUID.randomUUID().toString(),
                userId = com.financeapp.nativeapp.data.remote.AccountScope.requireUid(),
                title = currentState.title.trim(),
                amount = amountValue,
                type = currentState.type,
                category = currentState.category.trim(),
                date = currentState.date,
                syncStatus = SyncStatus.PENDENTE
            )

            val result = repository.addTransaction(transaction)
            
            if (result.isSuccess) {
                // A gravação local com status PENDENTE foi um sucesso. O WorkManager fará o resto.
                _uiState.update { it.copy(isSaving = false, isSuccess = true) }
            } else {
                _uiState.update { it.copy(isSaving = false, errorMessage = "Erro ao salvar transação. Tente novamente.") }
            }
        }
    }

    fun resetSuccessState() {
        _uiState.update { it.copy(isSuccess = false) }
    }

    class Factory(private val repository: TransactionRepository, private val plans: FinancialPlanRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AddViewModel::class.java)) {
                return AddViewModel(repository, plans) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
