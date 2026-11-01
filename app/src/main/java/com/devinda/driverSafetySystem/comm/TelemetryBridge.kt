package com.devinda.driverSafetySystem.comm

import com.devinda.driverSafetySystem.model.Command
import com.devinda.driverSafetySystem.model.DriverState
import com.devinda.driverSafetySystem.model.TelemetryModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

class TelemetryBridge(
    private var activeModel: TelemetryModel
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val incomingData: SharedFlow<String> get() = activeModel.incomingDataStream

    fun switchProtocol(newModel: TelemetryModel) {
        activeModel.disconnect()
        activeModel = newModel
    }

    fun connect(onResult: (Result<String>) -> Unit) {
        scope.launch {
            val res = activeModel.connect()
            onResult(res)
        }
    }

    fun handleDriverState(state: DriverState) {
        when {

            !state.faceDetected -> {
                dispatchCommand(Command.BRAKE_ENABLE)
            }

            state.isDrowsy || state.isDistracted -> {
                dispatchCommand(Command.VIBRATE_MOTOR)
            }

            else -> {
                dispatchCommand(Command.ACCELERATE_ENABLE)
            }
        }
    }

    fun dispatchCommand(command: Command, onResponse: ((Result<String>) -> Unit)? = null) {
        scope.launch {
            val result = activeModel.sendCommand(command)
            onResponse?.invoke(result)
        }
    }

    fun cleanup() {
        activeModel.disconnect()
    }
}