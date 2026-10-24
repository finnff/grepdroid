package com.grepdroid.data

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager

/**
 * Decides how hard transcription may push the phone and opens [SpeechTranscriber]s.
 * Parakeet keeps every core it gets busy, so on battery it is held to two threads;
 * on the charger it gets four.
 */
class SpeechEngine(private val context: Context, val model: SpeechModel) {

    fun preferredThreads(): Int {
        val battery = context.getSystemService(BatteryManager::class.java)
        return if (battery?.isCharging == true) THREADS_CHARGING else THREADS_BATTERY
    }

    /** True once the system reports severe thermal throttling (API 29+). */
    fun isOverheating(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val power = context.getSystemService(PowerManager::class.java) ?: return false
        return power.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
    }

    fun open(threads: Int) = SpeechTranscriber(context, model, threads)

    companion object {
        const val THREADS_BATTERY = 2
        const val THREADS_CHARGING = 4
    }
}
