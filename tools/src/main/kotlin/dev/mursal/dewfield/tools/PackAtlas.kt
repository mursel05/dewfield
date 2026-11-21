package dev.mursal.dewfield.tools

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.tools.texturepacker.TexturePacker

/** Packs the rendered art (tools/art/out) into assets/atlas/game.atlas. */
fun main(args: Array<String>) {
    require(args.size == 2) { "usage: PackAtlas <inputDir> <outputDir>" }
    val settings = TexturePacker.Settings().apply {
        maxWidth = 2048
        maxHeight = 2048
        paddingX = 4
        paddingY = 4
        duplicatePadding = true
        edgePadding = true
        bleed = true
        filterMin = Texture.TextureFilter.MipMapLinearLinear
        filterMag = Texture.TextureFilter.Linear
        combineSubdirectories = true
        flattenPaths = false
        useIndexes = false
        stripWhitespaceX = false
        stripWhitespaceY = false
        silent = true
    }
    TexturePacker.process(settings, args[0], args[1], "game")
}
