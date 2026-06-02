# CVBridge - Zero-Install Native OpenCV Server
![CVBridge Banner](https://raw.githubusercontent.com/opencv/opencv/master/doc/pattern.png)

**CVBridge** transforms your Android device into a completely standalone, robust network-based computer vision microservice. Without requiring any complex C++ build tools, Python pip installations, or convoluted toolchains on your client computers, you can simply stream images via REST API directly to your phone, where the native OpenCV engine will instantly process them. It acts as a powerful bridge connecting your terminal (e.g. Termux, bash, python requests) immediately with the world of Computer Vision.

## 🌟 Core Philosophy
Most developers who want to perform operations like Face Detection or Gaussian blurring either need to setup heavy OpenCV Python environments or compile C++ programs. **CVBridge completely eliminates this overhead.** 
Your phone now serves as a high-speed, local computer vision "API provider".

## 🔥 Features Summary

- **⚡ Zero-Install Network Processing:** Features a built-in Netty Ktor server that handles REST requests. Runs efficiently as an **Android Foreground Service** (`dataSync`), meaning you can minimize the app or turn off your screen, and the processing engine will stay alive in the background without getting killed by the OS!
- **📸 Live Camera Feed with CV Processing:** Switch on your device's camera inside the app's `Live Camera` tab to view real-time filtering (Front & Back camera supported properly handling mirroring and rotation!). **Test all 30+ native OpenCV operations directly via horizontal scrolling tabs** instantly to find the best algorithms before implementing them over the network.
- **🌐 Network-to-Screen `imshow` Emulator:** No screen attached to your server? Send standard `POST /imshow` and see the result instantly in the CVBridge API Docs dashboard GUI. 
- **🔗 Instant REST Endpoints to Test:** Hit the processing queue by just invoking basic cURL commands; results are sent back as `.jpeg` binaries!
- **🤖 Built-in Haar Cascade & HOG Detections:** The application comes with **OpenCV 4.x engine** (the latest stable native Android distribution), bundled internally for instant offline edge detection, human tracking, and facial tracking. Processing has been highly optimized with automatic internal downsampling to ensure lightning-fast detection times.
- **📝 Live Network Logging Tab:** See all server interactions, errors, endpoints hit, and detailed diagnostics immediately via the "Live Logs" tab inside the app.

---

## 🛠️ API & Endpoint Reference

All REST endpoints operate on `HTTP`. The `Network & Photo` tab inside the Application will display the active IP (e.g. `192.168.x.x:8080`) assigned to your phone. 

All endpoints return standard HTTP codes:
- **`200 OK`**: Task processed successfully.
- **`400 Bad Request`**: Received an invalid image, or an invalid task query parameter. 
- **`413 Payload Too Large`**: The image exceeds your set "Max Image Size" limit.

### 1. The Main Process Command
**Endpoint:** `POST /process?task=[TASK_NAME]`
Directly transform a raw image and download the results.

**Basic Setup:**
- `face`, `head`: Haar Cascade Frontal Face Detector.
- `person`, `human`: HOG People Detector (Standard SVM).
- `grayscale`, `gray`: Convert color dimensions to Gray.
- `blur`: Standard Gaussian blurring (`15x15` kernel).
- `edges`, `canny`: Canny Edge detector matrices.

**Advanced OpenCV Mappings (Native OpenCV 4.x operations):**
You can also directly invoke these comprehensive, natively bound OpenCV functions by passing the function name (e.g., `task=resize`, `task=gaussianBlur`). Optional processing parameters can be passed in the URL string.

*Supported Core / Image Processing:*
- `resize`, `cvtColor`, `flip`, `rotate`, `warpAffine`, `warpPerspective`, `getPerspectiveTransform`, `getRotationMatrix2D`
- `threshold`, `adaptiveThreshold`, `equalizeHist`, `calcHist`, `inRange`, `split`, `merge`
- `gaussianBlur`, `medianBlur`, `bilateralFilter`
- `sobel`, `laplacian`, `scharr`
- `erode`, `dilate`, `morphologyEx`
- `findContours`, `drawContours`, `contourArea`, `boundingRect`, `minAreaRect`, `convexHull`, `approxPolyDP`, `matchShapes`
- `matchTemplate`

*Feature Detection / Primitive Drawing:*
- `orb`, `sift`, `drawKeypoints`, `qrCode` (detects and draws bounding boxes on QRs)
- `line`, `rectangle`, `circle`, `putText`, `polylines`, `fillPoly`
- `bitwise_not`, `bitwise_and`, `bitwise_or`, `bitwise_xor`, `add`, `subtract`

*Example Usage in Bash (or Termux):*
```bash
# Apply the Canny Edge Detection and save output.jpg
curl -X POST --data-binary @input.jpg "http://192.168.x.x:8080/process?task=edges" > output.jpg

# Requesting an invalid task returns an error message:
curl -X POST --data-binary @input.jpg "http://192.168.x.x:8080/process?task=ede"
# Returns: Error: Invalid task 'ede'. Supported tasks are: face, person, grayscale, blur, edges
```

### 2. View Stream Real-Time "Camera Process" 
**Endpoint:** `GET /camera/process?task=[TASK_NAME]`
This pulls the *current frame* from the live camera stream inside the `Live Camera` tab, applies the computer vision task requested, and returns it.
*(Note: Requires the Engine to be ON, 'Live Camera' tab must be actively rendering, and camera permissions granted!)*

### 3. Emulate `cv2.imshow()`
**Endpoint:** `POST /imshow`
Sends bytes through the network and the CVBridge application instantly displays it on its screen. Ideal for remote Python scripts over Wi-Fi!

```python
import requests
with open('my_plot.png', 'rb') as f:
    requests.post('http://192.168.x.x:8080/imshow', data=f.read())
```

---

## ⚙️ Advanced Customizability & Engine Control
Navigate to the **Settings** tab within CVBridge to fine-tune operations. These parameters strictly alter the mathematical tracking algorithms used natively by the C++ core:

- **Resolution Max Dimension**: Computer vision requires dense matrix operations. Large images can trigger Out-Of-Memory exceptions or lag. Set this value (e.g., `1024` px) to automatically downscale huge inputs safely *before* OpenCV processing.
- **Max Image Size Limit**: Keeps the Netty server from crashing by rejecting REST payloads larger than your set limit (e.g. `5` MB). You can define this completely manually or restore defaults.
  - The API will accurately return a `413 Payload Too Large` error immediately using robust `Content-Length` and stream size validations, saving processing power.
- **Robust Error Handling**: Any invalid endpoint API tasks (e.g. `task=bur` instead of `blur`) are strictly rejected with a `400 Bad Request` and helpful hints without crashing or locking the server thread.

### Open CV Model Options:
- **Haar Cascade Scale Factor**: Defines how much the image size is reduced at each image scale. 
  - `1.05`: Slow but extremely thorough (finds smaller faces).
  - `1.15` (Default): Balance between speed and detection rate.
  - `1.30`: Very fast, missing smaller faraway faces.
- **Min Neighbors**: How strict the scanner is when classifying a group of pixels as a 'Face'.
  - `3`: Typical value. Lower numbers lead to higher false positives. Higher numbers lead to missed faces.
- **Canny Edge Detection Thresholds (T1 / T2)**: The well-known dual-threshold levels. 
  - Pixels with gradients above `T2` are definitely edges.
  - Pixels below `T1` are definitely ignored.
  - Gradients between `T1` and `T2` are considered edges *only if* they connect directly to strong edges. Tuning this clarifies messy/noisy images.

## 🏗️ Technical Stack

- **Primary Language**: Kotlin `1.9.0`
- **Native CV Module**: OpenCV 4.x compiled native binaries for ARM64 loaded securely via `.so`.
- **UI Framework**: Modern Jetpack Compose & Material Design 3.
- **Server Engine**: Embedded Netty powered by Ktor APIs.
- **Camera Pipeline**: Jetpack CameraX.

## 👨‍💻 Quick Setup & Integration
1. Run the App on Android and accept permissions.
2. Under "Network & Photo" click **Toggle On** for the **OpenCV Processing Engine**.
3. Access the IP address shown locally on any computer/device in the same network.
4. Experiment using the Web Sandbox Dashboard directly on your PC browser!
