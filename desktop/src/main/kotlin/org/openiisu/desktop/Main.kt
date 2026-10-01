package org.openiisu.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
    val catalog = ConfigLoader.parseCatalog(resource("emuladores_default.jsonc"))
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
    Window(onCloseRequest = ::exitApplication, title = "openiisu") {
        MaterialTheme {
            var library by remember { mutableStateOf(state.scanner.scan(root)) }
            var selected by remember { mutableStateOf<ConsoleConfig?>(null) }
            var status by remember { mutableStateOf("ROM folder: ${root.path}") }
            Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Button(onClick = { library = state.scanner.scan(root) }) { Text("Rescan") }
                    LazyColumn {
                        items(library.keys.toList()) { c ->
                            Text("${c.longName} (${library[c]!!.size})", Modifier.clickable { selected = c }.padding(6.dp))
                        }
                    }
                }
                Column(Modifier.weight(2f)) {
                    Text(status)
                    LazyColumn {
                        items(library[selected].orEmpty()) { rom ->
                            val pt = state.playtime.entry(rom.id)
                            Row(Modifier.clickable { status = state.launch(rom) }.padding(6.dp)) {
                                Text(rom.title, Modifier.weight(1f))
                                if (pt != null) Text("${pt.totalPlaytimeMs / 60000} min")
                            }
                        }
                    }
                }
            }
        }
    }
}
