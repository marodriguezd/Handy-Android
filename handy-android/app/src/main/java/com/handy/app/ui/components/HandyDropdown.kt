package com.handy.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * MD3-native `ExposedDropdownMenuBox` wrapper.  Replaces every setting
 * screen's ad-hoc `ExposedDropdownMenuBox + OutlinedButton + menuAnchor`
 * pattern.  Designed for the Settings/About/Models selects — backend
 * callbacks come from the ViewModel and the chosen option is rendered as
 * a compact label inside an outlined text field.
 *
 * @param label displayed inside the field's label slot.
 * @param options (key, display label) pairs.
 * @param selected currently selected key (matches one of the options).
 * @param onSelect invoked with the new key on selection.
 * @param enabled when false, both the field and the menu render disabled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> HandyDropdown(
    label: String,
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second.orEmpty()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(
                    type = MenuAnchorType.PrimaryNotEditable,
                    enabled = enabled,
                )
                .fillMaxWidth(),
            enabled = enabled,
            textStyle = MaterialTheme.typography.bodyLarge,
        )
        // `ExposedDropdownMenu` resolves through the
        // `ExposedDropdownMenuBoxScope` receiver. We deliberately do
        // NOT add a top-level `import …ExposedDropdownMenu` because
        // the M3 BOM versions we resolve to expose it as a scope-member
        // only — a top-level sibling of the same simple name does not
        // exist, so an import would error. The bare-form call below
        // resolves through the receiver regardless of which Compose
        // BOM reaches gradle.
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (value, display) ->
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = display,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
