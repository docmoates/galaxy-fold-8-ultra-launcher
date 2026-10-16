package com.foldos.launcher.builtins

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldos.launcher.theme.FoldOsSurface
import com.foldos.launcher.theme.LocalFoldOsTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The READ_MEDIA_IMAGES permission string the Photos widget requests. */
internal const val PHOTOS_PERMISSION: String = Manifest.permission.READ_MEDIA_IMAGES

/**
 * Either of these counts as photo access: full READ_MEDIA_IMAGES, or the partial
 * READ_MEDIA_VISUAL_USER_SELECTED that Android 14's "Select photos" choice grants instead (the
 * widget then shows the images the user picked; MediaStore filters the rest).
 */
internal val PHOTOS_ACCESS_PERMISSIONS: List<String> = listOf(
    Manifest.permission.READ_MEDIA_IMAGES,
    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
)

/** A recent image from MediaStore with its decoded thumbnail. */
internal data class RecentPhoto(
    val uri: Uri,
    val displayName: String,
    val album: String,
    val dateAddedMillis: Long,
    val thumbnail: ImageBitmap?,
)

/**
 * Photo frame (mock-up `wgRender` kind `photo`): the newest image from MediaStore fills the card
 * behind a "Photos" chip and a caption; wide multi-row sizes add a strip of the next thumbnails.
 * Without READ_MEDIA_IMAGES (or the partial READ_MEDIA_VISUAL_USER_SELECTED) it shows an "Allow
 * photo access" button. Tap opens the image.
 */
@Composable
fun PhotosWidget(spanX: Int, spanY: Int, modifier: Modifier) {
    val one = spanY == 1
    val tokens = LocalFoldOsTokens.current
    val granted = rememberAnyPermissionGranted(PHOTOS_ACCESS_PERMISSIONS)
    val requester = LocalPermissionRequester.current
    if (!granted) {
        BuiltInCard(modifier, one = one) {
            WidgetPermissionPrompt(
                message = "Show your latest photo here.",
                buttonText = "Allow photo access",
                onClick = { requester(PHOTOS_PERMISSION) },
                one = one,
            )
        }
        return
    }

    val context = LocalContext.current
    val resumeTick = rememberResumeTick()
    val wantStrip = !one && spanX >= 3 && spanY >= 2
    val photos by produceState(initialValue = emptyList<RecentPhoto>(), resumeTick, wantStrip) {
        value = loadRecentPhotos(context, limit = if (wantStrip) 5 else 1)
    }
    val latest = photos.firstOrNull()

    // Full-bleed card: no inner padding so the photo reaches the rounded edge.
    FoldOsSurface(modifier = modifier, contentPadding = PaddingValues(0.dp)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (latest?.thumbnail != null) {
                Image(
                    bitmap = latest.thumbnail,
                    contentDescription = latest.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clickable { openPhoto(context, latest.uri) },
                )
            } else {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(listOf(tokens.accent.copy(alpha = 0.55f), tokens.highlight, tokens.surface)),
                    ),
                )
            }
            // bottom scrim so the caption reads on any photo
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.55f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.62f),
                        ),
                    ),
            )
            CompositionLocalProvider(LocalContentColor provides Color.White) {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = if (one) 8.dp else 12.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (!one) {
                        PhotosChip()
                    } else {
                        Box(Modifier.height(0.dp))
                    }
                    Column {
                        if (wantStrip && photos.size > 1) {
                            Row(
                                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                photos.drop(1).take(4).forEach { photo ->
                                    val t = photo.thumbnail
                                    Box(
                                        Modifier
                                            .size(if (maxHeight > 220.dp) 48.dp else 36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.White.copy(alpha = 0.2f))
                                            .clickable { openPhoto(context, photo.uri) },
                                    ) {
                                        if (t != null) {
                                            Image(t, contentDescription = photo.displayName, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                        }
                                    }
                                }
                            }
                        }
                        Text(
                            text = latest?.album?.takeIf { it.isNotBlank() } ?: if (latest == null) "No photos yet" else "Photos",
                            fontSize = if (one) 13.sp else 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (spanX >= 2 && latest != null) {
                            Text(
                                text = latest.dateText(),
                                fontSize = 11.5.sp,
                                color = Color.White.copy(alpha = 0.8f),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Mock-up `.pchip`: translucent pill with the gallery icon and "Photos". */
@Composable
private fun PhotosChip() {
    Row(
        Modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(14.dp))
        Text("Photos", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun RecentPhoto.dateText(): String =
    Instant.ofEpochMilli(dateAddedMillis).atZone(ZoneId.systemDefault()).toLocalDate().format(photoDate)

private val photoDate: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

private fun openPhoto(context: Context, uri: Uri) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "image/*")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }
}

/** Newest [limit] images (DATE_ADDED desc) with 512px thumbnails; empty on any failure. */
internal suspend fun loadRecentPhotos(context: Context, limit: Int): List<RecentPhoto> = withContext(Dispatchers.IO) {
    if (!context.isAnyPermissionGranted(PHOTOS_ACCESS_PERMISSIONS)) return@withContext emptyList()
    val resolver = context.contentResolver
    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        MediaStore.Images.Media.DATE_ADDED,
    )
    val args = Bundle().apply {
        putInt(ContentResolver.QUERY_ARG_LIMIT, limit.coerceAtLeast(1))
        putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.Images.Media.DATE_ADDED))
        putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
    }
    val rows = ArrayList<RecentPhoto>()
    runCatching {
        resolver.query(collection, projection, args, null)?.use { cursor ->
            while (cursor.moveToNext() && rows.size < limit) {
                val id = cursor.getLong(0)
                val uri = ContentUris.withAppendedId(collection, id)
                rows += RecentPhoto(
                    uri = uri,
                    displayName = cursor.getString(1) ?: "",
                    album = cursor.getString(2) ?: "",
                    dateAddedMillis = cursor.getLong(3) * 1000L,
                    thumbnail = null,
                )
            }
        }
    }
    rows.mapIndexed { index, photo ->
        val px = if (index == 0) 640 else 192
        val bitmap = runCatching { resolver.loadThumbnail(photo.uri, Size(px, px), null) }.getOrNull()
        photo.copy(thumbnail = bitmap?.asImageBitmap())
    }
}
