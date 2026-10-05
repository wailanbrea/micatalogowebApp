package com.example.bspos.data.micatalogo

import com.example.bspos.data.micatalogo.dto.ApiErrorDto
import kotlinx.serialization.json.Json
import retrofit2.Response

private val errorJson = Json { ignoreUnknownKeys = true }

internal fun Response<*>.apiErrorMessage(fallback: String): String =
    serverErrorMessage(errorBody()?.string(), fallback)

internal fun serverErrorMessage(body: String?, fallback: String): String {
    val error = body?.let { runCatching { errorJson.decodeFromString<ApiErrorDto>(it) }.getOrNull() }
    return error?.errors?.values?.flatten()?.firstOrNull { it.isNotBlank() }
        ?: error?.message?.takeIf { it.isNotBlank() }
        ?: fallback
}
