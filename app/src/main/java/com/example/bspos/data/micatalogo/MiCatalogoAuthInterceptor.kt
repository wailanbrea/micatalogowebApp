package com.example.bspos.data.micatalogo

import com.example.bspos.BuildConfig
import com.example.bspos.domain.repository.MiCatalogoConnectionRepository
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MiCatalogoAuthInterceptor @Inject constructor(
    private val connectionRepository: MiCatalogoConnectionRepository
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val accessToken = runBlocking { connectionRepository.accessToken() }
        val authenticatedRequest = request.newBuilder()
            .header("X-MiCatalogo-Version-Code", BuildConfig.VERSION_CODE.toString())
            .apply { accessToken?.let { header("Authorization", "Bearer $it") } }
            .build()
        return chain.proceed(authenticatedRequest)
    }
}
