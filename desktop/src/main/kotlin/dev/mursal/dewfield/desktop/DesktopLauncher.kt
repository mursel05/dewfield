package dev.mursal.dewfield.desktop

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import dev.mursal.dewfield.DewfieldGame

fun main() {
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("Dewfield")
        setWindowedMode(1280, 720)
        useVsync(true)
        setForegroundFPS(60)
    }
    Lwjgl3Application(DewfieldGame(), config)
}
