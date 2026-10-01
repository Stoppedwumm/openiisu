package org.openiisu.core

/** Values substituted into a command template. */
data class RomContext(
    val romPath: String?,
    val romUri: String,
    val romDir: String = "",
    val romName: String = "",
    val externalStorage: String = "/storage/emulated/0",
)

sealed interface ExtraValue {
    data class Str(val v: String) : ExtraValue
    data class Bool(val v: Boolean) : ExtraValue
    data class Int(val v: kotlin.Int) : ExtraValue
}

/** Android-agnostic description of the intent to fire; the app layer maps it to `Intent`. */
data class IntentSpec(
    val packageName: String,
    val activity: String?,
    val action: String? = null,
    val data: String? = null,
    val extras: Map<String, ExtraValue> = emptyMap(),
    val flags: Set<String> = emptySet(),
)

/**
 * Parses `am start`-style templates, e.g.
 * `%PACKAGE%/com.x.Activity -e KEY VAL --ez FLAG true --activity-clear-top`.
 */
object CommandTemplate {
    fun expand(template: String, pkg: String, rom: RomContext, route: RouteType): String {
        val romValue = if (route == RouteType.uri) rom.romUri else rom.romPath ?: rom.romUri
        return template
            .replace("%PACKAGE%", pkg)
            .replace("%ROM_PATH%", rom.romPath ?: rom.romUri)
            .replace("%ROM_URI%", rom.romUri)
            .replace("%ROM_DIR%", rom.romDir)
            .replace("%ROM_NAME%", rom.romName)
            .replace("%EXTERNAL_STORAGE%", rom.externalStorage)
            .replace("%ROM%", romValue)
    }

    fun parse(expanded: String): IntentSpec = parseTokens(tokenize(expanded))

    /** Splits the template first, then substitutes per token, so paths containing spaces stay one argument. */
    fun expandTokens(template: String, pkg: String, rom: RomContext, route: RouteType): List<String> =
        tokenize(template).map { expand(it, pkg, rom, route) }

    fun parseTokens(tokens: List<String>): IntentSpec {
        require(tokens.isNotEmpty()) { "empty command" }
        val first = tokens[0]
        val (pkg, activity) = if ('/' in first) first.substringBefore('/') to first.substringAfter('/') else first to null
        val fullActivity = activity?.let { if (it.startsWith(".")) pkg + it else it }

        var action: String? = null
        var data: String? = null
        val extras = linkedMapOf<String, ExtraValue>()
        val flags = linkedSetOf<String>()
        var i = 1
        fun arg(n: Int) = tokens.getOrNull(i + n) ?: error("missing argument after ${tokens[i]}")
        while (i < tokens.size) {
            when (val t = tokens[i]) {
                "-a" -> { action = arg(1); i += 2 }
                "-d" -> { data = arg(1); i += 2 }
                "-e", "--es" -> { extras[arg(1)] = ExtraValue.Str(arg(2)); i += 3 }
                "--ez" -> { extras[arg(1)] = ExtraValue.Bool(arg(2).toBoolean()); i += 3 }
                "--ei" -> { extras[arg(1)] = ExtraValue.Int(arg(2).toInt()); i += 3 }
                else -> {
                    if (t.startsWith("--")) flags += t.removePrefix("--") else error("unexpected token: $t")
                    i++
                }
            }
        }
        return IntentSpec(pkg, fullActivity, action, data, extras, flags)
    }

    /** Splits on whitespace, honouring single/double quotes. */
    fun tokenize(s: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var quote: Char? = null
        var has = false
        for (c in s) {
            when {
                quote != null -> if (c == quote) quote = null else cur.append(c)
                c == '"' || c == '\'' -> { quote = c; has = true }
                c.isWhitespace() -> if (has || cur.isNotEmpty()) { out += cur.toString(); cur.clear(); has = false }
                else -> cur.append(c)
            }
        }
        if (has || cur.isNotEmpty()) out += cur.toString()
        return out
    }

    /** One IntentSpec per candidate package, in declared order. */
    fun build(emulator: EmulatorConfig, command: LaunchCommand, rom: RomContext): List<IntentSpec> =
        emulator.packages.map { parseTokens(expandTokens(command.command, it, rom, emulator.routeType)) }
}
