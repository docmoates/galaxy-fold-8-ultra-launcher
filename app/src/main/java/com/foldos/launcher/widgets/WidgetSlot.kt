package com.foldos.launcher.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldos.launcher.core.LocalAppGraph
import com.foldos.launcher.core.model.HomeItem
import com.foldos.launcher.theme.LocalFoldOsTokens
import java.util.Locale

/**
 * Renders one widget [HomeItem] in the box the home grid gives it:
 * - [HomeItem.isAppWidget]: the hosted app widget via [AppWidgetView] (clipped to the theme
 *   radius), or [WidgetErrorPlaceholder] when its provider is no longer installed;
 * - [HomeItem.isBuiltInWidget]: `LocalAppGraph.current.builtIns.byId(builtinId).content`, or a
 *   placeholder when that id is unknown;
 * - anything else: a placeholder.
 *
 * [cellWidthDp] / [cellHeightDp] are the page's cell size; the dp box is derived with
 * [widgetWidthDp] / [widgetHeightDp] (free-placed items use their own dp size). Items sharing a
 * `stackId` are flipped by `home.HomeStackSlot`, which draws each one through this slot.
 */
@Composable
fun WidgetSlot(
    item: HomeItem,
    cellWidthDp: Int,
    cellHeightDp: Int,
    modifier: Modifier = Modifier,
) {
    val widthDp = item.widgetWidthDp(cellWidthDp)
    val heightDp = item.widgetHeightDp(cellHeightDp)
    val appWidgetId = item.appWidgetId
    val builtinId = item.builtinId
    when {
        item.isAppWidget && appWidgetId != null -> {
            val context = LocalContext.current
            val provider: AppWidgetProviderInfo? = remember(appWidgetId) {
                try {
                    AppWidgetManager.getInstance(context)?.getAppWidgetInfo(appWidgetId)
                } catch (e: RuntimeException) {
                    Log.w("WidgetSlot", "getAppWidgetInfo($appWidgetId) failed", e)
                    null
                }
            }
            if (provider == null) {
                WidgetErrorPlaceholder(
                    title = item.title ?: "Widget",
                    message = "This widget's app is no longer installed",
                    modifier = modifier,
                )
            } else {
                val radius = LocalFoldOsTokens.current.radius
                AppWidgetView(
                    appWidgetId = appWidgetId,
                    provider = provider,
                    widthDp = widthDp,
                    heightDp = heightDp,
                    modifier = modifier.clip(RoundedCornerShape(radius)),
                )
            }
        }
        item.isBuiltInWidget && builtinId != null -> {
            val builtIn = LocalAppGraph.current.builtIns.byId(builtinId)
            if (builtIn == null) {
                WidgetErrorPlaceholder(
                    title = item.title ?: "Widget",
                    message = "Unknown FoldOS widget \"$builtinId\"",
                    modifier = modifier,
                )
            } else {
                builtIn.content(item.spanX.coerceAtLeast(1), item.spanY.coerceAtLeast(1), modifier)
            }
        }
        else -> WidgetErrorPlaceholder(
            title = item.title ?: "Widget",
            message = "This item is not a widget",
            modifier = modifier,
        )
    }
}

/**
 * Themed card (mock-up `.w`) shown in place of a widget that cannot be drawn: small uppercase
 * [title], a widgets icon and a one- or two-line [message].
 */
@Composable
fun WidgetErrorPlaceholder(
    title: String,
    message: String?,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFoldOsTokens.current
    val shape = RoundedCornerShape(tokens.radius)
    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(shape)
            .background(tokens.surface, shape)
            .border(1.dp, tokens.surfaceBorder, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title.uppercase(Locale.getDefault()),
                color = tokens.onSurface.copy(alpha = 0.72f),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Outlined.Widgets,
                contentDescription = null,
                tint = tokens.onSurface.copy(alpha = 0.8f),
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        if (message != null) {
            Text(
                text = message,
                color = tokens.onSurface.copy(alpha = 0.8f),
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
        }
    }
}
