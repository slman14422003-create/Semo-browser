package com.nova.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovaSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    NovaStatefulSheet(onDismiss, rememberModalBottomSheetState(skipPartiallyExpanded = true), content)
}

@ExperimentalMaterial3Api
@Composable
fun NovaStatefulSheet(
    onDismiss: () -> Unit,
    state: SheetState,
    content: @Composable ColumnScope.() -> Unit
) {
    val ios = LocalIos.current
    val bounce = rememberRubberBand()
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = state, containerColor = Color.Transparent, shape = IosShape.sheet,
        scrimColor = Color.Black.copy(alpha = if (ios.dark) UiLayout.SHEET_SCRIM_DARK else UiLayout.SHEET_SCRIM), dragHandle = null
    ) {
        Column(Modifier.fillMaxWidth().glass(GlassStyles.Sheet, IosShape.sheet)) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(top = UiLayout.dp6, bottom = UiLayout.dp8).size(width = UiLayout.dp36, height = UiLayout.dp5).clip(CircleShape).background(ios.gray3))
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState(), overscrollEffect = bounce).padding(bottom = UiLayout.dp20), content = content)
        }
    }
}
