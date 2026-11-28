package com.nova.browser

import androidx.compose.animation.core.animate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MagneticDrag(
    private val scope: CoroutineScope,
    private val haptic: Haptics.On,
    detachPx: Float,
    attachPx: Float,
    private val pull: Float
) {
    private val policy = MagnetPolicy(detachPx, attachPx, pull)
    private var finger by mutableFloatStateOf(0f)
    private var factor by mutableFloatStateOf(pull)
    private var gap by mutableFloatStateOf(0f)
    private var catchUp: Job? = null

    val detached: Boolean get() = policy.isDetached
    val position: Float get() = finger * factor + gap

    fun start() {
        catchUp?.cancel()
        policy.start()
        finger = 0f
        factor = pull
        gap = 0f
    }

    fun move(value: Float) {
        val before = position
        finger = value
        val event = policy.move(value)
        if (event == MagnetPolicy.NONE) return
        factor = policy.factor()
        closeGap(before - finger * factor)
        haptic.threshold(event == MagnetPolicy.DETACHED)
    }

    fun finish() {
        catchUp?.cancel()
    }

    private fun closeGap(from: Float) {
        catchUp?.cancel()
        gap = from
        catchUp = scope.launch {
            animate(from, 0f, animationSpec = Motion.magnet()) { value, _ -> gap = value }
        }
    }
}
