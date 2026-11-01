package com.devinda.driverSafetySystem.model

enum class Command(val payload: String) {
    VIBRATE_MOTOR("VibrateMotor_active"),
    ACCELERATE_ENABLE("Accelerate_enable"),
    BRAKE_ENABLE("Brake_enable"),
    PING("PING")
}