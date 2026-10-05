package com.example.bspos.data.micatalogo

import android.content.Context
import com.example.bspos.core.network.MiCatalogoBaseUrl
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MiCatalogoImageDownloader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:MiCatalogoBaseUrl private val baseUrl: String
) {
    // Image hosts are external to the API. Do not reuse the authenticated API client,
    // otherwise the MiCatalogo bearer token could be sent to an image host.
    private val client = OkHttpClient()

    /** Resolves absolute, root-relative and relative URLs returned by the web catalog. */
    fun resolve(url: String?): String? {
        return MiCatalogoImageUrlResolver.resolve(baseUrl, url)
    }

    suspend fun download(url: String?): String? = withContext(Dispatchers.IO) {
        val source = resolve(url) ?: return@withContext null
        val directory = File(context.filesDir, "micatalogo/images").apply { mkdirs() }
        val target = File(directory, "${sha256(source)}.image")
        if (target.isFile && target.length() > 0) {
            target.setLastModified(System.currentTimeMillis())
            pruneCache(directory, target)
            return@withContext target.toURI().toString()
        }

        val temporary = File(directory, "${target.name}.tmp")
        temporary.delete()
        val downloaded = runCatching {
            client.newCall(
                Request.Builder()
                    .url(source)
                    .header("Accept", "image/avif,image/webp,image/svg+xml,image/jpeg,image/png")
                    .get()
                    .build()
            ).execute().use { response ->
                val body = response.body
                val contentType = response.body?.contentType()?.toString().orEmpty()
                if (!response.isSuccessful || body == null ||
                    (contentType.isNotBlank() && !contentType.startsWith("image/") && contentType != "application/octet-stream") ||
                    body.contentLength() > MAX_IMAGE_BYTES
                ) {
                    false
                } else {
                    var totalBytes = 0L
                    body.byteStream().use { input ->
                        FileOutputStream(temporary).use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                totalBytes += read
                                if (totalBytes > MAX_IMAGE_BYTES) error("Image exceeds the maximum size")
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                    totalBytes > 0
                }
            }
        }.getOrDefault(false)
        if (!downloaded) {
            temporary.delete()
            return@withContext null
        }
        runCatching {
            if (temporary.renameTo(target)) {
                pruneCache(directory, target)
                target.toURI().toString()
            } else {
                temporary.delete()
                null
            }
        }.getOrElse {
            temporary.delete()
            null
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private fun pruneCache(directory: File, keep: File) {
        var total = 0L
        val files = directory.listFiles()
            .orEmpty()
            .filter { it.isFile && it != keep }
            .onEach { file ->
                if (file.name.endsWith(".tmp")) file.delete() else total += file.length()
            }
            .filterNot { it.name.endsWith(".tmp") }
            .sortedBy { it.lastModified() }

        total += keep.length()
        files.forEach { file ->
            if (total <= MAX_CACHE_BYTES) return@forEach
            val size = file.length()
            if (file.delete()) total -= size
        }
    }

    private companion object {
        const val BUFFER_SIZE = 16 * 1024
        const val MAX_IMAGE_BYTES = 10L * 1024L * 1024L
        const val MAX_CACHE_BYTES = 100L * 1024L * 1024L
    }
}

internal object MiCatalogoImageUrlResolver {
    fun resolve(baseUrl: String, url: String?): String? {
        val value = url?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val base = baseUrl.trim().toHttpUrlOrNull() ?: return null
        val normalized = if (value.startsWith("//")) "${base.scheme}:$value" else value
        val resolved = base.resolve(normalized)?.takeIf { it.scheme == "http" || it.scheme == "https" } ?: return null
        return if (base.scheme == "https" && resolved.scheme == "http" && resolved.host == base.host) {
            resolved.newBuilder().scheme("https").build().toString()
        } else {
            resolved.toString()
        }
    }
}
