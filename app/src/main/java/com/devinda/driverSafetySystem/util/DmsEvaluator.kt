package com.devinda.driverSafetySystem.util

import com.devinda.driverSafetySystem.model.DriverState
import kotlin.math.abs

class DmsEvaluator(
    private val drowsinessThresholdMs: Long = 1500L,
    private val distractionThresholdMs: Long = 2000L,
    private val faceLostThresholdMs: Long = 2500L,
    private val eyeOpenThreshold: Float = 0.30f,
    private val headPitchDownThreshold: Float = -20.0f,
    private val headPitchUpThreshold: Float = 25.0f,
    private val headYawThreshold: Float = 28.0f
) {
    private var fatigueStartTime: Long = 0L
    private var distractionStartTime: Long = 0L
    private var faceLostStartTime: Long = 0L

    fun evaluate(
        faceDetected: Boolean,
        pitch: Float = 0f,
        yaw: Float = 0f,
        roll: Float = 0f,
        leftEyeProb: Float = -1f,
        rightEyeProb: Float = -1f
    ): DriverState {
        val currentTime = System.currentTimeMillis()

        if (!faceDetected) {
            if (faceLostStartTime == 0L) faceLostStartTime = currentTime
            val isSustainedLost = (currentTime - faceLostStartTime) >= faceLostThresholdMs

            return DriverState(
                faceDetected = false,
                isDistracted = isSustainedLost
            )
        } else {
            faceLostStartTime = 0L
        }

        val eyesClosedInstant = (leftEyeProb in 0.0f..eyeOpenThreshold) &&
                (rightEyeProb in 0.0f..eyeOpenThreshold)
        val headDroppedInstant = pitch < headPitchDownThreshold

        var isDrowsyConfirmed = false
        if (eyesClosedInstant || headDroppedInstant) {
            if (fatigueStartTime == 0L) fatigueStartTime = currentTime
            if (currentTime - fatigueStartTime >= drowsinessThresholdMs) {
                isDrowsyConfirmed = true
            }
        } else {
            fatigueStartTime = 0L
        }

        val lookingAwayInstant = abs(yaw) > headYawThreshold || pitch > headPitchUpThreshold

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
}