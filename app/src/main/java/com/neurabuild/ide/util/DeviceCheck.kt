package com.neurabuild.ide.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build

data class DeviceReport(
    val abiOk: Boolean,
    val abis: String,
    val freeMb: Long,
    val storageOk: Boolean,
    val totalRamMb: Long,
    val lowRam: Boolean
)

object DeviceCheck {
    fun report(context: Context): DeviceReport {
        val abiOk = Build.SUPPORTED_ABIS.contains("arm64-v8a")
        val freeMb = context.filesDir.freeSpace / (1024 * 1024)
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val totalMb = info.totalMem / (1024 * 1024)
        return DeviceReport(
            abiOk = abiOk,
            abis = Build.SUPPORTED_ABIS.joinToString(","),
            freeMb = freeMb,
            storageOk = freeMb >= 200,
            totalRamMb = totalMb,
            lowRam = totalMb < 3800
        )
    }
}
