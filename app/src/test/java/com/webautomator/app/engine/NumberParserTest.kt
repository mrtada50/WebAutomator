package com.webautomator.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumberParserTest {
    @Test fun parsesCommonFormats() {
        assertEquals(1234.56, NumberParser.parse("Price: $1,234.56")!!, 0.0001)
        assertEquals(1234.56, NumberParser.parse("1.234,56")!!, 0.0001)
        assertEquals(125.0, NumberParser.parse("125")!!, 0.0001)
        assertEquals(12.5, NumberParser.parse("12,5")!!, 0.0001)
        assertEquals(1299.0, NumberParser.parse("1,299")!!, 0.0001)
        assertEquals(-15.0, NumberParser.parse("-15")!!, 0.0001)
    }

    @Test fun parsesArabicDigits() {
        assertEquals(34.5, NumberParser.parse("٣٤٫٥")!!, 0.0001)
    }

    @Test fun noNumberIsNull() {
        assertNull(NumberParser.parse("abc"))
    }

    @Test fun strictRequiresWholeText() {
        assertEquals(125.0, NumberParser.parseStrict("125")!!, 0.0001)
        assertNull(NumberParser.parseStrict("Price 125"))
    }

    @Test fun formatsCleanly() {
        assertEquals("125", NumberParser.format(125.0))
        assertEquals("12.5", NumberParser.format(12.5))
        assertEquals("-15", NumberParser.format(-15.0))
    }
}
