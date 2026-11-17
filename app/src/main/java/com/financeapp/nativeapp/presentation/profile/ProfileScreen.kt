package com.financeapp.nativeapp.presentation.profile

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import com.financeapp.nativeapp.data.remote.FinanceAccountManager
import com.google.firebase.auth.GoogleAuthProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.financeapp.nativeapp.presentation.home.HomeViewModel
import com.financeapp.nativeapp.presentation.theme.*
import com.google.firebase.auth.FirebaseUser
import java.text.NumberFormat
import java.util.Locale
import com.financeapp.nativeapp.profile.ProfilePhotoStore
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    viewModel: HomeViewModel,
    isOnline: Boolean,
    user: FirebaseUser?,
    localGuestUid: String? = null,
    profilePhoto: ImageBitmap?,
    onPhotoChanged: () -> Unit,
    account: FinanceAccountManager,
    offlineMode: Boolean,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    onPhotoPickerOpening: () -> Unit,
    onPhotoPickerClosed: () -> Unit,
    onOfflineModeChange: (Boolean) -> Unit,
    onCreateAccount: () -> Unit,
    onSync: () -> Unit,
    onLogout: () -> Unit,
    googleBusy: Boolean = false,
    googleMessage: String? = null,
    googleLinkedThisSession: Boolean = false,
    onLinkGoogle: () -> Unit
) {
    var confirmLogout by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val photoScope = rememberCoroutineScope()
    val uid = user?.uid ?: localGuestUid
    var photoSaving by remember { mutableStateOf(false) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        onPhotoPickerClosed()
        val activeUid = uid
        if (uri != null && activeUid != null && !photoSaving) {
            photoSaving = true
            photoScope.launch {
                val saved = ProfilePhotoStore.save(context, activeUid, uri)
                photoSaving = false
                if (saved) onPhotoChanged()
                else Toast.makeText(context, "Não foi possível salvar a foto.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    fun openPhotoPicker() {
        onPhotoPickerOpening()
        try { photoPicker.launch("image/*") }
        catch (_: Exception) {
            onPhotoPickerClosed()
            Toast.makeText(context, "Não foi possível abrir a galeria.", Toast.LENGTH_SHORT).show()
        }
    }
    val googleLinked = googleLinkedThisSession ||
        (user?.providerData?.any { it.providerId == GoogleAuthProvider.PROVIDER_ID } == true)
    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Sair da conta?") },
            text = { Text(if (localGuestUid != null)
                "Os dados deste convidado continuarão salvos neste aparelho, mas não serão transferidos automaticamente para outra conta. Voltar ao login?"
                else "Os dados locais permanecem neste celular e só poderão ser acessados após entrar novamente nesta conta.") },
            confirmButton = { TextButton(onClick = { confirmLogout = false; onLogout() }) { Text("Sair") } },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancelar") } }
        )
    }
    val pendingCount by viewModel.pendingSyncCount.collectAsState()
    val totalReceitas by viewModel.totalReceitas.collectAsState()
    val totalDespesas by viewModel.totalDespesas.collectAsState()
    val saldo by viewModel.saldo.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val currency = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    val name = (if (localGuestUid != null) "Convidado offline" else null)
        ?: user?.displayName?.takeIf { it.isNotBlank() }
        ?: user?.email?.substringBefore('@')?.takeIf { it.isNotBlank() }
        ?: "Convidado"
    val initials = name.trim().split(Regex("\\s+")).take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }.joinToString("").ifEmpty { "U" }
    val cloudReady = isOnline && !offlineMode && user != null

    PremiumBackground {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Perfil", style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.size(108.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.size(92.dp).clip(CircleShape)
                                .background(Brush.linearGradient(listOf(GradientStart, GradientEnd))),
                            contentAlignment = Alignment.Center
                        ) {
                            if (profilePhoto != null) {
                                Image(
                                    bitmap = profilePhoto!!,
                                    contentDescription = "Foto de perfil",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(initials, style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                        FilledIconButton(
                            onClick = { openPhotoPicker() },
                            enabled = uid != null && !photoSaving,
                            modifier = Modifier.size(35.dp).align(Alignment.BottomEnd),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            if (photoSaving) CircularProgressIndicator(
                                modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            ) else Icon(Icons.Default.CameraAlt, "Alterar foto", modifier = Modifier.size(19.dp))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = { openPhotoPicker() },
                        enabled = uid != null && !photoSaving
                    ) { Text(if (profilePhoto == null) "Adicionar foto" else "Trocar foto") }
                    if (profilePhoto != null && uid != null) {
                        TextButton(onClick = {
                            photoScope.launch {
                                if (ProfilePhotoStore.remove(context, uid)) onPhotoChanged()
                            }
                        }) { Text("Remover foto", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(user?.email ?: if (localGuestUid != null) "Dados somente neste aparelho" else "Conta temporária", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(14.dp))
                    Surface(color = if (cloudReady) IncomeGreen.copy(alpha = .13f) else MaterialTheme.colorScheme.primary.copy(alpha = .12f),
                        shape = RoundedCornerShape(50.dp)) {
                        Row(Modifier.padding(horizontal = 15.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (cloudReady) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                contentDescription = null, modifier = Modifier.size(15.dp),
                                tint = if (cloudReady) IncomeGreen else MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(7.dp))
                            Text(
                                when { localGuestUid != null -> "Modo offline local";
                                    user?.isAnonymous == true -> "Conta temporária";
                                    cloudReady -> "Conta conectada";
                                    else -> "Sincronização pausada" },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (cloudReady) IncomeGreen else MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (user?.isAnonymous == true) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = onCreateAccount, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Criar conta e proteger meus dados")
                        }
                    }
                }
            }

            if (localGuestUid != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Acesso sem internet", style = MaterialTheme.typography.titleMedium)
                        Text("Seus registros deste modo ficam somente neste aparelho. Eles não são transferidos automaticamente ao entrar em outra conta.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { confirmLogout = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Voltar para login ou cadastro")
                        }
                    }
                }
            }

            if (user?.isAnonymous == false && !googleLinked) {
                OutlinedButton(
                    enabled = !googleBusy && !account.busy,
                    onClick = onLinkGoogle,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (googleBusy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    else {
                        Text("G", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(9.dp))
                        Text("Vincular conta Google")
                    }
                }
            }
            if (googleMessage != null) {
                Text(
                    googleMessage.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        googleBusy -> MaterialTheme.colorScheme.onSurfaceVariant
                        googleLinked -> IncomeGreen
                        else -> ExpenseRed
                    }
                )
            }
            Text("Estatísticas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ProfileStat(Modifier.weight(1f), Icons.Default.ReceiptLong, "Transações", "${transactions.size}", MaterialTheme.colorScheme.primary)
                ProfileStat(Modifier.weight(1f), Icons.Default.TrendingUp, "Receitas", currency.format(totalReceitas), IncomeGreen)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ProfileStat(Modifier.weight(1f), Icons.Default.TrendingDown, "Despesas", currency.format(totalDespesas), ExpenseRed)
                ProfileStat(Modifier.weight(1f), Icons.Default.AccountBalance, "Saldo", currency.format(saldo), if (saldo >= 0) IncomeGreen else ExpenseRed)
            }

            Text("Preferências", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (darkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                        contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Tema escuro", style = MaterialTheme.typography.titleSmall)
                        Text(if (darkTheme) "Preto e vermelho" else "Branco e vermelho",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = darkTheme, onCheckedChange = onDarkThemeChange)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AirplanemodeActive, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Modo Offline", style = MaterialTheme.typography.titleSmall)
                        Text("Pausar sincronização com a nuvem", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = offlineMode, onCheckedChange = onOfflineModeChange)
                }
            }
            // Mantém abaixo o restante da tela sem duplicar o cartão de preferências.
            Text("Sincronização", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (isOnline) Icons.Default.Wifi else Icons.Default.WifiOff, null,
                            tint = if (isOnline) IncomeGreen else ExpenseRed)
                        Spacer(Modifier.width(10.dp))
                        Text("Status da rede", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(if (isOnline) "Conectado" else "Sem internet",
                            color = if (isOnline) IncomeGreen else ExpenseRed,
                            style = MaterialTheme.typography.labelMedium)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudUpload, null, tint = if (pendingCount > 0) SyncPending else IncomeGreen)
                        Spacer(Modifier.width(10.dp))
                        Text("Itens pendentes", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text("$pendingCount", color = if (pendingCount > 0) SyncPending else IncomeGreen)
                    }
                }
            }
            OutlinedButton(
                onClick = onSync, enabled = cloudReady, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Sync, null)
                Spacer(Modifier.width(8.dp))
                Text("Sincronizar agora")
            }
            if (user?.isAnonymous == false) {
                TextButton(onClick = { confirmLogout = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Logout, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Sair da conta", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("FinanceAPP • Android", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .65f),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 28.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun ProfileStat(modifier: Modifier, icon: ImageVector, label: String, value: String, tint: Color) {
    Card(modifier = modifier, shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(Modifier.size(37.dp).clip(RoundedCornerShape(13.dp))
                .background(tint.copy(alpha = .13f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(21.dp))
            }
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
}
