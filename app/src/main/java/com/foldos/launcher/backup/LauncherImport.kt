package com.foldos.launcher.backup

import com.foldos.launcher.core.model.LauncherSettings
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/** What a best-effort read of another launcher's settings file found. Any field may be null. */
data class ImportedGrid(
    val columns: Int?,
    val rows: Int?,
    val dockCount: Int?,
    /** "Lawnchair", "Nova" or "settings file". */
    val source: String,
) {
    /** One line for the result dialog. */
    fun describe(): String {
        val parts = ArrayList<String>()
        if (columns != null) parts += if (rows != null) "grid $columns x $rows" else "$columns columns"
        if (dockCount != null) parts += "$dockCount dock icons"
        return "Found in the $source file: ${parts.joinToString(", ")}"
    }
}

/**
 * Best-effort reader for Lawnchair and Nova Launcher settings files. It only looks for the home
 * grid size and the dock icon count and never throws: anything unexpected yields null.
 *
 * Accepted input: a plain JSON object, a Preferences-style XML (`<int name=... value=.../>`), a
 * `key=value` text file, or a zip holding any of those (Lawnchair `.lawnchairbackup`, Nova
 * `.novabackup`). Keys are matched by name fragments, since both launchers renamed them across
 * versions. Full backup parsing (icons, folders, widgets) is not attempted.
 */
object LauncherImport {

    private const val MAX_ENTRY_BYTES = 2 * 1024 * 1024
    private const val MAX_ENTRIES = 300

    private val jsonNumber = Regex("\"([^\"\\\\]{1,80})\"\\s*:\\s*\"?(-?\\d{1,4})\"?(?=\\s*[,}\\]\\r\\n])")
    private val xmlInt = Regex("<(?:int|long)\\s+name=\"([^\"]{1,80})\"\\s+value=\"(-?\\d{1,4})\"")
    private val xmlString = Regex("<string\\s+name=\"([^\"]{1,80})\"\\s*>\\s*(-?\\d{1,4})\\s*</string>")
    private val keyValue = Regex("^\\s*([A-Za-z0-9_.\\-]{1,80})\\s*[=:]\\s*(-?\\d{1,4})\\s*$", RegexOption.MULTILINE)

    private val gridContext = listOf("workspace", "desktop", "home", "grid")
    private val gridExcluded = listOf("drawer", "allapps", "all_apps", "folder", "dock", "hotseat", "widget", "search", "page", "icon_size")
    private val dockContext = listOf("dock", "hotseat")
    private val dockCountWords = listOf("count", "num", "columns", "cols", "icons")
    private val dockExcluded = listOf("size", "margin", "padding", "height", "radius", "opacity", "color", "bg", "background", "scale", "alpha")

    /**
     * Reads [bytes] (the content of the picked file; [fileName] is only a hint for the source name)
     * and returns the grid it describes, or null when nothing usable was found.
     */
    fun parse(fileName: String?, bytes: ByteArray): ImportedGrid? = runCatching {
        val numbers = LinkedHashMap<String, Int>()
        var source: String? = null
        if (isZip(bytes)) {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var count = 0
                while (count < MAX_ENTRIES) {
                    val entry = zip.nextEntry ?: break
                    count++
                    if (entry.isDirectory) continue
                    val name = entry.name.lowercase()
                    if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".webp") || name.endsWith(".db")) continue
                    val data = readLimited(zip) ?: continue
                    collectNumbers(String(data, Charsets.UTF_8), numbers)
                    source = source ?: sourceName(name)
                }
            }
            source = source ?: sourceName(fileName?.lowercase().orEmpty())
        } else {
            collectNumbers(String(bytes, Charsets.UTF_8), numbers)
            source = sourceName(fileName?.lowercase().orEmpty())
        }
        grid(numbers, source ?: "settings file")
    }.getOrNull()

    /**
     * Applies [grid] to [settings]: the column count becomes the cover grid width (clamped to the
     * Settings slider range 3..5); the inner screen keeps its own width. The dock icon count has no
     * `LauncherSettings` field (the dock sizes itself), so it is only reported by [ImportedGrid.describe].
     */
    fun apply(settings: LauncherSettings, grid: ImportedGrid): LauncherSettings {
        val columns = grid.columns ?: return settings
        return settings.copy(colsCover = columns.coerceIn(3, 5))
    }

    private fun isZip(bytes: ByteArray): Boolean =
        bytes.size > 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()

    private fun sourceName(lowerName: String): String? = when {
        "lawnchair" in lowerName -> "Lawnchair"
        "nova" in lowerName -> "Nova"
        else -> null
    }

    private fun readLimited(input: java.io.InputStream): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_ENTRY_BYTES) return null
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    /** Collects `name -> small integer` pairs from JSON, Preferences XML and key=value text. */
    private fun collectNumbers(text: String, into: MutableMap<String, Int>) {
        for (regex in listOf(jsonNumber, xmlInt, xmlString, keyValue)) {
            for (match in regex.findAll(text)) {
                val key = match.groupValues[1].lowercase()
                val value = match.groupValues[2].toIntOrNull() ?: continue
                if (key !in into) into[key] = value
            }
        }
    }

    private fun grid(numbers: Map<String, Int>, source: String): ImportedGrid? {
        val columns = firstMatch(numbers, 2..12) { key ->
            gridContext.any { it in key } && gridExcluded.none { it in key } && ("column" in key || "cols" in key || key.endsWith("_x"))
        }
        val rows = firstMatch(numbers, 2..12) { key ->
            gridContext.any { it in key } && gridExcluded.none { it in key } && ("row" in key || key.endsWith("_y"))
        }
        val dock = firstMatch(numbers, 2..10) { key ->
            dockContext.any { it in key } && dockCountWords.any { it in key } && dockExcluded.none { it in key }
        }
        if (columns == null && dock == null) return null
        return ImportedGrid(columns = columns, rows = rows, dockCount = dock, source = source)
    }

    private fun firstMatch(numbers: Map<String, Int>, range: IntRange, accepts: (String) -> Boolean): Int? {
        for ((key, value) in numbers) {
            if (value in range && accepts(key)) return value
        }
        return null
    }
}
