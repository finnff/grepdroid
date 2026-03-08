package com.grepdroid.data

import com.grepdroid.data.db.ScannedImage

object FuzzyMatcher {

    data class ScoredResult(val image: ScannedImage, val matchPercent: Int)

    fun search(images: List<ScannedImage>, query: String, limit: Int = 50): List<ScoredResult> {
        if (query.isBlank()) return emptyList()

        val queryLower = query.lowercase()
        val queryWords = queryLower.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (queryWords.isEmpty()) return emptyList()

        return images.mapNotNull { image ->
            val score = score(image.extractedText, queryLower, queryWords)
            if (score > 0) ScoredResult(image, score) else null
        }
            .sortedByDescending { it.matchPercent }
            .take(limit)
    }

    private fun score(text: String, queryLower: String, queryWords: List<String>): Int {
        if (text.isBlank()) return 0
        val textLower = text.lowercase()

        // Exact substring match
        if (textLower.contains(queryLower)) return 100

        // Count how many query words appear in the text
        val foundWords = queryWords.count { word -> textLower.contains(word) }
        if (foundWords == queryWords.size) {
            // All words found — 80-99 proportional to word count
            return 80 + (19 * foundWords / queryWords.size)
        }

        if (foundWords > 0) {
            // Partial word matches — 40-79
            return 40 + (39 * foundWords / queryWords.size)
        }

        // Check partial word matches (word prefixes)
        val partialMatches = queryWords.count { word ->
            textLower.split("\\s+".toRegex()).any { textWord -> textWord.startsWith(word) || word.startsWith(textWord) }
        }
        if (partialMatches > 0) {
            return 40 + (39 * partialMatches / queryWords.size)
        }

        return 0
    }
}
