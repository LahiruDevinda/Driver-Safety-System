package com.devinda.driverSafetySystem.model.telemetryModel

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.devinda.driverSafetySystem.model.Command
import com.devinda.driverSafetySystem.model.TelemetryModel
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.resume

class UsbTelemetryModel(
    private val context: Context,
    private val baudRate: Int = 115200
) : TelemetryModel() {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val _incomingDataStream = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val incomingDataStream: SharedFlow<String> = _incomingDataStream.asSharedFlow()

    private var serialPort: UsbSerialPort? = null
    private var listeningJob: Job? = null
    private val modelScope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val ACTION_USB_PERMISSION = "com.devinda.driverSafetySystem.USB_PERMISSION"
    }

    override suspend fun connect(): Result<String> = withContext(Dispatchers.IO) {
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (drivers.isEmpty()) {
            return@withContext Result.failure(IOException("No USB devices detected."))
        }

        val driver = drivers[0]
        if (!usbManager.hasPermission(driver.device)) {
            val granted = requestUsbPermission(driver.device)
            if (!granted) {
                return@withContext Result.failure(SecurityException("USB permission denied."))
            }
        }

        try {
            val usbConnection = usbManager.openDevice(driver.device)
                ?: return@withContext Result.failure(IOException("Failed to open USB device."))

            val port = driver.ports[0]
            port.open(usbConnection)
            port.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            port.dtr = true
            port.rts = true

            serialPort = port
            startListening()

            Result.success("Connected via USB")
        } catch (e: Exception) {
            disconnect()
            Result.failure(e)
        }
    }

    private fun startListening() {
        listeningJob?.cancel()
        listeningJob = modelScope.launch {
            val port = serialPort ?: return@launch
            val buffer = ByteArray(256)
            val lineBuilder = StringBuilder()

            while (isActive && port.isOpen) {
                try {
                    val bytesRead = port.read(buffer, 200)
                    if (bytesRead > 0) {
                        val text = String(buffer, 0, bytesRead, Charsets.UTF_8)
                        lineBuilder.append(text)

                        var newlineIndex: Int
                        while (lineBuilder.indexOf("\n").also { newlineIndex = it } >= 0) {
                            val line = lineBuilder.substring(0, newlineIndex).trim()
                            lineBuilder.delete(0, newlineIndex + 1)
                            if (line.isNotEmpty()) {
                                _incomingDataStream.tryEmit(line)
                            }
                        }
                    }
                } catch (ignored: Exception) {
                    break
                }
            }
        }
    }

    override suspend fun sendCommand(command: Command): Result<String> = withContext(Dispatchers.IO) {
        val port = serialPort
        if (port == null || !port.isOpen) {
            return@withContext Result.failure(IllegalStateException("USB port closed."))
        }

        try {
            val payload = "${command.payload}\n".toByteArray(Charsets.UTF_8)
            port.write(payload, 1000)
            Result.success("Sent: ${command.payload}")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun disconnect() {
        listeningJob?.cancel()
        listeningJob = null
        try {
            serialPort?.close()
        } catch (ignored: Exception) {
        } finally {
            serialPort = null
        }
    }

    private suspend fun requestUsbPermission(device: UsbDevice): Boolean =
        suspendCancellableCoroutine { continuation ->
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val permissionIntent = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), flags)

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    if (intent?.action == ACTION_USB_PERMISSION) {
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        try { context.unregisterReceiver(this) } catch (ignored: Exception) {}
                        if (continuation.isActive) continuation.resume(granted)
                    }
                }
            }

            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(ACTION_USB_PERMISSION),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )

            usbManager.requestPermission(device, permissionIntent)
            continuation.invokeOnCancellation {
                try { context.unregisterReceiver(receiver) } catch (ignored: Exception) {}
            }
        }
}