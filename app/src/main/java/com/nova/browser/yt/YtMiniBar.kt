package com.nova.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Height the host must reserve for the bar (52dp card + 4dp padding top and bottom). */
val YT_MINI_BAR_HEIGHT = 60.dp

/** In-app mini player: stays on screen while the user browses; tap opens the video again at the same moment. */
@Composable
fun YtMiniBar(modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val frac = if (YtAudio.durMs > 0) (YtAudio.posMs.toFloat() / YtAudio.durMs).coerceIn(0f, 1f) else 0f
    Box(
        modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).height(52.dp)
            .clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainerHigh)
            .clickable { onOpen() }
    ) {
        Row(Modifier.fillMaxWidth().fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.fillMaxHeight().aspectRatio(16f / 9f)) {
                NetImage("https://i.ytimg.com/vi/${YtAudio.videoId}/mqdefault.jpg", Modifier.fillMaxHeight().aspectRatio(16f / 9f))
            }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(
                    YtAudio.title.ifBlank { "YouTube" }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = cs.onSurface
                )
                Text(
                    if (YtAudio.loading) "…" else YtAudio.author, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant
                )
            }
            MiniBtn(if (YtAudio.playing) "❚❚" else "▶") { YtAudio.control(if (YtAudio.playing) "pause" else "play") }
            MiniBtn("»") { YtAudio.control("next") }
            MiniBtn("✕") { YtAudio.close() }
        }
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(frac).height(2.dp).background(cs.primary))
    }
}

@Composable
private fun MiniBtn(glyph: String, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Text(glyph, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}
