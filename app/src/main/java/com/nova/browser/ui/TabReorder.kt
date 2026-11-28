package com.nova.browser

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

class TabReorder(
    private val grid: LazyGridState,
    private val scope: CoroutineScope,
    private val density: Float,
    private val haptic: Haptics.On,
    private val move: (Int, Int) -> Unit
) {
    var activeId by mutableIntStateOf(NONE)
        private set

    private val lift = Animatable(0f)
    private var pointer by mutableStateOf(Offset.Zero)
    private var rest by mutableStateOf(Offset.Zero)
    private var settling by mutableStateOf(false)
    private var settleShare by mutableFloatStateOf(0f)
    private var gapFrom by mutableStateOf(Offset.Zero)
    private var gapShare by mutableFloatStateOf(0f)
    private var grab = Offset.Zero
    private var lastSlot = Offset.Zero
    private var lastWidth = 0f
    private var candidate = NONE
    private var since = 0L
    private var frames: Job? = null
    private var gapJob: Job? = null
    private var settleJob: Job? = null
    private var liftJob: Job? = null

    fun liftOf(id: Int): Float = if (id == activeId) lift.value else 0f

    fun displacement(id: Int): Offset {
        if (id != activeId) return Offset.Zero
        if (settling) return rest * settleShare
        val info = info(id)
        val slot = slot(info)
        if (info != null) lastWidth = info.size.width.toFloat()
        return shaped(pointer - grab - slot, lastWidth) + gapFrom * gapShare
    }

    fun begin(id: Int, local: Offset) {
        frames?.cancel()
        gapJob?.cancel()
        settleJob?.cancel()
        liftJob?.cancel()
        settling = false
        activeId = id
        grab = local
        pointer = slot(info(id)) + local
        gapFrom = Offset.Zero
        gapShare = 0f
        candidate = NONE
        haptic.heavy()
        liftJob = scope.launch {
            lift.snapTo(0f)
            lift.animateTo(1f, Motion.drop())
        }
        frames = scope.launch { run() }
    }

    fun dragBy(delta: Offset) {
        pointer += delta
    }

    fun end(menu: Boolean, open: () -> Unit) {
        val id = activeId
        if (id == NONE) return
        frames?.cancel()
        gapJob?.cancel()
        liftJob?.cancel()
        rest = displacement(id)
        settleShare = 1f
        settling = true
        settleJob = scope.launch {
            launch { lift.animateTo(0f, Motion.drop()) }
            animate(1f, 0f, animationSpec = Motion.drop()) { value, _ -> settleShare = value }
            activeId = NONE
            settling = false
            lift.snapTo(0f)
        }
        if (menu) open()
    }

    private fun info(id: Int): LazyGridItemInfo? =
        grid.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }

    private fun slot(info: LazyGridItemInfo?): Offset {
        if (info != null) lastSlot = Offset(info.offset.x.toFloat(), info.offset.y.toFloat())
        return lastSlot
    }

    private fun shaped(raw: Offset, width: Float): Offset {
        val pull = MagnetPolicy.snapPull(raw.getDistance(), width * UiLayout.SNAP_RADIUS_SHARE, UiLayout.SNAP_STRENGTH)
        return raw * (1f - pull)
    }

    private suspend fun run() {
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = (now - last) / NANOS
            last = now
            edge(dt)
            hover(now / 1_000_000L)
        }
    }

    private suspend fun edge(dt: Float) {
        val layout = grid.layoutInfo
        val zone = UiLayout.EDGE_SCROLL_ZONE_DP * density
        val top = zone - pointer.y
        val bottom = pointer.y - (layout.viewportSize.height - layout.afterContentPadding - zone)
        val push = when {
            top > 0f -> -(top / zone)
            bottom > 0f -> bottom / zone
            else -> 0f
        }.coerceIn(-1f, 1f)
        if (push != 0f) grid.scrollBy(push * UiLayout.EDGE_SCROLL_DP_S * density * dt)
    }

    private fun hover(now: Long) {
        val items = grid.layoutInfo.visibleItemsInfo.filter { it.key is Int }
        val self = items.indexOfFirst { it.key == activeId }
        if (self < 0) return
        val box = items[self].size
        val center = pointer - grab + Offset(box.width / 2f, box.height / 2f)
        val target = MagnetPolicy.hoverTarget(
            center.x, center.y,
            FloatArray(items.size) { items[it].offset.x.toFloat() },
            FloatArray(items.size) { items[it].offset.y.toFloat() },
            FloatArray(items.size) { items[it].size.width.toFloat() },
            FloatArray(items.size) { items[it].size.height.toFloat() },
            self, UiLayout.SWAP_INSET_SHARE
        )
        val id = if (target >= 0) items[target].key as Int else NONE
        if (id != candidate) {
            candidate = id
            since = now
            return
        }
        if (id == NONE || now - since < Motion.millis(UiLayout.SWAP_GAP_MS)) return
        candidate = NONE
        swap(items[target], items[self])
    }

    private fun swap(to: LazyGridItemInfo, from: LazyGridItemInfo) {
        val before = displacement(activeId)
        val oldSlot = slot(from)
        val newSlot = Offset(to.offset.x.toFloat(), to.offset.y.toFloat())
        val after = shaped(pointer - grab - newSlot, from.size.width.toFloat())
        gapFrom = (oldSlot - newSlot) + before - after
        gapShare = 1f
        gapJob?.cancel()
        gapJob = scope.launch {
            animate(1f, 0f, animationSpec = Motion.magnet()) { value, _ -> gapShare = value }
        }
        haptic.tick()
        move(activeId, to.key as Int)
    }

    companion object {
        const val NONE = -1
        private const val NANOS = 1_000_000_000f
    }
}

