package com.nova.browser

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

class ZoomRun(val id: Int, val opening: Boolean, val serial: Int, val source: () -> Bitmap?)

private fun LayoutCoordinates?.rootRect(): Rect? {
    if (this == null || !isAttached) return null
    return findRootCoordinates().localBoundingBoxOf(this, false)
}

@Stable
class TabZoom {
    private val cards = HashMap<Int, LayoutCoordinates>()
    private var page: LayoutCoordinates? = null
    private var serial = 0

    var active by mutableStateOf<ZoomRun?>(null)
        private set
    var hiding by mutableIntStateOf(-1)
        private set

    fun attachPage(c: LayoutCoordinates) { page = c }

    fun attachCard(id: Int, c: LayoutCoordinates) { cards[id] = c }

    fun detachCard(id: Int) { cards.remove(id) }

    fun pageRect(): Rect? = page.rootRect()

    fun cardRect(id: Int): Rect? = cards[id].rootRect()

    fun open(id: Int, source: () -> Bitmap?) = start(id, true, source)

    fun close(id: Int, source: () -> Bitmap?) = start(id, false, source)

    private fun start(id: Int, opening: Boolean, source: () -> Bitmap?) {
        if (!Motion.enabled || source() == null || pageRect() == null) return
        serial++
        hiding = id
        active = ZoomRun(id, opening, serial, source)
    }

    suspend fun resolveFrom(zr: ZoomRun): Rect? {
        if (!zr.opening) return cardRect(zr.id)
        val p = pageRect() ?: return null
        var frames = 0
        while (cardRect(zr.id) == null && frames < UiLayout.ZOOM_WAIT_FRAMES) {
            withFrameNanos { }
            frames++
        }
        return if (cardRect(zr.id) == null) null else p
    }

    fun finish(zr: ZoomRun) {
        if (active === zr) {
            active = null
            hiding = -1
        }
    }
}

val LocalTabZoom = staticCompositionLocalOf<TabZoom?> { null }

@Composable
private fun ZoomImage(zoom: TabZoom, zr: ZoomRun, origin: () -> Offset) {
    val progress = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    var from by remember { mutableStateOf<Rect?>(null) }
    val last = remember { arrayOfNulls<Rect>(1) }
    LaunchedEffect(zr) {
        val r0 = zoom.resolveFrom(zr)
        if (r0 == null) {
            zoom.finish(zr)
            return@LaunchedEffect
        }
        from = r0
        progress.animateTo(1f, Motion.timed(UiLayout.ZOOM_MS, Motion.emphasized))
        fade.animateTo(0f, Motion.timed(UiLayout.ZOOM_FADE_MS))
        zoom.finish(zr)
    }
    val s0 = from ?: return
    val bitmap = zr.source() ?: return
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    Image(
        bitmap = image,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alignment = Alignment.TopCenter,
        modifier = Modifier
            .layout { measurable, constraints ->
                val end = (if (zr.opening) zoom.cardRect(zr.id) else zoom.pageRect())?.also { last[0] = it } ?: last[0] ?: s0
                val r = lerp(s0, end, progress.value).translate(-origin())
                val w = r.width.roundToInt().coerceAtLeast(1)
                val h = r.height.roundToInt().coerceAtLeast(1)
                val placeable = measurable.measure(Constraints.fixed(w, h))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(r.left.roundToInt(), r.top.roundToInt())
                }
            }
            .graphicsLayer {
                alpha = fade.value
                val k = if (zr.opening) progress.value else 1f - progress.value
                shape = RoundedCornerShape(UiLayout.ZOOM_CARD_R.toPx() * k)
                clip = true
            }
    )
}

@Composable
fun TabZoomGhost(zoom: TabZoom, modifier: Modifier = Modifier) {
    val zr = zoom.active
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        if (zr != null) key(zr.serial) { ZoomImage(zoom, zr) { origin } }
    }
}
