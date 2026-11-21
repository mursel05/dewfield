package dev.mursal.dewfield.i18n

import dev.mursal.dewfield.gfx.Fonts
import dev.mursal.dewfield.model.Category
import dev.mursal.dewfield.model.ItemDb
import dev.mursal.dewfield.model.Msg
import dev.mursal.dewfield.model.Weather
import dev.mursal.dewfield.ui.TvPanel
import dev.mursal.dewfield.world.Locations
import dev.mursal.dewfield.world.ObjKind
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StringsTest {
    private val dir = File("../assets/i18n")
    private val en = Strings.parse(File(dir, "strings_en.properties").reader(Charsets.UTF_8))
    private val az = Strings.parse(File(dir, "strings_az.properties").reader(Charsets.UTF_8))

    @Test
    fun `both languages have the same keys`() {
        assertEquals(emptySet(), en.keys - az.keys, "missing in Azerbaijani")
        assertEquals(emptySet(), az.keys - en.keys, "missing in English")
    }

    @Test
    fun `placeholders match between languages`() {
        val bad = en.keys.filter { Strings.placeholders(en.getValue(it)) != Strings.placeholders(az.getValue(it)) }
        assertEquals(emptyList(), bad)
    }

    @Test
    fun `every key the code uses exists`() {
        val literal = Regex(""""((?:msg|ui|hud|shop|bag|cook|orders|machine|morning|level|pause|stats|title|tv|sleep|hint|sign)\.[a-z0-9_.]+)"""")
        val used = File("src/main/kotlin").walkTopDown().filter { it.extension == "kt" }
            .flatMap { f -> literal.findAll(f.readText()).map { it.groupValues[1] } }
            .filterNot { it.endsWith(".") }
            .toSet()
        assertTrue(used.size > 150, "scan found too few keys: ${used.size}")
        assertEquals(emptySet(), used - en.keys)
    }

    @Test
    fun `every game id has a name`() {
        val keys = buildSet {
            ItemDb.items.keys.forEach { add("item.$it") }
            ItemDb.animals.keys.forEach { add("animal.$it") }
            ItemDb.machines.values.forEach { add("machine.${it.id}"); if (it.price > 0) add("machine.${it.id}.about") }
            ItemDb.upgrades.keys.forEach { add("upgrade.$it"); add("upgrade.$it.step"); add("upgrade.$it.max") }
            ItemDb.orderClients.forEach { add("client.$it") }
            ItemDb.animals.values.forEach { add("feed.${it.feed}") }
            ItemDb.troughs.keys.forEach { add("trough.$it") }
            Weather.entries.forEach { w -> listOf("weather", "tv.weather", "morning.weather").forEach { add("$it.${w.name.lowercase()}") } }
            Category.entries.forEach { add("category.${it.name.lowercase()}") }
            (0..2).forEach { add("quality.$it") }
            listOf("coop", "barn").forEach { add("building.$it") }
            Locations.ALL.forEach { add("loc.$it") }
            (0 until TvPanel.TIPS).forEach { add("tip.$it") }
            Locations.ALL.map { Locations.create(it) }.flatMap { it.objects }.filter { it.kind != ObjKind.DECOR }
                .forEach { add("about.${it.id.trimEnd('1', '2', '3', '4')}") }
        }
        assertEquals(emptySet(), keys - en.keys)
    }

    @Test
    fun `the font has every character the strings use`() {
        val missing = (en.values + az.values).flatMap { it.toList() }.toSet().filterNot { it in Fonts.CHARS || it == ' ' }
        assertEquals(emptyList(), missing.sorted(), "add these to Fonts.CHARS (and check the font has them)")
    }

    @Test
    fun `messages translate nested arguments`() {
        val s = Strings(Lang.AZ, az, en)
        assertEquals("3 Yumurta 90 qızıla satıldı.", s.msg(Msg("msg.sold", 3, Msg.item("egg"), 90)))
        val e = Strings(Lang.EN, en)
        assertEquals("Sold Egg ×3 for 90 coins.", e.msg(Msg("msg.sold", 3, Msg.item("egg"), 90)))
        assertEquals("no.such.key", e["no.such.key"])
    }

    @Test
    fun `missing keys fall back to English`() {
        val s = Strings(Lang.AZ, mapOf("a" to "A-az"), mapOf("a" to "A", "b" to "B"))
        assertEquals("A-az", s["a"])
        assertEquals("B", s["b"])
    }
}
