package com.foldos.launcher.builtins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldos.launcher.theme.LocalFoldOsTokens
import kotlin.math.floor
import kotlin.math.max

/** A headline shown by the News widget (static until an RSS / news integration lands). */
data class NewsHeadline(val title: String, val source: String, val age: String)

/** Placeholder stories from the mock-up's News pool. */
val PlaceholderNews: List<NewsHeadline> = listOf(
    NewsHeadline("Fold 8 Ultra review roundup", "The Verge", "1h"),
    NewsHeadline("Austin FC clinch a playoff spot", "Statesman", "3h"),
    NewsHeadline("Android 16 QPR brings new foldable gestures", "9to5Google", "4h"),
    NewsHeadline("City council approves new rail line", "KUT", "6h"),
    NewsHeadline("Why on-device AI is winning the launcher race", "Ars Technica", "8h"),
    NewsHeadline("Weekend forecast: clear skies, cooler nights", "KXAN", "9h"),
)

/**
 * News list (mock-up `wgRender` kind `news` -> `list()`): single row shows the top story; taller
 * sizes pick the one-line or two-line row style by how many fit (28dp vs 42dp per row).
 */
@Composable
fun NewsWidget(spanX: Int, spanY: Int, modifier: Modifier, headlines: List<NewsHeadline> = PlaceholderNews) {
    val one = spanY == 1
    BuiltInCard(modifier, one = one) {
        val ih = maxHeight.value
        val tokens = LocalFoldOsTokens.current
        if (headlines.isEmpty()) {
            WidgetDimText("No stories yet", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            return@BuiltInCard
        }
        if (one) {
            val top = headlines.first()
            Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.AutoMirrored.Outlined.Article, contentDescription = null, tint = tokens.accent, modifier = Modifier.size(22.dp))
                Column(Modifier.weight(1f)) {
                    WidgetTitleText(top.title, size = 13.5.sp)
                    if (spanX >= 2) {
                        val second = headlines.getOrNull(1)
                        WidgetDimText(if (second != null) "${second.title} · ${second.source}" else top.source)
                    }
                }
                if (spanX >= 4) {
                    Column(horizontalAlignment = Alignment.End) {
                        WidgetDimText(top.age, size = 12.sp, alpha = 0.65f)
                        WidgetDimText("${headlines.size} stories", size = 12.sp, alpha = 0.65f)
                    }
                }
            }
        } else {
            val ch = ih - WidgetHeaderHeight.value
            val n1 = floor((ch + 6f) / 28f).toInt()
            val n2 = floor((ch + 6f) / 42f).toInt()
            val twoLine = (spanY >= 3 && n2 >= 3) || (spanX <= 2 && spanY >= 2 && n2 >= 2)
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                WidgetHeader(Icons.AutoMirrored.Outlined.Article, "News", trailingText = headlines.size.toString())
                Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.SpaceEvenly) {
                    if (twoLine) {
                        headlines.take(n2).forEach { h ->
                            Column {
                                WidgetTitleText(h.title, size = 13.sp)
                                WidgetDimText("${h.source} · ${h.age}", size = 11.5.sp, alpha = 0.6f)
                            }
                        }
                    } else {
                        headlines.take(max(1, n1)).forEach { h ->
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = LocalContentColor.current.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp),
                                )
                                WidgetDimText(h.title, size = 13.sp, alpha = 1f, modifier = Modifier.weight(1f))
                                WidgetDimText(h.age, size = 11.5.sp, alpha = 0.6f)
                            }
                        }
                    }
                }
            }
        }
    }
}
