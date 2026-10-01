package org.openiisu.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoreTest {
    private val ref = File("../docs/reference")

    @Test fun parsesReferenceCatalog() {
        val cat = ConfigLoader.parseCatalog(File(ref, "emuladores_default.jsonc").readText())
        val gb = cat.consoles.first { it.shortName == "gb" }
        assertTrue(gb.accepts("tetris.gb"))
        assertTrue(!gb.accepts("tetris.txt"))
        assertEquals(3, gb.emulators.first().packages.size)
    }

    @Test fun parsesSupportedEmulators() {
        val l = ConfigLoader.parseSupportedEmulators(File(ref, "supported_emulators_default.json").readText())
        assertEquals(152, l.size)
    }

    @Test fun retroArchCommandBecomesIntentPerPackage() {
        val cat = ConfigLoader.parseCatalog(File(ref, "emuladores_default.jsonc").readText())
        val emu = cat.consoles.first { it.shortName == "gb" }.emulators.first()
        val specs = CommandTemplate.build(emu, emu.commands.first(), RomContext("/sdcard/ROMs/gb/a.gb", "file:///sdcard/ROMs/gb/a.gb"))
        assertEquals(listOf("com.retroarch", "com.retroarch.aarch64", "com.retroarch.ra32"), specs.map { it.packageName })
        val s = specs[0]
        assertEquals("com.retroarch.browser.retroactivity.RetroActivityFuture", s.activity)
        assertEquals(ExtraValue.Str("/sdcard/ROMs/gb/a.gb"), s.extras["ROM"])
        assertEquals(ExtraValue.Str("/storage/emulated/0/Android/data/com.retroarch/files/retroarch.cfg"), s.extras["CONFIGFILE"])
        assertTrue("activity-clear-top" in s.flags)
    }

    @Test fun commentStripKeepsUrlsInStrings() {
        assertEquals("{\"a\":\"http://x\"} \n", ConfigLoader.stripComments("{\"a\":\"http://x\"} // c\n"))
    }
}
