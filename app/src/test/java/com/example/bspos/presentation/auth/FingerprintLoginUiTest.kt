package com.example.bspos.presentation.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FingerprintLoginUiTest {
    @Test
    fun showsFingerprintButtonBesidePasswordVisibilityWhenSessionIsRemembered() {
        assertTrue(shouldShowFingerprintButton(hasAccessToken = true, isRemembered = true, biometricAvailable = true))
    }

    @Test
    fun hidesFingerprintButtonWhenThereIsNoRememberedSession() {
        assertFalse(shouldShowFingerprintButton(hasAccessToken = true, isRemembered = false, biometricAvailable = true))
    }

    @Test
    fun hidesFingerprintButtonWhenDeviceBiometricsAreUnavailable() {
        assertFalse(shouldShowFingerprintButton(hasAccessToken = true, isRemembered = true, biometricAvailable = false))
    }

    @Test
    fun hidesFingerprintButtonWithoutAStoredSessionToken() {
        assertFalse(shouldShowFingerprintButton(hasAccessToken = false, isRemembered = true, biometricAvailable = true))
    }

    @Test
    fun keepsRememberMeCheckedWhenTheSavedEmailIsAvailable() {
        assertTrue(shouldKeepRememberMeChecked(isRemembered = false, savedEmail = "seller@example.test"))
        assertFalse(shouldKeepRememberMeChecked(isRemembered = false, savedEmail = ""))
    }
}
