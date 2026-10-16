package com.foldos.launcher.data

import com.foldos.launcher.core.repo.AiCommandRouter
import com.foldos.launcher.core.repo.AiProvider
import com.foldos.launcher.core.repo.AiReply

/**
 * Placeholder [AiProvider]: echoes the prompt back as a plain sentence. Always available.
 * The real template engine lives in `ai/` (Hub-mail-AI agent).
 */
class TemplateAiProvider : AiProvider {
    override val name: String = "Template"

    override suspend fun complete(prompt: String, context: String): String {
        val trimmed = prompt.trim()
        return if (trimmed.isEmpty()) "Ask me about your day, your schedule or a setting to change."
        else "I heard: \"$trimmed\". On-device answers arrive with the AI engine."
    }

    override suspend fun available(): Boolean = true
}

/**
 * Placeholder [AiCommandRouter]: forwards everything to [TemplateAiProvider] and never applies
 * settings. Replaced by the Hub-mail-AI agent (`ai/`).
 */
class TemplateAiCommandRouter(
    private val provider: AiProvider = TemplateAiProvider(),
) : AiCommandRouter {
    override suspend fun handle(text: String): AiReply =
        AiReply(text = provider.complete(text, context = ""), appliedSettings = false)
}
