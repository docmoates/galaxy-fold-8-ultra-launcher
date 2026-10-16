package com.foldos.launcher.search

import com.foldos.launcher.core.model.LauncherSettings

/**
 * True when web results (the search-engine row, URLs, `!bang` shortcuts, the Lens / visual search
 * button) may be offered: the Web provider is switched on and No-network mode
 * (`LauncherSettings.noNetwork`) is off. Every web-facing check in `search/` goes through this.
 */
internal fun LauncherSettings.webSearchOn(): Boolean =
    searchProvider(LauncherSettings.SEARCH_WEB) && !noNetwork
