package com.danielealbano.androidremotecontrolmcp.services.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local Android TTS playback. Source text never passes through an LLM after the MCP request.
 * Pause stops the current short chunk; resume replays it (possible repetition, no omission).
 */
@Singleton
class ExactTtsController
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        data class Snapshot(
            val state: String,
            val sha256: String?,
            val characters: Int,
            val currentChunk: Int,
            val chunkCount: Int,
            val language: String?,
            val error: String?,
        ) {
            fun describe(): String =
                "state=$state sha256=${sha256 ?: "-"} characters=$characters " +
                    "chunk=$currentChunk/$chunkCount language=${language ?: "-"} " +
                    "error=${error ?: "-"}"
        }

        private data class Job(
            val chunks: List<String>,
            val digest: String,
            val characters: Int,
            val language: String,
            val rate: Float,
            val generation: Long,
            var index: Int = 0,
        )

        private val lock = Any()
        private var engine: TextToSpeech? = null
        private var ready = false
        private var engineEpoch = 0L
        private var state = "idle"
        private var error: String? = null
        private var job: Job? = null
        private var nextGeneration = 0L
        private var nextUtterance = 0L
        private var activeUtterance: String? = null

        fun speak(
            text: String,
            language: String = "ru-RU",
            rate: Float = 1f,
        ): Snapshot =
            synchronized(lock) {
                require(text.isNotEmpty()) { "Text must not be empty" }
                require(text.length <= MAX_LENGTH) { "Text exceeds $MAX_LENGTH UTF-16 characters" }
                require(language.matches(LANGUAGE_PATTERN)) { "Invalid BCP-47 language tag" }
                require(rate in 0.5f..2.0f) { "Rate must be in 0.5..2.0" }

                engine?.stop()
                activeUtterance = null
                error = null
                val chunks = ExactTextChunker.split(text)
                job = Job(chunks, sha256(text), text.length, language, rate, ++nextGeneration)
                state = if (ready) "playing" else "initializing"
                if (engine == null) {
                    val epoch = ++engineEpoch
                    engine =
                        TextToSpeech(context) { code ->
                            synchronized(lock) {
                                if (epoch != engineEpoch) return@synchronized
                                if (code != TextToSpeech.SUCCESS) {
                                    state = "error"
                                    error = "Android TTS engine initialization failed ($code)"
                                } else {
                                    ready = true
                                    engine?.setOnUtteranceProgressListener(listener)
                                    if (state == "initializing") {
                                        state = "playing"
                                        speakNextLocked()
                                    }
                                }
                            }
                        }
                } else if (ready) {
                    speakNextLocked()
                }
                snapshotLocked()
            }

        fun control(action: String): Snapshot =
            synchronized(lock) {
                when (action) {
                    "status" -> {
                        Unit
                    }

                    "pause" -> {
                        if (state == "playing" || state == "initializing") {
                            state = "paused"
                            activeUtterance = null
                            engine?.stop()
                        }
                    }

                    "resume" -> {
                        if (state == "paused" && job != null) {
                            state = if (ready) "playing" else "initializing"
                            if (ready) speakNextLocked()
                        }
                    }

                    "stop" -> {
                        activeUtterance = null
                        engine?.stop()
                        job = null
                        state = "idle"
                        error = null
                    }

                    else -> {
                        throw IllegalArgumentException("Unknown action: $action")
                    }
                }
                snapshotLocked()
            }

        fun shutdown() {
            synchronized(lock) {
                activeUtterance = null
                job = null
                state = "idle"
                error = null
                ready = false
                engineEpoch++
                engine?.stop()
                engine?.shutdown()
                engine = null
            }
        }

        private val listener =
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) = Unit

                override fun onDone(utteranceId: String) {
                    synchronized(lock) {
                        if (state != "playing" || activeUtterance != utteranceId) return
                        activeUtterance = null
                        job?.index = (job?.index ?: 0) + 1
                        speakNextLocked()
                    }
                }

                @Deprecated("Android TTS callback compatibility")
                override fun onError(utteranceId: String) {
                    synchronized(lock) {
                        if (activeUtterance != utteranceId) return
                        activeUtterance = null
                        state = "error"
                        error = "Android TTS synthesis failed"
                    }
                }
            }

        private fun speakNextLocked() {
            val current = job ?: return
            if (state != "playing") return
            if (current.index >= current.chunks.size) {
                activeUtterance = null
                state = "finished"
                return
            }
            val tts =
                engine ?: run {
                    state = "error"
                    error = "TTS engine unavailable"
                    return
                }
            val languageResult = tts.setLanguage(Locale.forLanguageTag(current.language))
            if (languageResult == TextToSpeech.LANG_NOT_SUPPORTED ||
                languageResult == TextToSpeech.LANG_MISSING_DATA
            ) {
                state = "error"
                error = "Language not installed: ${current.language}"
                return
            }
            tts.setSpeechRate(current.rate)
            val utterance = "${current.generation}:${current.index}:${++nextUtterance}"
            activeUtterance = utterance
            if (tts.speak(current.chunks[current.index], TextToSpeech.QUEUE_FLUSH, null, utterance) == TextToSpeech.ERROR) {
                activeUtterance = null
                state = "error"
                error = "TTS rejected chunk ${current.index + 1}"
            }
        }

        private fun snapshotLocked(): Snapshot {
            val current = job
            return Snapshot(
                state = state,
                sha256 = current?.digest,
                characters = current?.characters ?: 0,
                currentChunk = if (current == null) 0 else (current.index + 1).coerceAtMost(current.chunks.size),
                chunkCount = current?.chunks?.size ?: 0,
                language = current?.language,
                error = error,
            )
        }

        private fun sha256(text: String): String =
            MessageDigest
                .getInstance("SHA-256")
                .digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

        companion object {
            const val MAX_LENGTH = 160_000
            private val LANGUAGE_PATTERN = Regex("[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*")
        }
    }
