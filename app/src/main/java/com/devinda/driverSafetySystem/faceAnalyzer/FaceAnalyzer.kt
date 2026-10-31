package com.devinda.driverSafetySystem.faceAnalyzer

import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlin.math.abs

class FaceAnalyzer(
    private val onDriverStateUpdated: ((DriverState) -> Unit)? = null
) : ImageAnalysis.Analyzer {

    // 1. Configure detector to enable classification (eye openness)
    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .build()

    private val detector: FaceDetector = FaceDetection.getClient(options)

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val inputImage = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )

            detector.process(inputImage)
                .addOnSuccessListener { faces ->
                    if (faces.isNotEmpty()) {
                        val face = faces.first()
                        processDriverFace(face)
                    } else {
                        onDriverStateUpdated?.invoke(DriverState(faceDetected = false))
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("FaceAnalyzer", "Face detection failed", e)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun processDriverFace(face: Face) {

        val pitch = face.headEulerAngleX
        val yaw = face.headEulerAngleY
        val roll = face.headEulerAngleZ
        val leftEyeOpenProb = face.leftEyeOpenProbability ?: -1f
        val rightEyeOpenProb = face.rightEyeOpenProbability ?: -1f

        val eyesClosed = (leftEyeOpenProb in 0.0f..0.35f) && (rightEyeOpenProb in 0.0f..0.35f)
        val lookingAway = abs(yaw) > 25.0f || abs(pitch) > 20.0f

        val state = DriverState(
            faceDetected = true,
            pitch = pitch,
            yaw = yaw,
            roll = roll,
            leftEyeProb = leftEyeOpenProb,
            rightEyeProb = rightEyeOpenProb,
            isDrowsy = eyesClosed,
            isDistracted = lookingAway
        )

        onDriverStateUpdated?.invoke(state)

        Log.d("DriverSafety", "Pitch: $pitch, Yaw: $yaw, LeftEye: $leftEyeOpenProb, RightEye: $rightEyeOpenProb")
    }
}

data class DriverState(
    val faceDetected: Boolean = false,
    val pitch: Float = 0f,
    val yaw: Float = 0f,
    val roll: Float = 0f,
    val leftEyeProb: Float = -1f,
    val rightEyeProb: Float = -1f,
    val isDrowsy: Boolean = false,
    val isDistracted: Boolean = false
)