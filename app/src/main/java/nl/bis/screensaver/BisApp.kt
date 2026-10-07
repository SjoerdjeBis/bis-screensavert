package nl.bis.screensaver

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.OkHttpClient

class BisApp : Application(), ImageLoaderFactory {
    /**
     * Alle afbeeldingen gaan met een herkenbare afzender de deur uit. Het Art Institute of
     * Chicago weigert afbeeldingen aan onbekende afzenders; met deze header gaat het goed.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
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
