package com.financeapp.nativeapp.reminders

import android.content.Context
import com.google.firebase.auth.FirebaseAuth

/**
 * A identidade em disco pode ser consultada por um BroadcastReceiver iniciado pelo
 * Android sem nenhuma Activity aberta. O FirebaseAuth.currentUser pode ainda não
 * estar restaurado quando o processo nasce apenas para entregar um alarme.
 *
 * O marcador representa SOMENTE a sessão ativa neste aparelho; nunca o dono
 * original dos registros (que fica em financeapp_account_owner).
 */
object ReminderSession {
    private const val PREFS = "financeapp_reminder_active_session"
    private const val KEY_UID = "active_uid"

    fun update(context: Context, uid: String?) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_UID, uid).commit()
    }

    fun currentUid(context: Context): String? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.contains(KEY_UID)) return prefs.getString(KEY_UID, null)?.takeIf(String::isNotBlank)
        // Migração inicial da V12: ainda não existe marcador em disco.
        return FirebaseAuth.getInstance().currentUser?.uid
    }
}
