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
    data class InstallationPending(val update: AvailableAppUpdate) : AppUpdateState
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
        val pending = repository.pendingUpdate(BuildConfig.VERSION_CODE)
        if (pending != null) _state.value = if (repository.hasStartedUpdate(pending))
            AppUpdateState.InstallationPending(pending) else AppUpdateState.Available(pending)
        else checkForUpdate()
    }

    fun checkForUpdate() = viewModelScope.launch {
        lastCheckStartedAt = SystemClock.elapsedRealtime()
        val pending = repository.pendingUpdate(BuildConfig.VERSION_CODE)
        if (pending == null) _state.value = AppUpdateState.Checking
        val response = try {
            repository.check()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _state.value = pending?.takeIf { it.isRequired || repository.hasStartedUpdate(it) }?.let { AppUpdateState.Failed(it, "Sin conexión. Reintenta la actualización pendiente.") } ?: AppUpdateState.CheckFailed(
                error.message?.takeIf(String::isNotBlank)
                    ?: "No se pudo conectar con el servidor de actualizaciones."
            )
            return@launch
        }

        if (!response.isSuccessful) {
            _state.value = pending?.takeIf { it.isRequired || repository.hasStartedUpdate(it) }?.let { AppUpdateState.Failed(it, "El servidor respondió HTTP ${response.code()}.") }
                ?: AppUpdateState.CheckFailed("El servidor respondió HTTP ${response.code()}.")
            return@launch
        }

        val manifest = response.body()
        if (manifest == null) {
            _state.value = pending?.let { pendingState(it) }
                ?: AppUpdateState.CheckFailed("El servidor devolvió una respuesta vacía.")
            return@launch
        }
        if (manifest.versionCode <= BuildConfig.VERSION_CODE) {
            _state.value = pending?.let { pendingState(it) } ?: AppUpdateState.Current
            return@launch
        }

        val update = AppUpdatePolicy.available(manifest, BuildConfig.VERSION_CODE)
        val target = update?.takeIf { pending == null || it.versionCode >= pending.versionCode } ?: pending
        if (target != null) {
            if (pending != null && repository.hasStartedUpdate(pending)) repository.markUpdateStarted(target)
            repository.rememberUpdate(target)
            _state.value = pendingState(target)
        } else {
            _state.value = AppUpdateState.CheckFailed("El servidor anunció una versión, pero sus datos no pasaron la validación.")
        }
    }

    fun download(update: AvailableAppUpdate) = viewModelScope.launch {
        if (_state.value is AppUpdateState.Downloading) return@launch
        _state.value = AppUpdateState.Downloading(update, 0)
        runCatching {
            repository.markUpdateStarted(update)
            repository.download(update) { progress ->
                _state.value = AppUpdateState.Downloading(update, progress)
            }
        }.fold(
            onSuccess = { _state.value = AppUpdateState.ReadyToInstall(update, it) },
            onFailure = {
                if (it is CancellationException) throw it
                _state.value = AppUpdateState.Failed(update, it.message ?: "No se pudo descargar la actualizacion.")
            }
        )
    }

    fun onActivityResumed() {
        when (_state.value) {
            AppUpdateState.Current, is AppUpdateState.CheckFailed -> {
                val elapsedSinceCheck = SystemClock.elapsedRealtime() - lastCheckStartedAt
                if (elapsedSinceCheck >= RESUME_CHECK_INTERVAL_MS) checkForUpdate()
            }
            else -> Unit
        }
    }

    fun installationStarted() {
        val ready = _state.value as? AppUpdateState.ReadyToInstall ?: return
        _state.value = AppUpdateState.InstallationPending(ready.update)
    }

    fun installationFailed(message: String) {
        val pending = _state.value as? AppUpdateState.InstallationPending ?: return
        _state.value = AppUpdateState.Failed(pending.update, message)
    }

    fun skip(update: AvailableAppUpdate) {
        if (!update.isRequired && !repository.hasStartedUpdate(update) && _state.value is AppUpdateState.Available) {
            _state.value = AppUpdateState.Current
        }
    }

    fun dismissCheckFailure() {
        if (_state.value is AppUpdateState.CheckFailed) _state.value = AppUpdateState.Current
    }

    private fun pendingState(update: AvailableAppUpdate): AppUpdateState =
        if (repository.hasStartedUpdate(update)) AppUpdateState.InstallationPending(update)
        else AppUpdateState.Available(update)
}
