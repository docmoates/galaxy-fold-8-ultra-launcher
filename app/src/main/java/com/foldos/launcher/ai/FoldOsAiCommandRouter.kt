package com.foldos.launcher.ai

import android.content.Context
import android.util.Log
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.repo.AiCommandRouter
import com.foldos.launcher.core.repo.AiProvider
import com.foldos.launcher.core.repo.AiReply
import com.foldos.launcher.core.repo.AppsRepository
import com.foldos.launcher.core.repo.BadgeRepository
import com.foldos.launcher.core.repo.HubRepository
import com.foldos.launcher.core.repo.MailRepository
import com.foldos.launcher.core.repo.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The Hub's AI brain: natural-language launcher commands are parsed by [SettingsCommandParser]
 * and written through [SettingsRepository]; everything else is answered by the [AiProvider] for
 * `LauncherSettings.aiMode` ([FoldOsTemplateAiProvider] for `template`, [AiCoreProvider] for
 * `ondevice`, falling back to the template engine when Gemini Nano is unavailable). Every action is
 * written to the [AiUsageLog].
 *
 * Wire in `AppGraphImpl`:
 * `override val ai: AiCommandRouter by lazy { FoldOsAiCommandRouter(appContext, settings, apps, hub, badges, mail) }`.
 */
class FoldOsAiCommandRouter(
    context: Context,
    private val settings: SettingsRepository,
    apps: AppsRepository,
    hub: HubRepository,
    badges: BadgeRepository,
    mail: MailRepository,
    val usageLog: AiUsageLog = AiUsageLog(context),
    val contextBuilder: AiContextBuilder = AiContextBuilder(settings, apps, hub, badges, mail),
    val templateProvider: AiProvider = FoldOsTemplateAiProvider(contextBuilder),
    val onDeviceProvider: AiProvider = AiCoreProvider(context),
) : AiCommandRouter {

    private val apps: AppsRepository = apps
    private val parser = SettingsCommandParser()

    /** The provider that would answer right now for [settings], after availability checks. */
    suspend fun activeProvider(current: LauncherSettings): AiProvider? = when (current.aiMode) {
        LauncherSettings.AI_MODE_OFF -> null
        LauncherSettings.AI_MODE_ONDEVICE -> if (onDeviceProvider.usableUnder(current) && runCatching { onDeviceProvider.available() }.getOrDefault(false)) onDeviceProvider else templateProvider
        else -> templateProvider
    }

    override suspend fun handle(text: String): AiReply {
        val request = text.trim()
        val current = runCatching { settings.settings.first() }.getOrDefault(LauncherSettings())
        if (request.isEmpty()) {
            return AiReply("Ask me about your day, or tell the launcher what to change.", appliedSettings = false)
        }
        if (!current.feature(LauncherSettings.FEATURE_AI)) {
            return AiReply("AI features are switched off. Turn them on under Settings › AI to use the assistant.", appliedSettings = false)
        }

        // 1. Launcher commands: parsed locally, applied immediately.
        val appList = try {
            withTimeoutOrNull(APPS_TIMEOUT_MS) { apps.allApps.first() } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        val command = runCatching { parser.parse(request, appList, current) }
            .onFailure { Log.w(TAG, "Command parse failed", it) }
            .getOrNull()
        if (command != null) {
            val changed = command.apply(current) != current
            if (changed) {
                runCatching { settings.update(command.apply) }
                    .onFailure { Log.w(TAG, "Applying ${command.description} failed", it) }
                    .getOrElse {
                        return AiReply("I understood “${command.description}” but couldn't save it: ${it.message}", appliedSettings = false)
                    }
            }
            usageLog.record(
                action = if (changed) "Applied setting: ${command.description}" else "Command (no change): ${command.description}",
                data = command.dataUsed,
                engine = ENGINE_RULES,
            )
            return AiReply(command.confirmation, appliedSettings = changed)
        }

        // 2. Questions: the provider for the configured mode.
        if (current.aiMode == LauncherSettings.AI_MODE_OFF) {
            usageLog.record("Declined (assistant off): ${request.take(60)}", "nothing", ENGINE_RULES)
            return AiReply(
                "The assistant is off (Settings › AI › Engine). Launcher commands like “switch to dark mode” still work here.",
                appliedSettings = false,
            )
        }
        val wantsOnDevice = current.aiMode == LauncherSettings.AI_MODE_ONDEVICE
        val onDeviceReady = wantsOnDevice && onDeviceProvider.usableUnder(current) &&
            runCatching { onDeviceProvider.available() }.getOrDefault(false)
        val provider = if (onDeviceReady) onDeviceProvider else templateProvider
        val snapshot = runCatching { contextBuilder.snapshot() }.getOrNull()
        val rendered = snapshot?.render().orEmpty()
        val dataUsed = describeData(snapshot)

        val answer = try {
            provider.complete(request, rendered)
        } catch (e: Exception) {
            Log.w(TAG, "${provider.name} failed; falling back to template", e)
            if (provider !== templateProvider) {
                runCatching { templateProvider.complete(request, rendered) }
                    .getOrElse { "I couldn't answer that right now." }
            } else {
                "I couldn't answer that right now."
            }
        }
        val note = if (wantsOnDevice && !onDeviceReady) {
            val reason = (onDeviceProvider as? AiCoreProvider)?.unavailableReason() ?: "Gemini Nano is unavailable"
            "\n\n($reason Answered with the on-device template engine instead.)"
        } else {
            ""
        }
        usageLog.record("Answered: ${request.take(80)}", dataUsed, provider.name)
        return AiReply(answer + note, appliedSettings = false)
    }

    private fun describeData(snapshot: AiContextSnapshot?): String {
        if (snapshot == null) return "nothing (context unavailable)"
        val parts = ArrayList<String>()
        if (snapshot.todayEvents.isNotEmpty() || snapshot.upcomingEvents.isNotEmpty()) parts.add("calendar")
        if (snapshot.tasks.isNotEmpty()) parts.add("tasks")
        if (snapshot.missedCalls.isNotEmpty()) parts.add("call log")
        if (snapshot.unreadMail > 0) parts.add("mail headers")
        if (snapshot.badgeCounts.isNotEmpty()) parts.add("notification counts")
        parts.add("settings")
        return parts.joinToString(", ")
    }

    private companion object {
        const val TAG = "FoldOsAiCommandRouter"
        const val ENGINE_RULES = "Rules"
        const val APPS_TIMEOUT_MS = 1_500L
    }
}

/**
 * Marker for an [AiProvider] that sends data off the phone (a cloud model, a web lookup). The router
 * never selects one while `LauncherSettings.noNetwork` is on. [AiCoreProvider] and the template
 * engine run entirely on the device and do not implement it.
 */
interface NetworkAiProvider : AiProvider

/** False for a [NetworkAiProvider] while No-network mode is on; on-device providers are always usable. */
internal fun AiProvider.usableUnder(settings: LauncherSettings): Boolean =
    !(settings.noNetwork && this is NetworkAiProvider)
