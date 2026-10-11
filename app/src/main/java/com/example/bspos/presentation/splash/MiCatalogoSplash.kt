package com.example.bspos.presentation.splash

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.bspos.R
import kotlinx.coroutines.delay

@Composable
fun MiCatalogoSplash(
    statusMessage: String? = null,
    errorMessage: String? = null,
    onRetry: (() -> Unit)? = null,
    onFinished: () -> Unit
) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        entered = true
        delay(900)
        onFinished()
    }
    val progress by animateFloatAsState(
        targetValue = if (entered) 1f else .65f,
        animationSpec = tween(650, easing = FastOutSlowInEasing),
        label = "logo entrance"
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F7F7))
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_micatalogo_mark),
            contentDescription = null,
            modifier = Modifier
                .size(132.dp)
                .graphicsLayer(scaleX = progress, scaleY = progress)
                .alpha(progress)
        )
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Color(0xFF0F172A))) { append("Mi") }
                withStyle(SpanStyle(color = Color(0xFF2563EB))) { append("Catalogo") }
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.alpha(progress)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = errorMessage ?: statusMessage ?: "Preparando MiCatalogo…",
            color = if (errorMessage == null) Color(0xFF475569) else Color(0xFFB42318),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        if (errorMessage != null && onRetry != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) { Text("Reintentar") }
        } else if (statusMessage != null) {
            Spacer(Modifier.height(12.dp))
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "TU CATÁLOGO LISTO PARA VENDER",
            color = Color(0xFF475569),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.alpha(progress)
        )
    }
}
