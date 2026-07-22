package com.handy.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * MD3-native [Slider] wrapper with a label + discrete `formatValue`
 * format.  Replaces the PC `Slider.tsx` (volume, model unload, paste
 * delay, recording buffer) on Android.
 *
 * @param value: current value, must be in `[min, max]`.
 * @param onValueChange: invoked on user drag.
 * @param label: row label rendered above the slider.
 * @param formatValue: receives `(value)` and returns the string shown
 *   on the right (e.g. `"65%"`, `"1 min"`).
 * @param steps: when `> 0` the slider renders discrete stops.
 */
@Composable
fun HandySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    label: String,
    formatValue: (Float) -> String,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
) {
    // Sprint 30c-#6: do NOT force the outer Column to fill the parent
    // width. Inside `HandyListItem`'s trailing slot a `fillMaxWidth()`
    // here consumes the entire Row's width and starves the
    // title/subtitle `Column(weight=1f)` to 0dp, causing massive text
    // wrapping and the huge empty SettingsGroup card observed on A059.
    // `widthIn(min = 196.dp)` keeps the slider usable while letting the
    // outer Row distribute width correctly.
    Column(modifier = modifier.widthIn(min = 196.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatValue(value),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                activeTickColor = MaterialTheme.colorScheme.primary,
                inactiveTickColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        )
    }
}
