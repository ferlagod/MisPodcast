/*
 * Mis Podcast - Gestor y reproductor libre de podcasts para Android
 * Copyright (C) 2026 ferlagod
 *
 * Este programa es software libre: puedes redistribuirlo y/o modificarlo
 * bajo los términos de la Licencia Pública General de GNU tal como fue
 * publicada por la Free Software Foundation, ya sea la versión 3 de la
 * Licencia, o (a tu elección) cualquier versión posterior.
 *
 * Este programa se distribuye con la esperanza de que sea útil,
 * pero SIN NINGUNA GARANTÍA; ni siquiera la garantía implícita de
 * COMERCIABILIDAD o IDONEIDAD PARA UN PROPÓSITO PARTICULAR.
 * Consulta la Licencia Pública General de GNU para más detalles.
 *
 * Deberías haber recibido una copia de la Licencia Pública General de GNU
 * junto con este programa. Si no es así, consulta <https://www.gnu.org/licenses/>.
 */

package com.ferlagod.mispodcast.data.remote.api

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/**
 * Servicio Retrofit para la búsqueda y exploración de programas en el catálogo de Apple Podcasts.
 *
 * Permite buscar podcasts por términos textuales y explorar programas destacados por categorías temáticas y países.
 */
interface ApplePodcastSearchService {

    @GET("search")
    suspend fun searchPodcasts(
        @Query("term") term: String,
        @Query("media") media: String = "podcast",
        @Query("limit") limit: Int = 25,
        @Query("country") country: String = "ES",
        @Query("lang") lang: String? = null
    ): AppleSearchResponse

    @GET("search")
    suspend fun searchPodcastsByGenre(
        @Query("genreId") genreId: Int,
        @Query("term") term: String = "podcast",
        @Query("media") media: String = "podcast",
        @Query("limit") limit: Int = 50,
        @Query("country") country: String = "ES",
        @Query("lang") lang: String? = null
    ): AppleSearchResponse

    companion object {
        private const val BASE_URL = "https://itunes.apple.com/"

        fun create(baseClient: OkHttpClient? = null): ApplePodcastSearchService {
            val okHttpClientBuilder = (baseClient?.newBuilder() ?: OkHttpClient.Builder())
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)

            if (com.ferlagod.mispodcast.BuildConfig.DEBUG) {
                val loggingInterceptor = HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
                okHttpClientBuilder.addInterceptor(loggingInterceptor)
            }

            val okHttpClient = okHttpClientBuilder.build()

            val json = Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
                isLenient = true
            }

            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()

            return retrofit.create(ApplePodcastSearchService::class.java)
        }
    }
}
