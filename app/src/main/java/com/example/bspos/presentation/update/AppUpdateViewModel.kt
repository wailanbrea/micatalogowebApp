package com.example.bspos.presentation.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bspos.BuildConfig
import com.example.bspos.data.micatalogo.AppUpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.io.File
import android.os.SystemClock
import javax.inject.Inject

sealed interface AppUpdateState {
    data object Checking : AppUpdateState
    data object Current : AppUpdateState
    data class CheckFailed(val message: String) : AppUpdateState
    data class Available(val update: AvailableAppUpdate) : AppUpdateState
    data class Downloading(val update: AvailableAppUpdate, val progress: Int) : AppUpdateState
    data class ReadyToInstall(val update: AvailableAppUpdate, val apk: File) : AppUpdateState
    data class Failed(val update: AvailableAppUpdate, val message: String) : AppUpdateState
}

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val repository: AppUpdateRepository
) : ViewModel() {
    private val _state = MutableStateFlow<AppUpdateState>(AppUpdateState.Checking)
    val state = _state.asStateFlow()
    private var lastCheckStartedAt = SystemClock.elapsedRealtime()

    private companion object {
        const val RESUME_CHECK_INTERVAL_MS = 5 * 60 * 1000L
    }

    init {
        checkForUpdate()
    }

    fun checkForUpdate() = viewModelScope.launch {
        lastCheckStartedAt = SystemClock.elapsedRealtime()
        if (!AppUpdatePolicy.shouldCheckProductionUpdates(BuildConfig.DEBUG)) {
            // Debug builds are reserved for the emulator/QA runner. They must
            // not download a production-signed APK that Android cannot install
            // over the debug certificate.
            _state.value = AppUpdateState.Current
            return@launch
        }
        _state.value = AppUpdateState.Checking
        val response = try {
            repository.check()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _state.value = AppUpdateState.CheckFailed(
                error.message?.takeIf(String::isNotBlank)
                    ?: "No se pudo conectar con el servidor de actualizaciones."
            )
            return@launch
        }

        if (!response.isSuccessful) {
            _state.value = AppUpdateState.CheckFailed("El servidor respondió HTTP ${response.code()}.")
            return@launch
        }

        val manifest = response.body()
        if (manifest == null) {
            _state.value = AppUpdateState.CheckFailed("El servidor devolvió una respuesta vacía.")
            return@launch
        }
        if (manifest.versionCode <= BuildConfig.VERSION_CODE) {
            _state.value = AppUpdateState.Current
            return@launch
        }

        val update = AppUpdatePolicy.available(manifest, BuildConfig.VERSION_CODE)
        _state.value = update?.let(AppUpdateState::Available)
            ?: AppUpdateState.CheckFailed("El servidor anunció una versión, pero sus datos no pasaron la validación.")
    }

    fun download(update: AvailableAppUpdate) = viewModelScope.launch {
        _state.value = AppUpdateState.Downloading(update, 0)
        runCatching {
            repository.download(update) { progress ->
                _state.value = AppUpdateState.Downloading(update, progress)
            }
        }.fold(
            onSuccess = { _state.value = AppUpdateState.ReadyToInstall(update, it) },
            onFailure = { _state.value = AppUpdateState.Failed(update, it.message ?: "No se pudo descargar la actualizacion.") }
        )
    }

    fun onActivityResumed() {
        when (val currentState = _state.value) {
            is AppUpdateState.ReadyToInstall -> {
                _state.value = AppUpdateState.Available(currentState.update)
            }
            AppUpdateState.Current, is AppUpdateState.CheckFailed -> {
                val elapsedSinceCheck = SystemClock.elapsedRealtime() - lastCheckStartedAt
                if (elapsedSinceCheck >= RESUME_CHECK_INTERVAL_MS) checkForUpdate()
            }
            else -> Unit
        }
    }

    fun skip(update: AvailableAppUpdate) {
        if (!update.isRequired) _state.value = AppUpdateState.Current
    }

    fun dismissCheckFailure() {
        _state.value = AppUpdateState.Current
    }
}