private const val SWIPE = 1
private const val CANCEL = 2

suspend fun PointerInputScope.tabCardGestures(
    onSwipeStart: () -> Unit,
    onSwipe: (Float) -> Unit,
    onSwipeEnd: (travel: Float, velocity: Float, cancelled: Boolean) -> Unit,
    onLift: (Offset) -> Unit,
    onLiftDrag: (Offset) -> Unit,
    onLiftEnd: (stayed: Boolean) -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val slop = viewConfiguration.touchSlop
        var total = Offset.Zero
        var latest = down
        val verdict = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            var out = 0
            while (out == 0) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull CANCEL
                if (change.isConsumed || change.changedToUpIgnoreConsumed()) return@withTimeoutOrNull CANCEL
                latest = change
                total += change.positionChange()
                if (abs(total.x) > slop && abs(total.x) > abs(total.y)) {
                    change.consume()
                    out = SWIPE
                } else if (abs(total.y) > slop) {
                    out = CANCEL
                }
            }
            out
        }
        when (verdict) {
            SWIPE -> {
                val tracker = VelocityTracker()
                tracker.addPointerInputChange(latest)
                var travel = 0f
                onSwipeStart()
                val finished = drag(down.id) { change ->
                    tracker.addPointerInputChange(change)
                    travel += change.positionChange().x
                    onSwipe(travel)
                    change.consume()
                }
                currentEvent.changes.forEach { if (it.changedToUpIgnoreConsumed()) it.consume() }
                onSwipeEnd(travel, if (finished) tracker.calculateVelocity().x else 0f, !finished)
            }
            null -> {
                onLift(down.position + total)
                var net = Offset.Zero
                var stayed = false
                try {
                    val finished = drag(down.id) { change ->
                        val delta = change.positionChange()
                        net += delta
                        onLiftDrag(delta)
                        change.consume()
                    }
                    currentEvent.changes.forEach { if (it.changedToUpIgnoreConsumed()) it.consume() }
                    stayed = finished && net.getDistance() <= slop
                } finally {
                    onLiftEnd(stayed)
                }
            }
            else -> Unit
        }
    }
}
