package com.danielealbano.androidremotecontrolmcp.services.tts

/**
 * Keeps every source character in the same order. Audio rendering is engine-dependent.
 */
object ExactTextChunker {
    const val CHUNK_LIMIT = 220

    fun split(
        text: String,
        limit: Int = CHUNK_LIMIT,
    ): List<String> {
        require(limit >= 2) { "Chunk limit must be at least 2" }
        if (text.isEmpty()) return emptyList()
        val result = ArrayList<String>()
        var offset = 0
        while (offset < text.length) {
            var end = (offset + limit).coerceAtMost(text.length)
            if (end < text.length) {
                var boundary = end
                while (boundary > offset + limit / 2 && !text[boundary - 1].isWhitespace()) {
                    boundary--
                }
                if (boundary > offset + limit / 2) end = boundary
                if (end < text.length && end > offset &&
                    Character.isHighSurrogate(text[end - 1]) && Character.isLowSurrogate(text[end])
                ) {
                    end--
                }
            }
            if (end <= offset) end = (offset + limit).coerceAtMost(text.length)
            result.add(text.substring(offset, end))
            offset = end
        }
        check(result.joinToString(separator = "") == text)
        return result
    }
}
