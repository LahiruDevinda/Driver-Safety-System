package com.devinda.driverSafetySystem.util

import com.devinda.driverSafetySystem.model.DriverState
import kotlin.math.abs

class DmsEvaluator(
    private val drowsinessThresholdMs: Long = 1500L,
    private val distractionThresholdMs: Long = 2000L
) {
    private var fatigueStartTime: Long = 0L
    private var distractionStartTime: Long = 0L

    fun evaluate(
        faceDetected: Boolean,
        pitch: Float,
        yaw: Float,
        roll: Float,
        leftEyeProb: Float,
        rightEyeProb: Float
    ): DriverState {
        if (!faceDetected) {
            resetTimers()
            return DriverState(faceDetected = false, isDistracted = true)
        }

        val currentTime = System.currentTimeMillis()

        val eyesClosedInstant = (leftEyeProb in 0.0f..0.30f) && (rightEyeProb in 0.0f..0.30f)
        val headDroppedInstant = pitch < -20.0f // Chin dropped toward chest (nodding/slumping)
        val lookingAwayInstant = abs(yaw) > 28.0f || pitch > 25.0f // Turned or looking upward

        var isDrowsyConfirmed = false
        if (eyesClosedInstant || headDroppedInstant) {
            if (fatigueStartTime == 0L) fatigueStartTime = currentTime
            if (currentTime - fatigueStartTime >= drowsinessThresholdMs) {
                isDrowsyConfirmed = true
            }
        } else {
            fatigueStartTime = 0L
        }

        var isDistractedConfirmed = false
        if (lookingAwayInstant) {
            if (distractionStartTime == 0L) distractionStartTime = currentTime
            if (currentTime - distractionStartTime >= distractionThresholdMs) {
                isDistractedConfirmed = true
            }
        } else {
            distractionStartTime = 0L
        }

        return DriverState(
            faceDetected = true,
            pitch = pitch,
            yaw = yaw,
            roll = roll,
            leftEyeProb = leftEyeProb,
            rightEyeProb = rightEyeProb,
            isDrowsy = isDrowsyConfirmed,
            isDistracted = isDistractedConfirmed
        )
    }

    private fun resetTimers() {
        fatigueStartTime = 0L
        distractionStartTime = 0L
    }
}