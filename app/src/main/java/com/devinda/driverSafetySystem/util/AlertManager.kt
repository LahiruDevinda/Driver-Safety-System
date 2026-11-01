package com.devinda.driverSafetySystem.util

import android.Manifest
import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresPermission
import com.devinda.driverSafetySystem.R

class AlertManager(private val context: Context) {

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(audioAttributes)
        .build()

    private val sleepySoundId: Int = soundPool.load(context, R.raw.sleepy_chime, 1)
    private val distractedSoundId: Int = soundPool.load(context, R.raw.distracted_alert, 1)
    private val criticalSoundId: Int = soundPool.load(context, R.raw.critical_alarm, 1)

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private var lastWarningTimestamp = 0L
    private val alertCooldownMs = 1000L

    @RequiresPermission(Manifest.permission.VIBRATE)
    fun sleepyWarning() {
        val now = System.currentTimeMillis()
        if (now - lastWarningTimestamp < alertCooldownMs) return
        lastWarningTimestamp = now

        soundPool.play(sleepySoundId, 1.0f, 1.0f, 1, 0, 1.0f)
        vibrate(250)
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    fun distractedWarning() {
        val now = System.currentTimeMillis()
        if (now - lastWarningTimestamp < alertCooldownMs) return
        lastWarningTimestamp = now

        soundPool.play(distractedSoundId, 1.0f, 1.0f, 1, 0, 1.0f)
        vibrate(200)
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    fun criticalWarning() {
        soundPool.play(criticalSoundId, 1.0f, 1.0f, 2, 0, 1.0f)
        vibrate(600)
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun vibrate(durationMs: Long) {
        try {
            vibrator?.vibrate(
                VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } catch (_: Exception) {}
    }

    fun release() {
        soundPool.release()
    }
}