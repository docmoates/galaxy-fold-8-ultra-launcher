package com.foldos.launcher.profiles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.foldos.launcher.core.AppGraph
import com.foldos.launcher.core.FoldOsApplication
import com.foldos.launcher.core.model.Profile
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Locale

/*
 * Pieces shared by the profile switcher sheet, the profile editor and Settings > Profiles. They
 * read the AppGraph from the Application (the same instance `LocalAppGraph` carries in the
 * launcher), so they work from both the launcher and the Settings activity, which does not provide
 * the composition local.
 */

/** The process-wide [AppGraph]. */
@Composable
fun rememberProfileGraph(): AppGraph {
    val context = LocalContext.current
    return remember(context) { FoldOsApplication.from(context).graph }
}

/** Monday..Sunday as `java.util.Calendar` day constants, the order the day chips use. */
val PROFILE_DAY_ORDER: List<Int> = listOf(
    Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
    Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY,
)

private val WEEKEND: Set<Int> = setOf(Calendar.SATURDAY, Calendar.SUNDAY)

/** An icon suggested by the profile's name (Work, Home, Night, Travel, Kids), else a person. */
fun profileIcon(name: String): ImageVector {
    val lower = name.lowercase(Locale.ROOT)
    return when {
        lower.contains("work") || lower.contains("office") -> Icons.Outlined.Work
        lower.contains("home") -> Icons.Outlined.Home
        lower.contains("night") || lower.contains("sleep") || lower.contains("bed") -> Icons.Outlined.Bedtime
        lower.contains("travel") || lower.contains("trip") || lower.contains("flight") -> Icons.Outlined.Flight
        lower.contains("kid") || lower.contains("child") -> Icons.Outlined.ChildCare
        else -> Icons.Outlined.Person
    }
}

/** "9:00 AM" / "09:00" for minutes after midnight, in the device's short time format. */
fun formatMinuteOfDay(minute: Int): String {
    val clamped = minute.coerceIn(0, 24 * 60 - 1)
    return LocalTime.of(clamped / 60, clamped % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
}

/** "Mon" for `Calendar.MONDAY`, localised. */
fun dayShortName(calendarDay: Int): String =
    toDayOfWeek(calendarDay).getDisplayName(TextStyle.SHORT, Locale.getDefault())

private fun toDayOfWeek(calendarDay: Int): DayOfWeek = when (calendarDay) {
    Calendar.MONDAY -> DayOfWeek.MONDAY
    Calendar.TUESDAY -> DayOfWeek.TUESDAY
    Calendar.WEDNESDAY -> DayOfWeek.WEDNESDAY
    Calendar.THURSDAY -> DayOfWeek.THURSDAY
    Calendar.FRIDAY -> DayOfWeek.FRIDAY
    Calendar.SATURDAY -> DayOfWeek.SATURDAY
    else -> DayOfWeek.SUNDAY
}

/** "Every day", "Mon-Fri", "Weekends" or the listed days. */
fun daysSummary(days: Set<Int>): String = when {
    days.isEmpty() || days.size >= 7 -> "Every day"
    days == FoldOsProfileRepository.WEEKDAYS -> "Mon–Fri"
    days == WEEKEND -> "Weekends"
    else -> PROFILE_DAY_ORDER.filter { it in days }.joinToString(" ") { dayShortName(it) }
}

/** One line describing when the profile switches on by itself; "Manual" without rules. */
fun scheduleSummary(profile: Profile): String {
    val parts = ArrayList<String>(2)
    val start = profile.startMinute
    val end = profile.endMinute
    if (start != null && end != null) {
        parts += "${formatMinuteOfDay(start)} – ${formatMinuteOfDay(end)} · ${daysSummary(profile.days)}"
    }
    if (profile.onDnd) parts += "When Do Not Disturb is on"
    return if (parts.isEmpty()) "Manual" else parts.joinToString(" · ")
}

/**
 * One profile in a list: icon, name, schedule summary, a tick when it is the active one and a
 * pencil that opens the editor. Tapping the row calls [onActivate].
 */
@Composable
fun ProfileRow(
    profile: Profile,
    active: Boolean,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = ListItemDefaults.containerColor,
) {
    val primary = MaterialTheme.colorScheme.primary
    ListItem(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onActivate),
        colors = ListItemDefaults.colors(containerColor = containerColor),
        leadingContent = {
            Icon(
                imageVector = profileIcon(profile.name),
                contentDescription = null,
                tint = if (active) primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        headlineContent = {
            Text(
                text = profile.name,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(text = scheduleSummary(profile), maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (active) {
                    Icon(imageVector = Icons.Filled.Check, contentDescription = "Active", tint = primary)
                }
                IconButton(onClick = onEdit) {
                    Icon(imageVector = Icons.Outlined.Edit, contentDescription = "Edit ${profile.name}")
                }
            }
        },
    )
}

/**
 * "Save current as…": asks for a name and snapshots the current settings and hidden apps into a
 * new profile through `ProfileRepository.captureCurrent` on the app scope.
 */
@Composable
fun SaveCurrentProfileDialog(onDismiss: () -> Unit) {
    val graph = rememberProfileGraph()
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Save current as…") },
        text = {
            Column {
                Text(
                    text = "Saves today's look, home styles, dock, gestures and hidden apps as a profile you can switch to or schedule.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(text = "Profile name") },
                    placeholder = { Text(text = "Travel") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    val trimmed = name.trim()
                    graph.appScope.launch { graph.profiles.captureCurrent(trimmed) }
                    onDismiss()
                },
            ) { Text(text = "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(text = "Cancel") } },
    )
}
