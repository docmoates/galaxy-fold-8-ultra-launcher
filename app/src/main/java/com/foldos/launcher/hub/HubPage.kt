package com.foldos.launcher.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.semantics.Role
import com.foldos.launcher.a11y.a11yButton
import com.foldos.launcher.ai.AiTab
import com.foldos.launcher.core.LocalAppGraph
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.ScreenKind
import com.foldos.launcher.core.repo.MailState
import com.foldos.launcher.mail.MailTab
import com.foldos.launcher.theme.FoldOsSurface
import com.foldos.launcher.theme.LocalFoldOsTokens

/** The Hub's tabs (mock-up `HUBTABS`); [id] matches `LauncherSettings.HUB_TAB_*` ("today" for Today). */
enum class HubTab(val id: String, val label: String, val icon: ImageVector) {
    TODAY("today", "Today", Icons.Rounded.WbSunny),
    AI(LauncherSettings.HUB_TAB_AI, "AI", Icons.Rounded.AutoAwesome),
    MAIL(LauncherSettings.HUB_TAB_MAIL, "Mail", Icons.Rounded.Mail),
    CALENDAR(LauncherSettings.HUB_TAB_CAL, "Calendar", Icons.Rounded.CalendarMonth),
    CONTACTS(LauncherSettings.HUB_TAB_PEOPLE, "Contacts", Icons.Rounded.Person),
    LAUNCH(LauncherSettings.HUB_TAB_LAUNCH, "Launch", Icons.Rounded.Apps);

    companion object {
        fun byId(id: String): HubTab? = entries.firstOrNull { it.id == id }

        /** Today plus the tabs switched on in Settings > Hub, in tab-bar order. */
        fun enabled(settings: LauncherSettings): List<HubTab> =
            entries.filter { it == TODAY || it.id in settings.hubTabs }

        /**
         * The tabs of the standalone Hub app ([HubActivity], for people who keep the Pixel / One UI
         * launcher as home): Launch first, then Today and the other enabled tabs. Launch is always
         * there, whatever Settings > Hub says.
         */
        fun standalone(settings: LauncherSettings): List<HubTab> =
            listOf(LAUNCH) + enabled(settings).filter { it != LAUNCH }
    }
}

/**
 * The Hub page beside home (PLAN.md 2.5, mock-up `hub()`): a tab bar at the bottom or top
 * (`settings.hubBar`) listing Today and the tabs in `settings.hubTabs`, and the selected tab's content
 * filling the page underneath it. Today, Calendar and Contacts live in this package; Mail and AI
 * come from `com.foldos.launcher.mail.MailTab` and `com.foldos.launcher.ai.AiTab`.
 *
 * [screen] picks the cover (single column) or inner / flex (two panes split at the hinge) layout.
 * Data comes from `LocalAppGraph.current.hub` / `.mail`; the host must provide `LocalAppGraph` and
 * `builtins.LocalPermissionRequester` (for the "Allow" buttons) and compose inside `FoldOsTheme`.
 */
@Composable
fun HubPage(
    screen: ScreenKind,
    settings: LauncherSettings,
    modifier: Modifier = Modifier,
    standalone: Boolean = false,
) {
    val tabs = remember(settings.hubTabs, standalone) {
        if (standalone) HubTab.standalone(settings) else HubTab.enabled(settings)
    }
    var selectedId by rememberSaveable { mutableStateOf(tabs.first().id) }
    val selected = tabs.firstOrNull { it.id == selectedId } ?: tabs.first()
    val barOnTop = settings.hubBar == LauncherSettings.HUB_BAR_TOP
    val inner = screen != ScreenKind.COVER

    val graph = LocalAppGraph.current
    // Permission grants arrive through a resume (the system dialog pauses the launcher); one bump
    // here lets every provider flow re-check instead of polling.
    LifecycleResumeEffect(graph) {
        (graph.hub as? FoldOsHubRepository)?.notifyPermissionsChanged()
        onPauseOrDispose { }
    }
    val mailState by graph.mail.state.collectAsStateWithLifecycle(initialValue = MailState.NotConfigured)
    val mailUnread = if (settings.feature(LauncherSettings.FEATURE_BADGES)) {
        (mailState as? MailState.Connected)?.unread ?: 0
    } else {
        0
    }

    // Content scrolls under the floating tab bar; these paddings keep the first / last card clear of it.
    val contentPadding = PaddingValues(
        top = if (barOnTop) HubTabBarInset + HubTabBarHeight + 12.dp else 10.dp,
        bottom = if (barOnTop) 24.dp else HubTabBarInset + HubTabBarHeight + 16.dp,
    )
    val openTab: (HubTab) -> Unit = { tab -> if (tab in tabs) selectedId = tab.id }

    Box(modifier = modifier.fillMaxSize()) {
        when (selected) {
            HubTab.TODAY -> HubTodayTab(
                screen = screen,
                settings = settings,
                contentPadding = contentPadding,
                onOpenTab = openTab,
                modifier = Modifier.fillMaxSize(),
            )
            HubTab.CALENDAR -> HubCalendarTab(
                screen = screen,
                settings = settings,
                contentPadding = contentPadding,
                modifier = Modifier.fillMaxSize(),
            )
            HubTab.CONTACTS -> HubContactsTab(
                screen = screen,
                settings = settings,
                contentPadding = contentPadding,
                modifier = Modifier.fillMaxSize(),
            )
            HubTab.LAUNCH -> HubLaunchTab(
                screen = screen,
                settings = settings,
                contentPadding = contentPadding,
                modifier = Modifier.fillMaxSize(),
            )
            HubTab.MAIL -> Box(Modifier.fillMaxSize().padding(contentPadding)) { MailTab(screen, settings) }
            HubTab.AI -> Box(Modifier.fillMaxSize().padding(contentPadding)) { AiTab(screen, settings) }
        }
        HubTabBarRow(
            tabs = tabs,
            selected = selected,
            mailUnread = mailUnread,
            compact = inner,
            onSelect = { selectedId = it.id },
            modifier = Modifier
                .align(if (barOnTop) Alignment.TopCenter else Alignment.BottomCenter)
                .padding(horizontal = 12.dp, vertical = HubTabBarInset),
        )
    }
}

