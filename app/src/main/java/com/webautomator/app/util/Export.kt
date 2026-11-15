package com.webautomator.app.util

import com.webautomator.app.data.MapJson
import com.webautomator.app.data.RunEntity
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Export {
    private fun time(ts: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(ts))

    fun csvEscape(s: String): String {
        val needs = s.contains(',') || s.contains('"') || s.contains('\n') || s.contains('\r')
        return if (needs) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }

    fun csv(runs: List<RunEntity>): String {
        val sb = StringBuilder("\uFEFF")
        sb.append("time,script,success,changed,key,value,change_summary,error\n")
        for (r in runs) {
            val values = MapJson.decode(r.valuesJson)
            if (r.success && values.isNotEmpty()) {
                var first = true
                for ((k, v) in values) {
                    sb.append(
                        listOf(
                            time(r.timestamp), r.scriptName, "true", r.changed.toString(), k, v,
                            if (first) r.changeSummary else "", ""
                        ).joinToString(",") { csvEscape(it) }
                    ).append('\n')
                    first = false
                }
            } else {
                sb.append(
                    listOf(
                        time(r.timestamp), r.scriptName, r.success.toString(), r.changed.toString(),
                        "", "", r.changeSummary, r.error
                    ).joinToString(",") { csvEscape(it) }
                ).append('\n')
            }
        }
        return sb.toString()
    }

    fun json(runs: List<RunEntity>): String {
        val arr = JSONArray()
        for (r in runs) {
            val o = JSONObject()
            o.put("time", time(r.timestamp))
            o.put("script", r.scriptName)
            o.put("success", r.success)
            o.put("changed", r.changed)
            val vals = JSONObject()
            for ((k, v) in MapJson.decode(r.valuesJson)) vals.put(k, v)
            o.put("values", vals)
            o.put("change", r.changeSummary)
            o.put("error", r.error)
            arr.put(o)
        }
        return arr.toString(2)
    }
}
