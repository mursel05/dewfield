package dev.mursal.dewfield.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.Disposable

/**
 * A light map: a half-resolution buffer cleared to the ambient color, with soft light sprites
 * added on top, then multiplied over the finished scene. Cheap enough for low-end phones.
 */
class Lighting : Disposable {
    private var fbo: FrameBuffer? = null
    private val region = TextureRegion()
    private val screen = Matrix4()

    fun resize(width: Int, height: Int) {
        fbo?.dispose()
        fbo = FrameBuffer(Pixmap.Format.RGBA8888, (width / 2).coerceAtLeast(1), (height / 2).coerceAtLeast(1), false).also {
            it.colorBufferTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
            region.setRegion(it.colorBufferTexture)
            region.flip(false, true)
        }
        screen.setToOrtho2D(0f, 0f, width.toFloat(), height.toFloat())
    }

    fun render(batch: SpriteBatch, cam: OrthographicCamera, ambient: Color, drawLights: (SpriteBatch) -> Unit) {
        val buffer = fbo ?: return
        buffer.begin()
        Gdx.gl.glClearColor(ambient.r, ambient.g, ambient.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        batch.projectionMatrix = cam.combined
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
        batch.begin()
        drawLights(batch)
        batch.end()
        buffer.end()

        batch.projectionMatrix = screen
        batch.setBlendFunction(GL20.GL_DST_COLOR, GL20.GL_ZERO)
        batch.begin()
        batch.setColor(Color.WHITE)
        batch.draw(region, 0f, 0f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        batch.end()
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
    }

    override fun dispose() {
        fbo?.dispose()
    }
}
