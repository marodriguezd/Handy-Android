package com.handy.app.util

import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap

/**
 * Pure-JVM in-memory implementation of [android.content.SharedPreferences] for
 * unit tests. No Robolectric, no platform-side StateFlows.
 *
 * Mirrors the contract of `android.app.SharedPreferencesImpl` enough for
 * [com.handy.app.SettingsStore] tests:
 *   - `commit()`/`apply()` semantics: `apply()` returns immediately and writes
 *     in-memory; `commit()` writes in-memory then returns true.
 *   - `clear()` wipes all keys but only commits if explicitly followed by
 *     `commit()`/`apply()`.
 *   - `remove(key)` after a pending `put(key, ...)` cancels the pending put.
 *
 * Designed to be hand-rolled (no Robolectric) per the user's regression-test
 * brief; the in-memory map is backed by `ConcurrentHashMap` so concurrent reads
 * from the production-style `prefs.edit().apply()` path stay consistent.
 */
class InMemorySharedPreferences(
    initial: Map<String, Any> = emptyMap(),
    val name: String = "test_prefs"
) : SharedPreferences {

    private val store: ConcurrentHashMap<String, Any> =
        ConcurrentHashMap<String, Any>().apply { putAll(initial) }

    override fun getAll(): MutableMap<String, *> = store.toMutableMap()

    override fun getString(key: String?, defValue: String?): String? =
        key?.let { store[it] as? String } ?: defValue

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (key?.let { store[it] as? Set<String> })?.toMutableSet() ?: defValues

    override fun getInt(key: String?, defValue: Int): Int = key?.let { store[it] as? Int } ?: defValue

    override fun getLong(key: String?, defValue: Long): Long = key?.let { store[it] as? Long } ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float = key?.let { store[it] as? Float } ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean = key?.let { store[it] as? Boolean } ?: defValue

    override fun contains(key: String?): Boolean = key != null && store.containsKey(key)

    override fun edit(): SharedPreferences.Editor = InMemoryEditor(store)

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) { /* no-op */ }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) { /* no-op */ }

    /**
     * Editor implementation. Tracks pending puts/removals separately from the
     * backing store so the `apply()`/`commit()` semantics stay atomic.
     */
    class InMemoryEditor(
        private val store: ConcurrentHashMap<String, Any>
    ) : SharedPreferences.Editor {

        private val pending: MutableMap<String, Any?> = LinkedHashMap()
        private val pendingRemovals: MutableSet<String> = LinkedHashSet()
        private var clearAll: Boolean = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
            if (key != null) {
                pendingRemovals.remove(key)
                pending[key] = value
            }
        }

        override fun putStringSet(
            key: String?,
            values: MutableSet<String>?
        ): SharedPreferences.Editor = apply {
            if (key != null) {
                pendingRemovals.remove(key)
                pending[key] = values?.toMutableSet()
            }
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
            if (key != null) {
                pendingRemovals.remove(key)
                pending[key] = value
            }
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
            if (key != null) {
                pendingRemovals.remove(key)
                pending[key] = value
            }
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
            if (key != null) {
                pendingRemovals.remove(key)
                pending[key] = value
            }
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
            if (key != null) {
                pendingRemovals.remove(key)
                pending[key] = value
            }
        }

        override fun remove(key: String?): SharedPreferences.Editor = apply {
            if (key != null) {
                pending.remove(key)
                pendingRemovals.add(key)
            }
        }

        override fun clear(): SharedPreferences.Editor = apply {
            clearAll = true
            pending.clear()
            pendingRemovals.clear()
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clearAll) {
                store.clear()
                clearAll = false
            } else {
                pendingRemovals.forEach { store.remove(it) }
            }
            pending.forEach { (k, v) -> if (v != null) store[k] = v }
            pending.clear()
            pendingRemovals.clear()
        }
    }
}
