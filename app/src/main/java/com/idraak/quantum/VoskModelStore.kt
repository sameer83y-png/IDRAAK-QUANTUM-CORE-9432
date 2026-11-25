package com.idraak.quantum

/**
 * The app now exposes a single speech language: Urdu.
 * This enum keeps the existing settings/service contract stable during migration.
 */
class VoskModelStore {
    enum class VoskLanguage {
        URDU
    }
}
