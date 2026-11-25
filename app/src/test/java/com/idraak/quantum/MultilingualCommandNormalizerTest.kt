package com.idraak.quantum

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MultilingualCommandNormalizerTest {

    @Test
    fun normalizesEnglishYoutubeSearch() {
        assertEquals(
            "یوٹیوب پر lo-fi study music تلاش کرو",
            MultilingualCommandNormalizer.normalize(
                "Open YouTube and search for lo-fi study music"
            )
        )
    }

    @Test
    fun normalizesHinglishYoutubeSearch() {
        assertEquals(
            "یوٹیوب پر cricket highlights تلاش کرو",
            MultilingualCommandNormalizer.normalize(
                "YouTube kholo aur cricket highlights search karo"
            )
        )
    }

    @Test
    fun normalizesHindiYoutubeSearch() {
        assertEquals(
            "یوٹیوب پر पुराने गाने تلاش کرو",
            MultilingualCommandNormalizer.normalize(
                "यूट्यूब खोलो और पुराने गाने खोजो"
            )
        )
    }

    @Test
    fun normalizesUrduYoutubeSearch() {
        assertEquals(
            "یوٹیوب پر موسم کی خبریں تلاش کرو",
            MultilingualCommandNormalizer.normalize(
                "یوٹیوب کھولو اور موسم کی خبریں تلاش کرو"
            )
        )
    }

    @Test
    fun normalizesCommonAppOpenPhrase() {
        assertEquals(
            "کھولو YouTube",
            MultilingualCommandNormalizer.normalize("Please open YouTube")
        )
    }

    @Test
    fun preservesExplicitRefusal() {
        assertEquals(
            "مت کھولو youtube",
            MultilingualCommandNormalizer.normalize("Don't open YouTube")
        )
    }

    @Test
    fun leavesUnknownNaturalLanguageUnchangedForClarificationFlow() {
        val input = "Could you do that thing from yesterday?"
        assertEquals(input, MultilingualCommandNormalizer.normalize(input))
    }

    @Test
    fun blankInputRemainsBlank() {
        assertEquals("", MultilingualCommandNormalizer.normalize("   "))
    }
}
