package com.example.bspos.presentation.auth

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import com.example.bspos.presentation.common.BSPOSButton as Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.bspos.domain.model.MiCatalogoConnectionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Navy = Color(0xFF071B35)
private val NavySecondary = Color(0xFF0A2D59)
private val BrandBlue = Color(0xFF008CFF)
private val StrongBlue = Color(0xFF0568F5)
private val LightBlue = Color(0xFF49B5FF)
private val AppBackground = Color(0xFFF5F7FA)
private val TextSecondary = Color(0xFF718096)
private val Border = Color(0xFFDCE6F2)

private const val BIOMETRIC_AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK

internal fun shouldShowFingerprintButton(
    hasAccessToken: Boolean,
    isRemembered: Boolean,
    biometricAvailable: Boolean
): Boolean = hasAccessToken && isRemembered && biometricAvailable

internal fun shouldAutomaticallyRequestFingerprint(
    autoRequestBiometric: Boolean,
    hasAccessToken: Boolean,
    isRemembered: Boolean,
    biometricAvailable: Boolean
): Boolean = autoRequestBiometric && shouldShowFingerprintButton(hasAccessToken, isRemembered, biometricAvailable)

internal fun shouldKeepRememberMeChecked(isRemembered: Boolean, savedEmail: String): Boolean =
    isRemembered || savedEmail.isNotBlank()

private fun biometricPromptInfo() = BiometricPrompt.PromptInfo.Builder()
    .setTitle("Desbloquear MiCatalogo")
    .setSubtitle("Confirma tu identidad con la huella registrada")
    .setAllowedAuthenticators(BIOMETRIC_AUTHENTICATORS)
    .setNegativeButtonText("Usar contrasena")
    .build()

@Composable
fun LoginScreen(
    connection: MiCatalogoConnectionState,
    state: LoginUiState,
    onLogin: (String, String, Boolean) -> Unit,
    onAuthenticated: () -> Unit,
    onAuthenticatedConsumed: () -> Unit,
    autoRequestBiometric: Boolean = false
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val savedEmail = connection.rememberedEmail.ifBlank { connection.accountEmail }
    var email by remember(savedEmail) { mutableStateOf(savedEmail) }
    var password by remember { mutableStateOf("") }
    var rememberMe by remember(connection.isRemembered, savedEmail) { mutableStateOf(shouldKeepRememberMeChecked(connection.isRemembered, savedEmail)) }
    var biometricMessage by remember { mutableStateOf<String?>(null) }
    var fingerprintAccepted by remember { mutableStateOf(false) }
    var biometricRequested by remember(connection.isRemembered, autoRequestBiometric) { mutableStateOf(false) }
    val biometricAvailable = activity != null && BiometricManager.from(context)
        .canAuthenticate(BIOMETRIC_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
    val prompt = remember(activity) {
        activity?.let { host ->
            BiometricPrompt(host, ContextCompat.getMainExecutor(context), object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    fingerprintAccepted = true
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON && errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                        biometricMessage = errString.toString()
                    }
                }
            })
        }
    }

    LaunchedEffect(state.authenticated) {
        if (state.authenticated) {
            onAuthenticated()
            onAuthenticatedConsumed()
        }
    }
    LaunchedEffect(fingerprintAccepted) {
        if (fingerprintAccepted) {
            onAuthenticated()
            fingerprintAccepted = false
        }
    }
    LaunchedEffect(connection.hasAccessToken, connection.isRemembered, biometricAvailable, prompt, autoRequestBiometric) {
        if (shouldAutomaticallyRequestFingerprint(autoRequestBiometric, connection.hasAccessToken, connection.isRemembered, biometricAvailable) && prompt != null && !biometricRequested) {
            biometricRequested = true
            prompt.authenticate(biometricPromptInfo())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(14.dp))
            BrandHeader()
            Spacer(Modifier.height(28.dp))
            LoginCard(
                connection = connection,
                state = state,
                email = email,
                password = password,
                rememberMe = rememberMe,
                biometricAvailable = biometricAvailable,
                biometricMessage = biometricMessage,
                onEmailChange = { email = it },
                onPasswordChange = { password = it },
                onRememberChange = { rememberMe = it },
                onLogin = { onLogin(email, password, rememberMe) },
                onFingerprint = {
                    biometricMessage = null
                    prompt?.authenticate(biometricPromptInfo())
                }
            )
        }
    }
}

@Composable
private fun BrandHeader() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        CatalogBrandIcon()
        Spacer(Modifier.size(12.dp))
        Text("Mi", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Navy)
        Text("Catalogo", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = LightBlue)
    }
}

@Composable
private fun CatalogBrandIcon() {
    Box(Modifier.size(45.dp)) {
        Box(
            modifier = Modifier
                .size(width = 24.dp, height = 38.dp)
                .align(Alignment.CenterStart)
                .clip(RoundedCornerShape(4.dp))
                .background(Brush.verticalGradient(listOf(LightBlue, BrandBlue)))
        )
        Box(
            modifier = Modifier
                .size(width = 24.dp, height = 38.dp)
                .align(Alignment.CenterEnd)
                .clip(RoundedCornerShape(4.dp))
                .background(Brush.verticalGradient(listOf(StrongBlue, Color(0xFF0054D1))))
        )
    }
}

