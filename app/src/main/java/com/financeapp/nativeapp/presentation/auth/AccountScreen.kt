package com.financeapp.nativeapp.presentation.auth

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.financeapp.nativeapp.data.remote.FinanceAccountManager
import com.financeapp.nativeapp.presentation.theme.*
import kotlinx.coroutines.launch

private enum class AccessMode { LOGIN, REGISTER, RESET }

@Composable
fun AccountScreen(
    account: FinanceAccountManager,
    onConnected: () -> Unit,
    onContinueGuest: () -> Unit,
    onBack: (() -> Unit)? = null,
    startWithRegister: Boolean = false,
    googleBusy: Boolean = false,
    googleMessage: String? = null,
    onGoogleLogin: () -> Unit
) {
    var mode by rememberSaveable(startWithRegister) {
        mutableStateOf(if (startWithRegister) AccessMode.REGISTER else AccessMode.LOGIN)
    }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var revealPassword by rememberSaveable { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                if (MaterialTheme.colorScheme.background == Color.White)
                    listOf(Color.White, Color(0xFFFFF3F5), Color.White)
                else listOf(Color(0xFF100E12), Color(0xFF1A0B10), Color(0xFF09090D))
            )
        )
    ) {
        Box(
            Modifier.fillMaxWidth().height(275.dp).align(Alignment.TopCenter)
                .background(Brush.radialGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = .25f), Color.Transparent)))
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (onBack != null) {
                Row(Modifier.fillMaxWidth()) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            } else Spacer(Modifier.height(12.dp))

            Box(
                Modifier.size(80.dp).clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, PrimaryVariant))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.ShowChart, contentDescription = null, modifier = Modifier.size(45.dp), tint = Color.White)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Finance", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("APP", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Text("Seu controle financeiro, com segurança.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(22.dp))

            Text(
                when (mode) {
                    AccessMode.LOGIN -> "Bem-vindo de volta!"
                    AccessMode.REGISTER -> "Criar conta"
                    AccessMode.RESET -> "Recuperar senha"
                },
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                when (mode) {
                    AccessMode.LOGIN -> "Entre para manter suas finanças conectadas."
                    AccessMode.REGISTER -> "Proteja seus dados e sincronize na nuvem."
                    AccessMode.RESET -> "Enviaremos um link de recuperação para seu e-mail."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))

            if (mode == AccessMode.REGISTER) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(70) },
                    label = { Text("Nome") }, leadingIcon = { Icon(Icons.Default.PersonOutline, null) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)
                )
            }
            OutlinedTextField(
                value = email, onValueChange = { email = it.trim().take(140) },
                label = { Text("E-mail") }, leadingIcon = { Icon(Icons.Default.Email, null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)
            )
            if (mode != AccessMode.RESET) {
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Senha") }, leadingIcon = { Icon(Icons.Default.Lock, null) },
                    visualTransformation = if (revealPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { revealPassword = !revealPassword }) {
                            Icon(if (revealPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Mostrar ou ocultar senha")
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)
                )
            }
            if (mode == AccessMode.REGISTER) {
                OutlinedTextField(
                    value = confirmation, onValueChange = { confirmation = it },
                    label = { Text("Confirmar senha") }, leadingIcon = { Icon(Icons.Default.Lock, null) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)
                )
            }
            if (mode == AccessMode.LOGIN) {
                TextButton(
                    onClick = { account.clearError(); feedback = null; mode = AccessMode.RESET },
                    modifier = Modifier.align(Alignment.End)
                ) { Text("Esqueceu sua senha?") }
            }

            val errorMessage = feedback ?: googleMessage ?: account.error
            if (errorMessage != null) {
                Text(
                    errorMessage,
                    color = when {
                        googleBusy -> MaterialTheme.colorScheme.onSurfaceVariant
                        feedback == "E-mail enviado. Verifique sua caixa de entrada." -> SyncSynced
                        else -> ExpenseRed
                    },
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(4.dp))
            Button(
                enabled = !account.busy && !googleBusy,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp),
                onClick = {
                    if (mode == AccessMode.REGISTER && password != confirmation) {
                        feedback = "As senhas precisam ser iguais."
                    } else {
                        feedback = null
                        scope.launch {
                            val successful = when (mode) {
                                AccessMode.LOGIN -> account.login(email, password)
                                AccessMode.REGISTER -> account.register(name, email, password)
                                AccessMode.RESET -> account.resetPassword(email)
                            }
                            if (successful) {
                                if (mode == AccessMode.RESET) feedback = "E-mail enviado. Verifique sua caixa de entrada."
                                else onConnected()
                            }
                        }
                    }
                }
            ) {
                if (account.busy) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text(when (mode) { AccessMode.LOGIN -> "Entrar"; AccessMode.REGISTER -> "Cadastrar"; AccessMode.RESET -> "Enviar link" }, fontWeight = FontWeight.Bold)
            }

            if (mode != AccessMode.RESET) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                    Text("ou", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                }
                OutlinedButton(
                    enabled = !account.busy && !googleBusy,
                    onClick = {
                        feedback = null
                        // A Activity mantem a operacao viva durante a UI do Google.
                        onGoogleLogin()
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                ) {
                    if (googleBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("G", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp)
                        Spacer(Modifier.width(12.dp))
                        Text("Continuar com Google", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (mode == AccessMode.LOGIN) {
                OutlinedButton(
                    onClick = { mode = AccessMode.REGISTER; account.clearError(); feedback = null },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(18.dp)
                ) { Text("Criar conta") }
                TextButton(enabled = !account.busy, onClick = {
                    scope.launch { if (account.asGuest()) onContinueGuest() }
                }) { Text("Continuar como convidado", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text("Sem internet, o modo convidado salva seus dados somente neste aparelho. Uma conta nova precisa de conexão para cadastrar ou entrar.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall)
            } else {
                TextButton(onClick = { mode = AccessMode.LOGIN; account.clearError(); feedback = null }) {
                    Text("Já tem uma conta? Entrar")
                }
            }
            if (mode == AccessMode.REGISTER) {
                Text(
                    "Se você já usava o app como convidado, seus dados serão mantidos ao criar a conta.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}
