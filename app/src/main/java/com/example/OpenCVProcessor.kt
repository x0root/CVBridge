package com.example

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfRect
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.CascadeClassifier
import org.opencv.objdetect.HOGDescriptor
import java.io.File
import java.io.FileOutputStream
import java.net.URL

class OpenCVProcessor(private val context: Context) {
    var isInitialized = false
        private set

    var isFaceModelReady = false
        private set

    var faceModelStatusMessage = "Not loaded"
        private set

    private var faceCascade: CascadeClassifier? = null
    private var hog: HOGDescriptor? = null

    suspend fun initialize(onFaceModelStatusUpdate: () -> Unit = {}) = withContext(Dispatchers.IO) {
        try {
            if (OpenCVLoader.initDebug()) {
                LogManager.log("OpenCV", "OpenCV loaded successfully")
                isInitialized = true
                
                // Initialize HOG natively (blazing fast and always ready offline)
                hog = HOGDescriptor()
                hog?.setSVMDetector(HOGDescriptor.getDefaultPeopleDetector())
                
                // Start background download and initialization of face cascade
                downloadAndLoadFaceModel(onFaceModelStatusUpdate)
            } else {
                LogManager.error("OpenCV", "OpenCV initialization failed.")
            }
        } catch (e: Exception) {
            LogManager.error("OpenCV", "Error initializing OpenCV: ${e.message}", e)
        }
    }

    suspend fun downloadAndLoadFaceModel(onFaceModelStatusUpdate: () -> Unit = {}) = withContext(Dispatchers.IO) {
        faceModelStatusMessage = "Checking offline model..."
        onFaceModelStatusUpdate()

        val faceCascadeFile = File(context.filesDir, "haarcascade_frontalface_default.xml")

        // 1. Copy the embedded XML file from assets to app files storage (offline, fast)
        try {
            LogManager.log("OpenCV", "Copying face model from bundled assets...")
            context.assets.open("haarcascade_frontalface_default.xml").use { input ->
                FileOutputStream(faceCascadeFile).use { output ->
                    input.copyTo(output)
                }
            }
            LogManager.log("OpenCV", "Successfully copied bundle face cascade model!")
        } catch (e: Exception) {
            LogManager.error("OpenCV", "Assets model loading error: ${e.message}", e)
        }

        // 2. Load and verify CascadeClassifier from internal storage
        if (faceCascadeFile.exists() && faceCascadeFile.length() > 0) {
            try {
                val classifier = CascadeClassifier(faceCascadeFile.absolutePath)
                if (!classifier.empty()) {
                    faceCascade = classifier
                    isFaceModelReady = true
                    faceModelStatusMessage = "Ready (Offline)"
                    onFaceModelStatusUpdate()
                    return@withContext
                }
            } catch (e: Exception) {
                LogManager.error("OpenCV", "Loaded cascade file is invalid. Deleting...", e)
                faceCascadeFile.delete()
            }
        }

        faceModelStatusMessage = "Downloading face detection model..."
        onFaceModelStatusUpdate()

        // 2. Try fast/unblocked CDNs first, then GitHub direct backup
        val mirrors = listOf(
            "https://cdn.jsdelivr.net/gh/opencv/opencv@master/data/haarcascades/haarcascade_frontalface_default.xml",
            "https://raw.githubusercontent.com/opencv/opencv/master/data/haarcascades/haarcascade_frontalface_default.xml"
        )

        var success = false
        for (url in mirrors) {
            try {
                LogManager.log("OpenCV", "Downloading face XML model from: $url")
                val connection = URL(url).openConnection()
                connection.connectTimeout = 8000 // 8s connect timeout
                connection.readTimeout = 12000    // 12s read timeout

                connection.getInputStream().use { input ->
                    FileOutputStream(faceCascadeFile).use { output ->
                        input.copyTo(output)
                    }
                }

                // If file is valid, load it
                if (faceCascadeFile.exists() && faceCascadeFile.length() > 0) {
                    val classifier = CascadeClassifier(faceCascadeFile.absolutePath)
                    if (!classifier.empty()) {
                        faceCascade = classifier
                        isFaceModelReady = true
                        faceModelStatusMessage = "Ready (Downloaded)"
                        onFaceModelStatusUpdate()
                        success = true
                        break
                    }
                }
            } catch (e: Exception) {
                LogManager.error("OpenCV", "Failed to download face model from $url", e)
                if (faceCascadeFile.exists()) {
                    faceCascadeFile.delete() // Clean up corrupt partial file
                }
            }
        }

        if (!success) {
            isFaceModelReady = false
            faceModelStatusMessage = "Download failed. Check connection & retry."
            onFaceModelStatusUpdate()
        }
    }

