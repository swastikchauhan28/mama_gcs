package com.mamadrones.gcs

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import okhttp3.Dispatcher
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil
import com.mamadrones.gcs.presentation.map.OfflineDownloadBudget

@HiltAndroidApp
class MamaGcsApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // MapLibre's HTTP implementation reads the application context during static
        // initialization, so the SDK must be configured before replacing its client.
        MapLibre.getInstance(this)

        // One process-wide cap also bounds concurrent requests to the offline tile host.
        val mapDispatcher = Dispatcher().apply { maxRequests = 4; maxRequestsPerHost = 4 }
        val mapHttpClient = OkHttpClient.Builder()
            .dispatcher(mapDispatcher)
            .addInterceptor { chain ->
                val request = chain.request()
                    .newBuilder()
                    .header("User-Agent", "$MAP_USER_AGENT/${BuildConfig.VERSION_NAME}")
                    .build()
                chain.proceed(request)
            }
            .addNetworkInterceptor { chain ->
                val tracked = OfflineDownloadBudget.shouldTrack(chain.request().url.host)
                if (tracked) OfflineDownloadBudget.beginRequest()
                val response = chain.proceed(chain.request())
                val body = response.body
                if (body == null || !tracked) response
                else response.newBuilder().body(object : ResponseBody() {
                    override fun contentType() = body.contentType()
                    override fun contentLength() = body.contentLength()
                    private val counted: BufferedSource by lazy {
                        object : ForwardingSource(body.source()) {
                            override fun read(sink: Buffer, byteCount: Long): Long {
                                val read = super.read(sink, byteCount)
                                OfflineDownloadBudget.count(read)
                                return read
                            }
                        }.buffer()
                    }
                    override fun source(): BufferedSource = counted
                }).build()
            }
            .build()

        HttpRequestUtil.setOkHttpClient(mapHttpClient)
    }

    private companion object {
        const val MAP_USER_AGENT = "MAMA-GCS-Android"
    }
}
