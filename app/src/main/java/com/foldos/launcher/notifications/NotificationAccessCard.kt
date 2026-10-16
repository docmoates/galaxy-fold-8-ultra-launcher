package com.foldos.launcher.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * Whether notification access is granted right now, re-checked every time the lifecycle resumes
 * so the UI flips from the prompt to live badges as soon as the user returns from system Settings.
 * Also true whenever the listener reports itself connected.
 */
@Composable
fun rememberNotificationAccessGranted(): Boolean {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(isNotificationAccessGranted(context)) }
    LifecycleResumeEffect(Unit) {
        granted = isNotificationAccessGranted(context)
        onPauseOrDispose { }
    }
    return granted
}

/**
 * Card asking the user to grant notification access, for Settings > Notifications / Features and
 * for the quiet drawer. [onOpenSettings] normally calls [openNotificationAccessSettings]; the card
 * draws nothing while access is already granted unless [showWhenGranted] is set.
 */
@Composable
fun NotificationAccessCard(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Allow notification access",
    message: String = "FoldOS needs notification access to show badges, previews and the quiet drawer. It never reads notification content off the device.",
    buttonText: String = "Open settings",
    showWhenGranted: Boolean = false,
) {
    val granted = rememberNotificationAccessGranted()
    if (granted && !showWhenGranted) return
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    imageVector = Icons.Outlined.NotificationsActive,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Text(text = title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onOpenSettings) {
                Text(text = if (granted) "Notification access granted" else buttonText)
            }
        }
    }
}
