package com.idraak.quantum

/** Deterministic local planner for explicitly chained voice commands. */
object OfflineTaskPlanner {
    fun split(command: String): List<String> {
        val separator = Regex(
            """(?i)\\s+(?:and\\s+then|after\\s+that|then|next|اور\\s+پھر|اس\\s+کے\\s+بعد|پھر|بعد\\s+میں|ثم|uske\\s+baad|phir|fir)\\s+"""
        )
        val parts = command.trim().split(separator)
            .map { it.trim().trim(',', '،', ';', '؛', '.') }
            .filter { it.isNotBlank() }
            .take(6)
            .toMutableList()
        if (parts.isNotEmpty()) {
            parts[0] = parts[0].replace(
                Regex("""(?i)^(?:first|firstly|pehle|pehlay|sabse\\s+pehle|پہلے|سب سے پہلے|पहले|सबसे पहले)\\s+"""),
                ""
            ).trim()
        }
        return parts.filter { it.isNotBlank() }
    }

    fun isMultiStep(command: String): Boolean = split(command).size in 2..5
}
