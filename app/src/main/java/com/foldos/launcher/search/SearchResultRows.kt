package com.foldos.launcher.search

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CenterFocusWeak
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Message
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.foldos.launcher.home.HomeAppIcon
import com.foldos.launcher.theme.LocalFoldOsTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Section title inside the results surface (mock-up section labels: accent `labelLarge`). */
@Composable
fun SearchSectionHeader(title: String, modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = tokens.accent,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
    )
}

/**
 * The Apps section: a scrolling row of icon tiles instead of list rows, so the first few matches
 * read at a glance. [highlightedIndex] is the index within [apps] of the keyboard-selected tile.
 */
@Composable
fun SearchAppsRow(
    apps: List<SearchResult.App>,
    highlightedIndex: Int?,
    onActivate: (SearchResult.App) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFoldOsTokens.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        apps.forEachIndexed { index, result ->
            val highlighted = highlightedIndex == index
            Column(
                modifier = Modifier
                    .width(76.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (highlighted) tokens.highlight else Color.Transparent)
                    .clickable { onActivate(result) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HomeAppIcon(app = result.app, size = 48.dp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = result.app.label,
                    color = if (highlighted) tokens.onHighlight else tokens.onSurface,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * One result row: leading icon or photo, title, subtitle, and for contacts the call / message
 * actions. [highlighted] marks the keyboard selection (also the row Enter activates).
 */
@Composable
fun SearchResultRow(
    result: SearchResult,
    highlighted: Boolean,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFoldOsTokens.current
    val context = LocalContext.current
    val titleColor = if (highlighted) tokens.onHighlight else tokens.onSurface
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (highlighted) tokens.highlight else Color.Transparent)
            .clickable(onClick = onActivate)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SearchResultLeading(result)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.title,
                color = titleColor,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (result.section == SearchSection.ANSWER) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = if (result.section == SearchSection.ANSWER) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = result.subtitle
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = titleColor.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (result is SearchResult.Contact) {
            val phone: String? = result.phone
            val email: String? = result.email
            if (phone != null) {
                IconButton(onClick = { SearchActions.call(context, phone) }) {
                    Icon(imageVector = Icons.Rounded.Call, contentDescription = "Call ${result.name}", tint = tokens.accent)
                }
                IconButton(onClick = { SearchActions.message(context, phone) }) {
                    Icon(imageVector = Icons.Rounded.Message, contentDescription = "Message ${result.name}", tint = tokens.accent)
                }
            } else if (email != null) {
                IconButton(onClick = { SearchActions.email(context, email) }) {
                    Icon(imageVector = Icons.Rounded.Message, contentDescription = "Email ${result.name}", tint = tokens.accent)
                }
            }
        }
    }
}

/** A recent query in the empty state. */
@Composable
fun SearchRecentRow(query: String, highlighted: Boolean, onActivate: () -> Unit, modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (highlighted) tokens.highlight else Color.Transparent)
            .clickable(onClick = onActivate)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.History,
            contentDescription = null,
            tint = (if (highlighted) tokens.onHighlight else tokens.onSurface).copy(alpha = 0.7f),
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = query,
            color = if (highlighted) tokens.onHighlight else tokens.onSurface,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SearchResultLeading(result: SearchResult) {
    when (result) {
        is SearchResult.App -> HomeAppIcon(app = result.app, size = 40.dp)
        is SearchResult.Shortcut -> DrawableIcon(result.icon, Icons.Rounded.Bolt)
        is SearchResult.Contact -> ContactAvatar(result)
        is SearchResult.Setting -> GlyphIcon(Icons.Rounded.Settings)
        is SearchResult.MediaFile -> GlyphIcon(fileIcon(result.mimeType))
        is SearchResult.CalendarEvent -> GlyphIcon(Icons.Rounded.Event)
        is SearchResult.Calculation -> GlyphIcon(Icons.Rounded.Calculate, accent = true)
        is SearchResult.Conversion -> GlyphIcon(Icons.Rounded.SwapHoriz, accent = true)
        is SearchResult.Web -> GlyphIcon(Icons.Rounded.Public)
        is SearchResult.Url -> GlyphIcon(Icons.Rounded.Link)
        is SearchResult.Visual -> GlyphIcon(Icons.Rounded.CenterFocusWeak)
        is SearchResult.Permission -> GlyphIcon(Icons.Rounded.Lock)
    }
}

private fun fileIcon(mimeType: String?): ImageVector {
    val mime = mimeType.orEmpty()
    return when {
        mime.startsWith("image/") -> Icons.Rounded.Photo
        mime.startsWith("video/") -> Icons.Rounded.Videocam
        mime.startsWith("audio/") -> Icons.Rounded.MusicNote
        mime == "application/pdf" || mime.startsWith("text/") -> Icons.Rounded.Description
        else -> Icons.Rounded.InsertDriveFile
    }
}

/** A 40dp tinted circle with a Material glyph. */
@Composable
private fun GlyphIcon(icon: ImageVector, accent: Boolean = false) {
    val tokens = LocalFoldOsTokens.current
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (accent) tokens.accent else tokens.highlight),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (accent) tokens.onAccent else tokens.onHighlight,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** A shortcut icon drawable rasterised at 40dp, or [fallback] when the system gave none. */
@Composable
private fun DrawableIcon(drawable: Drawable?, fallback: ImageVector) {
    val tokens = LocalFoldOsTokens.current
    val sizePx = with(LocalDensity.current) { 40.dp.roundToPx() }
    val bitmap: ImageBitmap? = remember(drawable, sizePx) {
        drawable?.let { d -> runCatching { d.toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).asImageBitmap() }.getOrNull() }
    }
    if (bitmap == null) {
        GlyphIcon(fallback)
    } else {
        val percent = (tokens.iconCornerFraction * 100f).toInt().coerceIn(0, 50)
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(percent = percent)),
            contentScale = ContentScale.Fit,
        )
    }
}

/** Contact photo thumbnail, or initials on the accent color. */
@Composable
private fun ContactAvatar(contact: SearchResult.Contact) {
    val tokens = LocalFoldOsTokens.current
    val context = LocalContext.current
    val photo by produceState<ImageBitmap?>(initialValue = null, key1 = contact.photoUri) {
        val uri = contact.photoUri
        value = if (uri == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
                }.getOrNull()
            }
        }
    }
    val current = photo
    if (current != null) {
        Image(
            bitmap = current,
            contentDescription = null,
            modifier = Modifier.size(40.dp).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(tokens.accent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initials(contact.name),
                color = tokens.onAccent,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
        }
    }
}

private fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
    }
}
