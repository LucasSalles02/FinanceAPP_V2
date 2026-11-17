package com.financeapp.nativeapp.sync

import android.content.Context

/** Pausa apenas a sincronizacao da nuvem; nao controla o modo aviao do Android. */
object OfflineMode {
    private const val PREFS = "financeapp_sync_preferences"
    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("offline", false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("offline", enabled).apply()
        if (enabled) androidx.work.WorkManager.getInstance(context)
            .cancelUniqueWork(SyncWorker.WORK_NAME)
    }
}
