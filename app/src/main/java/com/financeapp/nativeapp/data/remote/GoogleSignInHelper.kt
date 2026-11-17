package com.financeapp.nativeapp.data.remote

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * O Web Client ID e gerado pelo plugin Google Services SOMENTE depois que
 * Google e SHA-1/SHA-256 forem configurados no Firebase e o JSON for atualizado.
 * Nao armazenar tokens ou IDs de cliente manualmente no codigo.
 */
object GoogleSignInHelper {
    suspend fun requestIdToken(context: Context): String {
        val activity = findActivity(context)
            ?: error("Nao foi possivel abrir o seletor Google nesta tela.")
        @Suppress("DiscouragedApi")
        val resourceId = context.resources.getIdentifier(
            "default_web_client_id", "string", context.packageName
        )
        require(resourceId != 0) {
            "Configure o provedor Google e a assinatura SHA no Firebase; depois baixe o novo google-services.json."
        }
        val webClientId = context.getString(resourceId)
        require(webClientId.isNotBlank()) { "ID OAuth Google ausente na configuracao Firebase." }
        // O usuario acionou explicitamente "Continuar com Google".
        // Para botoes, o Android recomenda GetSignInWithGoogleOption,
        // nao a opcao de bottom-sheet usada para login automatico.
        val googleOption = GetSignInWithGoogleOption.Builder(webClientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleOption)
            .build()
        val result = CredentialManager.create(activity).getCredential(
            context = activity,
            request = request
        )
        val credential = result.credential as? CustomCredential
            ?: error("A conta escolhida nao retornou uma credencial Google valida.")
        require(credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "O provedor retornou um tipo de credencial inesperado."
        }
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }

    private fun findActivity(context: Context): Activity? {
        var current: Context = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            val next = current.baseContext
            if (next === current) break
            current = next
        }
        return current as? Activity
    }
}
