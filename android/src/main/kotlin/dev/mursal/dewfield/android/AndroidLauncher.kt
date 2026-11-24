package dev.mursal.dewfield.android

import android.os.Bundle
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import dev.mursal.dewfield.DewfieldGame

/**
 * Starts the game full screen in portrait. It draws under the notch; the HUD and panels keep
 * their controls inside `Gdx.graphics.safeInset*` (see `Ui.SafeArea`). Local reminders go
 * through [AndroidNotifier].
 */
class AndroidLauncher : AndroidApplication() {
    private lateinit var notifier: AndroidNotifier

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
        notifier = AndroidNotifier(this)
        initialize(DewfieldGame(notifier = notifier), config)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        notifier.onPermissionResult(requestCode, grantResults)
    }
}
