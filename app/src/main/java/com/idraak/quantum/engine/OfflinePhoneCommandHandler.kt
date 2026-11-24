package com.idraak.quantum.engine

import android.content.Context

/**
 * External app launching is intentionally disabled in Idraak Offline.
 *
 * Idraak handles supported actions through its own local controllers.
 * This compatibility handler returns a clear response for app-launch
 * requests without opening third-party apps or websites.
 */
object OfflinePhoneCommandHandler {

    data class Result(
        val handled: Boolean,
        val success: Boolean,
        val message: String,
        val appName: String = ""
    )

    fun execute(
        context: Context,
        userCommand: String
    ): Result? {
        val command = userCommand.trim()
        if (command.isBlank()) return null

        return Result(
            handled = true,
            success = false,
            message = "बाहरी ऐप खोलना Idraak Offline में बंद है। मैं केवल अपनी अंदरूनी सुविधाएँ और फोन के स्थानीय कंट्रोल संभालूँगा।",
            appName = command
        )
    }
}
