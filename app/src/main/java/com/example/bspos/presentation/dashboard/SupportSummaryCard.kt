package com.example.bspos.presentation.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.example.bspos.presentation.common.BSPOSActionTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bspos.core.ui.theme.BSPOSTheme

@Composable
internal fun SupportSummaryCard(
    onSupport: () -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = BSPOSTheme.colors.primaryLight,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, ResumenAccent.copy(alpha = .25f))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, tint = ResumenAccent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text("¿Necesitas ayuda?", color = ResumenInk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                TextButton(onClick = onHide, contentPadding = ButtonDefaults.TextButtonContentPadding) { Text("Ocultar", color = ResumenMuted, fontSize = 12.sp) }
            }
            OutlinedButton(
                onClick = onSupport,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(9.dp),
                border = BorderStroke(1.dp, ResumenAccent.copy(alpha = .45f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ResumenInk)
            ) {
                Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text("Abrir soporte", fontWeight = FontWeight.Bold)
            }
        }
    }
}
