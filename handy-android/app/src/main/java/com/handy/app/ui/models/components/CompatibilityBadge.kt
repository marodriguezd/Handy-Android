package com.handy.app.ui.models.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.handy.app.R
import com.handy.app.capability.CompatibilityBadge

@Composable
fun CompatibilityBadgeChip(badge: CompatibilityBadge, modifier: Modifier = Modifier) {
    var label: String = badge.name
    var accent: Color = MaterialTheme.colorScheme.secondary
    when (badge) {
        CompatibilityBadge.EXPERIMENTAL -> {
            label = stringResource(R.string.badge_experimental)
            accent = MaterialTheme.colorScheme.secondary
        }
        CompatibilityBadge.HEAVY_GATE -> {
            label = stringResource(R.string.badge_heavy)
            accent = MaterialTheme.colorScheme.error
        }
        CompatibilityBadge.EXCEEDS_RAM -> {
            label = stringResource(R.string.badge_exceeds_ram)
            accent = MaterialTheme.colorScheme.error
        }
        CompatibilityBadge.LARGE_HEAP_REQUIRED -> {
            label = stringResource(R.string.badge_large_heap)
            accent = MaterialTheme.colorScheme.secondary
        }
    }
    Surface(
        modifier = modifier,
        color = accent.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.extraSmall,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun ActiveBadge(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.models_active)
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.primary,
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
