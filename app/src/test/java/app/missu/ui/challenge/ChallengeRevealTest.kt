package app.missu.ui.challenge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeRevealTest {

    @Test
    fun `real challenge and preview reveal immediately`() {
        assertTrue(shouldRevealChallenge(true, blankPage = false, identifiedPage = true, attempts = 1, preview = false))
        assertTrue(shouldRevealChallenge(false, blankPage = false, identifiedPage = false, attempts = 1, preview = true))
    }

    @Test
    fun `transient blank and unidentified pages stay hidden during normal navigation`() {
        assertFalse(shouldRevealChallenge(false, blankPage = true, identifiedPage = true, attempts = 1, preview = false))
        assertFalse(shouldRevealChallenge(false, blankPage = false, identifiedPage = false, attempts = 1, preview = false))
        assertFalse(shouldRevealChallenge(false, blankPage = true, identifiedPage = true, attempts = 11, preview = false))
        assertFalse(shouldRevealChallenge(false, blankPage = false, identifiedPage = false, attempts = 11, preview = false))
    }

    @Test
    fun `persistent blank and unidentified pages reveal after the grace period`() {
        assertTrue(shouldRevealChallenge(false, blankPage = true, identifiedPage = true, attempts = 12, preview = false))
        assertTrue(shouldRevealChallenge(false, blankPage = false, identifiedPage = false, attempts = 12, preview = false))
    }

    @Test
    fun `identified nonempty normal page never reveals`() {
        assertFalse(shouldRevealChallenge(false, blankPage = false, identifiedPage = true, attempts = 120, preview = false))
    }
}
