package org.openiisu.core

/** A resolved process invocation (desktop platforms). */
data class ProcessSpec(val command: List<String>)

/** Platform strategy: given resolved intents (Android) or process specs (desktop), start the game. */
fun interface LaunchBackend<T> {
    fun launch(spec: T): Boolean
}