@Composable
internal fun LoginCard(
    connection: MiCatalogoConnectionState,
    state: LoginUiState,
    email: String,
    password: String,
    rememberMe: Boolean,
    biometricAvailable: Boolean,
    biometricMessage: String?,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onRememberChange: (Boolean) -> Unit,
    onLogin: () -> Unit,
    onFingerprint: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val emailBringIntoViewRequester = remember { BringIntoViewRequester() }
    val passwordBringIntoViewRequester = remember { BringIntoViewRequester() }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        contentColor = Navy,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Mi Cuenta", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Navy)
                    Spacer(Modifier.height(7.dp))
                    Text("Accede a tu panel", fontSize = 14.sp, color = TextSecondary)
                }
            }

            Box(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp)
                ) {
                    AnimatedVisibility(visible = state.errorMessage != null) {
                        state.errorMessage?.let {
                            ErrorCard(it)
                            Spacer(Modifier.height(14.dp))
                        }
                    }
                    AnimatedVisibility(visible = biometricMessage != null) {
                        biometricMessage?.let {
                            ErrorCard(it)
                            Spacer(Modifier.height(14.dp))
                        }
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = onEmailChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(emailBringIntoViewRequester)
                            .onFocusEvent {
                                if (it.isFocused) {
                                    coroutineScope.launch {
                                        delay(250)
                                        emailBringIntoViewRequester.bringIntoView()
                                    }
                                }
                            },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        placeholder = { Text("Correo electronico") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        colors = authTextFieldColors()
                    )
                    Spacer(Modifier.height(15.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = onPasswordChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(passwordBringIntoViewRequester)
                            .onFocusEvent {
                                if (it.isFocused) {
                                    coroutineScope.launch {
                                        delay(250)
                                        passwordBringIntoViewRequester.bringIntoView()
                                    }
                                }
                            },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        placeholder = { Text("Contrasena") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            Row {
                                if (shouldShowFingerprintButton(connection.hasAccessToken, connection.isRemembered, biometricAvailable)) {
                                    IconButton(onClick = onFingerprint) {
                                        Icon(
                                            imageVector = Icons.Default.Fingerprint,
                                            contentDescription = "Desbloquear con huella"
                                        )
                                    }
                                }
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (passwordVisible) "Ocultar contrasena" else "Mostrar contrasena"
                                    )
                                }
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            onLogin()
                        }),
                        colors = authTextFieldColors()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = rememberMe,
                            onCheckedChange = onRememberChange,
                            colors = CheckboxDefaults.colors(
                                checkedColor = BrandBlue,
                                uncheckedColor = TextSecondary,
                                checkmarkColor = Color.White
                            )
                        )
                        Text("Recordarme en este dispositivo", fontSize = 13.sp, color = Navy)
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onLogin,
                         modifier = Modifier.fillMaxWidth().height(48.dp),
                        enabled = !state.isSubmitting,
                         shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandBlue,
                            contentColor = Color.White
                        )
                    ) {
                        if (state.isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                             Text("Iniciar sesion", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Spacer(Modifier.size(12.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    SecureAccessCard()
                }
            }
        }
    }
}

@Composable
private fun HeaderDecorations() {
    Canvas(Modifier.fillMaxSize()) {
        val rightPath = Path().apply {
            moveTo(size.width * .65f, size.height)
            lineTo(size.width, size.height * .38f)
            lineTo(size.width, size.height)
            close()
        }
        drawPath(rightPath, Color.White.copy(alpha = .10f))
        val leftPath = Path().apply {
            moveTo(0f, size.height * .62f)
            lineTo(size.width * .46f, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(leftPath, BrandBlue.copy(alpha = .34f))
    }
}

@Composable
private fun LoginGeometricBackground() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Navy, NavySecondary, BrandBlue),
                start = Offset.Zero,
                end = Offset(size.width, size.height * .75f)
            )
        )
        val leftLight = Path().apply {
            moveTo(0f, size.height * .58f)
            lineTo(size.width * .64f, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(leftLight, Brush.linearGradient(listOf(Color.White, Color(0xFFB9E3FF))))
        val rightBlue = Path().apply {
            moveTo(size.width * .78f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height * .55f)
            lineTo(size.width * .53f, size.height * .30f)
            close()
        }
        drawPath(rightBlue, Brush.linearGradient(listOf(BrandBlue, StrongBlue)))
    }
}

@Composable
private fun SecureAccessCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(13.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF5F8FC),
            contentColor = Navy
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Shield, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(7.dp))
            Text("Acceso seguro cifrado", color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Surface(
        color = Color(0xFFFFF0F2),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(message, color = Color(0xFFB82237), fontSize = 13.sp, modifier = Modifier.padding(14.dp))
    }
}

@Composable
private fun authTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Navy,
    unfocusedTextColor = Navy,
    disabledTextColor = Navy.copy(alpha = .38f),
    errorTextColor = Navy,
    focusedBorderColor = BrandBlue,
    unfocusedBorderColor = Border,
    disabledBorderColor = Border,
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color(0xFFFBFCFE),
    disabledContainerColor = Color(0xFFF5F8FC),
    focusedLeadingIconColor = BrandBlue,
    unfocusedLeadingIconColor = TextSecondary,
    focusedTrailingIconColor = TextSecondary,
    unfocusedTrailingIconColor = TextSecondary,
    focusedPlaceholderColor = TextSecondary,
    unfocusedPlaceholderColor = TextSecondary,
    focusedLabelColor = BrandBlue,
    unfocusedLabelColor = TextSecondary,
    cursorColor = BrandBlue
)
