package com.foldos.launcher.builtins

import android.content.Context
import com.foldos.launcher.core.model.WidgetCategory
import com.foldos.launcher.core.repo.BuiltInWidget
import com.foldos.launcher.core.repo.BuiltInWidgetRegistry

/**
 * The FoldOS built-in widget library (PLAN.md 3.1, mock-up `WG`): Compose widgets themed by the
 * launcher, each adapting to its (spanX, spanY) like the mock-up's `wgRender` fill rules.
 *
 * Wire it in `AppGraphImpl`:
 * ```
 * override val builtIns: BuiltInWidgetRegistry by lazy { FoldOsBuiltInWidgets(appContext) }
 * ```
 *
 * Every widget's content expects `FoldOsTheme` (for `LocalFoldOsTokens`) above it, and the
 * permission-gated ones ([ID_CALENDAR], [ID_PHOTOS], [ID_DEVICES]) call [LocalPermissionRequester]
 * from their "Allow ..." buttons. The Now Playing widget needs [FoldOsNotificationListener]
 * declared in the manifest.
 *
 * @param weather where Clock, Glance and Weather read conditions from; defaults to the offline
 *   [FakeWeatherProvider].
 */
class FoldOsBuiltInWidgets(
    context: Context,
    val weather: WeatherProvider = FakeWeatherProvider(),
) : BuiltInWidgetRegistry {

    private val appContext: Context = context.applicationContext

    /** The To-do widget's list; exposed so Settings or a sync job can edit it too. */
    val todoStore: TodoStore = TodoStore(appContext)

    /** The Focus timer's persisted state. */
    val focusStore: FocusTimerStore = FocusTimerStore(appContext)

    override val all: List<BuiltInWidget> = listOf(
        BuiltInWidget(
            id = ID_CLOCK,
            name = "Clock",
            category = WidgetCategory.CLOCK,
            sizes = listOf(2 to 2, 4 to 1, 2 to 1, 4 to 2, 3 to 2, 4 to 3, 2 to 3, 4 to 4, 1 to 1),
            content = { x, y, m -> ClockWidget(x, y, m, weather) },
        ),
        BuiltInWidget(
            id = ID_GLANCE,
            name = "Glance",
            category = WidgetCategory.CLOCK,
            sizes = listOf(4 to 1, 4 to 2, 3 to 1, 2 to 1, 2 to 2, 4 to 3),
            content = { x, y, m -> GlanceWidget(x, y, m, weather) },
        ),
        BuiltInWidget(
            id = ID_WEATHER,
            name = "Weather",
            category = WidgetCategory.WEATHER,
            sizes = listOf(2 to 2, 4 to 2, 4 to 1, 2 to 1, 1 to 1, 3 to 2, 4 to 3, 2 to 3, 4 to 4),
            content = { x, y, m -> WeatherWidget(x, y, m, weather) },
        ),
        BuiltInWidget(
            id = ID_CALENDAR,
            name = "Calendar agenda",
            category = WidgetCategory.CALENDAR,
            sizes = listOf(2 to 2, 4 to 2, 4 to 1, 2 to 1, 4 to 3, 2 to 3, 3 to 2, 4 to 4),
            content = { x, y, m -> CalendarAgendaWidget(x, y, m) },
        ),
        BuiltInWidget(
            id = ID_DEVICES,
            name = "Devices battery",
            category = WidgetCategory.DEVICE,
            sizes = listOf(2 to 2, 4 to 1, 2 to 1, 1 to 1, 4 to 2, 3 to 2, 2 to 3),
            content = { x, y, m -> DevicesBatteryWidget(x, y, m) },
        ),
        BuiltInWidget(
            id = ID_TODO,
            name = "To-do",
            category = WidgetCategory.PRODUCTIVITY,
            sizes = listOf(2 to 2, 4 to 2, 4 to 1, 2 to 1, 2 to 3, 4 to 3, 4 to 4),
            content = { x, y, m -> TodoWidget(x, y, m, todoStore) },
        ),
        BuiltInWidget(
            id = ID_MEDIA,
            name = "Now Playing",
            category = WidgetCategory.MEDIA,
            sizes = listOf(4 to 2, 2 to 2, 4 to 1, 2 to 1, 3 to 2, 2 to 3, 4 to 3),
            content = { x, y, m -> MediaNowPlayingWidget(x, y, m) },
        ),
        BuiltInWidget(
            id = ID_FOCUS,
            name = "Focus timer",
            category = WidgetCategory.PRODUCTIVITY,
            sizes = listOf(2 to 2, 4 to 1, 2 to 1, 1 to 1, 4 to 2, 2 to 3, 4 to 4),
            content = { x, y, m -> FocusTimerWidget(x, y, m, focusStore) },
        ),
        BuiltInWidget(
            id = ID_PHOTOS,
            name = "Photos",
            category = WidgetCategory.PHOTOS,
            sizes = listOf(2 to 2, 4 to 2, 4 to 1, 2 to 1, 1 to 1, 2 to 3, 4 to 3, 4 to 4, 3 to 2),
            content = { x, y, m -> PhotosWidget(x, y, m) },
        ),
        BuiltInWidget(
            id = ID_NEWS,
            name = "News",
            category = WidgetCategory.NEWS,
            sizes = listOf(2 to 2, 4 to 2, 4 to 1, 2 to 1, 4 to 3, 2 to 3, 4 to 4, 3 to 2),
            content = { x, y, m -> NewsWidget(x, y, m) },
        ),
    )

    private val byIdMap: Map<String, BuiltInWidget> = all.associateBy { it.id }

    /** Looks an id up; a bare id without the `foldos.` prefix (`"clock"`) is accepted as well. */
    override fun byId(id: String): BuiltInWidget? = byIdMap[id] ?: byIdMap["$ID_PREFIX$id"]

    companion object {
        /** Every built-in id starts with this; `home.HomeBuiltInIds` uses the same ids. */
        const val ID_PREFIX = "foldos."
        const val ID_CLOCK = "foldos.clock"
        const val ID_GLANCE = "foldos.glance"
        const val ID_WEATHER = "foldos.weather"
        const val ID_CALENDAR = "foldos.calendar"
        const val ID_DEVICES = "foldos.devices"
        const val ID_TODO = "foldos.todo"
        const val ID_MEDIA = "foldos.media"
        const val ID_FOCUS = "foldos.focus"
        const val ID_PHOTOS = "foldos.photos"
        const val ID_NEWS = "foldos.news"

        /** Every built-in id, in picker order. */
        val ALL_IDS: List<String> = listOf(
            ID_CLOCK, ID_GLANCE, ID_WEATHER, ID_CALENDAR, ID_DEVICES,
            ID_TODO, ID_MEDIA, ID_FOCUS, ID_PHOTOS, ID_NEWS,
        )
    }
}
