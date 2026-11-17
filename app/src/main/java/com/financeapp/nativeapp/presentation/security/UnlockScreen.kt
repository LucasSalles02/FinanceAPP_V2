package com.financeapp.nativeapp.presentation.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.financeapp.nativeapp.presentation.theme.*

@Composable
fun UnlockScreen(message: String, pending: Boolean, credentialAvailable: Boolean,
    onUnlock: () -> Unit, onCredential: () -> Unit) {
    PremiumBackground {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier.size(92.dp).clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(GradientStart, GradientEnd))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Shield, "Segurança", Modifier.size(50.dp), tint = Color.White)
            }
            Spacer(Modifier.height(24.dp))
            Text("FinanceAPP", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Suas finanças protegidas", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(34.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Fingerprint, "Biometria", Modifier.size(58.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(18.dp))
                    Text("Desbloquear aplicativo", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(22.dp))
                    Button(onClick = onUnlock, enabled = !pending,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp)) {
                        Text("Desbloquear", fontWeight = FontWeight.Bold)
                    }
                    if (credentialAvailable) {
                        TextButton(onClick = onCredential, enabled = !pending) {
                            Text("Usar PIN, senha ou padrão do celular")
                        }
                    }
                }
            }
        }
    }
}
