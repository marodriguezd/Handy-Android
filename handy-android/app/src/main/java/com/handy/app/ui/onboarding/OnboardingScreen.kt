package com.handy.app.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.handy.app.R
import com.handy.app.ui.components.MotionTokens
import com.handy.app.ui.onboarding.components.OnboardingButtonRow
import com.handy.app.ui.onboarding.components.OnboardingIconContainer
import com.handy.app.ui.onboarding.components.OnboardingProgressBar
import com.handy.app.ui.onboarding.components.StepIndicator
import com.handy.app.viewmodel.OnboardingViewModel

@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel, onComplete: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.setMicrophonePermission(granted)
    }
    val appContext = LocalContext.current
    val totalSteps = uiState.totalSteps
    val isLastStep = uiState.currentStep >= totalSteps - 1

    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.currentStep == 0) {
            TextButton(
                onClick = {
                    viewModel.completeOnboarding()
                    onComplete()
                },
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            ) {
                Text(stringResource(R.string.onboarding_skip_all))
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier.weight(0.6f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = uiState.currentStep,
                    transitionSpec = {
                        // Sprint 27a upgrade: motion spec is now wired
                        // through MotionTokens.PopEasing (0.22 / 1 / 0.36 / 1
                        // cubic-bezier) + DurationLong (500ms) instead of
                        // the pre-MD3 defaults. Two typed motion specs:
                        //  - translateMotion runs IntOffset-based because
                        //    slideInHorizontally takes a FiniteAnimationSpec
                        //    <IntOffset> (offset is in pixels, not normalized
                        //    Float). Forgetting this caused the pre-fix
                        //    "TweenSpec<Float> inferred instead of
                        //    FiniteAnimationSpec<IntOffset>" compile error.
                        //  - fadeMotion runs Float-based because fadeIn /
                        //    fadeOut take FiniteAnimationSpec<Float>
                        //    (alpha is 0..1 normalized Float).
                        val translateMotion = androidx.compose.animation.core.tween<IntOffset>(
                            durationMillis = MotionTokens.DurationLong,
                            easing = MotionTokens.PopEasing,
                        )
                        val fadeMotion = androidx.compose.animation.core.tween<Float>(
                            durationMillis = MotionTokens.DurationLong,
                            easing = MotionTokens.PopEasing,
                        )
                        (slideInHorizontally(animationSpec = translateMotion, initialOffsetX = { it }) +
                            fadeIn(animationSpec = fadeMotion)) togetherWith
                            (slideOutHorizontally(animationSpec = translateMotion, targetOffsetX = { -it }) +
                                fadeOut(animationSpec = fadeMotion))
                    },
                ) { step ->
                    when (step) {
                        0 -> WelcomeContent()
                        1 -> MicPermissionContent(
                            hasPermission = uiState.hasMicrophonePermission,
                            onRequestPermission = {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                        )
                        2 -> ImeSetupContent(appContext)
                        3 -> ModelDownloadContent(viewModel)
                        4 -> ReadyContent()
                    }
                }
            }

            Box(
                modifier = Modifier.weight(0.15f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                StepIndicator(
                    currentStep = uiState.currentStep,
                    totalSteps = totalSteps,
                )
            }

            // Sprint 27a perf nit: wrap nullable click handlers in
            // remember(uiState.currentStep) so Compose memoizes the
            // lambda identities across recompositions when the active
            // step hasn't changed. Without these, every OnboardingScreen
            // recomposition allocates fresh `(()->Unit)?` lambdas,
            // marking OnboardingButtonRow params as unstable and forcing
            // a recomposition of that subtree even when nothing relevant
            // changed.
            val onBack: (() -> Unit)? = remember(uiState.currentStep) {
                if (uiState.currentStep > 0) ({ viewModel.previousStep() }) else null
            }
            val onSkip: (() -> Unit)? = remember(uiState.currentStep) {
                if (uiState.currentStep == 1 || uiState.currentStep == 2) ({ viewModel.nextStep() }) else null
            }
            OnboardingButtonRow(
                currentStep = uiState.currentStep,
                totalSteps = totalSteps,
                onBack = onBack,
                onPrimary = {
                    when {
                        isLastStep -> {
                            viewModel.completeOnboarding()
                            onComplete()
                        }
                        else -> viewModel.nextStep()
                    }
                },
                onSkip = onSkip,
            )
        }
    }
}

@Composable
private fun WelcomeContent() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        OnboardingIconContainer(icon = Icons.Default.Mic)
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MicPermissionContent(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        OnboardingIconContainer(icon = Icons.Default.Mic)
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.onboarding_mic_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_mic_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequestPermission) {
            Text(stringResource(R.string.onboarding_grant))
        }
        if (hasPermission) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.onboarding_permission_granted),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ImeSetupContent(appContext: Context) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        OnboardingIconContainer(icon = Icons.Default.Keyboard)
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.onboarding_ime_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_ime_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = {
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
        }) {
            Text(stringResource(R.string.onboarding_ime_open))
        }
    }
}

@Composable
private fun ModelDownloadContent(viewModel: OnboardingViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        OnboardingIconContainer(icon = Icons.Default.Download)
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.onboarding_model_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_model_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        when {
            uiState.downloadError != null -> {
                Text(
                    text = uiState.downloadError ?: "",
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { viewModel.nextStep() }) {
                    Text(stringResource(R.string.onboarding_continue_anyway))
                }
            }
            uiState.isDownloading -> {
                OnboardingProgressBar(progress = uiState.downloadProgress)
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = { viewModel.skipDownload() }) {
                    Text(stringResource(R.string.onboarding_skip))
                }
            }
            uiState.isDownloadCanceled -> {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.onboarding_model_canceled),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    viewModel.retryDownload()
                }) {
                    Text(stringResource(R.string.onboarding_model_download))
                }
            }
            uiState.isDownloadReady -> {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.onboarding_model_ready),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            else -> {
                Button(onClick = {
                    viewModel.skipToModelDownload()
                }) {
                    Text(stringResource(R.string.onboarding_model_download))
                }
            }
        }
    }
}

@Composable
private fun ReadyContent() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        OnboardingIconContainer(icon = Icons.Default.CheckCircle)
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.onboarding_ready_title),
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_ready_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// Sprint 27a: inline StepIndicator removed in favor of
// com.handy.app.ui.onboarding.components.StepIndicator (MD3-elevated,
// 48dp touch targets per dot, Surface(tonalElevation = 3.dp) wrapper,
// gentle() spring-driven animations, with a "Step N of M" label below
// the row). Inline 120dp hero Icons (5×) replaced by OnboardingIconContainer;
// inline Row{OutlinedButton+Button} / TextButton skip affordance replaced
// by OnboardingButtonRow; inline LinearProgressIndicator-block-replaced by
// OnboardingProgressBar. The AnimatedContent transitionSpec was upgraded
// to tween(500ms, MotionTokens.PopEasing) — see the comment block above.
