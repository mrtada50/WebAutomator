package com.webautomator.app.engine

/**
 * Recognizes when a site is refusing automated access (rate limits, human-verification pages).
 * The app never tries to get past these: it pauses the script and tells the user.
 */
object BlockDetector {
    private val TEXT_MARKERS = listOf(
        "captcha", "are you a robot", "are you human", "verify you are human", "verify you are a human",
        "unusual traffic", "access denied", "just a moment", "checking your browser",
        "attention required", "too many requests",
        "تحقق من أنك", "لست روبوت", "لست برنامج"
    )

    fun isBlockStatus(code: Int): Boolean = code == 403 || code == 429

    fun isBlockText(text: String): Boolean {
        val t = text.lowercase()
        return TEXT_MARKERS.any { t.contains(it) }
    }
}
