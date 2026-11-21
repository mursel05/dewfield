package dev.mursal.dewfield.android

import android.os.Bundle
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import dev.mursal.dewfield.DewfieldGame

/**
 * Starts the game full screen in portrait. It draws under the notch; the HUD and panels keep
 * their controls inside `Gdx.graphics.safeInset*` (see `Ui.SafeArea`).
 */
class AndroidLauncher : AndroidApplication() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = AndroidApplicationConfiguration().apply {
            useImmersiveMode = true
            renderUnderCutout = true
            useAccelerometer = false
            useCompass = false
            numSamples = 0
            r = 8; g = 8; b = 8; a = 8
        }
        initialize(DewfieldGame(), config)
    }
}
