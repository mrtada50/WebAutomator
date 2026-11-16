package com.webautomator.app.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockDetectorTest {
    @Test fun statusCodes() {
        assertTrue(BlockDetector.isBlockStatus(429))
        assertTrue(BlockDetector.isBlockStatus(403))
        assertFalse(BlockDetector.isBlockStatus(200))
        assertFalse(BlockDetector.isBlockStatus(404))
    }

    @Test fun pageText() {
        assertTrue(BlockDetector.isBlockText("Just a moment..."))
        assertTrue(BlockDetector.isBlockText("Please verify you are human"))
        assertTrue(BlockDetector.isBlockText("تحقق من أنك لست روبوت"))
        assertFalse(BlockDetector.isBlockText("Product price 125 USD"))
    }
}
