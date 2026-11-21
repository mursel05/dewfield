package dev.mursal.dewfield.i18n

import dev.mursal.dewfield.model.Msg
import java.io.Reader
import java.util.Properties

/** Languages the game ships with. [code] is the bundle suffix and the saved setting. */
enum class Lang(val code: String) {
    EN("en"), AZ("az");

    companion object {
        fun of(code: String?): Lang = entries.firstOrNull { it.code == code } ?: EN
    }
}

/**
 * Translated text. Values are `.properties` entries with `{0}`, `{1}` placeholders.
 * A [Msg] argument is translated recursively, so the rules layer can say
 * `Msg("msg.sold", 3, Msg.item("egg"), 90)` and the UI gets "Sold 3 Egg for 90".
 *
 * Missing keys fall back to English, then to the key itself, so a gap never crashes the game.
 */
class Strings(val lang: Lang, private val table: Map<String, String>, private val fallback: Map<String, String> = emptyMap()) {

    fun has(key: String): Boolean = key in table || key in fallback

    operator fun get(key: String): String = table[key] ?: fallback[key] ?: key

    fun format(key: String, vararg args: Any): String = fill(get(key), args.asList())

    fun msg(m: Msg): String = fill(get(m.key), m.args)

    private fun fill(pattern: String, args: List<Any>): String {
        if (args.isEmpty() || '{' !in pattern) return pattern
        val out = StringBuilder(pattern.length + 16)
        var i = 0
        while (i < pattern.length) {
            val c = pattern[i]
            if (c == '{') {
                val end = pattern.indexOf('}', i)
                val index = if (end > i) pattern.substring(i + 1, end).toIntOrNull() else null
                if (index != null && index in args.indices) {
                    out.append(text(args[index]))
                    i = end + 1
                    continue
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }

    private fun text(arg: Any): String = when (arg) {
        is Msg -> msg(arg)
        is Float -> if (arg == arg.toInt().toFloat()) arg.toInt().toString() else "%.1f".format(arg)
        else -> arg.toString()
    }

    companion object {
        fun parse(reader: Reader): Map<String, String> {
            val p = Properties()
            reader.use { p.load(it) }
            return p.stringPropertyNames().associateWith { p.getProperty(it) }
        }

        /** Placeholder indexes used by a pattern, for the bundle consistency test. */
        fun placeholders(pattern: String): Set<Int> =
            Regex("""\{(\d+)\}""").findAll(pattern).map { it.groupValues[1].toInt() }.toSet()
    }
}
