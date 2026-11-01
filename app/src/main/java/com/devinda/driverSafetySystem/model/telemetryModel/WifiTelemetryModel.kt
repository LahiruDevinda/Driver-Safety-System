package com.devinda.driverSafetySystem.model.telemetryModel

import com.devinda.driverSafetySystem.model.Command
import com.devinda.driverSafetySystem.model.TelemetryModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class WifiTelemetryModel(
    private val ipAddress: String = "192.168.4.1",
    private val port: Int = 81
) : TelemetryModel() {

    private val _incomingDataStream = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val incomingDataStream: SharedFlow<String> = _incomingDataStream.asSharedFlow()

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null

    override suspend fun connect(): Result<String> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            val wsUrl = "ws://$ipAddress:$port"
            val request = Request.Builder().url(wsUrl).build()

            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    this@WifiTelemetryModel.webSocket = webSocket
                    if (continuation.isActive) {
                        continuation.resume(Result.success("Connected to Wi-Fi WebSocket at $wsUrl"))
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    _incomingDataStream.tryEmit(text.trim())
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    this@WifiTelemetryModel.webSocket = null
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(t))
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    this@WifiTelemetryModel.webSocket = null
                }
            }

            client.newWebSocket(request, listener)

            continuation.invokeOnCancellation {
                disconnect()
            }
        }
    }

    override suspend fun sendCommand(command: Command): Result<String> = withContext(Dispatchers.IO) {
        val ws = webSocket
            ?: return@withContext Result.failure(IllegalStateException("Wi-Fi WebSocket is not connected."))

        val sent = ws.send(command.payload)
        if (sent) {
            Result.success("Sent: ${command.payload}")
        } else {
            Result.failure(Exception("Failed to enqueue WebSocket frame."))
        }
    }

    override fun disconnect() {
        try {
            webSocket?.close(1000, "App disconnected")
        } catch (_: Exception) {
        } finally {
            webSocket = null
        }
    }
}