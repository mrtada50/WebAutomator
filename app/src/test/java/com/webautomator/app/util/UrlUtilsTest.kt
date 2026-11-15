package com.webautomator.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlUtilsTest {
    @Test fun addsHttpsWhenMissing() {
        assertEquals(UrlResult.Valid("https://example.com"), UrlUtils.normalize("example.com"))
    }

    @Test fun keepsHttpAndTrims() {
        assertEquals(UrlResult.Valid("http://a.com/x"), UrlUtils.normalize("  http://a.com/x "))
    }

    @Test fun rejectsBadInput() {
        assertEquals(UrlResult.Invalid, UrlUtils.normalize("   "))
        assertEquals(UrlResult.Invalid, UrlUtils.normalize("not a url"))
        assertEquals(UrlResult.Invalid, UrlUtils.normalize("ftp://x.com"))
        assertEquals(UrlResult.Invalid, UrlUtils.normalize("javascript:alert(1)"))
        assertEquals(UrlResult.Invalid, UrlUtils.normalize("localhost"))
    }

    @Test fun sameSiteRules() {
        assertTrue(UrlUtils.sameSite("www.a.com", "a.com"))
        assertTrue(UrlUtils.sameSite("m.a.com", "www.a.com"))
        assertFalse(UrlUtils.sameSite("a.com", "b.com"))
    }

    @Test fun pageKeyIgnoresFragmentAndSlash() {
        assertEquals("https://a.com/x", UrlUtils.pageKey("https://a.com/x/#top"))
        assertEquals("www.a.com", UrlUtils.hostOf("https://www.a.com/p"))
    }
}
