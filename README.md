# Idraak Quantum

Idraak Quantum is an Android voice assistant built with Kotlin and Jetpack Compose. The current foundation is offline-first: speech recognition and command routing run locally, and the APK is checked in CI to ensure it does not request Internet or network-state permissions.

## Current capabilities

- Local multilingual speech recognition with a bundled Whisper Small model; spoken responses are in Urdu.
- Offline command routing for supported phone, app, call, camera, torch, device-status and screen-control actions.
- Explicit multi-step task planning across Urdu, Hindi, Roman Urdu and English phrasing.
- Optional continuous foreground voice session with a persistent notification.
- Accessibility-based screen reading, clicking, scrolling and gestures after the user explicitly enables the service in Android Settings.
- Local memory with review/verification states, correction feedback, search and encrypted backup/restore.
- Encrypted local memory with review/verification, correction feedback, searchable categories and passphrase-protected backup/restore.
- Voice reminders with relative/clock times, daily/weekly repeats, listing, individual/all cancellation and reboot rescheduling across English, Urdu, Hindi, Roman Urdu and Arabic.
- Voice alarm requests in supported languages are handed to the Android Clock app; ambiguous AM/PM is clarified rather than guessed.
- Alarm Clock handoff for setting, showing and dismissing alarms; Android Clock confirms the final alarm action.
- Opt-in daily proactive check-in notification with quiet hours; it never starts the microphone in the background.
- Context-aware suggestions, selectable visual themes, Urdu speech-rate settings and a proactive-assistant toggle.
- Regression tests for supported command phrases and safety confirmations.

## Android requirements and permissions

- Minimum Android version: Android 9 (API 28).
- Target SDK: 34.
- Microphone permission is required for voice input.
- Camera permission is required for camera actions.
- Screen-control actions require the user to enable Idraak Screen Control in Android Accessibility Settings.
- Some settings are protected by Android. Idraak can open the relevant system settings panel, but cannot silently change every protected radio or hardware setting.
- Calls and messages use Android-supported flows and may require confirmation or interaction with the system app.

## Offline behavior

Idraak itself is built without the INTERNET and network-state permissions. The Whisper model is bundled into the APK by the GitHub Actions build, so the APK is large (roughly 440–460 MB). The CI workflow downloads/caches this model while building; that build-time download does not add network access to the installed app.

Opening an external app such as YouTube or a browser is a handoff. That external app may need its own Internet access for online content. Local Idraak command processing does not provide offline access to online videos or websites.

Reminder delivery uses exact alarms when Android allows them and falls back to inexact scheduling when exact-alarm access is unavailable; timing can therefore be delayed by Android power management. Notifications must be allowed by the user. Alarm creation is handed to the device Clock app for final confirmation.

Proactive check-ins are opt-in notifications (daily at 9:00 local time, subject to Android's inexact alarm scheduling) and respect quiet hours from 22:00 to 08:00. Android does not permit Idraak to start a microphone conversation automatically from the background; the user opens the notification to continue.

Urdu speech output depends on an Urdu-capable text-to-speech voice already installed on the Android device. If no local Urdu voice is available, speech output may be unavailable even though text responses work.

## Build and verification

GitHub Actions runs the following checks on every push to main:

1. Build the Debug APK.
2. Run offline command regression tests.
3. Run Android Lint.
4. Inspect the built APK and fail if Internet/network-state permissions are present.
5. Upload the IDRAAK-QUANTUM-DEBUG-APK artifact.

Open GitHub Actions and select the latest successful IDRAAK QUANTUM APK Build run to download its APK artifact:
https://github.com/sameer83y-png/IDRAAK-QUANTUM-CORE-9432/actions

## Known platform boundaries

Android does not allow an ordinary app to control every hardware feature or perform unrestricted background screen interaction. Accessibility access must be enabled by the user, protected settings remain under Android's control, and background app launches may require a notification tap. Continuous speech capture and speech playback are coordinated to reduce audio feedback; this is not guaranteed full-duplex conversation on every device.

A successful CI build verifies compilation, tests, lint and manifest permissions. It does not replace installation and real-device testing across Android versions and device manufacturers.
