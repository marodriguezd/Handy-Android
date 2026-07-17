package com.handy.app.settings

/**
 * Sprint 25b Phase C — user-selectable inference backend.
 *
 * Sprint 25b persistence-half: the choice is stored in
 * `SettingsStore.accelerationBackend` and rendered as a segmented
 * button in Advanced Settings. It is intentionally gated behind
 * `experimentalEnabled` because the only currently stable backend
 * is `CPU`.
 *
 * Wiring-half (Rust JNI): the actual backend selection calls into
 * [com.handy.app.bridge.EngineBridge.nativeSetAcceleration] are
 * deferred until the Vulkan/QNN/NNAPI backends are actually testing
 * green on Android (per `BACKENDS.md` Sprint 26+ backlog). CPU is
 * the runtime default regardless of the user's persisted choice —
 * the Kotlin side keeps the value as a UI hint, no JNI dispatch.
 *
 * `isExperimental` flag drives the MD3 segmented button's enabled
 * state through `HandySegmentedButton(enabled = !state.isExperimental)`.
 */
enum class AccelerationBackend(
    val labelKey: String,
    val isExperimental: Boolean,
) {
    CPU("\u00acceleration_cpu", false),
    Vulkan("\u00acceleration_vulkan", true),
    NNAPI("\u00acceleration_nnapi", true),
}
