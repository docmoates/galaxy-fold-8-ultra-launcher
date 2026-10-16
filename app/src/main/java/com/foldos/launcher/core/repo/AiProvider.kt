package com.foldos.launcher.core.repo

/**
 * A text completion engine behind the Hub's AI tab and the Today briefing: on-device Gemini Nano
 * (AICore), a Galaxy AI intent bridge, or the rule-based template engine.
 */
interface AiProvider {
    /** Human-readable engine name shown in Settings > AI ("Gemini Nano", "Template"). */
    val name: String

    /**
     * Answers [prompt] given [context] (the hub's current events, tasks and mail, already rendered
     * as plain text). Throws when the engine fails; callers fall back to the template engine.
     */
    suspend fun complete(prompt: String, context: String): String

    /** True when the engine can run on this device right now (model downloaded, feature enabled). */
    suspend fun available(): Boolean
}

/**
 * The AI tab's reply. [appliedSettings] is true when the request was a natural-language settings
 * change ("make my inner screen a dashboard") that has already been written through
 * `SettingsRepository`.
 */
data class AiReply(
    val text: String,
    val appliedSettings: Boolean,
)

/**
 * Routes a typed request: settings commands are parsed and applied, everything else goes to the
 * [AiProvider] picked by `LauncherSettings.aiMode`. Implemented by the Hub-mail-AI agent;
 * `data.TemplateAiCommandRouter` is the placeholder.
 */
interface AiCommandRouter {
    suspend fun handle(text: String): AiReply
}
