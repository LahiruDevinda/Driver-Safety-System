package com.devinda.driverSafetySystem.ui.screen

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devinda.driverSafetySystem.faceAnalyzer.FaceAnalyzer
import com.devinda.driverSafetySystem.ui.TelemetryViewModel
import java.util.concurrent.Executors

@Composable
fun DmsCameraScreen(
    viewModel: TelemetryViewModel = viewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    val driverState by viewModel.driverState.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()

                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { analysis ->
                                analysis.setAnalyzer(
                                    cameraExecutor,
                                    FaceAnalyzer { updatedState ->
                                        // Feeds the ViewModel -> updates UI state & evaluates CommandController
                                        viewModel.onDriverStateUpdated(updatedState)
                                    }
                                )
                            }

                        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )
                        } catch (exc: Exception) {
                            exc.printStackTrace()
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                }
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        !driverState.faceDetected -> Color(0xCC2A2A2A)
                        driverState.isDrowsy -> Color(0xDDB00020)
                        driverState.isDistracted -> Color(0xDDFF8C00)
                        else -> Color(0xCC1E1E1E)
                    }
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (!driverState.faceDetected) {
                        Text(
                            text = "👁️ NO DRIVER DETECTED IN SIGHT",
                            color = Color.Yellow,
                            fontSize = 16.sp
                        )
                    } else {
                        Text(
                            text = when {
                                driverState.isDrowsy -> "⚠️ CRITICAL: DROWSINESS DETECTED!"
                                driverState.isDistracted -> "⚠️ WARNING: LOOKING AWAY FROM ROAD!"
                                else -> "✅ Driver Status: Attentive"
                            },
                            color = Color.White,
                            fontSize = 17.sp
                        )

                        Text(
                            text = "Angles: Pitch %.1f° | Yaw %.1f° | Roll %.1f°".format(
                                driverState.pitch, driverState.yaw, driverState.roll
                            ),
                            color = Color.LightGray,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        val leftEyePct = if (driverState.leftEyeProb >= 0f) driverState.leftEyeProb * 100f else 0f
                        val rightEyePct = if (driverState.rightEyeProb >= 0f) driverState.rightEyeProb * 100f else 0f

                        Text(
                            text = "Eyes Open: L %.0f%% | R %.0f%%".format(leftEyePct, rightEyePct),
                            color = Color.LightGray,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}