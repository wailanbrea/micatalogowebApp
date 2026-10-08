package com.example.bspos.data.micatalogo

import android.content.SharedPreferences
import com.example.bspos.data.micatalogo.dto.AndroidUpdateDto
import com.example.bspos.presentation.update.AppUpdatePolicy
import com.example.bspos.presentation.update.AvailableAppUpdate

/**
 * Keeps the last valid update announcement until the installed app moves past
 * it. This lets the blocking dialog return after a process death, a cancelled
 * installer, or a temporary loss of connectivity.
 */
class AppUpdatePendingStore(
    private val preferences: SharedPreferences
) {
    fun save(update: AvailableAppUpdate) {
        preferences.edit()
            .putInt(KEY_VERSION_CODE, update.versionCode)
            .putString(KEY_VERSION_NAME, update.versionName)
            .putInt(KEY_MINIMUM_VERSION_CODE, update.minimumSupportedVersionCode)
            .putString(KEY_APK_URL, update.apkUrl)
            .putString(KEY_APK_SHA256, update.apkSha256)
            .putString(KEY_RELEASE_NOTES, update.releaseNotes)
            .apply()
    }

    fun load(installedVersionCode: Int): AvailableAppUpdate? {
        val versionCode = preferences.getInt(KEY_VERSION_CODE, 0)
        if (versionCode <= 0) return null

        val manifest = AndroidUpdateDto(
            versionCode = versionCode,
            versionName = preferences.getString(KEY_VERSION_NAME, "").orEmpty(),
            minimumSupportedVersionCode = preferences.getInt(KEY_MINIMUM_VERSION_CODE, 0),
            apkUrl = preferences.getString(KEY_APK_URL, "").orEmpty(),
            apkSha256 = preferences.getString(KEY_APK_SHA256, "").orEmpty(),
            releaseNotes = preferences.getString(KEY_RELEASE_NOTES, "").orEmpty()
        )
        return AppUpdatePolicy.available(manifest, installedVersionCode).also {
            if (it == null) clear()
        }
    }

    fun clear() {
        preferences.edit()
            .remove(KEY_VERSION_CODE)
            .remove(KEY_VERSION_NAME)
            .remove(KEY_MINIMUM_VERSION_CODE)
            .remove(KEY_APK_URL)
            .remove(KEY_APK_SHA256)
            .remove(KEY_RELEASE_NOTES)
            .apply()
    }

    private companion object {
        const val KEY_VERSION_CODE = "pending_version_code"
        const val KEY_VERSION_NAME = "pending_version_name"
        const val KEY_MINIMUM_VERSION_CODE = "pending_minimum_version_code"
        const val KEY_APK_URL = "pending_apk_url"
        const val KEY_APK_SHA256 = "pending_apk_sha256"
        const val KEY_RELEASE_NOTES = "pending_release_notes"
    }
}
