package org.openiisu.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** macOS emulator definition (reference/macos_emulators.jsonc). */
@Serializable
data class MacEmulator(
    val id: String,
    val name: String,
    /** Bundle name looked up in /Applications and ~/Applications, e.g. "RetroArch.app". */
    val app: String,
    /** Executable inside Contents/MacOS; when null the bundle is started through `open -a ... --args`. */
    val executable: String? = null,
    val commands: List<LaunchCommand> = emptyList(),
)

@Serializable
data class MacConsole(val shortName: String, val emulators: List<MacEmulator> = emptyList())

@Serializable
data class MacCatalog(val consoles: List<MacConsole>) {
    fun emulators(shortName: String) = consoles.firstOrNull { it.shortName == shortName }?.emulators.orEmpty()
}

object MacConfig {
    private val json = Json { ignoreUnknownKeys = true }
    fun parse(jsonc: String): MacCatalog =
        json.decodeFromString(MacCatalog.serializer(), ConfigLoader.stripComments(jsonc))
}

class MacEmulatorResolver(
    private val searchDirs: List<File> = listOf(File("/Applications"), File(System.getProperty("user.home"), "Applications")),
) {
    fun findApp(emu: MacEmulator): File? = searchDirs.map { File(it, emu.app) }.firstOrNull { it.isDirectory }

    fun isInstalled(emu: MacEmulator) = findApp(emu) != null

    /** `%ROM%` etc. use the same placeholders as Android; `%APP%` expands to the bundle path. */
    fun build(emu: MacEmulator, cmd: LaunchCommand, rom: RomContext): ProcessSpec? {
        val app = findApp(emu) ?: return null
        val args = CommandTemplate.expandTokens(cmd.command, emu.id, rom, RouteType.path)
            .map { it.replace("%APP%", app.path) }
        val argv = if (emu.executable != null) {
            listOf(File(app, "Contents/MacOS/${emu.executable}").path) + args
        } else {
            listOf("open", "-n", "-a", app.path, "--args") + args
        }
        return ProcessSpec(argv)
    }
}

class ProcessBackend : LaunchBackend<ProcessSpec> {
    override fun launch(spec: ProcessSpec): Boolean = try {
        ProcessBuilder(spec.command).inheritIO().start(); true
    } catch (e: java.io.IOException) { false }
}
