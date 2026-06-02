package com.example

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun LiveDetectionScreen(
    processor: OpenCVProcessor, 
    isReady: Boolean,
    onFrameUpdate: (Bitmap) -> Unit,
    cvParams: CvParams,
    engineOn: Boolean
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    if (cameraPermissionState.status.isGranted) {
        if (!engineOn) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Turn on OpenCV Engine to use live camera.", style = MaterialTheme.typography.titleMedium)
            }
        } else if (!isReady) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
                Text("Initializing OpenCV models...")
            }
        } else {
            CameraView(processor, onFrameUpdate, cvParams, engineOn)
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Camera permission is required for live detection.")
            Spacer(Modifier.height(8.dp))
            Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                Text("Grant Permission")
            }
        }
    }
}

@Composable
fun CameraView(processor: OpenCVProcessor, onFrameUpdate: (Bitmap) -> Unit, cvParams: CvParams, engineOn: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var detectionMode by remember { mutableStateOf("Faces") }
    var resultBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    val imageAnalysis = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
    }

    val currentEngineOn by rememberUpdatedState(engineOn)
    val currentDetectionMode by rememberUpdatedState(detectionMode)
    val currentCvParams by rememberUpdatedState(cvParams)
    val currentLensFacing by rememberUpdatedState(lensFacing)

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            try {
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()
            } catch (e: Exception) {
                Log.e("CameraView", "Failed to unbind camera", e)
            }
        }
    }

    LaunchedEffect(currentEngineOn, currentDetectionMode, currentCvParams, currentLensFacing) {
        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
            try {
                val bitmap = imageProxy.toBitmap()
                val rotationMatrix = Matrix()
                val rotationDegrees = imageProxy.imageInfo.rotationDegrees.toFloat()
                
                rotationMatrix.postRotate(rotationDegrees)
                if (currentLensFacing == CameraSelector.LENS_FACING_FRONT) {
                    // Mirror horizontally after rotation
                    rotationMatrix.postScale(-1f, 1f)
                }
                
                val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, rotationMatrix, true)
                val mutableRotated = rotatedBitmap.copy(Bitmap.Config.ARGB_8888, true)
                onFrameUpdate(mutableRotated)
                
                val processed = if (!currentEngineOn) mutableRotated else {
                    val defaultParams = io.ktor.http.Parameters.Empty
                    val advancedProcessed = processAdvancedOpenCV(mutableRotated, currentDetectionMode.lowercase(), defaultParams)
                    if (advancedProcessed != null) {
                        advancedProcessed
                    } else {
                        when (currentDetectionMode) {
                            "Faces" -> processor.detectFaces(mutableRotated, currentCvParams.scaleFactor.toDouble(), currentCvParams.minNeighbors, currentCvParams.minSize)
                            "People" -> processor.detectPeople(mutableRotated, currentCvParams.scaleFactor.toDouble())
                            "Grayscale" -> processor.toGrayscale(mutableRotated)
                            "Blur" -> processor.applyBlur(mutableRotated, currentCvParams.blurSize)
                            "Edges" -> processor.detectEdges(mutableRotated, currentCvParams.cannyT1.toDouble(), currentCvParams.cannyT2.toDouble())
                            else -> mutableRotated
                        }
                    }
                }
                
                resultBitmap = processed
                
                if (bitmap != rotatedBitmap) bitmap.recycle()
                if (rotatedBitmap != mutableRotated) rotatedBitmap.recycle()
            } catch (e: Exception) {
                Log.e("CameraView", "Analysis error", e)
            } finally {
                imageProxy.close()
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        val allModes = listOf("Faces", "People", "Grayscale", "Blur", "Edges", "resize", "cvtColor", "flip", "rotate", "threshold", "adaptiveThreshold", "gaussianBlur", "medianBlur", "bilateralFilter", "sobel", "laplacian", "scharr", "erode", "dilate", "morphologyEx", "findContours", "orb", "sift", "houghLines", "houghCircles", "equalizeHist", "qrCode", "bitwise_not", "add", "subtract")
        
        ScrollableTabRow(
            selectedTabIndex = allModes.indexOf(detectionMode).takeIf { it >= 0 } ?: 0,
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            edgePadding = 8.dp
        ) {
            allModes.forEachIndexed { index, mode ->
                Tab(
                    selected = detectionMode == mode,
                    onClick = { detectionMode = mode },
                    text = { Text(mode) }
                )
            }
        }
        
        Box(Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    previewView.scaleType = PreviewView.ScaleType.FIT_CENTER
                    
                    previewView.tag = null // force update on first pass

                    cameraProviderFuture.addListener({
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                update = { previewView ->
                    // Rebind ONLY when lens facing changes
                    if (previewView.tag != lensFacing) {
                        previewView.tag = lensFacing
                        cameraProviderFuture.addListener({
                            try {
                                val cameraProvider = cameraProviderFuture.get()
                                val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }
                                
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageAnalysis
                                )
                            } catch(e: Exception) {
                                Log.e("Camera", "Update bind error", e)
                            }
                        }, ContextCompat.getMainExecutor(context))
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Overlay processed image on top of preview
            resultBitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Processed Image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
            
            // Floating Camera Switch Button
            FloatingActionButton(
                onClick = { 
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Switch Camera")
            }
        }
    }
}
