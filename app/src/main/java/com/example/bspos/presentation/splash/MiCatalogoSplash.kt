package com.example.bspos.presentation.splash

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.bspos.R
import kotlinx.coroutines.delay

@Composable fun MiCatalogoSplash(onFinished:()->Unit){var entered by remember{mutableStateOf(false)};LaunchedEffect(Unit){entered=true;delay(900);onFinished()};val progress by animateFloatAsState(if(entered)1f else .65f,tween(650,easing=FastOutSlowInEasing),label="logo entrance");Column(Modifier.fillMaxSize().background(Color(0xFFF8F7F7)),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Image(painterResource(R.drawable.ic_micatalogo_mark),null,Modifier.size(132.dp).graphicsLayer(scaleX=progress,scaleY=progress).alpha(progress));Text(buildAnnotatedString{withStyle(SpanStyle(color=Color(0xFF0F172A))){append("Mi")};withStyle(SpanStyle(color=Color(0xFF2563EB))){append("CatalogoApp")}},style=MaterialTheme.typography.titleLarge,modifier=Modifier.alpha(progress));Text("TU CATALOGO LISTO PARA VENDER",color=Color(0xFF475569),style=MaterialTheme.typography.labelSmall,modifier=Modifier.alpha(progress))}}
