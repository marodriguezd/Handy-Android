package com.handy.app.ui.theme

import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════════════════
// Handy Android — Material Design 3 color tokens, aligned with the Handy PC
// palette.  PC dark reference: background #2c2b29, text #fbfbfb, logo
// primary #f28cbb, ui pink #da5893.  PC light reference: background
// #fbfbfb, text #0f0f0f, logo primary #faa2ca.
// All tokens below stay consistent with the brand; pairings (X / onX) follow
// the standard MD3 tonal-pairing rules.  Hardcoded colors anywhere else in
// the codebase should use these via `MaterialTheme.colorScheme.*`.
// ═══════════════════════════════════════════════════════════════════════════

// ── Dark scheme tokens ───────────────────────────────────────────────────
val HandyBackground              = Color(0xFF2C2B29)
val HandyOnBackground            = Color(0xFFFDFBFB)

val HandySurface                 = Color(0xFF2C2B29)
val HandySurfaceVariant          = Color(0xFF3A3835)
val HandyOnSurface               = Color(0xFFFDFBFB)
val HandyOnSurfaceVariant        = Color(0xFFA3A09A)
val HandySurfaceDim              = Color(0xFF1F1E1C)
val HandySurfaceBright           = Color(0xFF3A3835)
val HandySurfaceContainerLowest = Color(0xFF1A1917)
val HandySurfaceContainerLow     = Color(0xFF25241F)
val HandySurfaceContainer        = Color(0xFF2C2B29)
val HandySurfaceContainerHigh    = Color(0xFF34322F)
val HandySurfaceContainerHighest = Color(0xFF3F3D3A)

// ── Brand / accents (dark) ────────────────────────────────────────────────
val HandyPrimary                 = Color(0xFFF28CBB)
val HandyOnPrimary               = Color(0xFF1C1B1F)
val HandyPrimaryContainer        = Color(0xFF5A3A4B)
val HandyOnPrimaryContainer      = Color(0xFFFFD9E4)
val HandyPrimaryFixed            = Color(0xFFFFD9E4)
val HandyPrimaryFixedDim         = Color(0xFFF28CBB)
val HandyOnPrimaryFixed          = Color(0xFF1C1B1F)

val HandySecondary               = Color(0xFFA48C8F)
val HandyOnSecondary             = Color(0xFF1C1B1F)
val HandySecondaryContainer      = Color(0xFF4A4041)
val HandyOnSecondaryContainer    = Color(0xFFF0DEE0)
val HandySecondaryFixed          = Color(0xFFF0DEE0)
val HandySecondaryFixedDim       = Color(0xFFA48C8F)
val HandyOnSecondaryFixed        = Color(0xFF1C1B1F)

val HandyTertiary                = Color(0xFFD4A5A9)
val HandyOnTertiary              = Color(0xFF1C1B1F)
val HandyTertiaryContainer       = Color(0xFF524344)
val HandyOnTertiaryContainer     = Color(0xFFFFDADC)
val HandyTertiaryFixed           = Color(0xFFFFDADC)
val HandyTertiaryFixedDim        = Color(0xFFD4A5A9)
val HandyOnTertiaryFixed         = Color(0xFF1C1B1F)

// ── Status / error (dark) ─────────────────────────────────────────────────
val HandyError                   = Color(0xFFF2B8B5)
val HandyOnError                 = Color(0xFF1C1B1F)
val HandyErrorContainer          = Color(0xFF5C3A39)
val HandyOnErrorContainer        = Color(0xFFFFDAD6)

// ── Outlines (dark) ───────────────────────────────────────────────────────
val HandyOutline                 = Color(0xFF4A4845)
val HandyOutlineVariant          = Color(0xFF5A5753)

val HandyInverseSurface          = Color(0xFFFDFBFB)
val HandyInverseOnSurface        = Color(0xFF2C2B29)
val HandyInversePrimary          = Color(0xFFDA5893)

val HandyScrim                   = Color(0xFF000000)

// ── Semantic accents (PC overlay waveform / recording dot, etc.) ──────────
val HandyAccent                  = Color(0xFFF28CBB)
val HandySuccess                 = Color(0xFF81C995)
val YellowStar                   = Color(0xFFFFD700)

// ── Light scheme tokens ───────────────────────────────────────────────────
val HandyLightBackground              = Color(0xFFFDFBFB)
val HandyLightOnBackground            = Color(0xFF1C1B1F)

val HandyLightSurface                 = Color(0xFFFDFBFB)
val HandyLightSurfaceVariant          = Color(0xFFECE0E1)
val HandyLightOnSurface               = Color(0xFF1C1B1F)
val HandyLightOnSurfaceVariant        = Color(0xFF4A4546)
val HandyLightSurfaceDim              = Color(0xFFDDC8CB)
val HandyLightSurfaceBright           = Color(0xFFFDFBFB)
val HandyLightSurfaceContainerLowest = Color(0xFFFFFFFF)
val HandyLightSurfaceContainerLow     = Color(0xFFF7EDF1)
val HandyLightSurfaceContainer        = Color(0xFFF1E7EB)
val HandyLightSurfaceContainerHigh    = Color(0xFFEBE0E4)
val HandyLightSurfaceContainerHighest = Color(0xFFE5DADE)

val HandyLightPrimary                 = Color(0xFF9A3C6A)
val HandyLightOnPrimary               = Color(0xFFFFFFFF)
val HandyLightPrimaryContainer        = Color(0xFFFFD9E4)
val HandyLightOnPrimaryContainer      = Color(0xFF3E0024)
val HandyLightPrimaryFixed            = Color(0xFFFFD9E4)
val HandyLightPrimaryFixedDim         = Color(0xFF9A3C6A)
val HandyLightOnPrimaryFixed          = Color(0xFFFFFFFF)

val HandyLightSecondary               = Color(0xFF6E5659)
val HandyLightOnSecondary             = Color(0xFFFFFFFF)
val HandyLightSecondaryContainer      = Color(0xFFF0DEE0)
val HandyLightOnSecondaryContainer    = Color(0xFF261A1C)
val HandyLightSecondaryFixed          = Color(0xFFF0DEE0)
val HandyLightSecondaryFixedDim       = Color(0xFF6E5659)
val HandyLightOnSecondaryFixed        = Color(0xFFFFFFFF)

val HandyLightTertiary                = Color(0xFF815356)
val HandyLightOnTertiary              = Color(0xFFFFFFFF)
val HandyLightTertiaryContainer       = Color(0xFFFFDADC)
val HandyLightOnTertiaryContainer     = Color(0xFF331019)
val HandyLightTertiaryFixed           = Color(0xFFFFDADC)
val HandyLightTertiaryFixedDim        = Color(0xFF815356)
val HandyLightOnTertiaryFixed         = Color(0xFFFFFFFF)

val HandyLightError                   = Color(0xFFBA1A1A)
val HandyLightOnError                 = Color(0xFFFFFFFF)
val HandyLightErrorContainer          = Color(0xFFFFDAD6)
val HandyLightOnErrorContainer        = Color(0xFF410002)

val HandyLightOutline                 = Color(0xFF7D7475)
val HandyLightOutlineVariant          = Color(0xFFD6C3C5)

val HandyLightInverseSurface          = Color(0xFF2C2B29)
val HandyLightInverseOnSurface        = Color(0xFFFDFBFB)
val HandyLightInversePrimary          = Color(0xFFFFAFD0)

val HandyLightScrim                   = Color(0xFF000000)
