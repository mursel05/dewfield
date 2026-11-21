package dev.mursal.dewfield.audio

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.Disposable
import dev.mursal.dewfield.app.Settings

/**
 * Sound effects and music. All audio is synthesized by tools/audio and shipped as OGG.
 * Music tracks crossfade; a separate ambient loop carries rain.
 */
class Audio(private val settings: Settings) : Disposable {
    private val sounds = HashMap<String, Sound>()
    private val lastPlayed = HashMap<String, Long>()

    private var music: Music? = null
    private var musicId: String? = null
    private var fadingOut: Music? = null
    private var musicVolume = 0f
    private var fadeOutVolume = 0f

    private var ambient: Music? = null
    private var ambientId: String? = null
    private var ambientVolume = 0f
    private var ambientTarget = 0f

    init {
        for (id in SOUNDS) {
            val f = Gdx.files.internal("sfx/$id.ogg")
            if (f.exists()) sounds[id] = Gdx.audio.newSound(f) else Gdx.app.error("Audio", "Missing sound $id")
        }
    }

    /** Plays a sound with a little pitch variation so repeats do not sound robotic. */
    fun play(id: String, volume: Float = 1f, jitter: Float = 0.06f) {
        if (!settings.sound) return
        val s = sounds[ALIASES[id] ?: id] ?: return
        // The same sound fired many times in one frame (a long queue) plays once.
        val now = System.currentTimeMillis()
        if (now - (lastPlayed[id] ?: 0L) < 45L) return
        lastPlayed[id] = now
        s.play(volume * 0.8f, 1f + MathUtils.random(-jitter, jitter), 0f)
    }

    /** Switches the music track with a crossfade. Null fades the music out. */
    fun music(id: String?) {
        if (id == musicId) return
        musicId = id
        fadingOut?.let { it.stop(); it.dispose() }
        fadingOut = music
        fadeOutVolume = musicVolume
        music = null
        musicVolume = 0f
        if (id == null) return
        val f = Gdx.files.internal("music/$id.ogg")
        if (!f.exists()) return
        music = Gdx.audio.newMusic(f).apply {
            isLooping = true
            volume = 0f
            if (settings.music) play()
        }
    }

    /** Looping background ambience (rain). */
    fun ambient(id: String?) {
        if (id == ambientId) return
        ambientId = id
        ambientTarget = if (id == null) 0f else 1f
        if (id != null) {
            ambient?.let { it.stop(); it.dispose() }
            ambientVolume = 0f
            val f = Gdx.files.internal("sfx/$id.ogg")
            ambient = if (f.exists()) Gdx.audio.newMusic(f).apply { isLooping = true; volume = 0f; if (settings.sound) play() } else null
        }
    }

    fun update(dt: Float) {
        val musicOn = settings.music
        music?.let { m ->
            if (musicOn && !m.isPlaying) m.play()
            if (!musicOn && m.isPlaying) m.pause()
            musicVolume = (musicVolume + dt / FADE).coerceAtMost(1f)
            m.volume = musicVolume * MUSIC_LEVEL
        }
        fadingOut?.let { m ->
            fadeOutVolume -= dt / FADE
            if (fadeOutVolume <= 0f) {
                m.stop()
                m.dispose()
                fadingOut = null
            } else m.volume = fadeOutVolume * MUSIC_LEVEL
        }
        ambient?.let { a ->
            val on = settings.sound
            if (on && !a.isPlaying && ambientTarget > 0f) a.play()
            if (!on && a.isPlaying) a.pause()
            ambientVolume += (ambientTarget - ambientVolume).coerceIn(-dt / FADE, dt / FADE)
            a.volume = ambientVolume * 0.5f
            if (ambientTarget == 0f && ambientVolume <= 0f) {
                a.stop()
                a.dispose()
                ambient = null
            }
        }
    }

    fun pause() {
        music?.pause()
        ambient?.pause()
    }

    fun resume() {
        if (settings.music) music?.play()
        if (settings.sound && ambientTarget > 0f) ambient?.play()
    }

    override fun dispose() {
        sounds.values.forEach { it.dispose() }
        listOfNotNull(music, fadingOut, ambient).forEach { it.dispose() }
    }

    companion object {
        private const val FADE = 1.6f
        private const val MUSIC_LEVEL = 0.45f

        val SOUNDS = listOf(
            "click", "open", "close", "tick", "error", "coin", "till", "plant", "water", "harvest",
            "pop", "door", "pet_chicken", "pet_cow", "pet_sheep", "levelup", "sleep", "rooster", "cook", "step",
        )

        /** World events use descriptive ids; several share one sample. */
        private val ALIASES = mapOf("buy" to "coin", "sell" to "coin", "collect" to "harvest", "fill" to "pop")
    }
}
