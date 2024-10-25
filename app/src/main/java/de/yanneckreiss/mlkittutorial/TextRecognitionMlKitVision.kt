package de.yanneckreiss.mlkittutorial

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Rect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

@Composable
fun LicensePlateScannerScreen2(modifier: Modifier = Modifier) {
    var recognizedText by remember { mutableStateOf("") }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }






    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        Box(modifier = Modifier.fillMaxSize()) {
            CameraPreviewWithOverlay(
                modifier = Modifier.fillMaxSize(),
                onTextRecognized = { text ->
                    recognizedText = text
                }
            )
            OverlayFrame()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    text = "Recognized Text: $recognizedText",
                    style = MaterialTheme.typography.body1,
                    modifier = Modifier
                        .background(Color.White)
                        .padding(8.dp)
                )
            }
        }
    } else {
        Text(text = "Camera permission is required to use this feature.")
    }
}

@Composable
fun CameraPreviewWithOverlay(
    modifier: Modifier = Modifier,
    onTextRecognized: (String) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    AndroidView(
        factory = { context ->
            val previewView = PreviewView(context).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            val cameraController = LifecycleCameraController(context).apply {
                bindToLifecycle(lifecycleOwner)
                cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                setImageAnalysisAnalyzer(ContextCompat.getMainExecutor(context),
                    MlKitAnalyzer(
                        listOf(textRecognizer),
                        ImageAnalysis.COORDINATE_SYSTEM_ORIGINAL,
                        ContextCompat.getMainExecutor(context)
                    ) { result ->
                        val visionText = result.getValue(textRecognizer)
                        if (visionText != null) {
                            val filteredText = filterTextByROI(visionText.text, previewView)
                            onTextRecognized(filteredText)
                        }
                    })
            }

            previewView.controller = cameraController

            previewView
        },
        modifier = modifier
    )
}

@Composable
fun OverlayFrame(modifier: Modifier = Modifier) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val rectWidth = canvasWidth * 0.8f
        val rectHeight = canvasHeight * 0.2f
        val rectLeft = (canvasWidth - rectWidth) / 2
        val rectTop = (canvasHeight - rectHeight) / 2

        // Draw the darkened areas outside the frame
        drawRect(
            color = Color.Black.copy(alpha = 0.5f),
            topLeft = Offset(0f, 0f),
            size = androidx.compose.ui.geometry.Size(canvasWidth, rectTop)
        )
        drawRect(
            color = Color.Black.copy(alpha = 0.5f),
            topLeft = Offset(0f, rectTop + rectHeight),
            size = androidx.compose.ui.geometry.Size(
                canvasWidth,
                canvasHeight - rectTop - rectHeight
            )
        )
        drawRect(
            color = Color.Black.copy(alpha = 0.5f),
            topLeft = Offset(0f, rectTop),
            size = androidx.compose.ui.geometry.Size(rectLeft, rectHeight)
        )
        drawRect(
            color = Color.Black.copy(alpha = 0.5f),
            topLeft = Offset(rectLeft + rectWidth, rectTop),
            size = androidx.compose.ui.geometry.Size(canvasWidth - rectLeft - rectWidth, rectHeight)
        )

        // Draw the frame
        drawRoundRect(
            color = Color.Red,
            topLeft = Offset(rectLeft, rectTop),
            size = androidx.compose.ui.geometry.Size(rectWidth, rectHeight),
            style = Stroke(width = 4.dp.toPx()),
            cornerRadius = CornerRadius(16.dp.toPx())
        )
    }
}

fun filterTextByROI(recognizedText: String, previewView: androidx.camera.view.PreviewView): String {
    // Get the size of the previewView
    val width = previewView.width
    val height = previewView.height

    // Define the ROI in the coordinates of the previewView
    val rectWidth = width * 0.8f
    val rectHeight = height * 0.2f
    val rectLeft = (width - rectWidth) / 2
    val rectTop = (height - rectHeight) / 2
    val roi = Rect(
        rectLeft.toInt(),
        rectTop.toInt(),
        (rectLeft + rectWidth).toInt(),
        (rectTop + rectHeight).toInt()
    )

    // Filter the recognized text to include only those within the ROI
    val lines = recognizedText.split("\n")
    val filteredText = StringBuilder()

    for (line in lines) {
        val words = line.split(" ")
        for (word in words) {
            // Here we assume each word is represented by its bounding box in the previewView coordinates
            // In a real implementation, you would need to map the bounding boxes of the recognized text to the coordinates of the previewView
            val wordBoundingBox = getWordBoundingBox(word) // Placeholder function

            if (roi.contains(wordBoundingBox)) {
                filteredText.append(word).append(" ")
            }
        }
        filteredText.append("\n")
    }

    return filteredText.toString().trim()
}

fun getWordBoundingBox(word: String): Rect {
    // Placeholder function to simulate getting the bounding box of a word
    // In a real implementation, you would use the bounding box data provided by the ML Kit text recognition result
    return Rect()
}
