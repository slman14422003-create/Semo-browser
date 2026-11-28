package com.nova.browser

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

class SnackData(val text: String, val actionLabel: String?, val action: ((Activity) -> Unit)?)

object Snacks {
    private val queue = SnackQueue<SnackData>(UiLayout.SNACK_CAPACITY, UiLayout.SNACK_TTL_MS)
    private val main by lazy { Handler(Looper.getMainLooper()) }
    var current by mutableStateOf<SnackData?>(null)
        private set

    fun show(text: String, actionLabel: String? = null, action: ((Activity) -> Unit)? = null) {
        main.post {
            if (current?.text != text) {
                queue.offer(text, SnackData(text, actionLabel, action), SystemClock.elapsedRealtime())
                if (current == null) next()
            }
        }
    }

    fun next() {
        current = queue.poll(SystemClock.elapsedRealtime())
    }
}

@Composable
fun SnackHost(modifier: Modifier = Modifier) {
    val activity = LocalActivity.current
    val state by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val resumed = state.isAtLeast(Lifecycle.State.RESUMED)
    val item = Snacks.current
    var last by remember { mutableStateOf(item) }
    if (item != null) last = item
    LaunchedEffect(item, resumed) {
        if (item != null && resumed) {
            delay(if (item.action != null) UiLayout.SNACK_ACTION_MS else UiLayout.SNACK_MS)
            Snacks.next()
        }
    }
    Box(modifier.fillMaxSize().navigationBarsPadding().imePadding(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = item != null && resumed,
            enter = slideInVertically(Motion.timed(Motion.MEDIUM)) { it } + fadeIn(Motion.timed(Motion.SHORT)),
            exit = slideOutVertically(Motion.timed(Motion.SHORT)) { it } + fadeOut(Motion.timed(Motion.SHORT))
        ) {
            val data = last ?: return@AnimatedVisibility
            val ios = LocalIos.current
            Row(
                Modifier.padding(start = UiLayout.dp16, end = UiLayout.dp16, bottom = UiLayout.SNACK_LIFT)
                    .widthIn(max = UiLayout.SNACK_MAX_W).fillMaxWidth()
                    .liquidGlass(IosShape.menu)
                    .tapClickable { Snacks.next() }
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .heightIn(min = UiLayout.dp48).padding(start = UiLayout.dp16, end = UiLayout.dp6),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(data.text, Modifier.weight(1f).padding(vertical = UiLayout.dp10), style = MaterialTheme.typography.bodyMedium, color = ios.label)
                val label = data.actionLabel
                if (label != null) IosTextButton(label, {
                    Snacks.next()
                    activity?.let { data.action?.invoke(it) }
                }, bold = true)
            }
        }
    }
}
