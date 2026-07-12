package com.handy.app.injection

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.InputEvent
import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.ServiceConnection
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper

class ShizukuInjector(
    private val context: Context,
    private val requestPermissionCode: Int = 1001,
) : InjectorStrategy {

    @Volatile
    private var userService: IHandyUserService? = null
    private var reconnectAttempts = 0
    private var reconnectJob: Job? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(component: ComponentName, binder: IBinder) {
            userService = IHandyUserService.Stub.asInterface(binder)
            reconnectAttempts = 0
            Log.i("ShizukuInjector", "HandyUserService connected")
        }

        override fun onServiceDisconnected(component: ComponentName) {
            userService = null
            Log.w("ShizukuInjector", "HandyUserService disconnected")
            reconnectJob?.cancel()
            reconnectJob = CoroutineScope(Dispatchers.IO).launch {
                val delay = (1000L * (1 shl reconnectAttempts.coerceAtMost(5))).coerceAtMost(30_000L)
                delay(delay)
                reconnectAttempts++
                if (Shizuku.pingBinder()) {
                    bindService()
                }
            }
        }
    }

    fun bindService() {
        if (!Shizuku.pingBinder()) return
        try {
            val component = ComponentName(context, HandyUserService::class.java)
            val args = Shizuku.UserServiceArgs(component)
                .processNameSuffix("shizuku")
                .daemon(true)
                .version(1)
            Shizuku.bindUserService(args, serviceConnection)
        } catch (e: Exception) {
            Log.e("ShizukuInjector", "Failed to bind HandyUserService", e)
        }
    }

    fun unbindService() {
        try {
            val component = ComponentName(context, HandyUserService::class.java)
            val args = Shizuku.UserServiceArgs(component)
                .processNameSuffix("shizuku")
                .daemon(true)
                .version(1)
            Shizuku.unbindUserService(args, serviceConnection, true)
        } catch (_: Exception) {
        }
        userService = null
    }

    override val displayName: String get() = "Shizuku (UID 2000)"

    fun ensureConnected() {
        if (userService == null && Shizuku.pingBinder()) {
            bindService()
        }
    }

    override fun isAvailable(): Boolean {
        ensureConnected()
        return try {
            Shizuku.pingBinder()
                && Shizuku.getVersion() >= 13
                && Shizuku.checkSelfPermission() == 0
                && userService != null
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun inject(text: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Handy", text))

            delay(50L)

            val svc = userService
                ?: return@withContext Result.failure(
                    IllegalStateException("HandyUserService not connected")
                )
            val inputBinder = svc.inputServiceBinder

            val wrapper = ShizukuBinderWrapper(inputBinder)

            val inputManagerClass = Class.forName("android.hardware.input.IInputManager")
            val asInterfaceMethod = inputManagerClass.getDeclaredMethod("asInterface", IBinder::class.java)
            val inputManager = asInterfaceMethod.invoke(null, wrapper)

            val injectMethod = inputManagerClass.getDeclaredMethod(
                "injectInputEvent", InputEvent::class.java, Int::class.java
            )

            val now = SystemClock.uptimeMillis()
            val downEvent = KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PASTE, 0)
            val upEvent = KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_PASTE, 0)

            injectMethod.invoke(inputManager, downEvent, 0)
            delay(10L)
            injectMethod.invoke(inputManager, upEvent, 0)

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ShizukuInjector", "Injection failed", e)
            Result.failure(e)
        }
    }

    fun requestPermissionIfNeeded(activity: android.app.Activity) {
        if (Shizuku.checkSelfPermission() == 0) return
        Shizuku.requestPermission(requestPermissionCode)
    }
}
