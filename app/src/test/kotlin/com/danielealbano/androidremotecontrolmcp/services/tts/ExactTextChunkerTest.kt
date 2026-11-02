package com.danielealbano.androidremotecontrolmcp.services.tts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExactTextChunkerTest {
    @Test
    fun preservesEveryCharacterIncludingWhitespaceAndEmoji() {
        val original = "Начало  \n	«Маркс»: 😀 — 1858.\n".repeat(91)
        val chunks = ExactTextChunker.split(original, 27)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 27 && it.isNotEmpty() })
        assertEquals(original, chunks.joinToString(separator = ""))
    }

    @Test
    fun preservesUnbrokenRunsAndSurrogatePairs() {
        val original = "abcdefghij😀klmnopqrst".repeat(100)
        val chunks = ExactTextChunker.split(original, 11)
        assertEquals(original, chunks.joinToString(separator = ""))
        assertTrue(chunks.none { Character.isHighSurrogate(it.last()) })
        assertTrue(chunks.none { Character.isLowSurrogate(it.first()) })
    }

    @Test
    fun handlesEmptyAndSingleChunkText() {
        assertEquals(emptyList<String>(), ExactTextChunker.split(""))
        assertEquals(listOf("abc"), ExactTextChunker.split("abc"))
    }

    @Test
    fun rejectsUnsafeLimit() {
        assertThrows(IllegalArgumentException::class.java) { ExactTextChunker.split("abc", 1) }
    }
}
