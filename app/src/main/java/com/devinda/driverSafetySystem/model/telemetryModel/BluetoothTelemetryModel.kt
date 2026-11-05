package com.devinda.driverSafetySystem.model.telemetryModel

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
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
    private val context: Context,
    private val adapter: BluetoothAdapter?,
    private val targetDeviceName: String = "ESP32_Receiver"
) : TelemetryModel() {

    // Standard SPP UUID
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val _incomingDataStream = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val incomingDataStream: SharedFlow<String> = _incomingDataStream.asSharedFlow()

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var reader: BufferedReader? = null
    private var listeningJob: Job? = null
    private val modelScope = CoroutineScope(Dispatchers.IO)

    fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect(): Result<String> = withContext(Dispatchers.IO) {
        if (!hasConnectPermission()) {
            return@withContext Result.failure(
                SecurityException("Need android.permission.BLUETOOTH_CONNECT permission.")
            )
        }

        if (adapter == null || !adapter.isEnabled) {
            return@withContext Result.failure(IllegalStateException("Bluetooth is disabled."))
        }

        try {

            val pairedDevices: Set<BluetoothDevice> = adapter.bondedDevices ?: emptySet()
            val device: BluetoothDevice = pairedDevices.find { it.name == targetDeviceName }
                ?: return@withContext Result.failure(
                    NoSuchElementException("Device '$targetDeviceName' not paired. Pair it first in Bluetooth Settings.")
                )

            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }

            val btSocket = try {
                device.createRfcommSocketToServiceRecord(sppUuid)
            } catch (e: Exception) {
                val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                method.invoke(device, 1) as BluetoothSocket
            }

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
            } catch (_: Exception) {
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
        } catch (_: Exception) {
        } finally {
            reader = null
            outputStream = null
            socket = null
        }
    }
}