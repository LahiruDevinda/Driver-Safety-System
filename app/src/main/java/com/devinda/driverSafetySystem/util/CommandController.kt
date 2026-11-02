package com.devinda.driverSafetySystem.util

import android.Manifest
import androidx.annotation.RequiresPermission
import com.devinda.driverSafetySystem.comm.TelemetryBridge
import com.devinda.driverSafetySystem.model.Command
import com.devinda.driverSafetySystem.model.DriverState
import kotlin.math.abs

class CommandController(
    private val bridge: TelemetryBridge,
    private val alertManager: AlertManager,
    private val softWarningThresholdMs: Long = 500L,
    private val warningThresholdMs: Long = 1500L,
    private val criticalThresholdMs: Long = 3000L,
) {
    private var eyesClosedStartTime: Long = 0L
    private var drowsinessStartTime: Long = 0L
    private var headSlumpStartTime: Long = 0L
    private var headTurnStartTime: Long = 0L
    private var faceLostStartTime: Long = 0L

    private var hasTriggeredWarningAlert = false
    private var hasTriggeredCriticalAlert = false

    private var lastDispatchedCommand: Command? = null

    @RequiresPermission(Manifest.permission.VIBRATE)
    fun processDriverState(state: DriverState) {
        val currentTime = System.currentTimeMillis()

        if (!state.faceDetected) {
            if (faceLostStartTime == 0L) faceLostStartTime = currentTime
            val absentDuration = currentTime - faceLostStartTime

            val mode = if (absentDuration >= criticalThresholdMs) {
                Command.WARNING_MODE
            } else {
                if (!hasTriggeredWarningAlert) {
                    alertManager.distractedWarning()
                    hasTriggeredWarningAlert = true
                }
                Command.WARNING_MODE
            }
            dispatchDebounced(mode)
            return
        } else {
            faceLostStartTime = 0L
        }

        val isHeadDrooping = state.pitch < -18.0f
        val isHeadTurnedOrTilted = abs(state.yaw) > 28.0f || abs(state.roll) > 22.0f

        val headSlumpDuration = if (isHeadDrooping) {
            if (headSlumpStartTime == 0L) headSlumpStartTime = currentTime
            currentTime - headSlumpStartTime
        } else {
            headSlumpStartTime = 0L
            0L
        }

        val headTurnDuration = if (isHeadTurnedOrTilted || state.isDistracted) {
            if (headTurnStartTime == 0L) headTurnStartTime = currentTime
            currentTime - headTurnStartTime
        } else {
            headTurnStartTime = 0L
            0L
        }

        val validLeft = if (state.leftEyeProb >= 0f) state.leftEyeProb else 1.0f
        val validRight = if (state.rightEyeProb >= 0f) state.rightEyeProb else 1.0f
        val avgEyeProb = (validLeft + validRight) / 2.0f

        val targetMode = when {

            headSlumpDuration >= criticalThresholdMs -> {
                if (!hasTriggeredCriticalAlert) {
                    alertManager.criticalWarning()
                    hasTriggeredCriticalAlert = true
                }
                Command.CRITICAL_MODE
            }

            avgEyeProb < 0.40f -> {
                drowsinessStartTime = 0L
                if (eyesClosedStartTime == 0L) eyesClosedStartTime = currentTime
                val closedDuration = currentTime - eyesClosedStartTime

                when {
                    closedDuration >= criticalThresholdMs -> {
                        if (!hasTriggeredCriticalAlert) {
                            alertManager.criticalWarning()
                            hasTriggeredCriticalAlert = true
                        }
                        Command.CRITICAL_MODE
                    }
                    closedDuration >= warningThresholdMs -> {
                        if (!hasTriggeredWarningAlert) {
                            alertManager.distractedWarning()
                            hasTriggeredWarningAlert = true
                        }
                        Command.WARNING_MODE
                    }
                    else -> Command.SAFE_MODE
                }
            }

            headTurnDuration >= warningThresholdMs -> {
                if (!hasTriggeredWarningAlert) {
                    alertManager.distractedWarning()
                    hasTriggeredWarningAlert = true
                }
                Command.WARNING_MODE
            }

            avgEyeProb in 0.40f..0.70f || headSlumpDuration >= 1000L -> {
                eyesClosedStartTime = 0L
                if (drowsinessStartTime == 0L) drowsinessStartTime = currentTime
                val drowsyDuration = currentTime - drowsinessStartTime

                when {
                    drowsyDuration >= warningThresholdMs || headSlumpDuration >= warningThresholdMs -> {
                        if (!hasTriggeredWarningAlert) {
                            alertManager.sleepyWarning()
                            hasTriggeredWarningAlert = true
                        }
                        Command.WARNING_MODE
                    }
                    drowsyDuration >= softWarningThresholdMs || headSlumpDuration >= softWarningThresholdMs -> {
                        Command.WARNING_MODE
                    }
                    else -> Command.SAFE_MODE
                }
            }

            else -> {

                eyesClosedStartTime = 0L
                drowsinessStartTime = 0L
                headSlumpStartTime = 0L
                headTurnStartTime = 0L

                if (hasTriggeredWarningAlert || hasTriggeredCriticalAlert) {
                    alertManager.stopAllAlerts()
                    hasTriggeredWarningAlert = false
                    hasTriggeredCriticalAlert = false
                }

                Command.SAFE_MODE
            }
        }

        dispatchDebounced(targetMode)
    }

    private fun dispatchDebounced(command: Command) {
        if (command != lastDispatchedCommand) {
            lastDispatchedCommand = command
            bridge.dispatchCommand(command)
        }
    }


    @RequiresPermission(Manifest.permission.VIBRATE)
    fun reset() {
        eyesClosedStartTime = 0L
        drowsinessStartTime = 0L
        headSlumpStartTime = 0L
        headTurnStartTime = 0L
        faceLostStartTime = 0L
        hasTriggeredWarningAlert = false
        hasTriggeredCriticalAlert = false
        lastDispatchedCommand = null
        alertManager.stopAllAlerts()
    }
}