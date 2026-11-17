package com.financeapp.nativeapp.presentation.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Fundo premium discreto, adaptado aos temas claro e escuro. */
@Composable
fun PremiumBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(
                    MaterialTheme.colorScheme.background,
                    if (MaterialTheme.colorScheme.background == Background)
                        Color(0xFF170D13) else Color(0xFFFFF3F5),
                    MaterialTheme.colorScheme.background
                )
            )
        ), content = content
    )
}

@Composable
fun PremiumSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.titleMedium)
}
