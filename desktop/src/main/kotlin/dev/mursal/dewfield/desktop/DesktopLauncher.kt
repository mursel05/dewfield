package dev.mursal.dewfield.desktop

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import dev.mursal.dewfield.DewfieldGame
import java.io.File

/**
 * Desktop entry point for development.
 *
 *   ./gradlew :desktop:run
 *   ./gradlew :desktop:run --args="--tour build/tour --size 1600x720"
 *
 * `--tour` plays a scripted walk through every screen and writes screenshots, then quits.
 */
fun main(args: Array<String>) {
    val opts = args.toList()
    val tour = opts.indexOf("--tour").takeIf { it >= 0 }?.let { File(opts.getOrElse(it + 1) { "tour" }) }
    val size = opts.indexOf("--size").takeIf { it >= 0 }?.let { opts.getOrNull(it + 1) }?.split('x')?.mapNotNull { it.toIntOrNull() }
    val (w, h) = if (size != null && size.size == 2) size[0] to size[1] else 1280 to 720
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("Dewfield")
        setWindowedMode(w, h)
        setResizable(tour == null)
        useVsync(tour == null)
        setForegroundFPS(60)
        setPreferencesConfig(".dewfield", com.badlogic.gdx.Files.FileType.External)
    }
    Lwjgl3Application(DewfieldGame(tour?.let { Tour(it) }), config)
}
