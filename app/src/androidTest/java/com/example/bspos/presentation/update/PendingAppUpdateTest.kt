package com.example.bspos.presentation.update

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bspos.data.micatalogo.dto.AndroidUpdateDto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PendingAppUpdateTest {
    @Test
    fun optionalAndRequiredUpdatesUseTheInstalledVersionAndMinimumVersion() {
        val optional = AppUpdatePolicy.available(
            AndroidUpdateDto(100, "1.0.100", 23, "https://example.test/app.apk", "b".repeat(64), ""),
            installedVersionCode = 24
        )
        assertNotNull(optional)
        assertFalse(optional!!.isRequired)

        val required = AppUpdatePolicy.available(
            AndroidUpdateDto(101, "1.0.101", 101, "https://example.test/app.apk", "a".repeat(64), ""),
            installedVersionCode = 100
        )
        assertNotNull(required)
        assertTrue(required!!.isRequired)
        assertNull(AppUpdatePolicy.available(requiredManifest(), installedVersionCode = 101))
    }

    @Test
    fun invalidManifestCannotCreateAnUpdate() {
        assertNull(AppUpdatePolicy.available(requiredManifest(apkUrl = "http://example.test/app.apk"), 1))
        assertNull(AppUpdatePolicy.available(requiredManifest(apkSha256 = "not-a-sha"), 1))
        assertNull(AppUpdatePolicy.available(requiredManifest(versionCode = 1), 1))
    }

    private fun requiredManifest(
        versionCode: Int = 2,
        apkUrl: String = "https://example.test/app.apk",
        apkSha256: String = "c".repeat(64)
    ) = AndroidUpdateDto(versionCode, "1.0.$versionCode", 2, apkUrl, apkSha256, "")
}
