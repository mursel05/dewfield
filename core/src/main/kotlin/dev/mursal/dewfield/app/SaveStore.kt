package dev.mursal.dewfield.app

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import dev.mursal.dewfield.model.FieldLayout
import dev.mursal.dewfield.model.GameState
import dev.mursal.dewfield.model.SaveCodec

/**
 * One save slot in app-private storage. Writes go to a temp file that then replaces the
 * save, and the previous save is kept as a backup, so a crash mid-write never loses the farm.
 */
class SaveStore(private val field: FieldLayout) {
    /**
     * App-private storage on Android. The desktop build runs with assets/ as its working
     * directory, so it saves under the user's home instead of next to the game's assets.
     */
    private val dir: FileHandle by lazy {
        if (Gdx.app.type == Application.ApplicationType.Desktop) Gdx.files.external(".dewfield/save") else Gdx.files.local("save")
    }
    private val main: FileHandle get() = dir.child("farm.json")
    private val backup: FileHandle get() = dir.child("farm.bak.json")
    private val temp: FileHandle get() = dir.child("farm.tmp.json")

    fun exists(): Boolean = main.exists() || backup.exists()

    /** The saved farm, falling back to the backup if the main file is damaged. */
    fun load(): GameState? {
        for (f in listOf(main, backup)) {
            if (!f.exists()) continue
            val state = runCatching { SaveCodec.decode(f.readString("UTF-8"), field) }.getOrNull()
            if (state != null) return state
            Gdx.app.error(TAG, "Unreadable save ${f.name()}, trying the next one")
        }
        return null
    }

    fun save(state: GameState) {
        try {
            temp.writeString(SaveCodec.encode(state), false, "UTF-8")
            if (main.exists()) main.copyTo(backup)
            temp.moveTo(main)
        } catch (e: Exception) {
            Gdx.app.error(TAG, "Save failed", e)
        }
    }

    fun delete() {
        main.delete()
        backup.delete()
        temp.delete()
    }

    private companion object {
        const val TAG = "SaveStore"
    }
}
