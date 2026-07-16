package com.handy.app.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * MD3-native [Badge] wrapper.  Used by Models cards to show download
 * progress count and by Settings rows to surface "experimental" or
 * "unread" indicators.
 *
 * The optional [contentDescription] is wired through `Modifier.semantics`
 * so TalkBack reads the meaningful label instead of just the count.
 * Pass `null` to fall back to the default (silent).
 */
@Composable
fun HandyCountBadge(
    count: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    if (count <= 0) return
    val a11y: Modifier = if (contentDescription != null) {
        modifier.semantics { this.contentDescription = contentDescription }
    } else {
        modifier
    }
    BadgedBox(
        modifier = a11y.padding(end = Spacing.sm),
        badge = {
            Badge(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Text(text = count.toString())
            }
        },
    ) {
        Spacer(modifier = Modifier.padding(0.dp))
    }
}

/**
 * Static dot badge — useful for "primary" or "experimental" feature
 * indicators without a count.
 */
@Composable
fun HandyDotBadge(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val a11y: Modifier = if (contentDescription != null) {
        modifier.semantics { this.contentDescription = contentDescription }
    } else {
        modifier
    }
    Badge(
        modifier = a11y.padding(end = Spacing.sm),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    )
}
