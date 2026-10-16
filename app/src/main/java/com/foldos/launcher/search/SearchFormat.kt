package com.foldos.launcher.search

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Subtitle formatting shared by the result model and the rows. */
internal object SearchFormat {
    private val time: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    private val dayShort: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
    private val dateShort: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

    /** "Today · 10:00 AM – 10:30 AM · Room 4" / "Tomorrow · All day" / "Fri, Oct 24 · 9:00 AM". */
    fun eventSubtitle(begin: Long, end: Long, allDay: Boolean, location: String?): String {
        val zone = ZoneId.systemDefault()
        val start = Instant.ofEpochMilli(begin).atZone(zone)
        val finish = Instant.ofEpochMilli(end).atZone(zone)
        val today = LocalDate.now(zone)
        val day = when (start.toLocalDate()) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            else -> start.format(dayShort)
        }
        val range = if (allDay) "All day" else "${start.format(time)} – ${finish.format(time)}"
        val where = location?.trim().orEmpty()
        return if (where.isEmpty()) "$day · $range" else "$day · $range · $where"
    }

    /** "Image · 2.4 MB · Oct 3, 2026". */
    fun fileSubtitle(mimeType: String?, sizeBytes: Long, modifiedMillis: Long): String {
        val kind = fileKind(mimeType)
        val size = fileSize(sizeBytes)
        val date = if (modifiedMillis > 0) {
            Instant.ofEpochMilli(modifiedMillis).atZone(ZoneId.systemDefault()).format(dateShort)
        } else {
            null
        }
        return listOfNotNull(kind, size, date).joinToString(" · ")
    }

    fun fileKind(mimeType: String?): String {
        val mime = mimeType.orEmpty()
        return when {
            mime.startsWith("image/") -> "Image"
            mime.startsWith("video/") -> "Video"
            mime.startsWith("audio/") -> "Audio"
            mime == "application/pdf" -> "PDF"
            mime.startsWith("text/") -> "Text"
            mime.contains("zip") || mime.contains("compressed") -> "Archive"
            mime.contains("word") || mime.contains("document") -> "Document"
            mime.contains("sheet") || mime.contains("excel") -> "Spreadsheet"
            mime.contains("presentation") || mime.contains("powerpoint") -> "Presentation"
            mime.isEmpty() -> "File"
            else -> mime.substringAfter('/').uppercase(Locale.ROOT).take(8)
        }
    }

    fun fileSize(bytes: Long): String? {
        if (bytes <= 0) return null
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.getDefault(), "%.0f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.getDefault(), "%.1f MB", mb)
        return String.format(Locale.getDefault(), "%.2f GB", mb / 1024.0)
    }
}
