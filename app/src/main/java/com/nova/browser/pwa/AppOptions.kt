package com.nova.browser

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** إعدادات كل تطبيق مثبّت على حدة (مفتاحها النطاق) — تُحفظ في ملف الإعدادات الموحّد. */
object AppPrefs {
    const val ORIENT_AUTO = 0
    const val ORIENT_PORTRAIT = 1
    const val ORIENT_LANDSCAPE = 2

    private fun c(ctx: Context) = ConfStore.open(ctx)

    fun orient(ctx: Context, host: String): Int = c(ctx).getInt("pwa_o_$host", ORIENT_AUTO)
    fun setOrient(ctx: Context, host: String, v: Int) = c(ctx).putInt("pwa_o_$host", v)

    fun keepAwake(ctx: Context, host: String): Boolean = c(ctx).getBoolean("pwa_k_$host", false)
    fun setKeepAwake(ctx: Context, host: String, v: Boolean) = c(ctx).putBoolean("pwa_k_$host", v)

    fun immersive(ctx: Context, host: String): Boolean = c(ctx).getBoolean("pwa_i_$host", false)
    fun setImmersive(ctx: Context, host: String, v: Boolean) = c(ctx).putBoolean("pwa_i_$host", v)

    fun pull(ctx: Context, host: String): Boolean = c(ctx).getBoolean("pwa_r_$host", true)
    fun setPull(ctx: Context, host: String, v: Boolean) = c(ctx).putBoolean("pwa_r_$host", v)

    /** ثابت اتجاه النظام المقابل للاختيار. */
    fun orientFlag(v: Int): Int = when (v) {
        ORIENT_PORTRAIT -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        ORIENT_LANDSCAPE -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val ios = LocalIos.current
    val src = remember { MutableInteractionSource() }
    Column(
        modifier.pressScale(src, 0.94f).clip(RoundedCornerShape(16.dp)).background(ios.cell)
            .clickable(interactionSource = src, indication = null, onClick = onClick).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, Modifier.size(24.dp), tint = ios.blue)
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = ios.label, maxLines = 1, textAlign = TextAlign.Center)
    }
}

/**
 * ورقة خيارات التطبيق المثبّت: تُفتح بلمس مقبض الحافة العلوية. تحديث/مشاركة/فتح في Nova، ثم اتجاه الشاشة وإبقاء الشاشة
 * مضاءة وملء الشاشة والسحب للتحديث (لكل تطبيق)، وأخيراً إزالة التطبيق.
 */
@Composable
fun AppOptionsSheet(
    visible: Boolean, name: String, orient: Int, keepAwake: Boolean, immersive: Boolean, pull: Boolean,
    onOrient: (Int) -> Unit, onKeepAwake: (Boolean) -> Unit, onImmersive: (Boolean) -> Unit, onPull: (Boolean) -> Unit,
    onReload: () -> Unit, onShare: () -> Unit, onOpenNova: () -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit
) {
    val ios = LocalIos.current
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(tween(Adaptive.ms(180))), exit = fadeOut(tween(Adaptive.ms(140)))) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            )
        }
        AnimatedVisibility(
            visible = visible, modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(Adaptive.ms(340), easing = NovaMotion.emphasized)) { it } + fadeIn(tween(Adaptive.ms(160))),
            exit = slideOutVertically(tween(Adaptive.ms(240), easing = NovaMotion.emphasized)) { it } + fadeOut(tween(Adaptive.ms(120)))
        ) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(ios.groupedBg)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                    .navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)
            ) {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(36.dp).height(5.dp).clip(RoundedCornerShape(50)).background(ios.fill2))
                }
                Text(
                    L("خيارات التطبيق") + " · " + name, style = MaterialTheme.typography.titleMedium, color = ios.label, maxLines = 1,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 12.dp)
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickAction(Icons.Default.Refresh, L("تحديث"), Modifier.weight(1f)) { onDismiss(); onReload() }
                    QuickAction(Icons.Default.Share, L("مشاركة"), Modifier.weight(1f)) { onDismiss(); onShare() }
                    QuickAction(Icons.Default.Home, L("فتح في Nova"), Modifier.weight(1f)) { onDismiss(); onOpenNova() }
                }
                Spacer(Modifier.height(16.dp))
                IosGroup {
                    IosCell(
                        L("اتجاه الشاشة"),
                        { onOrient((orient + 1) % 3) },
                        detail = when (orient) {
                            AppPrefs.ORIENT_PORTRAIT -> L("عمودي")
                            AppPrefs.ORIENT_LANDSCAPE -> L("أفقي")
                            else -> L("تلقائي")
                        },
                        leading = { IosTile(ios.indigo) { Icon(Icons.Default.Settings, null, Modifier.size(18.dp)) } },
                        chevron = true
                    )
                    IosCell(
                        L("إبقاء الشاشة مضاءة"), { onKeepAwake(!keepAwake) },
                        leading = { IosTile(ios.orange) { Icon(Icons.Default.Settings, null, Modifier.size(18.dp)) } },
                        trailing = { IosSwitch(checked = keepAwake, onCheckedChange = null) }
                    )
                    IosCell(
                        L("ملء الشاشة"), { onImmersive(!immersive) },
                        leading = { IosTile(ios.teal) { Icon(Icons.Default.Settings, null, Modifier.size(18.dp)) } },
                        trailing = { IosSwitch(checked = immersive, onCheckedChange = null) }
                    )
                    IosCell(
                        L("السحب للتحديث"), { onPull(!pull) }, divider = false,
                        leading = { IosTile(ios.green) { Icon(Icons.Default.Refresh, null, Modifier.size(18.dp)) } },
                        trailing = { IosSwitch(checked = pull, onCheckedChange = null) }
                    )
                }
                Spacer(Modifier.height(16.dp))
                IosGroup {
                    IosCell(
                        L("إزالة التطبيق"), { onDismiss(); onRemove() }, tint = ios.red, divider = false,
                        leading = { IosTile(ios.red) { Icon(Icons.Default.Delete, null, Modifier.size(18.dp)) } }
                    )
                }
            }
        }
    }
}
