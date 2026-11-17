package com.financeapp.nativeapp.data.remote

import com.google.firebase.auth.FirebaseAuth

/** Autentica silenciosamente esta instalacao antes de usar as regras do Firestore. */
internal object FirebaseSession {
    suspend fun currentUid(): String {
        val auth = FirebaseAuth.getInstance()
        return auth.currentUser?.uid
            ?: error("Entre na conta antes de sincronizar com o Firebase")
    }
}
