package org.openiisu.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.openiisu.core.*
import java.io.File

/** ~/Library/Application Support/openiisu on macOS, ~/.config/openiisu elsewhere. */
fun dataDir(): File {
    val home = System.getProperty("user.home")
    return if (System.getProperty("os.name").lowercase().contains("mac"))
        File(home, "Library/Application Support/openiisu") else File(home, ".config/openiisu")
}

private fun resource(name: String) =
    object {}.javaClass.classLoader.getResourceAsStream(name)!!.bufferedReader().readText()

class AppState(val romRoot: File) {
    val catalog = ConfigLoader.parseCatalog(resource("emuladores_default.jsonc")).let { base ->
        val extra = ConfigLoader.parseCatalog(resource("consoles_extra.jsonc")).consoles
        ConsoleCatalog(base.consoles + extra.filter { e -> base.consoles.none { it.shortName == e.shortName } })
    }
    val mac = MacConfig.parse(resource("macos_emulators.jsonc"))
    val scanner = RomScanner(catalog)
    val resolver = MacEmulatorResolver()
    val playtime = PlaytimeTracker(JsonFilePlaytimeStore(File(dataDir(), "playtime.json")))
    val backend = ProcessBackend()

    fun launch(rom: Rom): String {
        val ctx = RomContext(rom.path, File(rom.path).toURI().toString(), File(rom.path).parent.orEmpty(), rom.title)
        for (emu in mac.emulators(rom.consoleShortName)) {
            val spec = emu.commands.firstOrNull()?.let { resolver.build(emu, it, ctx) } ?: continue
            playtime.start(rom.id)
            if (backend.launch(spec)) return "Launched with ${emu.name}"
            playtime.stop()
        }
        return "No installed emulator found for ${rom.consoleShortName}"
    }
}

fun main() = application {
    val root = File(System.getProperty("openiisu.roms") ?: File(System.getProperty("user.home"), "ROMs").path)
    val state = remember { AppState(root) }
    Window(onCloseRequest = ::exitApplication, title = "openiisu", state = androidx.compose.ui.window.rememberWindowState(width = 1100.dp, height = 720.dp)) {
        LauncherScreen(state)
    }
}
