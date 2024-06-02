import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.YuvImage
import android.media.Image
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import de.yanneckreiss.mlkittutorial.util.LicenseTextDetector
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalGetImage::class)
@Composable
fun CameraPreview(
    roi: Rect,
    modifier: Modifier = Modifier, onTextRecognized: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = context as LifecycleOwner
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    val textRecognizer = remember {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

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

    LaunchedEffect(key1 = true) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        Box(modifier = modifier.fillMaxSize()) {
            AndroidView(
                factory = { context ->
                    val previewView = PreviewView(context)
                    val preview = Preview.Builder().build()
                    val selector =
                        CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK)
                            .build()
                    preview.setSurfaceProvider(previewView.surfaceProvider)

                    val imageAnalyzer = ImageAnalysis.Builder().build().also {
                        it.setAnalyzer(
                            ContextCompat.getMainExecutor(context)
                        ) { imageProxy ->
                            val mediaImage = imageProxy.image
                            if (mediaImage != null) {
                                val croppedBitmap = cropImage(
                                    mediaImage, imageProxy.imageInfo.rotationDegrees, roi
                                )
                                val image = InputImage.fromBitmap(croppedBitmap, 0)

                                textRecognizer.process(image).addOnSuccessListener { visionText ->
                                    visionText.textBlocks.forEach { block ->
                                        block.lines.forEach { line ->
                                            LicenseTextDetector().textDetector(line.text) { type, number ->
                                                Log.d("carNumberr", "type: $type, number: $number")
                                            }
                                        }
                                    }
                                    onTextRecognized(visionText.text)
                                }.addOnFailureListener {
                                    // Handle the error
                                }.addOnCompleteListener {
                                    imageProxy.close()
                                }
                            }
                        }
                    }

                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner, selector, preview, imageAnalyzer
                        )
                    } catch (exc: Exception) {
                        // Handle exception
                    }
                    previewView
                }, modifier = Modifier.fillMaxSize()
            )
        }
    } else {
        Text(text = "Camera permission is required to use this feature.")
    }
}

fun cropImage(image: Image, rotationDegrees: Int, roi: Rect): Bitmap {
    val yBuffer = image.planes[0].buffer // Y
    val uBuffer = image.planes[1].buffer // U
    val vBuffer = image.planes[2].buffer // V

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)

    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = YuvImage(
        nv21, ImageFormat.NV21, image.width, image.height, null
    )
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(android.graphics.Rect(0, 0, image.width, image.height), 100, out)
    val yuvBytes = out.toByteArray()
    val bitmap = BitmapFactory.decodeByteArray(yuvBytes, 0, yuvBytes.size)

    val matrix = Matrix()
    matrix.postRotate(rotationDegrees.toFloat())

    val scaledBitmap = Bitmap.createScaledBitmap(bitmap, bitmap.width, bitmap.height, true)
    val rotatedBitmap = Bitmap.createBitmap(
        scaledBitmap, 0, 0, scaledBitmap.width, scaledBitmap.height, matrix, true
    )

    val cropLeft = (roi.left * rotatedBitmap.width).toInt()
    val cropTop = (roi.top * rotatedBitmap.height).toInt()
    val cropWidth = (roi.width * rotatedBitmap.width).toInt()
    val cropHeight = (roi.height * rotatedBitmap.height).toInt()

    return Bitmap.createBitmap(rotatedBitmap, cropLeft, cropTop, cropWidth, cropHeight)
}

@Composable
fun OverlayFrame(
    roi: Rect,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        val rectLeft = roi.left * canvasWidth
        val rectTop = roi.top * canvasHeight
        val rectWidth = roi.width * canvasWidth
        val rectHeight = roi.height * canvasHeight

        // Draw the darkened areas
        drawRect(
            color = Color.Black.copy(alpha = 0.3f),
            topLeft = Offset(0f, 0f),
            size = Size(canvasWidth, rectTop)
        )
        drawRect(
            color = Color.Black.copy(alpha = 0.3f),
            topLeft = Offset(0f, rectTop + rectHeight),
            size = Size(
                canvasWidth, canvasHeight - rectTop - rectHeight
            )
        )
        drawRect(
            color = Color.Black.copy(alpha = 0.3f),
            topLeft = Offset(0f, rectTop),
            size = Size(rectLeft, rectHeight)
        )
        drawRect(
            color = Color.Black.copy(alpha = 0.3f),
            topLeft = Offset(rectLeft + rectWidth, rectTop),
            size = Size(canvasWidth - rectLeft - rectWidth, rectHeight)
        )

        // Draw the frame
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(rectLeft, rectTop),
            size = Size(rectWidth, rectHeight),
            style = Stroke(width = 4.dp.toPx()),
            cornerRadius = CornerRadius(5.dp.toPx())
        )
    }
}

@Composable
fun LicensePlateScannerScreen1(modifier: Modifier = Modifier) {
    var recognizedText by remember { mutableStateOf("") }

    val roi = Rect(
        left = 0.05f, top = 0.5f, right = 0.95f, bottom = 0.6f
    )

    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreview(
            roi = roi,
            modifier = Modifier.fillMaxSize(),
            onTextRecognized = { text ->
                recognizedText = text
            }
        )


        OverlayFrame(roi)
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
}
