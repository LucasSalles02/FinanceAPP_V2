package com.financeapp.nativeapp.data.remote

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Identidade ativa usada para separar todos os dados locais. Nunca é um identificador fixo. */
object AccountScope {
    private val _uid = MutableStateFlow(FirebaseAuth.getInstance().currentUser?.uid)
    val uid: StateFlow<String?> = _uid
    private var localGuest: String? = null

    /** Identidade local temporária, sempre distinta de um UID real do Firebase. */
    fun activateLocalGuest(uid: String) {
        check(uid.startsWith("local_guest_")) { "Identificador local inválido." }
        check(FirebaseAuth.getInstance().currentUser == null) { "Há uma conta conectada." }
        localGuest = uid
        _uid.value = uid
    }

    fun clearLocalGuest() {
        localGuest = null
        _uid.value = FirebaseAuth.getInstance().currentUser?.uid
    }

    fun onAuthChanged(uid: String?) {
        if (uid != null) localGuest = null
        _uid.value = uid ?: localGuest
    }

    fun requireUid(): String {
        val authenticated = FirebaseAuth.getInstance().currentUser?.uid
        if (authenticated != null) {
            check(_uid.value == authenticated) { "A conta mudou. Abra a tela novamente." }
            return authenticated
        }
        val guest = localGuest ?: error("Entre na conta ou ative o modo offline.")
        check(_uid.value == guest) { "A conta mudou. Abra a tela novamente." }
        return guest
    }
}
