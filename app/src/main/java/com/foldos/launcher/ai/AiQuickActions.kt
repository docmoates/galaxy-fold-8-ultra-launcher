package com.foldos.launcher.ai

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.ui.graphics.vector.ImageVector
import com.foldos.launcher.core.model.LauncherSettings

/**
 * One tile of the AI tab's quick-action grid. [prompt] is sent through `AiConversation` (the command router)
 * when set; the actions without one are handled by `AiTab` (system intents, or a follow-up question for the
 * task, reminder and directions actions).
 */
data class AiQuickAction(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val prompt: String? = null,
)

object AiQuickActions {
    /** All twelve actions in default order; ids match [LauncherSettings.AI_QUICK_ACTION_IDS]. */
    val all: List<AiQuickAction> = listOf(
        AiQuickAction("summarize_mail", "Summarize mail", Icons.Rounded.Description, "Tell me about my unread email in my inbox"),
        AiQuickAction("plan_day", "Plan my day", Icons.Rounded.AutoAwesome, "Give me my briefing for today"),
        AiQuickAction("draft_reply", "Draft a reply", Icons.AutoMirrored.Rounded.Reply, "Draft a short reply to my latest unread email"),
        AiQuickAction("read_mail", "Read my mail", Icons.Rounded.Email, "What's in my inbox?"),
        AiQuickAction("whats_next", "What's next?", Icons.Rounded.Schedule, "What's next?"),
        AiQuickAction("call_back", "Call back", Icons.Rounded.Call),
        AiQuickAction("find_contact", "Find a contact", Icons.Rounded.Person),
        AiQuickAction("add_event", "Add event", Icons.Rounded.Event),
        AiQuickAction("add_task", "Add task", Icons.Rounded.CheckCircle),
        AiQuickAction("set_reminder", "Set a reminder", Icons.Rounded.Notifications),
        AiQuickAction("focus_25", "Focus 25 min", Icons.Rounded.Timer),
        AiQuickAction("directions", "Directions", Icons.Rounded.DirectionsCar),
    )

    fun byId(id: String): AiQuickAction? = all.firstOrNull { it.id == id }

    /** The saved ids as actions, in saved order, skipping unknown ids and duplicates. */
    fun resolve(ids: List<String>): List<AiQuickAction> = normalize(ids).mapNotNull(::byId)

    /** Drops unknown ids and duplicates, keeping order. */
    fun normalize(ids: List<String>): List<String> {
        val known = LauncherSettings.AI_QUICK_ACTION_IDS.toSet()
        return ids.filter { it in known }.distinct()
    }

    /** Settings checklist order: shown actions first (saved order), then the hidden ones in default order. */
    fun checklistOrder(ids: List<String>): List<String> {
        val shown = normalize(ids)
        return shown + LauncherSettings.AI_QUICK_ACTION_IDS.filterNot { it in shown }
    }

    /** Shows or hides [id]; a newly shown action goes to the end. */
    fun toggled(ids: List<String>, id: String, show: Boolean): List<String> {
        val shown = normalize(ids)
        return when {
            show && id !in shown -> shown + id
            !show -> shown - id
            else -> shown
        }
    }

    /** Moves a shown action one place up ([delta] = -1) or down (+1); no-op at the ends or when hidden. */
    fun moved(ids: List<String>, id: String, delta: Int): List<String> {
        val shown = normalize(ids).toMutableList()
        val from = shown.indexOf(id)
        val to = from + delta
        if (from < 0 || to !in shown.indices) return shown
        shown.add(to, shown.removeAt(from))
        return shown
    }
}
