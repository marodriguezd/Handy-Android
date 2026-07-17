package com.handy.app.ui.onboarding.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Sprint 27a — MD3 hero-icon container for onboarding flows.
 *
 * Renders a 120dp `surfaceContainerHigh` rounded box (28dp corners)
 * with a 64dp primary-tinted Icon centered inside. Sized to the M3
 * hero-icon spec (88-120dp outer + 48-64dp inner glyph); the 120/64
 * pair matches the M3 hero sheet and the PC Handy's onboarding
 * glyph.
 *
 * Used by: WelcomeContent, MicPermissionContent, ImeSetupContent,
 * ModelDownloadContent, ReadyContent. The pre-Sprint-27 inline
 * variant was `Modifier.size(120dp); Icon(…tint = primary)`; the
 * new container adds the tonal background that signals
 * "this is a feature card" vs a bare-line icon.
 */
@Composable
fun OnboardingIconContainer(
    icon: ImageVector,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(120.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(28.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
