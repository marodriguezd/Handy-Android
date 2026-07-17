@file:Suppress("PrivateApi", "DiscouragedPrivateApi")

package com.handy.app.injection

import android.annotation.SuppressLint
import android.os.Binder
import android.os.IBinder
import android.os.Parcel

/**
 * Shizuku User Service binder. Lives in the `shizuku` process (UID 2000)
 * spawned by the Shizuku framework via [Shizuku.bindUserService]; runs
 * with elevated system privileges so it can return system-service binders
 * to the main app process without needing to expose them publicly.
 *
 * Hidden API: [Class.forName("android.os.ServiceManager")] + the
 * `getService` reflection lookup is `@hide`. Shizuku apps have
 * historical greylist access via the framework's hidden-API bypass,
 * so this resolves at runtime on Android 16 (API 36) without throwing.
 * There is no public IPC primitive for retrieving system-service
 * binders, so the reflection form is the canonical solution until a
 * future Android version exposes a public equivalent (none planned).
 *
 * Sprint 28b probe: lint suppresses [PrivateApi] / [DiscouragedPrivateApi]
 * warnings because the call site is essential and the function stays
 * correct under Shizuku's privileged context. Migration to a public
 * API is technically possible only if Android itself exposes
 * `ServiceManager.getService` (it does not, as of API 36).
 */
class HandyUserService : Binder() {

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == FIRST_CALL_TRANSACTION) {
            data.enforceInterface("com.handy.app.injection.IHandyUserService")
            val inputBinder = getInputServiceBinder()
            reply?.writeNoException()
            reply?.writeStrongBinder(inputBinder)
            return true
        }
        return super.onTransact(code, data, reply, flags)
    }

    @SuppressLint("PrivateApi")
    private fun getInputServiceBinder(): IBinder {
        val serviceManagerClass = Class.forName("android.os.ServiceManager")
        val getServiceMethod = serviceManagerClass.getDeclaredMethod("getService", String::class.java)
        return getServiceMethod.invoke(null, "input") as IBinder
    }
}
