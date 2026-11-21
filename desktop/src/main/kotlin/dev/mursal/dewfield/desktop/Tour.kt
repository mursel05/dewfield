package dev.mursal.dewfield.desktop

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import dev.mursal.dewfield.DewfieldGame
import dev.mursal.dewfield.Director
import dev.mursal.dewfield.i18n.Lang
import dev.mursal.dewfield.model.AnimalState
import dev.mursal.dewfield.model.Farm
import dev.mursal.dewfield.model.ItemDb
import dev.mursal.dewfield.model.MachineState
import dev.mursal.dewfield.model.Weather
import dev.mursal.dewfield.screen.GameScreen
import dev.mursal.dewfield.ui.BagPanel
import dev.mursal.dewfield.world.Locations
import dev.mursal.dewfield.world.Panel
import java.io.File

/**
 * A scripted walk through the game that saves a screenshot of every screen. It is the
 * visual check for changes to art, rendering or UI, and it runs headless under xvfb in CI.
 * Run it at portrait sizes (720x1600 for a modern phone, 960x1280 for a tablet).
 */
class Tour(private val out: File) : Director {
    private class Step(val frames: Int, val action: (DewfieldGame) -> Unit)

    private val steps = ArrayDeque<Step>()
    private var wait = 0
    private var shots = 0

    private fun step(frames: Int = 2, action: (DewfieldGame) -> Unit) {
        steps += Step(frames, action)
    }

    private fun shot(name: String) = step(1) { capture(name) }

    private fun DewfieldGame.play(): GameScreen = screen as GameScreen

    override fun started(game: DewfieldGame) {
        out.mkdirs()
        game.settings.lang = Lang.EN
        game.settings.sound = false
        game.settings.music = false
        game.reloadStrings()
        script()
    }

    override fun frame(game: DewfieldGame) {
        if (wait > 0) {
            wait--
            return
        }
        val s = steps.removeFirstOrNull()
        if (s == null) {
            Gdx.app.log("Tour", "Wrote $shots screenshots to ${out.absolutePath}")
            Gdx.app.exit()
            return
        }
        s.action(game)
        wait = s.frames
    }

    private fun script() {
        step(60) {}
        shot("01_title")
        step(80) { it.startGame(tourFarm(), fresh = true) }
        shot("02_house_morning")
        step(50) { g ->
            g.play().warp(Locations.FARM, "house_door")
            g.play().world.player.apply { x = 10.5f; y = 16.5f }
        }
        shot("03_farm_morning")
        step(12) { g ->
            val w = g.play().world
            g.play().farm.select("carrot_seeds")
            // Till the top row of the third field block, then water a crop in the second.
            for (x in 6..9) w.tap(x + 0.5f, 7.5f)
            w.tap(12.5f, 14.5f)
        }
        shot("04_farm_tasks")
        step(260) {}
        shot("05_farm_working")
        step(50) { g ->
            g.play().farm.state.minutes = 10 * 60.0
            g.play().warp(Locations.MARKET, "farm_road")
            g.play().world.player.apply { x = 7.5f; y = 20.5f }
        }
        shot("06_market")
        panel(Panel.SHOP_SEEDS, "07_seeds")
        panel(Panel.SHOP_FEED, "08_feed")
        panel(Panel.SELL, "09_sell")
        panel(Panel.WORKSHOP, "10_workshop")
        panel(Panel.ORDERS, "11_orders")
        step(30) { g ->
            g.play().closeAll()
            g.play().warp(Locations.FARM, "market_road")
            g.play().world.player.apply { x = 8.5f; y = 33.5f }
        }
        shot("12_farm_animals")
        panel(Panel.MACHINE, "13_mill", "mill")
        step(30) { g -> g.play().closeAll(); g.play().open(BagPanel(g.play())) }
        shot("14_bag")
        step(30) { g ->
            g.play().closeAll()
            g.play().warp(Locations.HOUSE, "door")
        }
        panel(Panel.COOK, "15_cook")
        panel(Panel.TV, "16_tv")
        panel(Panel.SLEEP, "17_sleep")
        step(240) { g -> g.play().closeAll(); g.play().sleep() }
        shot("18_morning_report")
        step(40) { g ->
            g.play().closeAll()
            g.play().farm.state.minutes = 19 * 60 + 15.0
            g.play().warp(Locations.FARM, "house_door")
            g.play().world.player.apply { x = 7.5f; y = 16.5f }
        }
        shot("19_farm_sunset")
        step(40) { g -> g.play().farm.state.minutes = 22 * 60.0 }
        shot("20_farm_night")
        step(60) { g ->
            g.play().farm.state.minutes = 11 * 60.0
            g.play().farm.state.weather = Weather.RAIN
        }
        shot("21_farm_rain")
        step(40) { g ->
            g.play().farm.state.weather = Weather.SUNNY
            g.play().farm.state.minutes = 21 * 60.0
            g.play().warp(Locations.MARKET, "farm_road")
            g.play().world.player.apply { x = 8.5f; y = 12.5f }
        }
        shot("22_market_night")
        step(40) { g ->
            g.play().farm.state.minutes = 9 * 60.0
            g.settings.lang = Lang.AZ
            g.play().relabel()
        }
        shot("23_menu_az")
        panel(Panel.SHOP_SEEDS, "24_seeds_az")
        step(40) { g ->
            g.play().closeAll()
            g.play().farm.addXp(600)
        }
        shot("25_levelup_az")
        step(5) { g -> g.play().closeAll() }
    }

