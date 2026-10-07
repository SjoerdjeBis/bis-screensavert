package nl.bis.screensaver

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

/**
 * Ontvangt de Google-sleutels vanaf de Mac (via het installatiescript), zodat je ze niet
 * met de afstandsbediening hoeft in te tikken.
 */
class SetupActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = Settings(this)
        val clientId = intent.getStringExtra("google_client_id")?.trim()
        val clientSecret = intent.getStringExtra("google_client_secret")?.trim()
        if (!clientId.isNullOrEmpty() && !clientSecret.isNullOrEmpty()) {
            if (clientId != settings.googleClientId) settings.googleRefreshToken = null
            settings.googleClientId = clientId
            settings.googleClientSecret = clientSecret
            Toast.makeText(this, "Google Foto's-koppeling opgeslagen", Toast.LENGTH_LONG).show()
        }
        finish()
    }
}
