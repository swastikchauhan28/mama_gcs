package com.mamadrones.gcs

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil

@HiltAndroidApp
class MamaGcsApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // MapLibre's HTTP implementation reads the application context during static
        // initialization, so the SDK must be configured before replacing its client.
        MapLibre.getInstance(this)

        val mapHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                    .newBuilder()
                    .header("User-Agent", "$MAP_USER_AGENT/${BuildConfig.VERSION_NAME}")
                    .build()
                chain.proceed(request)
            }
            .build()

        HttpRequestUtil.setOkHttpClient(mapHttpClient)
    }

    private companion object {
        const val MAP_USER_AGENT = "MAMA-GCS-Android"
    }
}
