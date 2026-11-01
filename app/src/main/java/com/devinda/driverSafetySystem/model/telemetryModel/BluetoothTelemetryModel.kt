package com.devinda.driverSafetySystem.model.telemetryModel

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.devinda.driverSafetySystem.model.Command
import com.devinda.driverSafetySystem.model.TelemetryModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.UUID

class BluetoothTelemetryModel(
    private val adapter: BluetoothAdapter?,
    private val targetDeviceName: String = "ESP32_Receiver"
) : TelemetryModel() {

    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val _incomingDataStream = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val incomingDataStream: SharedFlow<String> = _incomingDataStream.asSharedFlow()

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var reader: BufferedReader? = null
    private var listeningJob: Job? = null
    private val modelScope = CoroutineScope(Dispatchers.IO)

    @SuppressLint("MissingPermission")
    override suspend fun connect(): Result<String> = withContext(Dispatchers.IO) {
        if (adapter == null || !adapter.isEnabled) {
            return@withContext Result.failure(IllegalStateException("Bluetooth is disabled."))
        }

        try {
            val device: BluetoothDevice = adapter.bondedDevices.find { it.name == targetDeviceName }
                ?: return@withContext Result.failure(
                    NoSuchElementException("Device '$targetDeviceName' not paired.")
                )

            adapter.cancelDiscovery()

            val btSocket = device.createRfcommSocketToServiceRecord(sppUuid)
            btSocket.connect()

            socket = btSocket
            outputStream = btSocket.outputStream
            reader = BufferedReader(InputStreamReader(btSocket.inputStream))

            startListening()

            Result.success("Connected to $targetDeviceName")
        } catch (e: Exception) {
            disconnect()
            Result.failure(e)
        }
    }

    private fun startListening() {
        listeningJob?.cancel()
        listeningJob = modelScope.launch {
            val bufferedReader = reader ?: return@launch
            try {
                while (isActive && socket?.isConnected == true) {
                    val line = bufferedReader.readLine() ?: break
                    if (line.isNotBlank()) {
                        _incomingDataStream.tryEmit(line.trim())
                    }
                }
            } catch (ignored: Exception) {
            }
        }
    }

    override suspend fun sendCommand(command: Command): Result<String> = withContext(Dispatchers.IO) {
        val stream = outputStream
        if (socket?.isConnected != true || stream == null) {
            return@withContext Result.failure(IllegalStateException("Bluetooth not connected."))
        }

        try {
            val payload = "${command.payload}\n"
            stream.write(payload.toByteArray(Charsets.UTF_8))
            stream.flush()
            Result.success("Sent: ${command.payload}")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun disconnect() {
        listeningJob?.cancel()
        listeningJob = null
        try {
            reader?.close()
            outputStream?.close()
            socket?.close()
        } catch (ignored: Exception) {
        } finally {
            reader = null
            outputStream = null
            socket = null
        }
    }
}