    private fun scaleWorkingBitmap(bitmap: Bitmap, maxDim: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDim && height <= maxDim) {
            return bitmap
        }
        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        if (width > height) {
            newWidth = maxDim
            newHeight = (maxDim / ratio).toInt()
        } else {
            newHeight = maxDim
            newWidth = (maxDim * ratio).toInt()
        }
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    fun detectFaces(
        bitmap: Bitmap,
        scaleFactor: Double = 1.15,
        minNeighbors: Int = 3,
        minSize: Int = 24
    ): Bitmap {
        if (!isInitialized || faceCascade == null || faceCascade?.empty() == true) return bitmap
        
        LogManager.log("OpenCV", "Starting Face Detection...")
        val startTime = System.currentTimeMillis()
        try {
            // Downsample slightly to dramatically speed up face detection
            val targetSize = 512
            val workingBitmap = if (bitmap.width > targetSize || bitmap.height > targetSize) {
                scaleWorkingBitmap(bitmap, targetSize)
            } else {
                bitmap
            }
            val scaleRatioX = bitmap.width.toDouble() / workingBitmap.width.toDouble()
            val scaleRatioY = bitmap.height.toDouble() / workingBitmap.height.toDouble()

            val workingMat = Mat()
            Utils.bitmapToMat(workingBitmap, workingMat)
            
            val gray = Mat()
            Imgproc.cvtColor(workingMat, gray, Imgproc.COLOR_RGBA2GRAY)
            
            // Equalize histogram to enhance contrast
            Imgproc.equalizeHist(gray, gray)
            
            val faces = MatOfRect()
            // Precise parameters for reliable detection
            val validScaleFactor = if (scaleFactor <= 1.0) 1.05 else scaleFactor
            faceCascade?.detectMultiScale(
                gray, 
                faces, 
                validScaleFactor, 
                minNeighbors, 
                0, 
                org.opencv.core.Size(minSize.toDouble(), minSize.toDouble()),
                org.opencv.core.Size()
            )
            
            val originalMat = Mat()
            Utils.bitmapToMat(bitmap, originalMat)
            
            val rects = faces.toArray()
            for (rect in rects) {
                Imgproc.rectangle(
                    originalMat,
                    Point(rect.x * scaleRatioX, rect.y * scaleRatioY),
                    Point((rect.x + rect.width) * scaleRatioX, (rect.y + rect.height) * scaleRatioY),
                    Scalar(255.0, 0.0, 0.0, 255.0),
                    (4 * scaleRatioX).toInt().coerceAtLeast(4)
                )
            }
            
            val borderSz = (2 * scaleRatioX).toInt().coerceAtLeast(2)
            Imgproc.rectangle(
                originalMat,
                Point(borderSz.toDouble(), borderSz.toDouble()),
                Point((originalMat.cols() - borderSz).toDouble(), (originalMat.rows() - borderSz).toDouble()),
                Scalar(0.0, 0.0, 255.0, 255.0),
                borderSz
            )
            
            val result = Bitmap.createBitmap(originalMat.cols(), originalMat.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(originalMat, result)
            workingMat.release()
            gray.release()
            faces.release()
            originalMat.release()
            if (workingBitmap != bitmap) workingBitmap.recycle()
            
            val elapsed = System.currentTimeMillis() - startTime
            LogManager.log("OpenCV", "Face detection complete. Found ${rects.size} face(s) in ${elapsed}ms")
            return result
        } catch (e: Exception) {
            LogManager.error("OpenCV", "Face detection failed", e)
            return bitmap
        }
    }

    fun detectPeople(
        bitmap: Bitmap,
        scaleFactor: Double = 1.15,
        winStride: Int = 8
    ): Bitmap {
        if (!isInitialized || hog == null) return bitmap

        LogManager.log("OpenCV", "Starting People Detection (HOG)...")
        val startTime = System.currentTimeMillis()
        try {
            // Downsample HOG detection input to max 480px for massive mobile acceleration
            val targetSize = 480
            val workingBitmap = if (bitmap.width > targetSize || bitmap.height > targetSize) {
                scaleWorkingBitmap(bitmap, targetSize)
            } else {
                bitmap
            }
            val scaleRatioX = bitmap.width.toDouble() / workingBitmap.width.toDouble()
            val scaleRatioY = bitmap.height.toDouble() / workingBitmap.height.toDouble()

            val workingMat = Mat()
            Utils.bitmapToMat(workingBitmap, workingMat)
            
            val rgb = Mat()
            Imgproc.cvtColor(workingMat, rgb, Imgproc.COLOR_RGBA2RGB) 
            
            val foundLocations = MatOfRect()
            val foundWeights = org.opencv.core.MatOfDouble()
            
            // Tuned HOG parameters for blazing speed 
            val validScaleFactor = if (scaleFactor <= 1.0) 1.05 else scaleFactor
            hog?.detectMultiScale(
                rgb, 
                foundLocations, 
                foundWeights, 
                0.0, 
                org.opencv.core.Size(winStride.toDouble(), winStride.toDouble()), 
                org.opencv.core.Size(16.0, 16.0), 
                validScaleFactor, 
                2.0, 
                false
            )
            
            val originalMat = Mat()
            Utils.bitmapToMat(bitmap, originalMat)
            
            val rects = foundLocations.toArray()
            for (rect in rects) {
                Imgproc.rectangle(
                    originalMat, 
                    Point(rect.x * scaleRatioX, rect.y * scaleRatioY),
                    Point((rect.x + rect.width) * scaleRatioX, (rect.y + rect.height) * scaleRatioY),
                    Scalar(0.0, 255.0, 0.0, 255.0),
                    (4 * scaleRatioX).toInt().coerceAtLeast(4)
                )
            }
            
            val result = Bitmap.createBitmap(originalMat.cols(), originalMat.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(originalMat, result)
            workingMat.release()
            rgb.release()
            foundLocations.release()
            foundWeights.release()
            originalMat.release()
            if (workingBitmap != bitmap) workingBitmap.recycle()
            
            val elapsed = System.currentTimeMillis() - startTime
            LogManager.log("OpenCV", "People detection complete. Found ${rects.size} person(s) in ${elapsed}ms")
            
            return result
        } catch (e: Exception) {
            LogManager.error("OpenCV", "People detection failed", e)
            return bitmap
        }
    }

    fun toGrayscale(bitmap: Bitmap): Bitmap {
        if (!isInitialized) return bitmap
        LogManager.log("OpenCV", "Starting Grayscale conversion...")
        val startTime = System.currentTimeMillis()
        
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
        
        val result = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        val grayRgba = Mat()
        Imgproc.cvtColor(gray, grayRgba, Imgproc.COLOR_GRAY2RGBA)
        Utils.matToBitmap(grayRgba, result)
        
        mat.release()
        gray.release()
        grayRgba.release()
        
        val elapsed = System.currentTimeMillis() - startTime
        LogManager.log("OpenCV", "Grayscale conversion complete in ${elapsed}ms")
        return result
    }

    fun applyBlur(bitmap: Bitmap, kernelSize: Int = 15): Bitmap {
        if (!isInitialized) return bitmap
        LogManager.log("OpenCV", "Starting Gaussian Blur ($kernelSize x $kernelSize)...")
        val startTime = System.currentTimeMillis()
        
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        val blurred = Mat()
        val kSize = if (kernelSize % 2 == 0) kernelSize + 1 else kernelSize // must be odd
        Imgproc.GaussianBlur(mat, blurred, org.opencv.core.Size(kSize.toDouble(), kSize.toDouble()), 0.0)
        
        val result = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(blurred, result)
        mat.release()
        blurred.release()
        
        val elapsed = System.currentTimeMillis() - startTime
        LogManager.log("OpenCV", "Gaussian Blur complete in ${elapsed}ms")
        return result
    }

    fun detectEdges(
        bitmap: Bitmap,
        threshold1: Double = 100.0,
        threshold2: Double = 200.0
    ): Bitmap {
        if (!isInitialized) return bitmap
        
        LogManager.log("OpenCV", "Starting Canny Edge Detection (T1=$threshold1, T2=$threshold2)...")
        val startTime = System.currentTimeMillis()
        
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
        val edges = Mat()
        Imgproc.Canny(gray, edges, threshold1, threshold2)
        
        val result = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        val edgesRgba = Mat()
        Imgproc.cvtColor(edges, edgesRgba, Imgproc.COLOR_GRAY2RGBA)
        Utils.matToBitmap(edgesRgba, result)
        mat.release()
        gray.release()
        edges.release()
        edgesRgba.release()
        
        val elapsed = System.currentTimeMillis() - startTime
        LogManager.log("OpenCV", "Canny Edge Detection complete in ${elapsed}ms")
        
        return result
    }
}
