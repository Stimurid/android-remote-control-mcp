# Android Remote Control MCP — local fork frontier

Date: 2026-11-02.
Upstream: `danielealbano/android-remote-control-mcp` (GPL-3.0-or-later).
Maintained fork: `Stimurid/android-remote-control-mcp`.
Work branch: `feature/android-exact-tts`.

Implemented candidate:
- Local TextToSpeech controller, source-faithful text chunking, two MCP tools.
- Playback state, original payload SHA-256, pause/resume/stop/status.
- Tool registration using existing authorization, logging, and permissions infrastructure.
- Engine shutdown when MCP server stops.
- JVM unit tests for source-string chunking.

Unverified / next:
- Android Gradle build + JVM tests in a proper Java 17/Android SDK environment.
- Install and validate APK on actual Android phone.
- Simultaneous use with ChatGPT Voice audio output.
- Voice selection / alternate neural TTS engine.
- User acceptance.

Keep upstream history and GPL notices. Never describe text-level preservation as acoustic fidelity.
