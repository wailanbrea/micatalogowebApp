package com.example.bspos.presentation.sync

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.bspos.core.ui.theme.BSPOSTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SyncStatusBannerTest {
    @get:Rule val compose = createComposeRule()
    @Test fun statusIsVisibleAndRetryDoesNotTurnPendingWorkIntoASuccess() {
        val state = mutableStateOf(SyncStatus())
        var attempts = 0
        var resolutions = 0
        compose.setContent { BSPOSTheme { SyncStatusBanner(state.value, onRetry = { attempts++ }, onResolve = { resolutions++ }) } }
        compose.onNodeWithText("Verificando sincronización…").assertExists()
        compose.onNodeWithText("Sincronizado").assertDoesNotExist()
        compose.runOnIdle { state.value = SyncStatus(SyncPhase.PENDING, 2) }
        compose.onNodeWithText("Pendiente de sincronización · 2").assertExists()
        compose.runOnIdle { state.value = SyncStatus(SyncPhase.BLOCKED, 1, "La cantidad cambió; revisa el conteo.") }
        compose.onNodeWithText("Requiere revisión · 1").assertExists()
        compose.onNodeWithText("La cantidad cambió; revisa el conteo.").assertExists()
        compose.onNodeWithText("Resolver").performClick()
        compose.onNodeWithText("Operación detenida").assertExists()
        compose.onNodeWithText("La operación no se enviará de nuevo automáticamente para evitar duplicados o cambios incorrectos.").assertExists()
        compose.onNodeWithText("Reintentar").performClick()
        compose.runOnIdle { assertEquals(1, attempts); assertEquals(0, resolutions); state.value = SyncStatus(SyncPhase.RETRY, 1) }
        compose.onNodeWithText("Reintentando · 1").assertExists()
        compose.runOnIdle { state.value = SyncStatus(SyncPhase.SYNCED) }
        compose.onNodeWithText("Sincronizado").assertExists()
        compose.onNodeWithText("Reintentar").assertDoesNotExist()
    }
}
