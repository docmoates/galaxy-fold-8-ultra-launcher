package com.foldos.launcher.data.db

import androidx.room.TypeConverter
import com.foldos.launcher.core.model.HomeStyle
import com.foldos.launcher.core.model.ItemKind
import com.foldos.launcher.core.model.ScreenKind

/**
 * Stores enums by name so renaming a constant is the only thing that can break old rows, and
 * an unknown name degrades to a sensible default instead of crashing.
 */
class LayoutConverters {

    @TypeConverter
    fun screenKindToString(value: ScreenKind): String = value.name

    @TypeConverter
    fun stringToScreenKind(value: String): ScreenKind =
        ScreenKind.entries.firstOrNull { it.name == value } ?: ScreenKind.COVER

    @TypeConverter
    fun homeStyleToString(value: HomeStyle): String = value.name

    @TypeConverter
    fun stringToHomeStyle(value: String): HomeStyle =
        HomeStyle.entries.firstOrNull { it.name == value } ?: HomeStyle.GRID

    @TypeConverter
    fun itemKindToString(value: ItemKind): String = value.name

    @TypeConverter
    fun stringToItemKind(value: String): ItemKind =
        ItemKind.entries.firstOrNull { it.name == value } ?: ItemKind.APP
}
