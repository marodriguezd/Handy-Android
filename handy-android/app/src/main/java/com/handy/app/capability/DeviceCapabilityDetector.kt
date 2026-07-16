package com.handy.app.capability

import android.app.ActivityManager
import android.content.Context
import android.os.Build

object DeviceCapabilityDetector {
    fun detect(context: Context): CapabilitySnapshot {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }

        val total = memInfo.totalMem.takeIf { it > 0 } ?: 2L * 1024 * 1024 * 1024
        val avail = memInfo.availMem.takeIf { it >= 0 } ?: total
        val maxProcess = Runtime.getRuntime().maxMemory()

        return CapabilitySnapshot(
            totalMemBytes = total,
            availMemBytes = avail,
            maxMemoryProcessBytes = maxProcess,
            isLowRamDevice = am?.isLowRamDevice ?: false,
            memoryClassMb = am?.memoryClass ?: 192,
            largeMemoryClassMb = am?.largeMemoryClass ?: 512,
            cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
            sdkInt = Build.VERSION.SDK_INT,
        )
    }
}
