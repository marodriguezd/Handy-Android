package com.handy.android

import kotlinx.coroutines.Job
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry of in-flight LLM post-processing coroutines and their owning surface.
 *
 * Calls are keyed in a concurrent map so cancellation can be scoped: [cancelAll]
 * is the process-global shutdown path (feature disabled, IME death), while
 * [cancelAllForOwner] only cancels one owner's calls by identity — destroying
 * the popup or the settings screen must never interrupt a dictation from
 * another surface.
 *
 * Port of `PostProcessor.CallRegistry` (android_transcribe_app, Java/OkHttp)
 * to Kotlin coroutines. The origin keyed `okhttp3.Call` → owner; here we key
 * `kotlinx.coroutines.Job` → owner. The isolation semantics are identical and
 * are covered by plain-JVM tests ([LlmCallRegistryTest]) without touching
 * Android framework classes.
 */
object LlmCallRegistry {
    private val OWNERS = ConcurrentHashMap<Job, Any>()

    /** Sentinel for ownerless calls: still globally cancellable, never matched by an owner-scoped cancel. */
    private val NO_OWNER = Any()

    /** Registers [job] under [owner] (identity). A null owner is normalized to [NO_OWNER]. */
    fun register(job: Job, owner: Any?) {
        OWNERS[job] = owner ?: NO_OWNER
    }

    /** Removes [job] from the registry (call on completion/failure). */
    fun unregister(job: Job) {
        OWNERS.remove(job)
    }

    /** Cancels every registered job (global shutdown). */
    fun cancelAll() {
        for (job in OWNERS.keys) {
            if (job.isActive) job.cancel()
        }
    }

    /**
     * Cancels only the jobs registered with exactly [owner] (identity
     * comparison — surfaces pass `this`). A null owner is ignored: the
     * sentinel [NO_OWNER] is only ever cancelled by [cancelAll].
     */
    fun cancelAllForOwner(owner: Any?) {
        if (owner == null) return
        for ((job, registeredOwner) in OWNERS) {
            if (registeredOwner === owner && job.isActive) {
                job.cancel()
            }
        }
    }
}
