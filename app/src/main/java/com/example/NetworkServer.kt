package com.example

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.receive
import io.ktor.server.request.receiveChannel
import io.ktor.server.request.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class NetworkServer(
    private val openCVProcessor: OpenCVProcessor,
    private val getLatestCameraFrame: () -> Bitmap?,
    private val getMaxImageSizeMB: () -> Int,
    private val getMaxDimension: () -> Int,
    private val onShowImage: (Bitmap) -> Unit
) {
    private var server: NettyApplicationEngine? = null

    fun start() {
        if (server != null) return
        server = embeddedServer(Netty, host = "0.0.0.0", port = 8080, module = { module(openCVProcessor) }).start(wait = false)
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
    }

    private fun Application.module(processor: OpenCVProcessor) {
        install(CORS) {
            anyHost()
        }
        
        routing {
            get("/") {
                val html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <title>CVBridge Central Panel</title>
                        <style>
                            body {
                                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                                background-color: #0f172a;
                                color: #f1f5f9;
                                margin: 0;
                                padding: 20px;
                            }
                            .container {
                                max-width: 800px;
                                margin: 0 auto;
                            }
                            .header {
                                border-bottom: 2px solid #334155;
                                padding-bottom: 12px;
                                margin-bottom: 24px;
                            }
                            .title {
                                color: #38bdf8;
                                margin: 0;
                                font-size: 24px;
                            }
                            .subtitle {
                                color: #94a3b8;
                                margin: 4px 0 0;
                                font-size: 14px;
                            }
                            .card {
                                background-color: #1e293b;
                                border-radius: 12px;
                                padding: 20px;
                                margin-bottom: 20px;
                                box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -2px rgba(0, 0, 0, 0.1);
                            }
                            .card h2 {
                                margin-top: 0;
                                color: #e2e8f0;
                                font-size: 18px;
                            }
                            .tag {
                                display: inline-block;
                                background-color: #0284c7;
                                color: #ffffff;
                                padding: 2px 8px;
                                border-radius: 4px;
                                font-size: 11px;
                                font-weight: bold;
                                margin-right: 8px;
                                font-family: monospace;
                            }
                            .tag-get { background-color: #10b981; }
                            .tag-post { background-color: #3b82f6; }
                            .endpoint {
                                font-family: monospace;
                                background-color: #0f172a;
                                padding: 10px;
                                border-radius: 6px;
                                font-size: 14px;
                                margin-bottom: 10px;
                                display: flex;
                                align-items: center;
                            }
                            .desc {
                                color: #94a3b8;
                                font-size: 13.5px;
                                margin-bottom: 16px;
                                margin-left: 4px;
                            }
                            .btn {
                                background-color: #38bdf8;
                                color: #0f172a;
                                border: none;
                                padding: 10px 16px;
                                font-weight: bold;
                                border-radius: 6px;
                                cursor: pointer;
                                transition: background 0.2s;
                            }
                            .btn:hover { background-color: #7dd3fc; }
                            .form-group {
                                margin-bottom: 12px;
                            }
                            label {
                                display: block;
                                font-size: 12px;
                                text-transform: uppercase;
                                font-weight: bold;
                                margin-bottom: 6px;
                                color: #64748b;
                            }
                            select, input[type="file"] {
                                background-color: #0f172a;
                                border: 1px solid #334155;
                                color: white;
                                padding: 8px;
                                border-radius: 6px;
                                width: 100%;
                                box-sizing: border-box;
                            }
                            #result-container {
                                margin-top: 16px;
                                display: none;
                                text-align: center;
                            }
                            #result-img {
                                max-width: 100%;
                                border-radius: 8px;
                                max-height: 400px;
                                border: 2px solid #334155;
                            }
                        </style>
                    </head>
                    <body>
                        <div class="container">
                            <div class="header">
                                <h1 class="title">🤖 CVBridge Dashboard</h1>
                                <p class="subtitle">Fast, Zero-Install Native OpenCV Processing Service</p>
                            </div>
                            
                            <div class="card">
                                <h2>📱 Active API Endpoints</h2>
                                
                                <div class="endpoint"><span class="tag tag-get">GET</span>/</div>
                                <div class="desc">Open this companion web interface panel dashboard.</div>
                                
                                <div class="endpoint"><span class="tag tag-post">POST</span>/process?task={task}</div>
                                <div class="desc">Upload raw image bytes and process natively. Tasks: <b>face</b>, <b>person</b>, <b>grayscale</b>, <b>blur</b>, <b>edges</b>.</div>
                                
                                <div class="endpoint"><span class="tag tag-post">POST</span>/imshow</div>
                                <div class="desc">Replace standard <code>cv.imshow()</code>. Displays the sent image directly on the Android device GUI tab.</div>
                                
                                <div class="endpoint"><span class="tag tag-get">GET</span>/camera/frame</div>
                                <div class="desc">Fetch the latest captured raw frame from the active Camera View.</div>
                                
                                <div class="endpoint"><span class="tag tag-get">GET</span>/camera/process?task={task}</div>
                                <div class="desc">Instantly capture camera frame, apply task processing, and return the processed image directly.</div>
                            </div>

                            <div class="card">
                                <h2>🧪 Web Interface Sample Tester</h2>
                                <p style="color: #94a3b8; font-size: 13px;">Test the OpenCV server directly from this browser! Upload an JPEG/PNG image to process it.</p>
                                
                                <div class="form-group">
                                    <label>Select Local Image File</label>
                                    <input type="file" id="test-file" accept="image/*">
                                </div>
                                
                                <div class="form-group">
                                    <label>Choose CV Task</label>
                                    <select id="test-task">
                                        <!-- Core/Color -->
                                        <option value="grayscale">Grayscale Conversion</option>
                                        <option value="resize">Resize (0.5x)</option>
                                        <option value="flip">Flip Image</option>
                                        <option value="rotate">Rotate 90° Clockwise</option>
                                        <option value="warpAffine">Warp Affine (45° Rotate)</option>
                                        <option value="cvtColor">cvtColor (RGBA2GRAY)</option>
                                        
                                        <!-- Blurring / Smoothing -->
                                        <option value="blur">Standard Blur</option>
                                        <option value="gaussianBlur">Gaussian Blur</option>
                                        <option value="medianBlur">Median Blur</option>
                                        <option value="bilateralFilter">Bilateral Filter</option>
                                        
                                        <!-- Detection (Bundled Models) -->
                                        <option value="edges">Edges Detector (Canny)</option>
                                        <option value="face">Face Recognition (Haar Cascade)</option>
                                        <option value="person">People Detector (HOG descriptor)</option>
                                        <option value="qrCode">QR Code Detector (Bounding Box)</option>
                                        
                                        <!-- Edges / Gradients -->
                                        <option value="sobel">Sobel Derivatives</option>
                                        <option value="laplacian">Laplacian</option>
                                        <option value="scharr">Scharr Filter</option>
                                        
                                        <!-- Thresholding -->
                                        <option value="threshold">Simple Threshold (128)</option>
                                        <option value="adaptiveThreshold">Adaptive Threshold</option>
                                        
                                        <!-- Morphology -->
                                        <option value="erode">Erosion</option>
                                        <option value="dilate">Dilation</option>
                                        <option value="morphologyEx">Morphological Operations</option>
                                        
                                        <!-- Contours / Transforms -->
                                        <option value="findContours">Find & Draw Contours</option>
                                        <option value="houghLines">Hough Lines P</option>
                                        <option value="houghCircles">Hough Circles</option>
                                        <option value="equalizeHist">Equalize Histogram</option>
                                        
                                        <!-- Features / Feature Matching -->
                                        <option value="orb">ORB Keypoints Detection</option>
                                        <option value="sift">SIFT Keypoints Detection</option>
                                        <option value="matchTemplate">Template Matching</option>
                                        
                                        <!-- Primitive Drawing / Math -->
                                        <option value="circle">Draw Custom Circle</option>
                                        <option value="line">Draw Custom Line</option>
                                        <option value="bitwise_not">Bitwise NOT (Invert Color)</option>
                                    </select>
                                </div>
                                
                                <button class="btn" id="proc-btn" onclick="testImgProcess()">Process Image</button>
                                
                                <div id="result-container">
                                    <h3 style="font-size: 14px; text-align: left; color:#38bdf8;">Processed Output:</h3>
                                    <img id="result-img" src="" alt="Processing Output">
                                </div>
                            </div>
                        </div>

                        <script>
                            async function testImgProcess() {
                                const fileInput = document.getElementById('test-file');
                                const taskSelect = document.getElementById('test-task');
                                const resultContainer = document.getElementById('result-container');
                                const resultImg = document.getElementById('result-img');
                                const procBtn = document.getElementById('proc-btn');
                                
                                if (!fileInput.files || fileInput.files.length === 0) {
                                    alert('Please select an image file first.');
                                    return;
                                }
                                
                                const file = fileInput.files[0];
                                const task = taskSelect.value;
                                
                                procBtn.disabled = true;
                                procBtn.innerText = 'Processing...';
                                
                                try {
                                    const response = await fetch(`/process?task=${"$"}{task}`, {
                                        method: 'POST',
                                        body: file,
                                        headers: { 'Content-Type': 'image/jpeg' }
                                    });
                                    
                                    if (!response.ok) {
                                        const errMsg = await response.text();
                                        alert('Error: ' + errMsg);
                                        return;
                                    }
                                    
                                    const blob = await response.blob();
                                    const objectURL = URL.createObjectURL(blob);
                                    resultImg.src = objectURL;
                                    resultContainer.style.display = 'block';
                                } catch (err) {
                                    alert('Connection to bridge failed: ' + err.message);
                                } finally {
                                    procBtn.disabled = false;
                                    procBtn.innerText = 'Process Image';
                                }
                            }
                        </script>
                    </body>
                    </html>
                """.trimIndent()
                call.respondText(html, ContentType.Text.Html)
            }
            
            get("/camera/frame") {
                LogManager.log("Network Server", "GET /camera/frame requested")
                val bitmap = getLatestCameraFrame()
                if (bitmap == null) {
                    LogManager.error("Network Server", "GET /camera/frame - Frame not available")
                    call.respondText("Live camera frame not available. Ensure 'Live Camera' tab is active.", status = HttpStatusCode.ServiceUnavailable)
                    return@get
                }
                
                // Keep performance optimal
                val maxDim = getMaxDimension()
                val targetBitmap = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                    scaleBitmapToLimit(bitmap, maxDim)
                } else {
                    bitmap
                }
                
                val outputStream = ByteArrayOutputStream()
                targetBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                call.respondBytes(outputStream.toByteArray(), ContentType.Image.JPEG)
            }
            
            get("/camera/process") {
                val task = call.request.queryParameters["task"] ?: "face"
                LogManager.log("Network Server", "GET /camera/process?task=$task requested")
                val sourceBitmap = getLatestCameraFrame()
                if (sourceBitmap == null) {
                    LogManager.error("Network Server", "GET /camera/process - Frame not available")
                    call.respondText("Live camera frame not available.", status = HttpStatusCode.ServiceUnavailable)
                    return@get
                }
                
                // Downscale the camera frame to the maxDim limit to make live detection lightning fast!
                val maxDim = getMaxDimension()
                val targetBitmap = if (sourceBitmap.width > maxDim || sourceBitmap.height > maxDim) {
                    scaleBitmapToLimit(sourceBitmap, maxDim)
                } else {
                    sourceBitmap
                }
                
                val processedBitmap = when (val mappedTask = task.lowercase().replace("cv2.", "")) {
                    "face", "head" -> processor.detectFaces(targetBitmap)
                    "person", "human" -> processor.detectPeople(targetBitmap)
                    "grayscale", "gray" -> processor.toGrayscale(targetBitmap)
                    "blur" -> processor.applyBlur(targetBitmap)
                    "edges", "canny" -> processor.detectEdges(targetBitmap)
                    else -> {
                        val advancedResult = processAdvancedOpenCV(targetBitmap, mappedTask, call.request.queryParameters)
                        if (advancedResult != null) {
                            advancedResult
                        } else {
                            val valid = listOf("face", "person", "grayscale", "blur", "edges", "resize", "cvtColor", "flip", "rotate", "threshold", "adaptiveThreshold", "gaussianBlur", "medianBlur", "bilateralFilter", "sobel", "laplacian", "scharr", "erode", "dilate", "morphologyEx", "findContours", "orb", "sift", "houghLines", "houghCircles", "equalizeHist", "qrCode", "bitwise_not", "add", "subtract")
                            LogManager.error("Network Server", "GET /camera/process - Invalid task: $task")
                            call.respondText("Error: Invalid task '$task'. Supported tasks are: ${valid.joinToString()}", status = HttpStatusCode.BadRequest)
                            return@get
                        }
                    }
                }
                val outputStream = ByteArrayOutputStream()
                processedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                call.respondBytes(outputStream.toByteArray(), ContentType.Image.JPEG)
            }
            
            post("/imshow") {
                LogManager.log("Network Server", "POST /imshow requested")
                val maxBytes = getMaxImageSizeMB() * 1024 * 1024L
                
                val contentLength = call.request.header(io.ktor.http.HttpHeaders.ContentLength)?.toLongOrNull() ?: 0L
                if (contentLength > maxBytes) {
                    LogManager.error("Network Server", "POST /imshow - Payload too large (Content-Length: $contentLength)")
                    call.respondText("Image file size exceeds the allowed limit of ${getMaxImageSizeMB()} MB", status = HttpStatusCode.PayloadTooLarge)
                    return@post
                }
                
                val bytes = try {
                    call.receive<ByteArray>()
                } catch (e: Exception) {
                    null
                }
                
                if (bytes == null || bytes.size > maxBytes) {
                    LogManager.error("Network Server", "POST /imshow - Payload too large")
                    call.respondText("Image file size exceeds the allowed limit of ${getMaxImageSizeMB()} MB", status = HttpStatusCode.PayloadTooLarge)
                    return@post
                }
                
                val options = BitmapFactory.Options()
                options.inJustDecodeBounds = true
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                
                val maxDim = getMaxDimension()
                var inSampleSize = 1
                if (options.outWidth > maxDim || options.outHeight > maxDim) {
                    val halfHeight = options.outHeight / 2
                    val halfWidth = options.outWidth / 2
                    while ((halfHeight / inSampleSize) >= maxDim && (halfWidth / inSampleSize) >= maxDim) {
                        inSampleSize *= 2
                    }
                }
                
                options.inJustDecodeBounds = false
                options.inSampleSize = inSampleSize
                var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                
                if (bitmap == null) {
                    LogManager.error("Network Server", "POST /imshow - Invalid image representation")
                    call.respondText("Invalid image representation", status = HttpStatusCode.BadRequest)
                    return@post
                }
                
                if (bitmap.width > maxDim || bitmap.height > maxDim) {
                    bitmap = scaleBitmapToLimit(bitmap, maxDim)
                }
                
                withContext(Dispatchers.Main) {
                    onShowImage(bitmap)
                    LogManager.log("Network Server", "POST /imshow - Image displayed in GUI")
                }
                
                call.respondText("Image displayed in Android GUI", status = HttpStatusCode.OK)
            }
            get("/process") {
                val valid = listOf("face", "person", "grayscale", "blur", "edges", "resize", "cvtColor", "flip", "rotate", "threshold", "adaptiveThreshold", "gaussianBlur", "medianBlur", "bilateralFilter", "sobel", "laplacian", "scharr", "erode", "dilate", "morphologyEx", "findContours", "orb", "sift", "houghLines", "houghCircles", "equalizeHist", "qrCode", "bitwise_not", "add", "subtract")
                call.respondText("Send POST to /process?task=[task] with image bytes.\nAvailable tasks:\n" + valid.joinToString("\n- "), status = HttpStatusCode.OK)
            }
            
            post("/process") {
                val task = call.request.queryParameters["task"] ?: "face"
                LogManager.log("Network Server", "POST /process?task=$task requested")
                val maxBytes = getMaxImageSizeMB() * 1024 * 1024L
                
                val contentLength = call.request.header(io.ktor.http.HttpHeaders.ContentLength)?.toLongOrNull() ?: 0L
                if (contentLength > maxBytes) {
                    LogManager.error("Network Server", "POST /process?task=$task - Payload too large (Content-Length: $contentLength)")
                    call.respondText("Image file size exceeds the allowed limit of ${getMaxImageSizeMB()} MB", status = HttpStatusCode.PayloadTooLarge)
                    return@post
                }
                
                val bytes = try {
                    call.receive<ByteArray>()
                } catch (e: Exception) {
                    null
                }
                
                if (bytes == null || bytes.size > maxBytes) {
                    LogManager.error("Network Server", "POST /process?task=$task - Payload too large")
                    call.respondText("Image file size exceeds the allowed limit of ${getMaxImageSizeMB()} MB", status = HttpStatusCode.PayloadTooLarge)
                    return@post
                }
                
                val options = BitmapFactory.Options()
                options.inJustDecodeBounds = true
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                
                val maxDim = getMaxDimension()
                var inSampleSize = 1
                if (options.outWidth > maxDim || options.outHeight > maxDim) {
                    val halfHeight = options.outHeight / 2
                    val halfWidth = options.outWidth / 2
                    while ((halfHeight / inSampleSize) >= maxDim && (halfWidth / inSampleSize) >= maxDim) {
                        inSampleSize *= 2
                    }
                }
                
                options.inJustDecodeBounds = false
                options.inSampleSize = inSampleSize
                var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                
                if (bitmap == null) {
                    val taskName = call.request.queryParameters["task"] ?: "face"
                    LogManager.error("Network Server", "POST /process?task=$taskName - Invalid image representation")
                    call.respondText("Invalid image representation", status = HttpStatusCode.BadRequest)
                    return@post
                }
                
                if (bitmap.width > maxDim || bitmap.height > maxDim) {
                    bitmap = scaleBitmapToLimit(bitmap, maxDim)
                }
                
                val processedBitmap = when (val mappedTask = task.lowercase().replace("cv2.", "")) {
                    "face", "head" -> processor.detectFaces(bitmap)
                    "person", "human" -> processor.detectPeople(bitmap)
                    "grayscale", "gray" -> processor.toGrayscale(bitmap)
                    "blur" -> processor.applyBlur(bitmap)
                    "edges", "canny" -> processor.detectEdges(bitmap)
                    else -> {
                        val advancedResult = processAdvancedOpenCV(bitmap, mappedTask, call.request.queryParameters)
                        if (advancedResult != null) {
                            advancedResult
                        } else {
                            val valid = listOf("face", "person", "grayscale", "blur", "edges", "resize", "cvtColor", "flip", "rotate", "threshold", "adaptiveThreshold", "gaussianBlur", "medianBlur", "bilateralFilter", "sobel", "laplacian", "scharr", "erode", "dilate", "morphologyEx", "findContours", "orb", "sift", "houghLines", "houghCircles", "equalizeHist", "qrCode", "bitwise_not", "add", "subtract")
                            LogManager.error("Network Server", "POST /process - Invalid task: $task")
                            call.respondText("Error: Invalid task '$task'. Supported tasks are: ${valid.joinToString()}", status = HttpStatusCode.BadRequest)
                            return@post
                        }
                    }
                }
                
                val outputStream = ByteArrayOutputStream()
                processedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
                call.respondBytes(outputStream.toByteArray(), ContentType.Image.JPEG)
            }
        }
    }
    
}
