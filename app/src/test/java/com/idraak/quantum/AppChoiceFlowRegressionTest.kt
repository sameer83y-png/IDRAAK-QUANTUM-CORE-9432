package com.idraak.quantum

import com.idraak.quantum.engine.OfflinePhoneCommandHandler
import org.junit.Assert.assertEquals
import org.junit.Test

class AppChoiceFlowRegressionTest {
    @Test
    fun resolvesNumberedAndOrdinalAppChoicesAcrossLanguages() {
        assertEquals(0, OfflinePhoneCommandHandler.resolveChoiceIndex("1", 2))
        assertEquals(1, OfflinePhoneCommandHandler.resolveChoiceIndex("number 2", 2))
        assertEquals(0, OfflinePhoneCommandHandler.resolveChoiceIndex("pehla", 2))
        assertEquals(1, OfflinePhoneCommandHandler.resolveChoiceIndex("دوسرا", 2))
        assertEquals(1, OfflinePhoneCommandHandler.resolveChoiceIndex("दूसरा", 2))
        assertEquals(null, OfflinePhoneCommandHandler.resolveChoiceIndex("third", 2))
    }

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
    @Test
    fun splitsMultiwordRomanUrduSeparatorWithoutLeavingItsSecondWord() {
        assertEquals(
            listOf("youtube", "instagram"),
            OfflinePhoneCommandHandler.splitAppChoices("YouTube ya phir Instagram")
        )
    }

    @Test
    fun splitsMultiwordHindiSeparatorWithoutLeavingItsSecondWord() {
        assertEquals(
            listOf("यूट्यूब", "व्हाट्सएप"),
            OfflinePhoneCommandHandler.splitAppChoices("यूट्यूब या फिर व्हाट्सएप")
        )
    }

    @Test
    fun splitsMultiwordUrduSeparatorWithoutLeavingItsSecondWord() {
        assertEquals(
            listOf("یوٹیوب", "واٹس ایپ"),
            OfflinePhoneCommandHandler.splitAppChoices("یوٹیوب یا پھر واٹس ایپ")
        )
    }

    @Test
    fun rejectsOutOfRangeAndUnknownOrdinalChoices() {
        assertEquals(null, OfflinePhoneCommandHandler.resolveChoiceIndex("5", 2))
        assertEquals(null, OfflinePhoneCommandHandler.resolveChoiceIndex("fourth", 2))
        assertEquals(2, OfflinePhoneCommandHandler.resolveChoiceIndex("third", 3))
    }

}