/** Distance between the tab bar and the page edge (mock-up `.tabbar{bottom:24px}` minus the indicator). */
private val HubTabBarInset: Dp = 14.dp

/** Visual height of the tab bar: 7dp padding + 30dp icon pill + 3dp gap + label + 7dp padding. */
private val HubTabBarHeight: Dp = 64.dp

/**
 * Mock-up `.tabbar` / `.tb`: a frosted pill (`--drawer` fill, hairline border, 30dp radius). On the
 * cover it spans the width; on the inner screen ([compact]) it is a centred pill with 14dp gaps.
 * The Mail tab carries an unread badge (`.bdg`) when [mailUnread] > 0.
 */
@Composable
private fun HubTabBarRow(
    tabs: List<HubTab>,
    selected: HubTab,
    mailUnread: Int,
    compact: Boolean,
    onSelect: (HubTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFoldOsTokens.current
    val shape = RoundedCornerShape(30.dp)
    // Six tabs (standalone Hub) would overflow a cover-screen width at 54dp each.
    val pillWidth = if (!compact && tabs.size > 5) 46.dp else 54.dp
    FoldOsSurface(
        modifier = if (compact) modifier else modifier.fillMaxWidth(),
        shape = shape,
        color = tokens.drawerScrim,
        contentPadding = if (compact) PaddingValues(horizontal = 18.dp, vertical = 7.dp) else PaddingValues(horizontal = 4.dp, vertical = 7.dp),
    ) {
        Row(
            modifier = if (compact) Modifier else Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (compact) Arrangement.spacedBy(14.dp) else Arrangement.SpaceAround,
        ) {
            tabs.forEach { tab ->
                val isSelected = tab == selected
                val alpha = if (isSelected) 1f else 0.72f
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(15.dp))
                        // One tab element ("Mail, 3 unread, tab, selected"): icon, label and badge
                        // are not read separately.
                        .a11yButton(
                            label = if (tab == HubTab.MAIL && mailUnread > 0) "${tab.label}, $mailUnread unread" else tab.label,
                            onActivate = { onSelect(tab) },
                            role = Role.Tab,
                            selected = isSelected,
                        )
                        .clickable { onSelect(tab) }
                        .padding(horizontal = 2.dp, vertical = 2.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(pillWidth)
                                .height(30.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .background(if (isSelected) tokens.accent else Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                modifier = Modifier.size(22.dp),
                                tint = if (isSelected) tokens.onAccent else tokens.onSurface.copy(alpha = alpha),
                            )
                        }
                        Text(
                            text = tab.label,
                            color = tokens.onSurface.copy(alpha = alpha),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                    if (tab == HubTab.MAIL && mailUnread > 0) {
                        Text(
                            text = if (mailUnread > 99) "99+" else mailUnread.toString(),
                            color = Color.White,
                            fontSize = 11.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 4.dp, y = (-3).dp)
                                .defaultMinSize(minWidth = 20.dp)
                                .height(20.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(HubBadgeRed)
                                .padding(horizontal = 5.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Mock-up `.bdg` background. */
private val HubBadgeRed: Color = Color(0xFFFF3B4E)
