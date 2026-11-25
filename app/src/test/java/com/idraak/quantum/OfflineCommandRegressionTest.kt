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
    fun recognizesUrduQuietPhraseWithAssistantName() {
        assertTrue(
            VoiceSessionCommandHandler.isStopListeningCommand("ادراک، خاموش ہو جاؤ!")
        )
    }

    @Test
    fun recognizesUrduQuietPhraseWithoutAssistantName() {
        assertTrue(
            VoiceSessionCommandHandler.isStopListeningCommand("خاموش ہو جائیں۔")
        )
    }


    @Test
    fun requiresConfirmationBeforeGrantingPermissionByVoice() {
        val result = com.idraak.quantum.engine.OfflineScreenCommandHandler.execute(
            "کلک کرو اجازت دیں"
        )
        assertEquals("CLICK_CONFIRMATION_REQUIRED", result.intent)
    }

    @Test
    fun requiresConfirmationBeforeClickingAndroidAllowButton() {
        val result = com.idraak.quantum.engine.OfflineScreenCommandHandler.execute(
            "click Allow"
        )
        assertEquals("CLICK_CONFIRMATION_REQUIRED", result.intent)
    }

    @Test
    fun requiresConfirmationBeforeAcceptingPermissionInArabic() {
        val result = com.idraak.quantum.engine.OfflineScreenCommandHandler.execute(
            "اضغط السماح"
        )
        assertEquals("CLICK_CONFIRMATION_REQUIRED", result.intent)
    }


    @Test
    fun requiresConfirmationBeforeAgreeingToTerms() {
        val result = com.idraak.quantum.engine.OfflineScreenCommandHandler.execute(
            "click Agree"
        )
        assertEquals("CLICK_CONFIRMATION_REQUIRED", result.intent)
    }

    @Test
    fun requiresConfirmationBeforeInstallingFromScreen() {
        val result = com.idraak.quantum.engine.OfflineScreenCommandHandler.execute(
            "click Install"
        )
        assertEquals("CLICK_CONFIRMATION_REQUIRED", result.intent)
    }

    @Test
    fun requiresConfirmationBeforeArabicInstallAction() {
        val result = com.idraak.quantum.engine.OfflineScreenCommandHandler.execute(
            "اضغط تثبيت"
        )
        assertEquals("CLICK_CONFIRMATION_REQUIRED", result.intent)
    }


    @Test
    fun requiresConfirmationBeforeSigningOut() {
        val result = com.idraak.quantum.engine.OfflineScreenCommandHandler.execute(
            "click Sign out"
        )
        assertEquals("CLICK_CONFIRMATION_REQUIRED", result.intent)
    }

    @Test
    fun requiresConfirmationBeforeDeletingAccountInUrdu() {
        val result = com.idraak.quantum.engine.OfflineScreenCommandHandler.execute(
            "کلک کریں اکاؤنٹ حذف"
        )
        assertEquals("CLICK_CONFIRMATION_REQUIRED", result.intent)
    }

    @Test
    fun requiresConfirmationBeforeArabicFactoryResetAction() {
        val result = com.idraak.quantum.engine.OfflineScreenCommandHandler.execute(
            "اضغط ضبط المصنع"
        )
        assertEquals("CLICK_CONFIRMATION_REQUIRED", result.intent)
    }

    @Test
    fun requiresConfirmationBeforeHindiAccountRemoval() {
        val result = com.idraak.quantum.engine.OfflineScreenCommandHandler.execute(
            "दबाओ खाता हटाएं"
        )
        assertEquals("CLICK_CONFIRMATION_REQUIRED", result.intent)
    }


    @Test
    fun recognizesEnglishActionRefusal() {
        assertTrue(MultilingualCommandNormalizer.isExplicitRefusal("don't call Rahul"))
    }

    @Test
    fun recognizesHindiActionRefusal() {
        assertTrue(MultilingualCommandNormalizer.isExplicitRefusal("राहुल को कॉल मत करो"))
    }

    @Test
    fun recognizesRomanUrduActionRefusal() {
        assertTrue(MultilingualCommandNormalizer.isExplicitRefusal("Rahul ko call mat karo"))
    }

    @Test
    fun recognizesUrduActionRefusal() {
        assertTrue(MultilingualCommandNormalizer.isExplicitRefusal("احمد کو کال مت کرو"))
    }

    @Test
    fun recognizesArabicActionRefusal() {
        assertTrue(MultilingualCommandNormalizer.isExplicitRefusal("لا تتصل بأحمد"))
    }

    @Test
    fun doesNotTreatNormalCommandAsRefusal() {
        assertFalse(MultilingualCommandNormalizer.isExplicitRefusal("یوٹیوب کھولو"))
        assertFalse(MultilingualCommandNormalizer.isExplicitRefusal("call Rahul"))
    }

    @Test
    fun doesNotTreatBareStopAsVoiceStopCommand() {
        assertFalse(VoiceSessionCommandHandler.isStopListeningCommand("بس"))
    }
}
