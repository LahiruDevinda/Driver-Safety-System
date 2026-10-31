package com.devinda.driverSafetySystem.util

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.devinda.driverSafetySystem.faceAnalyzer.DriverState
import com.devinda.driverSafetySystem.faceAnalyzer.FaceAnalyzer

@Composable
fun FaceAnalyze() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    var driverState by remember { mutableStateOf(DriverState()) }

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
                    val executor = ContextCompat.getMainExecutor(ctx)

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
                                    executor,
                                    FaceAnalyzer { updatedState ->
                                        driverState = updatedState
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
                    }, executor)

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
                    containerColor = if (driverState.isDrowsy || driverState.isDistracted) {
                        Color(0xCCB00020)
                    } else {
                        Color(0xCC1E1E1E)
                    }
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (!driverState.faceDetected) {
                        Text(
                            text = "NO DRIVER DETECTED",
                            color = Color.Yellow,
                            fontSize = 16.sp
                        )
                    } else {
                        Text(
                            text = if (driverState.isDrowsy) "⚠️ DROWSINESS DETECTED!"
                            else if (driverState.isDistracted) "⚠️ LOOKING AWAY!"
                            else "Status: Attentive",
                            color = Color.White,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Head Angles: Pitch %.1f° | Yaw %.1f° | Roll %.1f°".format(
                                driverState.pitch, driverState.yaw, driverState.roll
                            ),
                            color = Color.LightGray,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Eyes Open: Left %.0f%% | Right %.0f%%".format(
                                driverState.leftEyeProb * 100, driverState.rightEyeProb * 100
                            ),
                            color = Color.LightGray,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}