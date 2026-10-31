package com.devinda.driverSafetySystem.faceAnalyzer

import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.devinda.driverSafetySystem.model.DriverState
import com.devinda.driverSafetySystem.util.DmsEvaluator
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

class FaceAnalyzer(
    private val evaluator: DmsEvaluator = DmsEvaluator(),
    private val onDriverStateUpdated: ((DriverState) -> Unit)? = null
) : ImageAnalysis.Analyzer {

    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
        .build()

    private val detector = FaceDetection.getClient(options)

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
                    val prominentFace = faces.firstOrNull()
                    if (prominentFace != null) {
                        val state = evaluator.evaluate(
                            faceDetected = true,
                            pitch = prominentFace.headEulerAngleX,
                            yaw = prominentFace.headEulerAngleY,
                            roll = prominentFace.headEulerAngleZ,
                            leftEyeProb = prominentFace.leftEyeOpenProbability ?: -1f,
                            rightEyeProb = prominentFace.rightEyeOpenProbability ?: -1f
                        )
                        onDriverStateUpdated?.invoke(state)
                    } else {
                        val emptyState = evaluator.evaluate(
                            faceDetected = false,
                            pitch = 0f, yaw = 0f, roll = 0f,
                            leftEyeProb = -1f, rightEyeProb = -1f
                        )
                        onDriverStateUpdated?.invoke(emptyState)
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
}