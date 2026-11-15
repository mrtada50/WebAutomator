package com.webautomator.app.engine

import com.webautomator.app.data.ConditionType
import java.util.Locale
import kotlin.math.abs

data class Comparison(val changed: Boolean, val summary: String, val first: Boolean)

object ValueComparator {
    fun compare(prev: Map<String, String>?, cur: Map<String, String>): Comparison {
        if (prev == null) return Comparison(false, "", true)
        val out = ArrayList<String>()
        for ((k, v) in cur) {
            val p = prev[k]
            if (p == null) {
                out.add("+ $k: ${clip(v)}")
            } else if (p != v) {
                out.add(describe(k, p, v))
            }
        }
        for (k in prev.keys) {
            if (!cur.containsKey(k)) out.add("- $k")
        }
        return Comparison(out.isNotEmpty(), out.joinToString("\n"), false)
    }

    private fun describe(k: String, p: String, v: String): String {
        val pn = NumberParser.parseStrict(p)
        val cn = NumberParser.parseStrict(v)
        if (pn != null && cn != null) {
            val d = cn - pn
            val sb = StringBuilder("$k: ${NumberParser.format(pn)} → ${NumberParser.format(cn)} (${signed(d)}")
            if (pn != 0.0) sb.append(", ").append(String.format(Locale.US, "%+.1f%%", d / abs(pn) * 100))
            sb.append(")")
            return sb.toString()
        }
        if (p.contains('\n') || v.contains('\n')) {
            val pl = p.lines().toSet()
            val cl = v.lines().toSet()
            val sb = StringBuilder("$k:")
            for (x in cl - pl) sb.append("\n+ ").append(clip(x))
            for (x in pl - cl) sb.append("\n- ").append(clip(x))
            return sb.toString()
        }
        return "$k: ${clip(p)} → ${clip(v)}"
    }

    private fun signed(d: Double): String = (if (d > 0) "+" else "") + NumberParser.format(d)

    private fun clip(s: String, n: Int = 120): String = if (s.length > n) s.take(n) + "…" else s
}

object Condition {
    fun evaluate(type: ConditionType, target: String?, ref: String, previous: String?): Boolean {
        if (target == null) return false
        return when (type) {
            ConditionType.NONE -> false
            ConditionType.GREATER -> compareNum(target, ref) { a, b -> a > b }
            ConditionType.LESS -> compareNum(target, ref) { a, b -> a < b }
            ConditionType.EQUAL -> {
                val a = NumberParser.parseStrict(target)
                val b = NumberParser.parse(ref)
                if (a != null && b != null) a == b else target.trim().equals(ref.trim(), ignoreCase = true)
            }
            ConditionType.CONTAINS -> target.contains(ref, ignoreCase = true)
            ConditionType.NOT_CONTAINS -> !target.contains(ref, ignoreCase = true)
            ConditionType.CHANGED -> previous != null && previous != target
        }
    }

    private fun compareNum(target: String, ref: String, op: (Double, Double) -> Boolean): Boolean {
        val a = NumberParser.parse(target) ?: return false
        val b = NumberParser.parse(ref) ?: return false
        return op(a, b)
    }
}
