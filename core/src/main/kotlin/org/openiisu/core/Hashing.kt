package org.openiisu.core

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * Simplified RetroAchievements hashing. RA hashes are per-console; this covers the
 * common "MD5 of the whole file" case plus NES (iNES header stripped).
 * Disc systems (PSX, Saturn, ...) need rcheevos' rc_hash and are not handled here.
 */
object RaHash {
    fun md5(input: InputStream, skip: Int = 0): String {
        val md = MessageDigest.getInstance("MD5")
        input.use { s ->
            var toSkip = skip.toLong()
            while (toSkip > 0) { val n = s.skip(toSkip); if (n <= 0) break; toSkip -= n }
            val buf = ByteArray(64 * 1024)
            while (true) { val n = s.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun forRom(file: File, consoleShortName: String): String? {
        if (!file.isFile) return null
        return when (consoleShortName) {
            "nes", "fds" -> {
                val header = file.inputStream().use { it.readNBytes(4) }
                val ines = header.contentEquals(byteArrayOf(0x4E, 0x45, 0x53, 0x1A))
                md5(file.inputStream(), if (ines) 16 else 0)
            }
            "psx", "ps2", "saturn", "segacd", "dreamcast", "3do", "pcengine-cd" -> null
            else -> md5(file.inputStream())
        }
    }
}
