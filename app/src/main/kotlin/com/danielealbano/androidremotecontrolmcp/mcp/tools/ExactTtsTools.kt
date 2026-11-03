package com.danielealbano.androidremotecontrolmcp.mcp.tools

import com.danielealbano.androidremotecontrolmcp.data.model.ToolPermissionsConfig
import com.danielealbano.androidremotecontrolmcp.services.tts.ExactTtsController
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Controlled local TTS; does not expose shell/ADB or gain permissions beyond this application. */
fun registerExactTtsTools(
    registrar: LoggedToolRegistrar,
    controller: ExactTtsController,
    prefix: String,
    perms: ToolPermissionsConfig,
) {
    if (perms.isToolEnabled("tts_speak")) {
        registerExactTtsSpeak(registrar, controller, prefix)
    }
    if (perms.isToolEnabled("tts_control")) {
        registerExactTtsControl(registrar, controller, prefix)
    }
}

private fun registerExactTtsSpeak(
    registrar: LoggedToolRegistrar,
    controller: ExactTtsController,
    prefix: String,
) {
    registrar.addTool(
        toolName = "tts_speak",
        name = "${prefix}tts_speak",
        description =
            "Speak the exact supplied UTF-8 text with the Android TTS engine. " +
                "Input chunks concatenate byte-for-byte to the text received; voice pronunciation may normalize " +
                "numbers/punctuation. Audio is played on the Android device, NOT in the ChatGPT voice stream. " +
                "Returns SHA-256 of original text and playback state. A new call replaces previous speech.",
        inputSchema =
            ToolSchema(
                properties =
                    buildJsonObject {
                        putJsonObject("text") {
                            put("type", "string")
                            put("description", "Entire original text, unmodified; no LLM summarization")
                            put("maxLength", ExactTtsController.MAX_LENGTH)
                        }
                        putJsonObject("language") {
                            put("type", "string")
                            put("description", "BCP-47 locale tag such as ru-RU or en-US")
                            put("default", "ru-RU")
                        }
                        putJsonObject("rate") {
                            put("type", "number")
                            put("description", "Speech rate 0.5 to 2.0")
                            put("default", 1.0)
                        }
                    },
                required = listOf("text"),
            ),
    ) { request ->
        val args: JsonObject? = request.arguments
        val text = McpToolUtils.requireString(args, "text")
        val language = McpToolUtils.optionalString(args, "language", "ru-RU")
        val rate = McpToolUtils.optionalFloat(args, "rate", 1f)
        try {
            McpToolUtils.textResult(controller.speak(text, language, rate).describe())
        } catch (e: IllegalArgumentException) {
            throw com.danielealbano.androidremotecontrolmcp.mcp.McpToolException.InvalidParams(
                e.message ?: "Invalid TTS parameters",
                e,
            )
        }
    }
}

private fun registerExactTtsControl(
    registrar: LoggedToolRegistrar,
    controller: ExactTtsController,
    prefix: String,
) {
    registrar.addTool(
        toolName = "tts_control",
        name = "${prefix}tts_control",
        description =
            "Control Android TTS on this device: pause, resume, stop, or status. " +
                "Pause stops the active chunk; resume replays that short chunk, which may repeat words " +
                "but never skips the remainder of the original text.",
        inputSchema =
            ToolSchema(
                properties =
                    buildJsonObject {
                        putJsonObject("action") {
                            put("type", "string")
                            put(
                                "enum",
                                kotlinx.serialization.json.buildJsonArray {
                                    add(kotlinx.serialization.json.JsonPrimitive("pause"))
                                    add(kotlinx.serialization.json.JsonPrimitive("resume"))
                                    add(kotlinx.serialization.json.JsonPrimitive("stop"))
                                    add(kotlinx.serialization.json.JsonPrimitive("status"))
                                },
                            )
                        }
                    },
                required = listOf("action"),
            ),
    ) { request ->
        val action = McpToolUtils.requireString(request.arguments, "action")
        try {
            McpToolUtils.textResult(controller.control(action).describe())
        } catch (e: IllegalArgumentException) {
            throw com.danielealbano.androidremotecontrolmcp.mcp.McpToolException.InvalidParams(
                e.message ?: "Invalid TTS control action",
                e,
            )
        }
    }
}
