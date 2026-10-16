package com.foldos.launcher.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldos.launcher.theme.LocalFoldOsTokens

/* Building blocks shared by SetupWizard and PresetGallery (mock-up .wprog / .wopt / .wcard / .wsw / .wnav). */

/** Mock-up `.wprog`: five 4dp segments, the first [step] in accent. */
@Composable
internal fun WizardProgress(step: Int, total: Int, modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (index in 1..total) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (index <= step) tokens.accent else tokens.onSurface.copy(alpha = 0.2f)),
            )
        }
    }
}

/** Mock-up `.wopt`: a wide option card with a 40dp accent icon, a title and one line of detail. */
@Composable
internal fun WizardOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFoldOsTokens.current
    val shape = RoundedCornerShape(tokens.radius)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(tokens.surface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) tokens.accent else tokens.surfaceBorder,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(modifier = Modifier.width(64.dp), contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(40.dp), tint = tokens.accent)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = title, color = tokens.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(text = subtitle, color = tokens.onSurface.copy(alpha = 0.7f), fontSize = 13.sp, lineHeight = 17.sp)
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = "Selected",
                modifier = Modifier.align(Alignment.TopEnd).size(24.dp),
                tint = tokens.accent,
            )
        }
    }
}

/**
 * Mock-up `.wcard`: a surface card holding a live [preview] with its [name] under it. The whole
 * card is one tap target: a clickable overlay sits above the preview so the real composables
 * inside (dock items and so on) never swallow the tap.
 */
@Composable
internal fun WizardPreviewCard(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    preview: @Composable () -> Unit,
) {
    val tokens = LocalFoldOsTokens.current
    val shape = RoundedCornerShape((tokens.radius - 2.dp).coerceAtLeast(6.dp))
    Box(
        modifier = modifier
            .clip(shape)
            .background(tokens.surface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) tokens.accent else tokens.surfaceBorder,
                shape = shape,
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.25f)),
            ) {
                preview()
            }
            Text(
                text = name,
                color = tokens.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp)
                    .size(24.dp)
                    .background(tokens.drawerScrim, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Selected",
                    modifier = Modifier.size(24.dp),
                    tint = tokens.accent,
                )
            }
        }
        Box(modifier = Modifier.matchParentSize().clickable(onClick = onClick))
    }
}

/** Mock-up `.wsw > div`: a 42dp color circle with a label under it. */
@Composable
internal fun ColorSwatch(
    name: String,
    brush: Brush,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFoldOsTokens.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(brush)
                .border(
                    width = if (selected) 3.dp else 2.dp,
                    color = if (selected) tokens.accent else tokens.surfaceBorder,
                    shape = CircleShape,
                ),
        )
        Text(
            text = name,
            color = if (selected) tokens.accent else tokens.onSurface,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** Mock-up `.wnav`: a plain text action on the left and the accent pill button on the right. */
@Composable
internal fun WizardNav(
    leftLabel: String,
    onLeft: () -> Unit,
    rightLabel: String,
    onRight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFoldOsTokens.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onLeft) {
            Text(text = leftLabel, color = tokens.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        AccentPillButton(label = rightLabel, onClick = onRight)
    }
}

/** Mock-up `.wbtn`: accent pill, 14dp x 30dp padding. */
@Composable
internal fun AccentPillButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = tokens.accent, contentColor = tokens.onAccent),
        contentPadding = PaddingValues(horizontal = 30.dp, vertical = 14.dp),
    ) {
        Text(text = label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Mock-up `.wsub`: the one-line explanation under the step title. */
@Composable
internal fun WizardSubtitle(text: String) {
    val tokens = LocalFoldOsTokens.current
    Text(text = text, color = tokens.onSurface.copy(alpha = 0.7f), fontSize = 14.5.sp, lineHeight = 19.sp)
}

/** ARGB Long as "#RRGGBB". */
internal fun hexOf(value: Long): String = "#%06X".format(value and 0xFFFFFFL)

/** Parses "#RRGGBB" / "RRGGBB" into an opaque ARGB Long, or null when malformed. */
internal fun parseHex(text: String): Long? {
    val digits = text.trim().removePrefix("#")
    if (digits.length != 6) return null
    val rgb = digits.toLongOrNull(16) ?: return null
    return 0xFF000000L or rgb
}

/** A hex text field with a color dot, committing every valid "#RRGGBB" as it is typed. */
@Composable
internal fun HexColorField(
    label: String,
    value: Long,
    onChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember(value) { mutableStateOf(hexOf(value)) }
    val valid = parseHex(text) != null
    OutlinedTextField(
        value = text,
        onValueChange = { next ->
            text = next
            val parsed = parseHex(next)
            if (parsed != null && parsed != value) onChange(parsed)
        },
        modifier = modifier,
        label = { Text(text = label) },
        singleLine = true,
        isError = !valid,
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(value)),
            )
        },
    )
}

/** Small vertical gap used between wizard blocks. */
@Composable
internal fun WizardGap(height: Int = 10) {
    Spacer(Modifier.height(height.dp))
}
