package org.openiisu.core

import java.io.File
import java.nio.file.Files
import kotlin.test.*

class MoreTest {
    private val ref = File("../docs/reference")
    private val catalog = ConfigLoader.parseCatalog(File(ref, "emuladores_default.jsonc").readText())

    @Test fun scannerFindsRomsByConsoleFolder() {
        val root = Files.createTempDirectory("roms").toFile()
        File(root, "gb").mkdirs(); File(root, "gb/Tetris (World).gb").writeText("x"); File(root, "gb/notes.txt").writeText("n")
        File(root, "gb/.hidden.gb").writeText("h")
        val found = RomScanner(catalog).scan(root)
        val roms = found.entries.single().value
        assertEquals(listOf("Tetris"), roms.map { it.title })
        assertEquals("gb/Tetris (World).gb", roms[0].id)
    }

    @Test fun playtimeAggregates() {
        var now = 1000L
        val f = Files.createTempFile("pt", ".json").toFile().also { it.delete() }
        val t = PlaytimeTracker(JsonFilePlaytimeStore(f)) { now }
        t.start("gb/a"); now = 4000; t.stop()
        t.start("gb/a"); now = 5000; t.start("gb/b"); now = 5500; t.stop()
        assertEquals(4000L, t.entry("gb/a")!!.totalPlaytimeMs)
        assertEquals(2, t.entry("gb/a")!!.sessionCount)
        assertEquals("gb/b", t.entries().first().entryId)
        // persisted
        assertEquals(4000L, PlaytimeTracker(JsonFilePlaytimeStore(f)).entry("gb/a")!!.totalPlaytimeMs)
    }

    @Test fun nesHashSkipsHeader() {
        val f = Files.createTempFile("r", ".nes").toFile()
        f.writeBytes(byteArrayOf(0x4E, 0x45, 0x53, 0x1A) + ByteArray(12) + "abc".toByteArray())
        assertEquals("900150983cd24fb0d6963f7d28e17f72", RaHash.forRom(f, "nes")) // md5("abc")
        assertNull(RaHash.forRom(f, "psx"))
    }

    @Test fun optionsRoundTrip() {
        val o = EmulatorOptions.parse(File(ref, "default_emulator_options.json").readText())
        assertEquals("RetroArch", o.forConsole("gb")!!.emulator)
        assertEquals("X", o.with(ConsoleOption("gb", "X")).forConsole("gb")!!.emulator)
    }

    @Test fun macBuildsOpenAndDirectCommands() {
        val apps = Files.createTempDirectory("apps").toFile()
        File(apps, "RetroArch.app").mkdirs(); File(apps, "melonDS.app").mkdirs()
        val mac = MacConfig.parse(File(ref, "macos_emulators.jsonc").readText())
        val r = MacEmulatorResolver(listOf(apps))
        val rom = RomContext("/r/a b.gb", "file:///r/a%20b.gb")
        val ra = mac.emulators("gb").single()
        val spec = r.build(ra, ra.commands.first(), rom)!!
        assertEquals(File(apps, "RetroArch.app/Contents/MacOS/RetroArch").path, spec.command[0])
        assertEquals("/r/a b.gb", spec.command.last())
    }

    @Test fun macOpenFallbackAndMissingApp() {
        val apps = Files.createTempDirectory("apps").toFile(); File(apps, "melonDS.app").mkdirs()
        val mac = MacConfig.parse(File(ref, "macos_emulators.jsonc").readText())
        val r = MacEmulatorResolver(listOf(apps))
        val rom = RomContext("/r/a.nds", "file:///r/a.nds")
        val m = mac.emulators("nds").single()
        assertEquals(listOf("open", "-n", "-a", File(apps, "melonDS.app").path, "--args", "/r/a.nds"), r.build(m, m.commands[0], rom)!!.command)
        val d = mac.emulators("gc").single()
        assertNull(r.build(d, d.commands[0], rom))
    }

    @Test fun tgdbParsing() {
        val body = """{"data":{"games":[{"id":7,"game_title":"Tetris","release_date":"1989-06-14","overview":"Blocks","developers":[1],"genres":[2]}]},
          "include":{"boxart":{"base_url":{"original":"https://cdn/x/"},"data":{"7":[{"side":"back","filename":"b.jpg"},{"side":"front","filename":"f.jpg"}]}}}}"""
        val g = TheGamesDbClient.parseSearch(body).single()
        assertEquals("Tetris", g.title); assertEquals("https://cdn/x/f.jpg", g.boxartUrl); assertEquals(listOf("1"), g.developers)
    }

    @Test fun steamGridParsing() {
        assertEquals(5, SteamGridDbClient.parseFirstId("""{"data":[{"id":5,"name":"x"}]}"""))
        assertEquals(listOf("u1", "u2"), SteamGridDbClient.parseUrls("""{"data":[{"url":"u1"},{"url":"u2"}]}"""))
    }

    @Test fun metadataServiceCaches() {
        val f = Files.createTempFile("m", ".json").toFile().also { it.delete() }
        var calls = 0
        val http = HttpGet { _, _ -> calls++; """{"data":{"games":[{"id":1,"game_title":"Tetris"}]}}""" }
        val svc = MetadataService(MetadataCache(f), TheGamesDbClient("k", http))
        val rom = Rom("gb", "/x/Tetris.gb", "Tetris.gb", 1)
        assertEquals("Tetris", svc.metadataFor(rom)!!.title); svc.metadataFor(rom)
        assertEquals(1, calls)
        assertEquals("Tetris", MetadataCache(f)[rom.id]!!.title)
    }
}
