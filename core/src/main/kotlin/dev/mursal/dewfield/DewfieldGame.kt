package dev.mursal.dewfield

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20

class DewfieldGame : ApplicationAdapter() {
    override fun render() {
        Gdx.gl.glClearColor(0.5f, 0.72f, 0.36f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
    }
}
