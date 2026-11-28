package com.nova.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign

private val QuickPicks = intArrayOf(
    0xFF3B30, 0xFF9500, 0xFFCC00, 0x34C759, 0x00C2B8, 0x0A84FF, 0x5E5CE6, 0xBF5AF2, 0xFF375F, 0x8E8E93
)

private suspend fun PointerInputScope.trackDrag(onPoint: (Float, Float) -> Unit) {
    awaitEachGesture {
        val down = awaitFirstDown()
        onPoint(down.position.x, down.position.y)
        down.consume()
        do {
            val ev = awaitPointerEvent()
            ev.changes.forEach { c ->
                if (c.pressed) {
                    onPoint(c.position.x, c.position.y)
                    c.consume()
                }
            }
        } while (ev.changes.any { it.pressed })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccentSheet(onDismiss: () -> Unit) {
    val ios = LocalIos.current
    var custom by remember { mutableStateOf(Prefs.accent == Accent.CUSTOM) }
    NovaSheet(onDismiss) {
        Column(
            Modifier.padding(horizontal = UiLayout.ACCENT_PAD).padding(bottom = UiLayout.ACCENT_PAD),
            verticalArrangement = Arrangement.spacedBy(UiLayout.ACCENT_GAP)
        ) {
            Text(S(R.string.set_accent), style = MaterialTheme.typography.titleMedium, color = ios.label)
            val rows = (0..Accent.CUSTOM).toList().chunked(4)
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(UiLayout.ACCENT_GAP)) {
                    row.forEach { i ->
                        AccentCard(i, Modifier.weight(1f)) { if (i == Accent.CUSTOM) custom = true else { custom = false; Prefs.pickAccent(i) } }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (custom) ColorPicker(Prefs.accentColor) { Prefs.pickAccentColor(it); onDismiss() }
        }
    }
}

@Composable
private fun AccentCard(index: Int, modifier: Modifier, onClick: () -> Unit) {
    val ios = LocalIos.current
    val color = Accent.color(index, ios.dark)
    val on = Accent.on(color)
    val picked = Prefs.accent == index
    val shape = RoundedCornerShape(UiLayout.ACCENT_CARD_R)
    Box(
        modifier.height(UiLayout.ACCENT_CARD_H).clip(shape)
            .background(Brush.linearGradient(listOf(color, Accent.end(color))), shape)
            .border(if (picked) UiLayout.ACCENT_RING else UiLayout.dp0, ios.label, shape)
            .tapClickable(onClick = onClick).iosButton(S(Accent.names[index])).padding(UiLayout.ACCENT_GAP),
        contentAlignment = Alignment.BottomStart
    ) {
        Text(S(Accent.names[index]), style = MaterialTheme.typography.labelMedium, color = on, maxLines = 1)
        if (picked) Box(Modifier.align(Alignment.TopEnd)) { IosIcon(IosGlyph.Check, on, UiLayout.dp16) }
    }
}

@Composable
fun ColorPicker(initial: Int, onUse: (Int) -> Unit) {
    val ios = LocalIos.current
    val start = remember { ColorPick.toHsv(initial and 0xFFFFFF) }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var sat by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2].coerceAtLeast(0.2f)) }
    val rgb = ColorPick.fromHsv(hue, sat, value)
    var hex by remember { mutableStateOf(ColorPick.hex(rgb)) }
    val color = Accent.opaque(rgb)
    val squareShape = RoundedCornerShape(UiLayout.PICKER_R)
    fun setHsv(h: Float, s: Float, v: Float) {
        hue = h; sat = s; value = v
        hex = ColorPick.hex(ColorPick.fromHsv(h, s, v))
    }
    Column(verticalArrangement = Arrangement.spacedBy(UiLayout.PICKER_SPACE)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UiLayout.PICKER_SPACE)) {
            Box(Modifier.size(UiLayout.PICKER_PREVIEW).clip(CircleShape).background(color))
            Column {
                Text(S(R.string.accent_pick_title), style = MaterialTheme.typography.titleSmall, color = ios.label)
                Text(hex, style = MaterialTheme.typography.bodySmall, color = ios.label2)
            }
        }
        Box(
            Modifier.fillMaxWidth().height(UiLayout.PICKER_SQUARE_H).clip(squareShape)
                .drawWithCache {
                    val hueColor = Accent.opaque(ColorPick.fromHsv(hue, 1f, 1f))
                    val across = Brush.horizontalGradient(listOf(Color.White, hueColor))
                    val down = Brush.verticalGradient(listOf(Color.Transparent, Color.Black))
                    val r = UiLayout.PICKER_THUMB.toPx()
                    val ring = Stroke(UiLayout.PICKER_THUMB_RING.toPx())
                    onDrawBehind {
                        drawRect(across)
                        drawRect(down)
                        val c = Offset(sat * size.width, (1f - value) * size.height)
                        drawCircle(Color.White, r, c, style = ring)
                        drawCircle(color, r - ring.width, c)
                    }
                }
                .pointerInput(Unit) {
                    trackDrag { x, y -> setHsv(hue, (x / size.width).coerceIn(0f, 1f), 1f - (y / size.height).coerceIn(0f, 1f)) }
                }
                .iosLabel(S(R.string.a11y_shade))
        )
        Box(
            Modifier.fillMaxWidth().height(UiLayout.PICKER_HUE_H).clip(CircleShape)
                .drawWithCache {
                    val sweep = Brush.horizontalGradient(
                        listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
                    )
                    val r = UiLayout.PICKER_HUE_H.toPx() / 2f
                    val ring = Stroke(UiLayout.PICKER_THUMB_RING.toPx())
                    onDrawBehind {
                        drawRect(sweep)
                        val c = Offset(r + (size.width - 2f * r) * hue / 360f, size.height / 2f)
                        drawCircle(Color.White, r - ring.width / 2f, c, style = ring)
                    }
                }
                .pointerInput(Unit) {
                    trackDrag { x, _ -> setHsv(((x / size.width).coerceIn(0f, 1f)) * 360f, sat, value) }
                }
                .iosLabel(S(R.string.a11y_hue))
        )
        Row(horizontalArrangement = Arrangement.spacedBy(UiLayout.dp6), modifier = Modifier.fillMaxWidth()) {
            QuickPicks.forEach { q ->
                Box(
                    Modifier.weight(1f).height(UiLayout.PICKER_SWATCH).clip(CircleShape).background(Accent.opaque(q))
                        .tapClickable {
                            val h = ColorPick.toHsv(q)
                            setHsv(h[0], h[1], h[2])
                        }
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().height(UiLayout.PICKER_FIELD_H).clip(IosShape.field).background(ios.fill3).padding(horizontal = UiLayout.PICKER_SPACE),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = hex,
                onValueChange = {
                    hex = it
                    val p = ColorPick.parse(it)
                    if (p >= 0) {
                        val h = ColorPick.toHsv(p)
                        hue = h[0]; sat = h[1]; value = h[2]
                    }
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = ios.label, textAlign = TextAlign.Start),
                cursorBrush = SolidColor(ios.blue),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Box(
            Modifier.fillMaxWidth().height(UiLayout.PICKER_BUTTON_H).clip(IosShape.pill).background(color).tapClickable { onUse(rgb) },
            contentAlignment = Alignment.Center
        ) {
            Text(S(R.string.accent_use), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Accent.on(color))
        }
    }
}
