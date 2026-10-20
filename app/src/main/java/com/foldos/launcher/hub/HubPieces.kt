package com.foldos.launcher.hub

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.semantics.Role
import com.foldos.launcher.a11y.a11yHeading
import com.foldos.launcher.a11y.a11yMinTouchTarget
import com.foldos.launcher.builtins.LocalPermissionRequester
import com.foldos.launcher.core.LocalAppGraph
import com.foldos.launcher.core.repo.HubContact
import com.foldos.launcher.core.repo.HubEvent
import com.foldos.launcher.theme.FoldOsSurface
import com.foldos.launcher.theme.LocalFoldOsTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Shared building blocks of the Hub page: the mock-up's `.pg-h`, `.lcard`, `.lc-h`, `.k2`, `.av`,
 * `.srch`, `.fab`, `.ev` blocks, permission cards, and the clock / formatting / intent helpers the
 * Today, Calendar and Contacts tabs share.
 */

/** Horizontal page padding (mock-up `.pg{padding:10px 16px}`). */
internal val HubPagePadding: Dp = 16.dp

/** Gap between stacked cards on a hub page (`.pg{gap:12px}`). */
internal val HubCardGap: Dp = 12.dp

/* ------------------------------------------------------------------------------------------ */
/* Permissions and clocks                                                                      */
/* ------------------------------------------------------------------------------------------ */

/**
 * Whether [permission] is granted; re-checked on every resume (the system permission dialog pauses
 * the launcher, so its result always arrives through a resume) and whenever the hub repository's
 * `permissionsVersion` is bumped (`MainActivity`'s permission callback). No polling.
 */
@Composable
internal fun rememberHubPermission(permission: String): Boolean {
    val context = LocalContext.current
    val repo = LocalAppGraph.current.hub as? FoldOsHubRepository
    val version = if (repo != null) repo.permissionsVersion.collectAsStateWithLifecycle().value else 0
    var granted by remember(permission) { mutableStateOf(context.hubPermissionGranted(permission)) }
    LifecycleResumeEffect(permission) {
        granted = context.hubPermissionGranted(permission)
        onPauseOrDispose { }
    }
    LaunchedEffect(permission, version) {
        granted = context.hubPermissionGranted(permission)
    }
    return granted
}

internal fun Context.hubPermissionGranted(permission: String): Boolean =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

/** Wall clock that recomposes at the start of every minute. */
@Composable
internal fun rememberHubMinute(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            val millisToNextMinute = (60 - now.second) * 1000L - (now.nano / 1_000_000L)
            delay(millisToNextMinute.coerceIn(250L, 60_000L))
        }
    }
    return now
}

/* ------------------------------------------------------------------------------------------ */
/* Page scaffolding                                                                            */
/* ------------------------------------------------------------------------------------------ */

/** A vertically scrolling page column with the mock-up's card gap; [contentPadding] clears the tab bar. */
@Composable
internal fun HubScrollColumn(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(HubCardGap),
        content = content,
    )
}

/** Inner-screen layout: two equal columns split at the hinge (mock-up `.pg.two`). */
@Composable
internal fun HubTwoPane(
    modifier: Modifier = Modifier,
    left: @Composable ColumnScope.() -> Unit,
    right: @Composable ColumnScope.() -> Unit,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight(), content = left)
        Column(Modifier.weight(1f).fillMaxHeight(), content = right)
    }
}

/** Mock-up `.pg-h`: 30sp title, 13.5sp subtitle with an optional glyph, big trailing icon. */
@Composable
internal fun HubPageHeader(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    subtitleIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    onTrailingClick: (() -> Unit)? = null,
) {
    val tokens = LocalFoldOsTokens.current
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = tokens.onSurface,
                fontSize = 30.sp,
                lineHeight = 33.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.3).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Row(
                    modifier = Modifier.padding(top = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (subtitleIcon != null) {
                        Icon(
                            imageVector = subtitleIcon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = tokens.onSurface.copy(alpha = 0.75f),
                        )
                    }
                    Text(
                        text = subtitle,
                        color = tokens.onSurface.copy(alpha = 0.75f),
                        fontSize = 13.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (trailingIcon != null) {
            val clickModifier = if (onTrailingClick != null) {
                Modifier.clip(CircleShape).clickable(onClick = onTrailingClick)
            } else {
                Modifier
            }
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                modifier = clickModifier.size(38.dp),
                tint = tokens.onSurface.copy(alpha = 0.85f),
            )
        }
    }
}

