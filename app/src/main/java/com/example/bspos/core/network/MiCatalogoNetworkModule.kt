package com.example.bspos.core.network

import com.example.bspos.BuildConfig
import com.example.bspos.data.micatalogo.MiCatalogoAuthInterceptor
import com.example.bspos.data.micatalogo.api.MiCatalogoApi
import com.example.bspos.data.micatalogo.api.MiCatalogoCustomerApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
object MiCatalogoNetworkModule {
    @Provides
    @Singleton
    fun provideOperationApi(retrofit: Retrofit): com.example.bspos.data.micatalogo.api.MiCatalogoOperationApi = retrofit.create(com.example.bspos.data.micatalogo.api.MiCatalogoOperationApi::class.java)
    @Provides
    @Singleton
    @MiCatalogoBaseUrl
    fun provideBaseUrl(): String = BuildConfig.MICATALOGO_API_BASE_URL

    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: MiCatalogoAuthInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .build()

    @Provides
    @Singleton
    @Named("update")
    fun provideUpdateHttpClient(): OkHttpClient = OkHttpClient.Builder().build()

    @Provides
    @Singleton
    @Named("update")
    fun provideUpdateRetrofit(
        json: Json,
        @Named("update") client: OkHttpClient,
        @MiCatalogoBaseUrl baseUrl: String
    ): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(json: Json, client: OkHttpClient, @MiCatalogoBaseUrl baseUrl: String): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideApi(retrofit: Retrofit): MiCatalogoApi = retrofit.create(MiCatalogoApi::class.java)

    @Provides
    @Singleton
    @Named("update")
    fun provideUpdateApi(@Named("update") retrofit: Retrofit): MiCatalogoApi =
        retrofit.create(MiCatalogoApi::class.java)

    @Provides
    @Singleton
    fun provideCustomerApi(retrofit: Retrofit): MiCatalogoCustomerApi = retrofit.create(MiCatalogoCustomerApi::class.java)
}
