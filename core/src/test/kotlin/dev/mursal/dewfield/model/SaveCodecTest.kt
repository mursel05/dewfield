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
}
