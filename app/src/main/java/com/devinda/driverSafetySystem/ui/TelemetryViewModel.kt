package com.devinda.driverSafetySystem.ui

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devinda.driverSafetySystem.comm.TelemetryBridge
import com.devinda.driverSafetySystem.model.Command
import com.devinda.driverSafetySystem.model.DriverState
import com.devinda.driverSafetySystem.model.TelemetryModel
import com.devinda.driverSafetySystem.model.telemetryModel.BluetoothTelemetryModel
import com.devinda.driverSafetySystem.model.telemetryModel.UsbTelemetryModel
import com.devinda.driverSafetySystem.model.telemetryModel.WifiTelemetryModel
import com.devinda.driverSafetySystem.util.AlertManager
import com.devinda.driverSafetySystem.util.CommandController
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TelemetryViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext: Context = getApplication<Application>().applicationContext

    // Transport models
    val usbModel: TelemetryModel by lazy {
        UsbTelemetryModel(appContext, baudRate = 115200)
    }
    val wifiModel: TelemetryModel by lazy {
        WifiTelemetryModel("192.168.4.1")
    }
    val bluetoothModel: TelemetryModel by lazy {
        val btManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        BluetoothTelemetryModel(btManager.adapter, targetDeviceName = "ESP32_Receiver")
    }

    // Hardware Bridge
    private val bridge = TelemetryBridge(bluetoothModel)

    // Alert and Command Management
    val alertManager = AlertManager(appContext)
    private val commandController = CommandController(
        bridge = bridge,
        alertManager = alertManager
    )

    // UI States
    private val _statusMessage = MutableStateFlow("Initializing...")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _driverState = MutableStateFlow(DriverState())
    val driverState: StateFlow<DriverState> = _driverState.asStateFlow()

    private val _liveTelemetry = MutableStateFlow("No incoming data")
    val liveTelemetry: StateFlow<String> = _liveTelemetry.asStateFlow()

    private var observationJob: Job? = null

    init {
        connect()
    }

    fun connect() {
        _statusMessage.value = "Connecting to telemetry..."
        bridge.connect { result ->
            result.onSuccess {
                _statusMessage.value = "Connected: $it"
                observeIncomingData()
            }
            result.onFailure {
                _statusMessage.value = "Error: ${it.message}"
            }
        }
    }

    fun switchTransport(model: TelemetryModel) {
        commandController.reset()
        bridge.switchProtocol(model)
        connect()
    }

    private fun observeIncomingData() {
        observationJob?.cancel()
        observationJob = viewModelScope.launch {
            bridge.incomingData.collect { rawLine ->
                _liveTelemetry.value = rawLine
                if (rawLine.startsWith("SENSOR_VAL:")) {
                    val value = rawLine.substringAfter(":").toIntOrNull()
                    // Handle sensor value
                }
            }
        }
    }

    fun onDriverStateUpdated(newState: DriverState) {
        _driverState.value = newState
        commandController.processDriverState(newState)
    }

    fun sendManualCommand(command: Command) {
        bridge.dispatchCommand(command) { result ->
            result.onSuccess { _statusMessage.value = "ACK: $it" }
            result.onFailure { _statusMessage.value = "Failed: ${it.message}" }
        }
    }

    @SuppressLint("EmptySuperCall")
    override fun onCleared() {
        super.onCleared()
        observationJob?.cancel()
        alertManager.release()
        commandController.reset()
        bridge.cleanup()
    }
}