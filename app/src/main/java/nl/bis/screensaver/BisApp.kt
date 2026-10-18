package nl.bis.screensaver

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.OkHttpClient
import java.io.File

class BisApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Housekeeping.run(this)
    }

    /**
     * Alle afbeeldingen gaan met een herkenbare afzender de deur uit. Het Art Institute of
     * Chicago weigert afbeeldingen aan onbekende afzenders; met deze header gaat het goed.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        // Zuinig met werkgeheugen: de Chromecast heeft maar 2 GB voor alles samen.
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.12).build() }
        // Kunstwerken worden tijdelijk bewaard, maar nooit meer dan 150 MB.
        .diskCache {
            DiskCache.Builder()
                .directory(File(cacheDir, "beelden"))
                .maxSizeBytes(Storage.IMAGE_CACHE_BYTES)
                .build()
        }
        .okHttpClient {
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("User-Agent", USER_AGENT)
                            .header("AIC-User-Agent", USER_AGENT)
                            .build(),
                    )
                }
                .build()
        }
        .build()

    private companion object {
        const val USER_AGENT = "BisScreensavert/1.0 (persoonlijke screensaver)"
    }
}
