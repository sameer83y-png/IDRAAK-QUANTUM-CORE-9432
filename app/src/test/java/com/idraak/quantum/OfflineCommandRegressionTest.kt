package com.idraak.quantum

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.idraak.quantum.engine.OfflinePhoneCommandHandler
import com.idraak.quantum.engine.MemoryFeedbackHandler
import com.idraak.quantum.engine.DeviceStatusController

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
    fun recognizesShortHindiDoNotOpenPhrase() {
        assertTrue(MultilingualCommandNormalizer.isExplicitRefusal("मत खोलो यूट्यूब"))
    }

    @Test
    fun recognizesShortUrduDoNotOpenPhrase() {
        assertTrue(MultilingualCommandNormalizer.isExplicitRefusal("مت کھولو یوٹیوب"))
    }

    @Test
    fun recognizesRomanUrduDoNotOpenPhrase() {
        assertTrue(MultilingualCommandNormalizer.isExplicitRefusal("mat kholo YouTube"))
    }

    @Test
    fun doesNotTreatNormalCommandAsRefusal() {
        assertFalse(MultilingualCommandNormalizer.isExplicitRefusal("یوٹیوب کھولو"))
        assertFalse(MultilingualCommandNormalizer.isExplicitRefusal("call Rahul"))
    }

    @Test
    fun suggestsRecoveryAfterLatestCommandNeedsClarification() {
        val suggestion = SmartSuggestionEngine.recoverySuggestion(
            listOf("OPEN_APP: SUCCESS", "UNRECOGNIZED_UTTERANCE: NEEDS_CLARIFICATION")
        )
        assertEquals("حکم مزید واضح کریں", suggestion?.title)
        assertEquals("توجہ طلب", suggestion?.category)
        assertEquals("مدد کرو", suggestion?.command)
    }

    @Test
    fun suggestsRecoveryAfterLatestCommandFails() {
        val suggestion = SmartSuggestionEngine.recoverySuggestion(
            listOf("OPEN_APP: SUCCESS", "SCREEN_CONTROL: FAILED")
        )
        assertEquals("پچھلا کام مکمل نہیں ہوا", suggestion?.title)
    }

    @Test
    fun doesNotShowRecoverySuggestionAfterSuccessfulLatestCommand() {
        val suggestion = SmartSuggestionEngine.recoverySuggestion(
            listOf("SCREEN_CONTROL: FAILED", "OPEN_APP: SUCCESS")
        )
        assertEquals(null, suggestion)
    }

    @Test
    fun doesNotShowRecoverySuggestionWithoutHistory() {
        assertEquals(null, SmartSuggestionEngine.recoverySuggestion(emptyList()))
    }


    @Test
    fun extractsAppNameFromNaturalUrduLaunchPhrases() {
        assertEquals("یوٹیوب", OfflinePhoneCommandHandler.extractAppName("یوٹیوب چلا کر دکھاؤ"))
        assertEquals("یوٹیوب", OfflinePhoneCommandHandler.extractAppName("یوٹیوب چلا کے رکھو"))
        assertEquals("یوٹیوب", OfflinePhoneCommandHandler.extractAppName("یوٹیوب کھول کے رکھو"))
        assertEquals("یوٹیوب", OfflinePhoneCommandHandler.extractAppName("یوٹیوب چالو کر دو"))
    }

    @Test
    fun extractsAppNameFromPoliteUrduLaunchPhrases() {
        assertEquals("یوٹیوب", OfflinePhoneCommandHandler.extractAppName("براہ کرم یوٹیوب کھول دیں"))
        assertEquals("یوٹیوب", OfflinePhoneCommandHandler.extractAppName("ذرا یوٹیوب کھول دو"))
    }

    @Test
    fun doesNotTreatBareStopAsVoiceStopCommand() {
        assertFalse(VoiceSessionCommandHandler.isStopListeningCommand("بس"))
    }


    @Test
    fun recognizesNaturalUrduCorrectionPhrases() {
        assertTrue(MemoryFeedbackHandler.isCorrectionRequest("یہ بات درست نہیں"))
        assertTrue(MemoryFeedbackHandler.isCorrectionRequest("آپ نے غلط بتایا"))
        assertTrue(MemoryFeedbackHandler.isCorrectionRequest("ذرا درست بتائیں"))
        assertTrue(MemoryFeedbackHandler.isCorrectionRequest("اسے صحیح کریں"))
    }

    @Test
    fun recognizesRomanUrduAndArabicCorrectionPhrases() {
        assertTrue(MemoryFeedbackHandler.isCorrectionRequest("yeh ghalat hai"))
        assertTrue(MemoryFeedbackHandler.isCorrectionRequest("إجابتك غير صحيحة"))
    }

    @Test
    fun doesNotTreatNormalConversationAsCorrection() {
        assertFalse(MemoryFeedbackHandler.isCorrectionRequest("مجھے یوٹیوب پسند ہے"))
        assertFalse(MemoryFeedbackHandler.isCorrectionRequest("یہ بہت اچھا ہے"))
    }

    @Test
    fun recognizesBatteryQuestionsAcrossSupportedLanguages() {
        assertTrue(DeviceStatusController.isBatteryStatusCommand("battery kitni hai"))
        assertTrue(DeviceStatusController.isBatteryStatusCommand("charger laga hai ya nahi"))
        assertTrue(DeviceStatusController.isBatteryStatusCommand("فون کی بیٹری کتنے فیصد ہے"))
        assertTrue(DeviceStatusController.isBatteryStatusCommand("चार्जर लगा है या नहीं"))
        assertTrue(DeviceStatusController.isBatteryStatusCommand("كم نسبة البطارية"))
    }

    @Test
    fun doesNotTreatUnrelatedConversationAsBatteryQuestion() {
        assertFalse(DeviceStatusController.isBatteryStatusCommand("مجھے یوٹیوب پسند ہے"))
        assertFalse(DeviceStatusController.isBatteryStatusCommand("آج موسم اچھا ہے"))
        assertFalse(DeviceStatusController.isBatteryStatusCommand("open camera"))
    }


    @Test
    fun prioritizesUnmuteOverMuteWhenCommandContainsBothWords() {
        assertEquals("UNMUTE", com.idraak.quantum.engine.HardwareController.classifyVolumeCommand("unmute volume"))
        assertEquals("UNMUTE", com.idraak.quantum.engine.HardwareController.classifyVolumeCommand("unmute sound!"))
        assertEquals("UNMUTE", com.idraak.quantum.engine.HardwareController.classifyVolumeCommand("آواز دوبارہ چالو کرو۔"))
        assertEquals("MUTE", com.idraak.quantum.engine.HardwareController.classifyVolumeCommand("mute volume"))
    }

    @Test
    fun recognizesVolumeCommandsWithPunctuationAndRejectsConversation() {
        assertEquals("UP", com.idraak.quantum.engine.HardwareController.classifyVolumeCommand("آواز بڑھاؤ!"))
        assertEquals("DOWN", com.idraak.quantum.engine.HardwareController.classifyVolumeCommand("volume down."))
        assertEquals("MAX", com.idraak.quantum.engine.HardwareController.classifyVolumeCommand("maximum volume"))
        assertEquals("MIN", com.idraak.quantum.engine.HardwareController.classifyVolumeCommand("volume zero"))
        assertEquals(null, com.idraak.quantum.engine.HardwareController.classifyVolumeCommand("مجھے موسیقی پسند ہے"))
    }


    @Test
    fun recognizesExplicitVoiceStopCommandsAcrossLanguages() {
        assertTrue(VoiceSessionCommandHandler.isStopListeningCommand("stop listening"))
        assertTrue(VoiceSessionCommandHandler.isStopListeningCommand("Idraak, stop listening now!"))
        assertTrue(VoiceSessionCommandHandler.isStopListeningCommand("sunna band karo"))
        assertTrue(VoiceSessionCommandHandler.isStopListeningCommand("सुनना बंद कर दो"))
        assertTrue(VoiceSessionCommandHandler.isStopListeningCommand("توقف عن الاستماع"))
        assertTrue(VoiceSessionCommandHandler.isStopListeningCommand("ادراک سننا بند کریں"))
    }

    @Test
    fun doesNotTreatBareMultilingualStopWordsAsVoiceStopCommands() {
        assertFalse(VoiceSessionCommandHandler.isStopListeningCommand("stop"))
        assertFalse(VoiceSessionCommandHandler.isStopListeningCommand("بس"))
        assertFalse(VoiceSessionCommandHandler.isStopListeningCommand("رک جاؤ"))
    }


    @Test
    fun contextualSuggestionsRecognizeRecentCommandsAcrossLanguages() {
        assertEquals("CAMERA", SmartSuggestionEngine.classifyRecentCommandCategory("camera kholo"))
        assertEquals("YOUTUBE", SmartSuggestionEngine.classifyRecentCommandCategory("यूट्यूब खोलो"))
        assertEquals("CALL", SmartSuggestionEngine.classifyRecentCommandCategory("call Rahul"))
        assertEquals("MESSAGE", SmartSuggestionEngine.classifyRecentCommandCategory("پیغام بھیجو"))
        assertEquals("HARDWARE", SmartSuggestionEngine.classifyRecentCommandCategory("آواز بڑھاؤ"))
        assertEquals("SCREEN", SmartSuggestionEngine.classifyRecentCommandCategory("scroll down"))
        assertEquals("MEMORY", SmartSuggestionEngine.classifyRecentCommandCategory("remember this"))
        assertEquals("APP", SmartSuggestionEngine.classifyRecentCommandCategory("open calculator"))
    }

    @Test
    fun contextualSuggestionsIgnoreUnrelatedConversation() {
        assertEquals(null, SmartSuggestionEngine.classifyRecentCommandCategory("آج موسم اچھا ہے"))
        assertEquals(null, SmartSuggestionEngine.classifyRecentCommandCategory("I like music"))
    }


    @Test
    fun recognizesPoliteVoiceStopCommandsAcrossLanguages() {
        assertTrue(VoiceSessionCommandHandler.isStopListeningCommand("please stop listening now"))
        assertTrue(VoiceSessionCommandHandler.isStopListeningCommand("kindly stop listening"))
        assertTrue(VoiceSessionCommandHandler.isStopListeningCommand("zara sunna band kar dein"))
        assertTrue(VoiceSessionCommandHandler.isStopListeningCommand("براہ کرم سننا بند کریں"))
        assertTrue(VoiceSessionCommandHandler.isStopListeningCommand("कृपया सुनना बंद कीजिए"))
        assertTrue(VoiceSessionCommandHandler.isStopListeningCommand("من فضلك توقف عن الاستماع"))
    }

    @Test
    fun politeStopParserDoesNotMatchUnrelatedRequests() {
        assertFalse(VoiceSessionCommandHandler.isStopListeningCommand("please open YouTube"))
        assertFalse(VoiceSessionCommandHandler.isStopListeningCommand("zara volume kam karo"))
        assertFalse(VoiceSessionCommandHandler.isStopListeningCommand("من فضلك افتح الكاميرا"))
    }


    @Test
    fun extractsAppNameWhenAssistantNameAndPoliteWordsAreIncluded() {
        assertEquals(
            "youtube",
            OfflinePhoneCommandHandler.extractAppName("Idraak, please open YouTube")
        )
        assertEquals(
            "یوٹیوب",
            OfflinePhoneCommandHandler.extractAppName("ادراک، ذرا یوٹیوب کھول دو")
        )
        assertEquals(
            "यूट्यूब",
            OfflinePhoneCommandHandler.extractAppName("इद्राक, कृपया यूट्यूब खोलो")
        )
    }


    @Test
    fun offlineBrainRecognizesAppLaunchCommandsAddressedToIdraak() {
        assertEquals(
            "OPEN_APP",
            com.idraak.quantum.engine.OfflineBrain.understand("Idraak, please open YouTube").intent
        )
        assertEquals(
            "OPEN_APP",
            com.idraak.quantum.engine.OfflineBrain.understand("ادراک، ذرا یوٹیوب کھول دو").intent
        )
        assertEquals(
            "OPEN_APP",
            com.idraak.quantum.engine.OfflineBrain.understand("इद्राक, कृपया यूट्यूब खोलो").intent
        )
    }

    @Test
    fun parsesAlarmWithExplicitAmPm() {
        val result = AlarmCommandParser.parse("set alarm for 7:30 pm")
        assertTrue(result is AlarmCommandParser.Result.Ready)
        result as AlarmCommandParser.Result.Ready
        assertEquals(19, result.hour)
        assertEquals(30, result.minute)
    }

    @Test
    fun asksMorningOrEveningInsteadOfGuessingAlarmTime() {
        assertEquals(
            AlarmCommandParser.Result.NeedsMeridiem,
            AlarmCommandParser.parse("alarm at 7")
        )
    }

    @Test
    fun parsesUrduMorningAlarm() {
        val result = AlarmCommandParser.parse("صبح 7 بجے الارم لگا دو")
        assertTrue(result is AlarmCommandParser.Result.Ready)
        result as AlarmCommandParser.Result.Ready
        assertEquals(7, result.hour)
    }

    @Test
    fun ignoresOrdinaryAlarmConversation() {
        assertEquals(AlarmCommandParser.Result.NotAlarm, AlarmCommandParser.parse("I like alarms"))
    }

}
