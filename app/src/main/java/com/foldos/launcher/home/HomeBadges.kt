package com.foldos.launcher.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldos.launcher.core.model.HomeItem
import com.foldos.launcher.core.model.ItemKind
import com.foldos.launcher.core.model.LauncherSettings

/** Badge fill of the mock-up (`.bdg` background #ff3b4e); the same in every theme. */
val HomeBadgeColor: Color = Color(0xFFFF3B4E)

/**
 * Unread count for [item] from the `BadgeRepository.counts` map ([counts]: package -> count): the
 * app's own count for an APP, the sum of both apps for a PAIR, and 0 for widgets. A folder's badge
 * is the sum of its children's, see [homeBadgeCount] with a list.
 */
fun homeBadgeCount(counts: Map<String, Int>, item: HomeItem): Int {
    fun of(packageName: String?): Int = if (packageName == null) 0 else counts[packageName] ?: 0
    return when (item.kind) {
        ItemKind.APP -> of(item.packageName)
        ItemKind.PAIR -> of(item.packageName) + of(item.packageName2)
        ItemKind.FOLDER, ItemKind.WIDGET -> 0
    }
}

/** Sum of the badge counts of [items] (a folder's children). */
fun homeBadgeCount(counts: Map<String, Int>, items: List<HomeItem>): Int = items.sumOf { homeBadgeCount(counts, it) }

/**
 * Lays [content] out and draws a notification badge over its top-right corner when [count] > 0 and
 * [style] is not [LauncherSettings.BADGE_OFF]: a red count pill (mock-up `.bdg`: 20dp tall,
 * radius 10, 11sp semi-bold white, 2dp dark ring, offset -5 / -6 dp outside the corner) for
 * [LauncherSettings.BADGE_COUNT], a 10dp dot for [LauncherSettings.BADGE_DOT]. The badge is drawn
 * outside [content]'s bounds, so do not clip the parent.
 */
@Composable
fun HomeBadgedBox(
    count: Int,
    style: String,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier) {
        content()
        if (count > 0 && style != LauncherSettings.BADGE_OFF) {
            HomeBadge(count = count, dot = style == LauncherSettings.BADGE_DOT, modifier = Modifier.align(Alignment.TopEnd))
        }
    }
}

/** The badge itself (count pill or dot), positioned by the caller at the top-right corner. */
@Composable
fun HomeBadge(count: Int, dot: Boolean, modifier: Modifier = Modifier) {
    val ring = Color.Black.copy(alpha = 0.2f)
    if (dot) {
        Box(
            modifier
                .offset(x = 3.dp, y = (-3).dp)
                .size(10.dp)
                .background(HomeBadgeColor, CircleShape)
                .border(2.dp, ring, CircleShape),
        )
    } else {
        val shape = RoundedCornerShape(10.dp)
        Box(
            modifier
                .offset(x = 6.dp, y = (-5).dp)
                .defaultMinSize(minWidth = 20.dp)
                .height(20.dp)
                .background(HomeBadgeColor, shape)
                .border(2.dp, ring, shape)
                .padding(horizontal = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (count > 99) "99+" else count.toString(),
                color = Color.White,
                fontSize = 11.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}
