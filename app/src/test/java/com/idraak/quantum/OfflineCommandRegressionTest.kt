package com.idraak.quantum

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineCommandRegressionTest {

    @Test
    fun normalizesPoliteUrduYoutubeSearch() {
        assertEquals(
            "یوٹیوب پر موسم کی خبریں تلاش کرو",
            MultilingualCommandNormalizer.normalize("یوٹیوب کھولیں اور موسم کی خبریں تلاش کریں")
        )
    }

    @Test
    fun normalizesYoutubeSearchWithSpeechPunctuation() {
        assertEquals(
            "یوٹیوب پر نعت تلاش کرو",
            MultilingualCommandNormalizer.normalize("یوٹیوب کھول دو اور نعت تلاش کرو۔")
        )
    }

    @Test
    fun preservesOrdinaryConversationAboutYoutube() {
        val message = "مجھے یوٹیوب پسند ہے"
        assertEquals(message, MultilingualCommandNormalizer.normalize(message))
    }

    @Test
    fun recognizesPoliteUrduVoiceStopCommand() {
        assertTrue(
            VoiceSessionCommandHandler.isStopListeningCommand("ادراک، سننا بند کریں۔")
        )
    }

    @Test
    fun doesNotTreatBareStopAsVoiceStopCommand() {
        assertFalse(VoiceSessionCommandHandler.isStopListeningCommand("بس"))
    }
}