/** Mock-up `.lcard`: a themed surface whose children stack vertically with no inner padding. */
@Composable
internal fun HubListCard(
    modifier: Modifier = Modifier,
    color: Color = LocalFoldOsTokens.current.surface,
    borderColor: Color = LocalFoldOsTokens.current.surfaceBorder,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    FoldOsSurface(modifier = modifier, color = color, borderColor = borderColor, contentPadding = contentPadding) {
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

/** Mock-up `.lc-h`: 16sp semibold card title with a right-aligned count or icon. */
@Composable
internal fun HubCardHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailingText: String? = null,
    trailingIcon: ImageVector? = null,
    onTrailingClick: (() -> Unit)? = null,
) {
    val tokens = LocalFoldOsTokens.current
    // A handler without an icon makes the whole header the tap target (mock-up "Reach out" card).
    val rowClick = if (onTrailingClick != null && trailingIcon == null) {
        Modifier.clickable(onClick = onTrailingClick)
    } else {
        Modifier
    }
    Row(
        modifier = modifier.then(rowClick).fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = tokens.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f).a11yHeading(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (trailingText != null) {
            Text(
                text = trailingText,
                color = tokens.onSurface.copy(alpha = 0.7f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
        if (trailingIcon != null) {
            val clickModifier = if (onTrailingClick != null) {
                Modifier.clip(CircleShape).clickable(onClick = onTrailingClick)
            } else {
                Modifier
            }
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                modifier = clickModifier.size(22.dp),
                tint = tokens.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}

/** Mock-up `.k2`: small uppercase section label. */
@Composable
internal fun HubKicker(text: String, modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    Text(
        text = text.uppercase(Locale.getDefault()),
        modifier = modifier.a11yHeading().padding(horizontal = 4.dp),
        color = tokens.onSurface.copy(alpha = 0.72f),
        fontSize = 11.5.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** A dim one-line message inside a card ("Nothing scheduled"). */
@Composable
internal fun HubEmptyRow(text: String, modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    Text(
        text = text,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        color = tokens.onSurface.copy(alpha = 0.65f),
        fontSize = 13.5.sp,
    )
}

/** Hairline between list rows (mock-up `--line2`). */
@Composable
internal fun HubRowDivider(modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    Box(modifier.fillMaxWidth().height(1.dp).background(tokens.onSurface.copy(alpha = 0.08f)))
}

/** Mock-up `.srch`: the 50dp search pill with a live text field. */
@Composable
internal fun HubSearchPill(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val tokens = LocalFoldOsTokens.current
    val shape = RoundedCornerShape(25.dp)
    FoldOsSurface(
        modifier = modifier.fillMaxWidth().height(50.dp),
        shape = shape,
        contentPadding = PaddingValues(horizontal = 18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = tokens.onSurface,
            )
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = tokens.onSurface.copy(alpha = 0.7f),
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = tokens.onSurface, fontSize = 15.sp),
                    singleLine = true,
                    cursorBrush = SolidColor(tokens.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
                )
            }
            if (trailing != null) trailing()
        }
    }
}

/** Mock-up `.fab`: accent pill with icon and label, 56dp high. */
@Composable
internal fun HubFab(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .height(56.dp)
            .clip(shape)
            .background(tokens.accent)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = tokens.onAccent)
        Text(text = label, color = tokens.onAccent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Mock-up `.cact div` / `.crow .ms`: a round icon button on an accent-tinted disc. */
@Composable
internal fun HubIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tinted: Boolean = true,
) {
    val tokens = LocalFoldOsTokens.current
    Box(
        modifier = modifier
            .a11yMinTouchTarget()
            .size(size)
            .clip(CircleShape)
            .background(if (tinted) tokens.accent.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(size * 0.5f),
            tint = if (tinted) tokens.accent else tokens.onSurface.copy(alpha = 0.65f),
        )
    }
}

/**
 * What the "Allow" control of a permission card / panel does. [opensSettings] is true once the
 * system will no longer show the permission dialog (the user denied it until Android stopped asking),
 * so a tap opens the app's system settings page instead of silently doing nothing.
 */
private class HubPermissionTap(val opensSettings: Boolean, val onTap: () -> Unit)

private tailrec fun Context.findHubActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHubActivity()
    else -> null
}

@Composable
private fun rememberHubPermissionTap(permission: String): HubPermissionTap {
    val context = LocalContext.current
    val request = LocalPermissionRequester.current
    var asked by remember(permission) { mutableStateOf(false) }
    var blocked by remember(permission) { mutableStateOf(false) }
    // The permission dialog pauses the launcher, so its outcome always arrives through a resume. After a
    // request that left the permission denied without a rationale, Android will not prompt again.
    LifecycleResumeEffect(permission) {
        if (asked) {
            val rationale = context.findHubActivity()?.shouldShowRequestPermissionRationale(permission) ?: true
            blocked = !context.hubPermissionGranted(permission) && !rationale
        }
        onPauseOrDispose { }
    }
    return HubPermissionTap(
        opensSettings = blocked,
        onTap = {
            val rationale = context.findHubActivity()?.shouldShowRequestPermissionRationale(permission) ?: true
            if (blocked || (asked && !rationale && !context.hubPermissionGranted(permission))) {
                blocked = true
                HubIntents.start(
                    context,
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            } else {
                asked = true
                request(permission)
            }
        },
    )
}

/**
 * Card shown in place of live data while [permission] is missing: title, why it is needed and an
 * "Allow" pill that goes through [LocalPermissionRequester] (or opens the app settings once the
 * system no longer shows the dialog).
 */
@Composable
internal fun HubPermissionCard(
    title: String,
    body: String,
    permission: String,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFoldOsTokens.current
    val tap = rememberHubPermissionTap(permission)
    HubListCard(modifier = modifier, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = tokens.onSurface.copy(alpha = 0.7f),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = title, color = tokens.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(text = body, color = tokens.onSurface.copy(alpha = 0.7f), fontSize = 12.5.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(
                text = if (tap.opensSettings) "Settings" else "Allow",
                color = tokens.onAccent,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(tokens.accent)
                    .clickable { tap.onTap() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/**
 * Full-tab state shown instead of the normal content while [permission] is missing: one centred
 * card with a large [icon] in a tinted circle, [title], a one-line [reason] and an "Allow" button
 * that goes through [LocalPermissionRequester]. Fills its parent; callers apply their own padding.
 */
@Composable
internal fun HubPermissionPanel(
    icon: ImageVector,
    title: String,
    reason: String,
    permission: String,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFoldOsTokens.current
    val tap = rememberHubPermissionTap(permission)
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        HubListCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(tokens.accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(34.dp),
                        tint = tokens.accent,
                    )
                }
                Text(
                    text = title,
                    color = tokens.onSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = reason,
                    color = tokens.onSurface.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (tap.opensSettings) "Open app settings" else "Allow",
                    color = tokens.onAccent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(tokens.accent)
                        .clickable(role = Role.Button) { tap.onTap() }
                        .padding(horizontal = 28.dp, vertical = 11.dp),
                )
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Avatars                                                                                     */
/* ------------------------------------------------------------------------------------------ */

/** Mock-up avatar palette; a contact's color is picked by hashing its name. */
private val HubAvatarPalette: List<Color> = listOf(
    Color(0xFF0EA5E9), Color(0xFFE11D48), Color(0xFF65A30D), Color(0xFFD97706), Color(0xFF7C3AED),
    Color(0xFFDB2777), Color(0xFF8B5CF6), Color(0xFF0891B2), Color(0xFFF43F5E), Color(0xFF10B981),
    Color(0xFF3B82F6), Color(0xFFF59E0B),
)

internal fun hubAvatarColor(name: String): Color =
    HubAvatarPalette[Math.floorMod(name.trim().lowercase(Locale.ROOT).hashCode(), HubAvatarPalette.size)]

/** "Sam Rivera" -> "SR", "Mom" -> "M", "+1 512" -> "#". */
internal fun hubInitials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() && it.first().isLetterOrDigit() }
    if (parts.isEmpty()) return "#"
    val first = parts.first().first().uppercaseChar()
    return if (parts.size >= 2) "$first${parts.last().first().uppercaseChar()}" else first.toString()
}

/** First name for compact rows ("Sam Rivera" -> "Sam"). */
internal fun hubFirstName(name: String): String = name.trim().split(Regex("\\s+")).firstOrNull() ?: name

/** Decodes a contact photo URI off the main thread; null while loading or when there is none. */
@Composable
internal fun rememberHubPhoto(photoUri: String?): ImageBitmap? {
    val context = LocalContext.current
    val state = produceState<ImageBitmap?>(initialValue = null, photoUri) {
        value = if (photoUri.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(Uri.parse(photoUri))?.use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                }.getOrNull()
            }
        }
    }
    return state.value
}

/** Mock-up `.av`: round photo or coloured initials. */
@Composable
internal fun HubAvatar(name: String, photoUri: String?, size: Dp, modifier: Modifier = Modifier) {
    val photo = rememberHubPhoto(photoUri)
    val color = remember(name) { hubAvatarColor(name) }
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            Image(
                bitmap = photo,
                contentDescription = null,
                modifier = Modifier.size(size),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = hubInitials(name),
                color = Color.White,
                fontSize = (size.value * 0.36f).sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Events                                                                                      */
/* ------------------------------------------------------------------------------------------ */

/** The event's calendar colour, or the theme accent when the provider gave none. */
@Composable
internal fun hubEventColor(event: HubEvent): Color {
    val tokens = LocalFoldOsTokens.current
    return if (event.color == 0) tokens.accent else Color(event.color)
}

/** Mock-up `.ev`: 4dp colour bar, bold "10:00 · Standup", dim subtitle. */
@Composable
internal fun HubEventRow(
    event: HubEvent,
    modifier: Modifier = Modifier,
    subtitle: String = hubEventSubtitle(event),
    onClick: (() -> Unit)? = null,
) {
    val tokens = LocalFoldOsTokens.current
    val color = hubEventColor(event)
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier = modifier.fillMaxWidth().then(clickModifier),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.width(4.dp).height(36.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Column(Modifier.weight(1f)) {
            Text(
                text = "${HubFormat.eventStart(event)} · ${event.title}",
                color = tokens.onSurface,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = tokens.onSurface.copy(alpha = 0.7f),
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** "Teams · 15 min" / "15 min" / "All day". */
internal fun hubEventSubtitle(event: HubEvent): String {
    if (event.allDay) return "All day"
    val where = event.location?.takeUnless { FoldOsHubRepository.findUrl(it) == it }?.trim().orEmpty()
    val duration = HubFormat.duration(event.begin, event.end)
    return if (where.isEmpty()) duration else "$where · $duration"
}

/** The URL a "Join" button opens for [event], or null when its location holds none. */
internal fun hubJoinUrl(event: HubEvent): String? = FoldOsHubRepository.findUrl(event.location)

/* ------------------------------------------------------------------------------------------ */
/* Formatting                                                                                  */
/* ------------------------------------------------------------------------------------------ */

internal object HubFormat {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val clock: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm", Locale.getDefault())
    private val clockAmPm: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    private val weekdayDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())
    private val weekdayLongDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())
    private val shortDayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
    private val monthDay: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    private val monthYear: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
    private val weekdayShort: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())

    fun toLocal(millis: Long): LocalDateTime = Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()

    fun toDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun startOfDay(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    /** "10:00" */
    fun clock(millis: Long): String = toLocal(millis).format(clock)

    /** "10:00 AM" */
    fun clockAmPm(millis: Long): String = toLocal(millis).format(clockAmPm)

    /** "All day" or "10:00" */
    fun eventStart(event: HubEvent): String = if (event.allDay) "All day" else clock(event.begin)

    /** "Friday, Oct 16" */
    fun weekdayDate(date: LocalDate): String = date.format(weekdayDate)

    /** "Friday, October 16" */
    fun weekdayLongDate(date: LocalDate): String = date.format(weekdayLongDate)

    /** "Sat, Oct 17" */
    fun shortDayMonth(date: LocalDate): String = date.format(shortDayMonth)

    /** "October 2026" */
    fun monthYear(date: LocalDate): String = date.format(monthYear)

    /** "Fri" */
    fun weekdayShort(date: LocalDate): String = date.format(weekdayShort)

    /** "15 min", "1 h", "1 h 30 min", "2 days". */
    fun duration(beginMillis: Long, endMillis: Long): String {
        val minutes = ((endMillis - beginMillis) / 60_000L).coerceAtLeast(0L)
        val hours = minutes / 60
        val rest = minutes % 60
        return when {
            minutes >= 24 * 60 -> "${minutes / (24 * 60)} days"
            hours == 0L -> "$minutes min"
            rest == 0L -> "$hours h"
            else -> "$hours h $rest min"
        }
    }

    /** "in 19 min", "in 1 h 5 min", "now · ends 10:15", "ended". */
    fun relativeToNow(event: HubEvent, nowMillis: Long): String {
        if (event.allDay) return "Today"
        val untilBegin = (event.begin - nowMillis) / 60_000L
        return when {
            untilBegin > 0 -> "in ${durationText(untilBegin)}"
            event.end > nowMillis -> "now · ends ${clock(event.end)}"
            else -> "ended"
        }
    }

    private fun durationText(minutes: Long): String {
        val hours = minutes / 60
        val rest = minutes % 60
        return when {
            minutes >= 24 * 60 -> "${minutes / (24 * 60)} d"
            hours == 0L -> "$minutes min"
            rest == 0L -> "$hours h"
            else -> "$hours h $rest min"
        }
    }

    /** "Due 12:00 PM" (today), "Due tomorrow", "Due Mon, Oct 19", "Overdue · Oct 14"; null without a date. */
    fun dueText(dueMillis: Long?, today: LocalDate): String? {
        if (dueMillis == null) return null
        val date = toDate(dueMillis)
        return when {
            date.isBefore(today) -> "Overdue · ${date.format(monthDay)}"
            date == today -> {
                val local = toLocal(dueMillis)
                if (local.hour == 0 && local.minute == 0) "Due today" else "Due ${local.format(clockAmPm)}"
            }
            date == today.plusDays(1) -> "Due tomorrow"
            else -> "Due ${date.format(shortDayMonth)}"
        }
    }

    /** True when a due date is today or already past. */
    fun isDueNow(dueMillis: Long?, today: LocalDate): Boolean = dueMillis != null && !toDate(dueMillis).isAfter(today)

    /** "8:02" (today), "Yesterday", "Mon", "Oct 12". */
    fun callTime(millis: Long, today: LocalDate): String {
        val date = toDate(millis)
        return when {
            date == today -> clock(millis)
            date == today.minusDays(1) -> "Yesterday"
            date.isAfter(today.minusDays(7)) -> date.format(weekdayShort)
            else -> date.format(monthDay)
        }
    }

    fun greeting(hour: Int): String = when {
        hour < 5 -> "Good night"
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Intents                                                                                     */
/* ------------------------------------------------------------------------------------------ */

/** Launches the system apps behind the Hub's buttons; every call is best effort and never throws. */
internal object HubIntents {
    private const val TAG = "HubIntents"

    fun dial(context: Context, number: String) =
        start(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))))

    fun message(context: Context, number: String) =
        start(context, Intent(Intent.ACTION_SENDTO, Uri.parse("sms:" + Uri.encode(number))))

    fun email(context: Context, address: String) =
        start(context, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(address))))

    fun openUrl(context: Context, url: String) = start(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    fun openContact(context: Context, contact: HubContact) =
        start(context, Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.getLookupUri(contact.id, contact.lookupKey)))

    /** Opens the system calendar app at [millis] (footer link of the agenda). */
    fun openCalendar(context: Context, millis: Long) =
        start(context, Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time/$millis")))

    fun newContact(context: Context) =
        start(context, Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI))

    fun openEvent(context: Context, event: HubEvent) {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id)
        start(
            context,
            Intent(Intent.ACTION_VIEW, uri)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end),
        )
    }

    /** `CalendarContract` insert intent for a new event starting at [beginMillis]. */
    fun newEvent(context: Context, beginMillis: Long) {
        start(
            context,
            Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginMillis)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, beginMillis + 60L * 60L * 1000L),
        )
    }

    fun start(context: Context, intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Log.w(TAG, "No activity for $intent", it) }
    }
}

/** A `Spacer` of [height] inside card columns. */
@Composable
internal fun HubGap(height: Dp) {
    Spacer(Modifier.height(height))
}

/**
 * 1.6.0 slim top bar of the fitted tabs (Calendar, Contacts): a 24sp title and trailing [actions], 48dp high
 * (scaled with the font scale by the callers that budget heights).
 */
@Composable
internal fun HubSlimBar(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val tokens = LocalFoldOsTokens.current
    Row(
        modifier = modifier.fillMaxWidth().height(HubSlimBarHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            color = tokens.onSurface,
            fontSize = 24.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f).padding(start = 4.dp).a11yHeading(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        actions()
    }
}

internal val HubSlimBarHeight: Dp = 48.dp
