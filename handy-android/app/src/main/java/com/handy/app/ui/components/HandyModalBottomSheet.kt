package com.handy.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * MD3-native [ModalBottomSheet] wrapper.  Used by the prompt editor
 * (Sprint 26) and any future sheet-style flows that don't fit a Dialog.
 *
 * Usage:
 * ```
 * var visible by remember { mutableStateOf(false) }
 * HandyModalBottomSheet(visible = visible, onDismiss = { visible = false }) {
 *     Text("Editor body")
 * }
 * ```
 *
 * Important: this composable is purely about visibility + theming; the
 * sheet's body decides when to programmatically close itself (typically
 * by mutating the parent's `visible` state directly).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandyModalBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    if (visible) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            modifier = modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            // ModalBottomSheet's content slot is itself a Column, so we just
            // forward the user's lambda directly.  The factory does NOT need
            // an extra wrapping Column.
            content()
        }
    }
}
