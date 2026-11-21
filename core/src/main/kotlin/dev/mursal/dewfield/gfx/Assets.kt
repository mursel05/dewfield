package dev.mursal.dewfield.gfx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.utils.Disposable
import kotlin.math.roundToInt

/**
 * Everything drawn comes from one texture atlas (rendered from SVG at 128 px per tile) and two
 * Nunito weights. Fonts are rasterised at the device's real pixel size and scaled back to the
 * 720x1280 portrait virtual UI, so text stays sharp on every screen density.
 */
class Assets(uiScale: Float) : Disposable {
    val atlas = TextureAtlas(Gdx.files.internal("atlas/game.atlas"))
    private val cache = HashMap<String, TextureRegion>()
    private val white: TextureRegion = atlas.findRegion("ui/white")
    val fonts = Fonts(uiScale)

    init {
        // Mipmapped atlas pages: smooth when the camera zooms out.
        atlas.textures.forEach { it.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear) }
    }

    fun region(name: String): TextureRegion = cache.getOrPut(name) {
        atlas.findRegion(name) ?: run {
            Gdx.app.error("Assets", "Missing sprite $name")
            white
        }
    }

    fun has(name: String): Boolean = cache.containsKey(name) || atlas.findRegion(name) != null

    fun icon(id: String): TextureRegion = region("icon/$id")

    /** A nine-patch from a UI sprite. Sources are drawn at 2x, so corners are halved on screen. */
    fun nine(name: String, left: Int, right: Int, top: Int, bottom: Int, scale: Float = 0.5f): NinePatch =
        NinePatch(region("ui/$name"), left, right, top, bottom).apply { scale(scale, scale) }

    override fun dispose() {
        atlas.dispose()
        fonts.dispose()
    }
}

class Fonts(private val scale: Float) : Disposable {
    val small: BitmapFont
    val text: BitmapFont
    val head: BitmapFont
    val title: BitmapFont
    /** Outlined fonts for text drawn over the world (HUD numbers, floating gains, banners). */
    val outline: BitmapFont
    val outlineBig: BitmapFont

    private val all = ArrayList<BitmapFont>()

    init {
        val bold = FreeTypeFontGenerator(Gdx.files.internal("fonts/Nunito-Bold.ttf"))
        val extra = FreeTypeFontGenerator(Gdx.files.internal("fonts/Nunito-ExtraBold.ttf"))
        try {
            small = make(bold, 20)
            text = make(bold, 24)
            head = make(extra, 30)
            title = make(extra, 40)
            outline = make(extra, 26, border = 3f)
            outlineBig = make(extra, 54, border = 5f)
        } finally {
            bold.dispose()
            extra.dispose()
        }
    }

    private fun make(gen: FreeTypeFontGenerator, size: Int, border: Float = 0f): BitmapFont {
        val p = FreeTypeFontGenerator.FreeTypeFontParameter().apply {
            this.size = (size * scale).roundToInt().coerceAtLeast(8)
            characters = CHARS
            minFilter = Texture.TextureFilter.Linear
            magFilter = Texture.TextureFilter.Linear
            kerning = true
            if (border > 0f) {
                borderWidth = border * scale
                borderColor = Color.valueOf("4a3524")
                borderStraight = false
            }
        }
        return gen.generateFont(p).apply {
            data.setScale(1f / scale)
            setUseIntegerPositions(false)
            data.markupEnabled = true
            all += this
        }
    }

    override fun dispose() = all.forEach { it.dispose() }

    companion object {
        /** Latin, Latin-1 and the Azerbaijani letters, plus the few symbols the UI uses. */
        val CHARS = FreeTypeFontGenerator.DEFAULT_CHARS + "ƏəĞğİıŞş×…•·–—“”«»₼"
    }
}
