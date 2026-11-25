package com.idraak.quantum

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

/**
 * Executes a deferred voice command only after the user taps
 * OPEN & RUN in the ongoing foreground-service notification.
 */
class ForegroundCommandActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val command = intent.getStringExtra(EXTRA_COMMAND).orEmpty().trim()
        if (command.isBlank()) {
            finish()
            return
        }

        val result = try {
            OfflineCommandRouter.process(
                context = this,
                command = command,
                allowForegroundUi = true
            )
        } catch (_: Exception) {
            OfflineCommandRouter.Result(
                response = "कमांड पूरी नहीं हो सकी। Idraak खोलकर दोबारा कोशिश करें।",
                intent = "ERROR"
            )
        }

        Toast.makeText(this, result.response, Toast.LENGTH_LONG).show()
        finish()
    }

    companion object {
        const val EXTRA_COMMAND = "idraak.extra.FOREGROUND_COMMAND"
    }
}