    private fun panel(p: Panel, name: String, arg: String = "") {
        step(30) { g ->
            g.play().closeAll()
            g.play().openPanel(p, arg)
        }
        shot(name)
    }

    private fun capture(name: String) {
        val w = Gdx.graphics.backBufferWidth
        val h = Gdx.graphics.backBufferHeight
        val pm = Pixmap.createFromFrameBuffer(0, 0, w, h)
        val flipped = Pixmap(w, h, Pixmap.Format.RGBA8888)
        for (y in 0 until h) flipped.drawPixmap(pm, 0, y, w, 1, 0, h - 1 - y, w, 1)
        // Force opaque alpha so viewers do not show the window background through.
        val px = flipped.pixels
        for (i in 3 until px.limit() step 4) px.put(i, 0xFF.toByte())
        PixmapIO.writePNG(Gdx.files.absolute(File(out, "$name.png").absolutePath), flipped)
        pm.dispose()
        flipped.dispose()
        shots++
    }

    /** A farm a few days in: crops at every stage, all three animals, goods to sell. */
    private fun tourFarm(): Farm {
        val f = Farm.newGame(1234, Locations.FIELD)
        val s = f.state
        s.money = 2450
        s.xp = 190
        s.upgrades[ItemDb.UPGRADE_FIELD] = 3
        s.upgrades[ItemDb.UPGRADE_CAN] = 2
        s.canWater = 40
        s.day = 6
        val crops = listOf("carrot", "tomato", "wheat", "lettuce", "strawberry", "corn", "radish", "potato")
        for (p in s.plots) {
            val block = Locations.FIELD.blockIndex(p.cell) ?: continue
            if (block >= 2) continue
            // Blocks are 4 wide: two crops side by side in each, alternating by row.
            val crop = crops[(p.x - 6) / 2 % 2 + block * 2 + (p.y % 2) * 4]
            val def = ItemDb.crop(crop)
            p.tilled = true
            p.crop = crop
            p.watered = (p.x + p.y) % 3 != 0
            p.fertilized = p.x % 4 == 0
            p.growth = ((p.x * 7 + p.y * 3) % (def.growDays + 1))
        }
        s.plots.firstOrNull { it.x == 9 && it.y == 13 }?.apply { dead = true }
        s.animals += AnimalState(s.nextAnimalId++, "chicken", "Mimi")
        s.animals += AnimalState(s.nextAnimalId++, "cow", "Marta", mood = 8)
        s.animals += AnimalState(s.nextAnimalId++, "cow", "Bella", mood = 6)
        s.animals += AnimalState(s.nextAnimalId++, "sheep", "Bulud", mood = 7)
        s.troughs["hopper"] = 12
        s.troughs["hay_rack"] = 25
        s.troughs["clover_tray"] = 4
        s.machines["cheese_press"] = MachineState("cheese_press")
        for ((k, n) in listOf("carrot" to 6, "carrot#2" to 3, "tomato#1" to 8, "egg" to 5, "egg#1" to 2, "milk" to 3, "wheat" to 12,
            "corn" to 4, "lettuce" to 3, "strawberry#2" to 6, "wool" to 1, "cheese#1" to 1, "carrot_seeds" to 8, "tomato_seeds" to 4,
            "wheat_seeds" to 10, "fertilizer" to 6, "hay" to 10, "clover_mix" to 6, "salad" to 1)) s.inventory[k] = n
        s.machines.getValue("mill").queue += dev.mursal.dewfield.model.MachineJob("mill_wheat")
        s.machines.getValue("mill").ready["hay"] = 5
        s.hotItem = "carrot"
        return f
    }
}
