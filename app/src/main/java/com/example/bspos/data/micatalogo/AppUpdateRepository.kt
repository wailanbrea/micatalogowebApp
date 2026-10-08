package com.example.bspos.data.micatalogo

import android.content.Context
import com.example.bspos.data.micatalogo.api.MiCatalogoApi
import com.example.bspos.presentation.update.AvailableAppUpdate
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
    private val pendingStore = AppUpdatePendingStore(
        context.getSharedPreferences(PENDING_UPDATES_PREFERENCES, Context.MODE_PRIVATE)
    )

    suspend fun check() = api.androidUpdate()

    fun rememberPendingUpdate(update: AvailableAppUpdate) = pendingStore.save(update)

    fun pendingUpdate(installedVersionCode: Int): AvailableAppUpdate? =
        pendingStore.load(installedVersionCode)

    fun clearPendingUpdate() = pendingStore.clear()

    suspend fun download(update: AvailableAppUpdate, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(update.apkUrl).build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "No se pudo descargar la actualizacion." }
            val body = response.body ?: error("La actualizacion no contiene un APK.")
            val totalBytes = body.contentLength()
            check(totalBytes <= MAX_APK_BYTES) { "La actualizacion es demasiado grande." }

            val updateDirectory = File(context.cacheDir, "updates").apply { mkdirs() }
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

            val apk = File(updateDirectory, "bspos-${update.versionCode}.apk")
            check(partialApk.renameTo(apk)) { "No se pudo preparar la actualizacion." }
            apk
        }
    }

    private companion object {
        const val MAX_APK_BYTES = 200L * 1024 * 1024
        const val PENDING_UPDATES_PREFERENCES = "app_update_pending"
    }
}
