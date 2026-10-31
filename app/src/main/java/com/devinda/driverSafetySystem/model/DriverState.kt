package com.devinda.driverSafetySystem.model

data class DriverState (
    val faceDetected: Boolean = false,
    val pitch: Float = 0f,
    val yaw: Float = 0f,
    val roll: Float = 0f,
    val leftEyeProb: Float = -1f,
    val rightEyeProb: Float = -1f,
    val isDrowsy: Boolean = false,
    val isDistracted: Boolean = false
)