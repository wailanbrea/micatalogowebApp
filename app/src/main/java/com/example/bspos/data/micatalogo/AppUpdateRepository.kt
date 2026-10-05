package com.example.bspos.data.micatalogo

import android.content.Context
import com.example.bspos.data.micatalogo.api.MiCatalogoApi
import com.example.bspos.presentation.update.AvailableAppUpdate
import com.example.bspos.presentation.update.AppUpdatePolicy
import com.example.bspos.data.micatalogo.dto.AndroidUpdateDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class AppUpdateRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:Named("update") private val api: MiCatalogoApi,
    @param:Named("update")
    private val client: OkHttpClient
) {
    private val pendingPreferences = context.getSharedPreferences("pending_app_update", Context.MODE_PRIVATE)

    fun pendingUpdate(installedVersionCode: Int): AvailableAppUpdate? {
        val encoded = pendingPreferences.getString("manifest", null) ?: return null
        val manifest = runCatching { Json.decodeFromString<AndroidUpdateDto>(encoded) }.getOrNull()
            ?: return null
        if (manifest.versionCode <= installedVersionCode) {
            pendingPreferences.edit().remove("manifest").remove("started_version").commit()
            return null
        }
        return AppUpdatePolicy.available(manifest, installedVersionCode)
    }

    fun rememberUpdate(update: AvailableAppUpdate) {
        val manifest = AndroidUpdateDto(
            versionCode = update.versionCode,
            versionName = update.versionName,
            minimumSupportedVersionCode = update.minimumSupportedVersionCode,
            apkUrl = update.apkUrl,
            apkSha256 = update.apkSha256,
            releaseNotes = update.releaseNotes
        )
        check(pendingPreferences.edit().putString("manifest", Json.encodeToString(manifest)).commit()) {
            "No se pudo guardar la actualización pendiente."
        }
    }

    fun hasStartedUpdate(update: AvailableAppUpdate): Boolean =
        pendingPreferences.getInt("started_version", 0) == update.versionCode

    fun markUpdateStarted(update: AvailableAppUpdate) {
        rememberUpdate(update)
        check(pendingPreferences.edit().putInt("started_version", update.versionCode).commit()) {
            "No se pudo guardar el inicio de la actualización."
        }
    }

    suspend fun check() = api.androidUpdate()

    suspend fun download(update: AvailableAppUpdate, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val updateDirectory = File(context.cacheDir, "updates").apply { mkdirs() }
        val apk = File(updateDirectory, "bspos-${update.versionCode}.apk")
        if (apk.isFile && apk.length() in 1..MAX_APK_BYTES) {
            val cachedDigest = MessageDigest.getInstance("SHA-256")
            apk.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    cachedDigest.update(buffer, 0, read)
                }
            }
            val cachedHash = cachedDigest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
            if (cachedHash == update.apkSha256) return@withContext apk
        }
        val request = Request.Builder().url(update.apkUrl).build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "No se pudo descargar la actualizacion." }
            val body = response.body ?: error("La actualizacion no contiene un APK.")
            val totalBytes = body.contentLength()
            check(totalBytes <= MAX_APK_BYTES) { "La actualizacion es demasiado grande." }

            val partialApk = File(updateDirectory, "bspos-${update.versionCode}.apk.part")
            val digest = MessageDigest.getInstance("SHA-256")
            var downloadedBytes = 0L

            body.byteStream().use { input ->
                partialApk.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        downloadedBytes += read
                        check(downloadedBytes <= MAX_APK_BYTES) { "La actualizacion es demasiado grande." }
                        if (totalBytes > 0) onProgress((downloadedBytes * 100 / totalBytes).toInt())
                    }
                }
            }

            check(downloadedBytes > 0) { "La actualizacion descargada esta vacia." }
            val sha256 = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
            check(sha256 == update.apkSha256) { "No se pudo verificar la actualizacion descargada." }

            partialApk.copyTo(apk, overwrite = true)
            apk
        }
    }

    private companion object {
        const val MAX_APK_BYTES = 200L * 1024 * 1024
    }
}
