package com.webautomator.app.engine

import java.util.Locale
import kotlin.math.floor

object NumberParser {
    private val FIND = Regex("-?\\d[\\d.,]*")
    private val STRICT = Regex("^[-+]?\\d[\\d.,]*$")

    private fun normalizeDigits(s: String): String {
        val sb = StringBuilder()
        for (ch in s) {
            when (ch) {
                in '\u0660'..'\u0669' -> sb.append('0' + (ch - '\u0660'))
                in '\u06F0'..'\u06F9' -> sb.append('0' + (ch - '\u06F0'))
                '\u066B' -> sb.append('.')
                '\u066C' -> sb.append(',')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /** First number found in the text, or null. */
    fun parse(text: String): Double? {
        val m = FIND.find(normalizeDigits(text)) ?: return null
        return convert(m.value)
    }

    /** Parses only when the whole text looks like a number. */
    fun parseStrict(text: String): Double? {
        val t = normalizeDigits(text.trim())
        if (!STRICT.matches(t)) return null
        return convert(t.removePrefix("+"))
    }

    private fun convert(raw: String): Double? {
        var s = raw.trimEnd('.', ',')
        val neg = s.startsWith("-")
        s = s.removePrefix("-")
        if (s.isEmpty()) return null
        val lastDot = s.lastIndexOf('.')
        val lastComma = s.lastIndexOf(',')
        val normalized = when {
            lastDot >= 0 && lastComma >= 0 ->
                if (lastDot > lastComma) s.replace(",", "") else s.replace(".", "").replace(',', '.')
            lastComma >= 0 -> {
                val count = s.count { it == ',' }
                val digitsAfter = s.length - lastComma - 1
                if (count > 1 || digitsAfter == 3) s.replace(",", "") else s.replace(',', '.')
            }
            lastDot >= 0 -> {
                if (s.count { it == '.' } > 1) s.replace(".", "") else s
            }
            else -> s
        }
        val d = normalized.toDoubleOrNull() ?: return null
        return if (neg) -d else d
    }

    fun format(d: Double): String {
        if (d == floor(d) && Math.abs(d) < 1e15) return d.toLong().toString()
        return String.format(Locale.US, "%.4f", d).trimEnd('0').trimEnd('.')
    }
}
