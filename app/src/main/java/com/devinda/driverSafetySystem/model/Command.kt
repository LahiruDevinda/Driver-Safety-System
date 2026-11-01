package com.devinda.driverSafetySystem.model

enum class Command(val payload: String) {
    VIBRATE_MOTOR("VibrateMotor_active"),
    ACCELERATE_ENABLE("Accelerate_enable"),
    BRAKE_ENABLE("Brake_enable"),
    PING("PING"),
    SAFE_MODE("SAFETY_MODE"),
    WARNING_MODE("WARNING_MODE"),
    CRITICAL_MODE("CRITICAL_MODE")
}