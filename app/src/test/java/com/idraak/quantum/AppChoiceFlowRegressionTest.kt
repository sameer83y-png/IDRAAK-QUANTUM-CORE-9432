package com.idraak.quantum

import com.idraak.quantum.engine.OfflinePhoneCommandHandler
import org.junit.Assert.assertEquals
import org.junit.Test

class AppChoiceFlowRegressionTest {
    @Test
    fun splitsEnglishAppAlternatives() {
        assertEquals(
            listOf("youtube", "whatsapp"),
            OfflinePhoneCommandHandler.splitAppChoices("YouTube or WhatsApp")
        )
    }

    @Test
    fun splitsRomanUrduAppAlternatives() {
        assertEquals(
            listOf("youtube", "instagram"),
            OfflinePhoneCommandHandler.splitAppChoices("YouTube ya Instagram")
        )
    }

    @Test
    fun splitsUrduAndHindiAppAlternatives() {
        assertEquals(
            listOf("یوٹیوب", "واٹس ایپ"),
            OfflinePhoneCommandHandler.splitAppChoices("یوٹیوب یا واٹس ایپ")
        )
        assertEquals(
            listOf("यूट्यूब", "व्हाट्सएप"),
            OfflinePhoneCommandHandler.splitAppChoices("यूट्यूब या व्हाट्सएप")
        )
    }
}
