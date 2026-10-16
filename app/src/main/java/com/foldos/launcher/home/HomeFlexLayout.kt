package com.foldos.launcher.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.foldos.launcher.core.model.HomeItem
import com.foldos.launcher.core.model.HomePage

/**
 * Flex Mode (half-opened, hinge horizontal): the first page's content sits above the hinge,
 * a six-app favorites row and the dock sit below it.
 *
 * @param originY window y of this layout's top edge, so the hinge rectangle (window coordinates)
 *   can be mapped into local space.
 */
@Composable
internal fun HomeFlexLayout(
    ctx: HomeContext,
    firstPage: HomePage?,
    items: List<HomeItem>,
    originY: Float,
    dock: @Composable () -> Unit,
    searchBar: @Composable (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val hinge = ctx.fold.hingeBounds
    val density = LocalDensity.current
    val topHeight: Dp = with(density) {
        val px = ((hinge?.top ?: 0) - originY).coerceAtLeast(0f)
        px.toDp()
    }.coerceAtLeast(MinFlexTopHeight)
    val hingeHeight: Dp = with(density) { (hinge?.height() ?: 0).toDp() }

    Column(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(topHeight)
                .then(ctx.pageModifier)
                .homeLongPress { ctx.emptyLongPress(firstPage?.id ?: 0L, -1, -1) }
                .homeSwipeUp(ctx.swipeUpOpensDrawer, ctx.onOpenDrawer)
                .padding(horizontal = HomeSidePadding, vertical = 10.dp),
        ) {
            if (searchBar != null) {
                searchBar()
                Spacer(Modifier.height(14.dp))
            }
            HomePageBody(ctx, firstPage, items, Modifier.weight(1f).fillMaxWidth(), publishGeometry = true)
        }
        Spacer(Modifier.height(hingeHeight))
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 12.dp),
        ) {
            HomeFavoriteGrid(
                ctx = ctx,
                favorites = ctx.favorites(items, 6),
                cols = 6,
                rows = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ctx.iconSize + (if (ctx.labels) 44.dp else 20.dp))
                    .padding(horizontal = HomeSidePadding),
                items = items,
            )
            Spacer(Modifier.weight(1f))
            dock()
        }
    }
}

private val MinFlexTopHeight: Dp = 160.dp
