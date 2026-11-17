package com.financeapp.nativeapp

import android.app.Application
import com.financeapp.nativeapp.reminders.ReminderScheduler
import com.financeapp.nativeapp.reminders.ReminderSession
import com.google.firebase.auth.FirebaseAuth

class FinanceApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ReminderScheduler.createChannel(this)
        // Ao receber um alarme com a Activity fechada, use a identidade da sessão
        // restaurada em disco. A primeira abertura após V12 também a preenche.
        FirebaseAuth.getInstance().currentUser?.uid?.let { ReminderSession.update(this, it) }
        ReminderScheduler.restorePending(this)
        // Ponto de entrada da aplicação.
        // A inicialização do banco de dados (Room), WorkManager e o monitor de conectividade
        // será integrada aqui nas próximas etapas (Injeção de dependências ou inicialização manual).
    }
}
