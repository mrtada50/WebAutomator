package com.webautomator.app.data

import com.webautomator.app.util.Export
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataTest {
    @Test fun actionJsonRoundTrip() {
        val list = listOf(
            Action(ActionType.OPEN_URL, value = "https://a.com"),
            Action(ActionType.EXTRACT_TEXT, selector = "#price", name = "price")
        )
        assertEquals(list, ActionJson.decode(ActionJson.encode(list)))
    }

    @Test fun xpathRoundTripAndLocator() {
        val a = Action(ActionType.CLICK, selector = "#go", xpath = "//button[1]")
        val back = ActionJson.decode(ActionJson.encode(listOf(a)))[0]
        assertEquals("//button[1]", back.xpath)
        assertTrue(back.hasLocator())
        assertTrue(Action(ActionType.CLICK, xpath = "//a").hasLocator())
        assertEquals(false, Action(ActionType.CLICK).hasLocator())
    }

    private val run = RunEntity(
        scriptId = 1, scriptName = "script1", timestamp = 0L, success = true,
        valuesJson = MapJson.encode(mapOf("price" to "125")),
        changeSummary = "", error = "", changed = false
    )

    @Test fun csvQuoting() {
        assertEquals("\"a,b\"", Export.csvEscape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", Export.csvEscape("say \"hi\""))
        assertEquals("plain", Export.csvEscape("plain"))
    }

    @Test fun csvContainsRow() {
        assertTrue(Export.csv(listOf(run)).contains("script1,true,false,price,125,"))
    }

    @Test fun jsonExport() {
        val arr = JSONArray(Export.json(listOf(run)))
        assertEquals(1, arr.length())
        assertEquals("125", arr.getJSONObject(0).getJSONObject("values").getString("price"))
    }
}
