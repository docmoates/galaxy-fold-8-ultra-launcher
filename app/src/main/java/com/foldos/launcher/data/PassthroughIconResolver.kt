package com.foldos.launcher.data

import android.graphics.drawable.Drawable
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.repo.IconPackInfo
import com.foldos.launcher.core.repo.IconResolver
import com.foldos.launcher.core.repo.IconShape
import com.foldos.launcher.core.repo.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Placeholder [IconResolver] until the Icons agent's resolver lands: returns each app's own icon,
 * reports no icon packs, persists the pack and shape choices to settings and ignores overrides.
 */
class PassthroughIconResolver(private val settings: SettingsRepository) : IconResolver {

    override fun icon(app: AppInfo): Drawable = app.iconLoader()

    override val packs: Flow<List<IconPackInfo>> = flowOf(emptyList())

    override suspend fun setPack(packageName: String?) {
        settings.update { it.copy(iconPack = packageName) }
    }

    override suspend fun setShape(shape: IconShape) {
        settings.update { it.copy(iconShape = shape) }
    }

    override suspend fun override(appKey: String, packageName: String?, drawableName: String?) {
        // No per-app overrides until the icon pack resolver exists.
    }
}
