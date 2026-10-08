package com.example.bspos.presentation.update

import com.example.bspos.data.micatalogo.dto.AndroidUpdateDto
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class AvailableAppUpdate(
    val versionCode: Int,
    val versionName: String,
    val minimumSupportedVersionCode: Int,
    val apkUrl: String,
    val apkSha256: String,
    val releaseNotes: String,
    val isRequired: Boolean
)

object AppUpdatePolicy {
    private val sha256Pattern = Regex("^[a-fA-F0-9]{64}$")

    /** Production releases must never be offered to a debug installation. */
    fun shouldCheckProductionUpdates(isDebugBuild: Boolean): Boolean = !isDebugBuild

    fun available(update: AndroidUpdateDto, installedVersionCode: Int): AvailableAppUpdate? {
        if (update.versionCode <= installedVersionCode) return null
        val apkUrl = update.apkUrl.toHttpUrlOrNull()?.takeIf { it.isHttps } ?: return null
        if (!update.apkSha256.matches(sha256Pattern)) return null

        return AvailableAppUpdate(
            versionCode = update.versionCode,
            versionName = update.versionName.ifBlank { update.versionCode.toString() },
            minimumSupportedVersionCode = update.minimumSupportedVersionCode,
            apkUrl = apkUrl.toString(),
            apkSha256 = update.apkSha256.lowercase(),
            releaseNotes = update.releaseNotes,
            isRequired = installedVersionCode < update.minimumSupportedVersionCode
        )
    }
}
