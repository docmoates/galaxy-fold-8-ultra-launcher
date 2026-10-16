package com.foldos.launcher.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foldos.launcher.a11y.a11yHeading
import com.foldos.launcher.core.model.DockPresets
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.matchingPresetId
import com.foldos.launcher.onboarding.SampleDockPreview

/**
 * "More docks": all 20 dock styles as live previews, two per row. Tapping
 * one applies it to both screens; the current style is outlined. Fine-tuning is on [DockPage].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DockGalleryPage(
    settings: LauncherSettings,
    update: SettingsUpdater,
    onBack: () -> Unit,
) {
    val current = settings.dockCover.matchingPresetId()
    val rows = DockPresets.all.chunked(2)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Dock styles", modifier = Modifier.a11yHeading()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding,
        ) {
            item(key = "intro") {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Text(
                        text = if (current == null) {
                            "Your dock is a custom mix. Tap a style to start from it."
                        } else {
                            "Tap a style to use it on both screens."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .widthIn(max = SampleMaxWidth)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            items(rows, key = { row -> row.first().id }) { row ->
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Row(
                        modifier = Modifier
                            .widthIn(max = SampleMaxWidth)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        row.forEach { preset ->
                            SampleCard(
                                label = preset.name,
                                description = preset.description,
                                selected = current == preset.id,
                                onClick = { update { it.copy(dockCover = preset.config, dockInner = preset.config) } },
                                modifier = Modifier.weight(1f),
                            ) {
                                SampleDockPreview(
                                    dock = preset.config,
                                    modifier = Modifier.fillMaxWidth().aspectRatio(DockStripRatio),
                                    base = settings,
                                )
                            }
                        }
                        if (row.size == 1) Column(modifier = Modifier.weight(1f)) { }
                    }
                }
            }
            item(key = "end") { SampleGap(height = 24.dp) }
        }
    }
}
