package com.foldos.launcher.search

import java.text.Normalizer
import java.util.Locale

/**
 * Fuzzy prefix matching shared by every provider. Scores are 0 (no match) to 100 (exact), so
 * results from different providers can be ordered together; [SearchIndex] adds the usage boost.
 */
object SearchMatch {
    private val diacritics = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val wordSplit = Regex("[\\s_\\-./]+")

    /** Lower-case, diacritics stripped and trimmed; what every comparison works on. */
    fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        return diacritics.replace(decomposed, "").lowercase(Locale.ROOT).trim()
    }

    /**
     * How well [label] (and optionally [secondary], e.g. a package name) matches [query], which
     * must already be [normalize]d. 0 means no match.
     */
    fun score(query: String, label: String, secondary: String? = null): Int {
        if (query.isEmpty()) return 0
        val text = normalize(label)
        if (text.isEmpty()) return 0
        if (text == query) return 100
        if (text.startsWith(query)) return 92 - lengthPenalty(text, query)
        val words = text.split(wordSplit).filter { it.isNotEmpty() }
        if (words.any { it.startsWith(query) }) return 82 - lengthPenalty(text, query)
        val initials = words.joinToString("") { it.take(1) }
        if (initials.length >= 2 && initials.startsWith(query)) return 74
        if (text.contains(query)) return 62 - lengthPenalty(text, query)
        val gaps = subsequenceGaps(query, text)
        if (gaps >= 0) return (50 - gaps * 3).coerceAtLeast(20)
        if (secondary != null) {
            val second = normalize(secondary)
            if (second.contains(query)) return 30
        }
        return 0
    }

    /** True when every word of [query] prefix-matches some word of [text]; for multi-word queries. */
    fun allWordsMatch(query: String, text: String): Boolean {
        val target = normalize(text).split(wordSplit).filter { it.isNotEmpty() }
        if (target.isEmpty()) return false
        return query.split(wordSplit).filter { it.isNotEmpty() }.all { q -> target.any { it.startsWith(q) } }
    }

    private fun lengthPenalty(text: String, query: String): Int =
        ((text.length - query.length) / 4).coerceIn(0, 10)

    /**
     * Number of skipped characters when [query] is matched as a subsequence of [text] (characters
     * in order, gaps allowed), or -1 when it is not one. Requires the first character to match a
     * word start so "gm" finds "Google Maps" but random letter soup does not match everything.
     */
    private fun subsequenceGaps(query: String, text: String): Int {
        if (query.length < 2) return -1
        var qi = 0
        var gaps = 0
        var started = false
        for ((index, c) in text.withIndex()) {
            if (qi < query.length && c == query[qi]) {
                if (!started) {
                    val wordStart = index == 0 || !text[index - 1].isLetterOrDigit()
                    if (!wordStart) continue
                    started = true
                }
                qi++
            } else if (started) {
                gaps++
            }
            if (qi == query.length) return gaps
        }
        return -1
    }
}
