package com.example.bspos

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.presentation.main.BSPOSMainScreen
import com.example.bspos.presentation.splash.MiCatalogoSplash
import com.example.bspos.presentation.auth.LoginScreen
import com.example.bspos.presentation.auth.LoginViewModel
import com.example.bspos.presentation.auth.shouldLockSession
import com.example.bspos.presentation.update.AppUpdateDialog
import com.example.bspos.presentation.update.AppUpdateState
import com.example.bspos.presentation.update.AppUpdateViewModel
import dagger.hilt.android.AndroidEntryPoint
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.io.File

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private var pendingSourcePermissionApk: File? = null

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val dataReady by (application as MiCatalogoApplication).dataReady.collectAsState()
            val loginViewModel: LoginViewModel = hiltViewModel()
            val appUpdateViewModel: AppUpdateViewModel = hiltViewModel()
            val connection by loginViewModel.connection.collectAsState()
            val appUpdateState by appUpdateViewModel.state.collectAsState()
            var showSplash by remember { mutableStateOf(true) }
            var sessionUnlocked by remember { mutableStateOf(false) }
            var lastBackgroundedAt by remember { mutableStateOf(0L) }
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_STOP -> lastBackgroundedAt = SystemClock.elapsedRealtime()
                        Lifecycle.Event.ON_START -> if (shouldLockSession(lastBackgroundedAt, SystemClock.elapsedRealtime())) {
                            sessionUnlocked = false
                        }
                        Lifecycle.Event.ON_RESUME -> appUpdateViewModel.onActivityResumed()
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            LaunchedEffect(connection.isConfigured) {
                if (!connection.isConfigured) sessionUnlocked = false
            }
            LaunchedEffect(connection.isConfigured, sessionUnlocked) {
                if (connection.isConfigured && sessionUnlocked) loginViewModel.syncCatalogs()
            }
            BSPOSTheme {
                Box {
                    when {
                        showSplash || !dataReady -> MiCatalogoSplash { showSplash = false }
                        connection.isConfigured && sessionUnlocked -> BSPOSMainScreen(windowWidthSizeClass = windowSizeClass.widthSizeClass)
                        else -> LoginScreen(connection, loginViewModel.uiState.collectAsState().value, loginViewModel::login, { sessionUnlocked = true }, loginViewModel::consumeAuthenticated)
                    }
                    when (appUpdateState) {
                        is AppUpdateState.Available,
                        is AppUpdateState.Downloading,
                        is AppUpdateState.Failed,
                        is AppUpdateState.CheckFailed -> AppUpdateDialog(
                            appUpdateState,
                            appUpdateViewModel::download,
                            appUpdateViewModel::skip,
                            onRetryCheck = { appUpdateViewModel.checkForUpdate() },
                            onDismissCheckFailure = appUpdateViewModel::dismissCheckFailure
                        )
                        is AppUpdateState.ReadyToInstall -> {
                            val apk = (appUpdateState as AppUpdateState.ReadyToInstall).apk
                            LaunchedEffect(apk) { installDownloadedUpdate(apk) }
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        pendingSourcePermissionApk?.let { apk ->
            pendingSourcePermissionApk = null
            if (packageManager.canRequestPackageInstalls()) {
                openPackageInstaller(apk)
            }
        }
    }

    private fun installDownloadedUpdate(apk: File) {
        if (!apk.exists()) return
        if (!packageManager.canRequestPackageInstalls()) {
            pendingSourcePermissionApk = apk
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        openPackageInstaller(apk)
    }

    private fun openPackageInstaller(apk: File) {
        val contentUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(contentUri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        runCatching { startActivity(intent) }
            .onFailure { Toast.makeText(this, "No se pudo abrir el instalador de la actualizacion.", Toast.LENGTH_LONG).show() }
    }
}
