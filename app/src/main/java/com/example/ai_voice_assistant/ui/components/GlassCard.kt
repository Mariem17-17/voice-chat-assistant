package com.example.ai_voice_assistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ai_voice_assistant.ui.theme.GlassBorder
import com.example.ai_voice_assistant.ui.theme.GlassSurface

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    borderWidth: Dp = 1.dp,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .border(
                width = borderWidth,
                brush = Brush.linearGradient(
                    colors = listOf(
                        GlassBorder.copy(alpha = 0.4f),
                        GlassBorder.copy(alpha = 0.1f)
                    )
                ),
                shape = RoundedCornerShape(cornerRadius)
            ),
        shape = RoundedCornerShape(cornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        )
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            GlassSurface.copy(alpha = 0.15f),
                            GlassSurface.copy(alpha = 0.05f)
                        )
                    ),
                    shape = RoundedCornerShape(cornerRadius)
                )
        ) {
            content()
        }
    }
}
