package com.example.bspos.presentation.update

import com.example.bspos.data.micatalogo.dto.AndroidUpdateDto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdatePolicyTest {
    @Test
    fun `debug builds do not check production update manifest`() {
        assertFalse(AppUpdatePolicy.shouldCheckProductionUpdates(isDebugBuild = true))
        assertTrue(AppUpdatePolicy.shouldCheckProductionUpdates(isDebugBuild = false))
    }

    @Test
    fun `published release prompts installed version 17 to update to version 18`() {
        val publishedRelease = AndroidUpdateDto(
            versionCode = 18,
            versionName = "1.0.17",
            minimumSupportedVersionCode = 11,
            apkUrl = "https://micatalogo.bsolutions.dev/downloads/bspos-1.0.17-auth-import.apk",
            apkSha256 = "e".repeat(64)
        )

        val update = AppUpdatePolicy.available(publishedRelease, installedVersionCode = 17)

        requireNotNull(update)
        assertFalse(update.isRequired)
        assertTrue(update.versionName == "1.0.17")
    }

    @Test
    fun `published MiCatalogo release prompts installed version 18 to update to version 19`() {
        val publishedRelease = AndroidUpdateDto(
            versionCode = 19,
            versionName = "1.0.18",
            minimumSupportedVersionCode = 11,
            apkUrl = "https://micatalogo.bsolutions.dev/downloads/bspos-1.0.18.apk",
            apkSha256 = "d".repeat(64)
        )

        val update = AppUpdatePolicy.available(publishedRelease, installedVersionCode = 18)

        requireNotNull(update)
        assertFalse(update.isRequired)
        assertTrue(update.versionName == "1.0.18")
    }

    @Test
    fun `only accepts a newer HTTPS APK with a valid digest`() {
        val update = AndroidUpdateDto(
            versionCode = 6,
            versionName = "1.0.5",
            minimumSupportedVersionCode = 6,
            apkUrl = "https://micatalogo.bsolutions.dev/downloads/bspos-1.0.5.apk",
            apkSha256 = "a".repeat(64)
        )

        val available = AppUpdatePolicy.available(update, installedVersionCode = 5)

        requireNotNull(available)
        assertTrue(available.isRequired)
        assertNull(AppUpdatePolicy.available(update.copy(versionCode = 5), installedVersionCode = 5))
        assertNull(AppUpdatePolicy.available(update.copy(apkUrl = "http://example.test/app.apk"), installedVersionCode = 5))
        assertFalse(available.apkSha256.isBlank())
    }

    @Test
    fun `keeps a newer update optional when the installed version is supported`() {
        val update = AndroidUpdateDto(
            versionCode = 7,
            versionName = "1.0.6",
            minimumSupportedVersionCode = 6,
            apkUrl = "https://micatalogo.bsolutions.dev/downloads/bspos-1.0.6.apk",
            apkSha256 = "b".repeat(64)
        )

        val available = AppUpdatePolicy.available(update, installedVersionCode = 6)

        requireNotNull(available)
        assertFalse(available.isRequired)
    }

    @Test
    fun `requires an update below the minimum supported version`() {
        val update = AndroidUpdateDto(
            versionCode = 8,
            versionName = "1.0.7",
            minimumSupportedVersionCode = 8,
            apkUrl = "https://micatalogo.bsolutions.dev/downloads/bspos-1.0.7.apk",
            apkSha256 = "c".repeat(64)
        )

        assertTrue(AppUpdatePolicy.available(update, installedVersionCode = 7)?.isRequired == true)
        assertFalse(AppUpdatePolicy.available(update, installedVersionCode = 8)?.isRequired == true)
    }
}
