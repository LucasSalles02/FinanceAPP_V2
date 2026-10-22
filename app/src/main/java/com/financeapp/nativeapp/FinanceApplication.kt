package com.financeapp.nativeapp

import android.app.Application

class FinanceApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Ponto de entrada da aplicação.
        // A inicialização do banco de dados (Room), WorkManager e o monitor de conectividade
        // será integrada aqui nas próximas etapas (Injeção de dependências ou inicialização manual).
    }
}
