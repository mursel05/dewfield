package dev.mursal.dewfield.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SaveCodecTest {

    @Test
    fun `round trip keeps the whole state`() {
        val f = newFarm()
        f.till(Cell(0, 0))
        f.plant(Cell(0, 0), "radish_seeds")
        f.add("carrot#2", 3)
        f.add("wheat", 4)
        f.loadMachine("mill", "mill_wheat", 2)
        f.state.playerX = 3.5f
        f.state.playerY = 7.25f
        f.state.money = 1234
        f.endDay()
        f.state.seenHints += "intro"

        val text = SaveCodec.encode(f.state)
        val back = assertNotNull(SaveCodec.decode(text, TEST_FIELD))
        assertEquals(f.state, back)
        assertEquals(text, SaveCodec.encode(back))
    }

    @Test
    fun `a fresh game with no player position survives a round trip`() {
        val f = newFarm()
        val back = assertNotNull(SaveCodec.decode(SaveCodec.encode(f.state), TEST_FIELD))
        assertTrue(back.playerX.isNaN())
    }

    @Test
    fun `garbage and future versions are rejected`() {
        assertNull(SaveCodec.decode("not json", TEST_FIELD))
        assertNull(SaveCodec.decode("{\"version\": 99}", TEST_FIELD))
        assertNull(SaveCodec.decode("[1,2,3]", TEST_FIELD))
    }

    @Test
    fun `damaged values are repaired`() {
        val f = newFarm()
        val text = SaveCodec.encode(f.state)
            .replace("\"energy\":100", "\"energy\":900")
            .replace("\"radish_seeds\":6", "\"radish_seeds\":6,\"dragonfruit\":3")
        val s = assertNotNull(SaveCodec.decode(text, TEST_FIELD))
        assertEquals(Farm.MAX_ENERGY, s.energy)
        assertTrue("dragonfruit" !in s.inventory)
        assertEquals(6, s.inventory["radish_seeds"])
    }

    @Test
    fun `missing fields fall back to defaults and plots match the field`() {
        val s = assertNotNull(SaveCodec.decode("{\"version\":2,\"day\":5,\"money\":77}", TEST_FIELD))
        assertEquals(5, s.day)
        assertEquals(77, s.money)
        assertEquals(TEST_FIELD.allCells.size, s.plots.size)
        assertTrue(s.machines.containsKey("mill"))
        val farm = Farm(s, TEST_FIELD)
        assertTrue(farm.till(Cell(0, 0)).ok)
    }

    @Test
    fun `version 2 saves move their plots onto the new field and forget the old standing spot`() {
        val old = FieldLayout(listOf(listOf(Cell(20, 20), Cell(21, 20)), listOf(Cell(30, 20), Cell(31, 20))))
        val f = Farm.newGame(7L, old)
        f.till(Cell(21, 20))
        f.plant(Cell(21, 20), "radish_seeds")
        f.state.version = 2
        f.state.playerX = 25.5f
        f.state.playerY = 20.5f
        val newField = FieldLayout(listOf(listOf(Cell(0, 0), Cell(0, 1)), listOf(Cell(5, 0), Cell(5, 1))))

        val s = assertNotNull(SaveCodec.decode(SaveCodec.encode(f.state), newField, old))
        assertEquals(SAVE_VERSION, s.version)
        assertEquals(newField.allCells.toSet(), s.plots.map { it.cell }.toSet())
        val moved = s.plots.single { it.cell == Cell(0, 1) }
        assertTrue(moved.tilled)
        assertEquals("radish", moved.crop)
        assertTrue(s.plots.filter { it.cell != Cell(0, 1) }.none { it.tilled })
        assertTrue(s.playerX.isNaN() && s.playerY.isNaN())
    }
}
