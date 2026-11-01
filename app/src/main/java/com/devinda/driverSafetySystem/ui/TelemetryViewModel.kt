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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TelemetryViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext: Context = getApplication<Application>().applicationContext
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

    private val bridge = TelemetryBridge(usbModel)
    private val _statusMessage = MutableStateFlow("Initializing...")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    init {
        connect()
    }

    fun connect() {
        _statusMessage.value = "Connecting to telemetry..."
        bridge.connect { result ->
            result.onSuccess { _statusMessage.value = "Connected: $it" }
            result.onFailure { _statusMessage.value = "Error: ${it.message}" }
        }
    }

    fun switchTransport(model: TelemetryModel) {
        bridge.switchProtocol(model)
        connect()
    }

    fun sendCommand(command: Command) {
        bridge.dispatchCommand(command) { result ->
            result.onSuccess { _statusMessage.value = "ACK: $it" }
            result.onFailure { _statusMessage.value = "Failed: ${it.message}" }
        }
    }

    fun onDriverStateEvaluated(state: DriverState) {
        bridge.handleDriverState(state)
    }

    @SuppressLint("EmptySuperCall")
    override fun onCleared() {
        super.onCleared()
        bridge.cleanup()
    }
    private val _liveTelemetry = MutableStateFlow("No incoming data")
    val liveTelemetry: StateFlow<String> = _liveTelemetry.asStateFlow()

    init {
        connect()
        observeIncomingData()
    }

    private fun observeIncomingData() {
        viewModelScope.launch {
            bridge.incomingData.collect { rawData ->
                _liveTelemetry.value = rawData
            }
        }
    }
}
