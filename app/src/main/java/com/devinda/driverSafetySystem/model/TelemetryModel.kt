package com.devinda.driverSafetySystem.model

import kotlinx.coroutines.flow.SharedFlow

abstract class TelemetryModel {
        abstract val incomingDataStream: SharedFlow<String>

        abstract suspend fun connect(): Result<String>
        abstract suspend fun sendCommand(command: Command): Result<String>
        abstract fun disconnect()
}