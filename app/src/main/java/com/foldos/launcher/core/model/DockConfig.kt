package com.foldos.launcher.core.model

import kotlinx.serialization.Serializable

/**
 * A dock is built from traits. Defaults match `DOCK_DEF` in the mock-up, which is also the
 * "Floating pill" preset.
 */
@Serializable
data class DockConfig(
    val bg: DockBackground = DockBackground.PILL,
    val pos: DockPosition = DockPosition.BOTTOM,
    val content: DockContent = DockContent.ICONS,
    val labels: Boolean = false,
    val two: Boolean = false,
    val scroll: Boolean = false,
    val search: Boolean = false,
    val taskbar: Boolean = false,
    val smart: Boolean = false,
    val folder: Boolean = false,
    val media: Boolean = false,
    val notify: Boolean = false,
    val center: Boolean = false,
    val handle: Boolean = false,
    val magnify: Boolean = false,
)

/** One of the 20 named dock styles (mock-up `DOCKS` array). */
data class DockPreset(
    val id: String,
    val name: String,
    val description: String,
    val config: DockConfig,
)

object DockPresets {
    const val CUSTOM_ID: String = "custom"

    val all: List<DockPreset> = listOf(
        DockPreset("pill", "Floating pill", "Rounded bar that floats above the bottom edge.", DockConfig()),
        DockPreset("shelf", "Edge shelf", "Full-width panel anchored to the bottom edge, Pixel-style.", DockConfig(bg = DockBackground.SHELF)),
        DockPreset("none", "Icons only", "No background. Icons sit directly on the wallpaper.", DockConfig(bg = DockBackground.NONE)),
        DockPreset("labeled", "Labeled", "Dock icons keep their app names.", DockConfig(labels = true)),
        DockPreset("tworow", "Two rows", "Ten favorites in two rows (sixteen on the inner screen).", DockConfig(two = true)),
        DockPreset("scroll", "Scrolling", "More apps than fit; swipe the dock sideways. From Nova.", DockConfig(scroll = true)),
        DockPreset("taskbar", "Taskbar", "Pinned apps, recent apps and an all-apps button.", DockConfig(taskbar = true)),
        DockPreset("search", "Search dock", "Search bar built into the dock, below the icons.", DockConfig(search = true)),
        DockPreset("magnify", "Magnify", "Icons grow under your finger as you slide across.", DockConfig(magnify = true)),
        DockPreset("reflect", "3D shelf", "Icons stand on a glass shelf with reflections.", DockConfig(bg = DockBackground.REFLECT)),
        DockPreset("line", "Hairline", "Small icons separated from home by a thin rule.", DockConfig(bg = DockBackground.LINE)),
        DockPreset("text", "Text dock", "App names instead of icons, for minimal setups.", DockConfig(bg = DockBackground.NONE, content = DockContent.TEXT)),
        DockPreset("bubble", "Bubbles", "Each app in its own floating circle.", DockConfig(bg = DockBackground.BUBBLE)),
        DockPreset("folder", "Folder dock", "Dock folders open as a pop-up above the dock.", DockConfig(folder = true)),
        DockPreset("smart", "Smart dock", "Pinned apps plus two AI-suggested apps that change.", DockConfig(smart = true)),
        DockPreset("rail", "Edge rail", "Vertical dock on the screen edge, good for one hand.", DockConfig(pos = DockPosition.RAIL)),
        DockPreset("handle", "Swipe handle", "Arrow and hint above the dock to open the drawer.", DockConfig(handle = true)),
        DockPreset("notify", "Notification dock", "Shows the newest notification above its app.", DockConfig(notify = true)),
        DockPreset("media", "Media dock", "Mini player built into the dock while music plays.", DockConfig(media = true)),
        DockPreset("center", "Center action", "Raised center button for AI or search, apps either side.", DockConfig(center = true)),
    )

    /** Preset by id, or null for unknown ids (including [CUSTOM_ID]). */
    fun byId(id: String): DockPreset? = all.firstOrNull { it.id == id }

    /** Config for a preset id; falls back to the first preset like the mock-up's `presetCfg`. */
    fun configFor(id: String): DockConfig = (byId(id) ?: all.first()).config
}

/** The id of the preset whose traits equal this config exactly, or null for a custom mix. */
fun DockConfig.matchingPresetId(): String? = DockPresets.all.firstOrNull { it.config == this }?.id
