App: CVBridge / OpenCV Complete
=======================================

Technical Details:
- Framework: Android Jetpack Compose
- Language: Kotlin
- Core Library: OpenCV 4 (via `org.opencv`) 
- Network Server: Ktor Netty, serving an internal dashboard and bridging REST endpoints.
- UI Architecture: Single-activity, Drawer-based navigation.
- Minimum SDK: Compatible with modern Android devices.

Features:
- Live Camera Processing: Real-time face detection, pedestrian detection (HOG), edge detection (Canny), blur, and grayscale filters using CameraX.
- OpenCV Customizable Engine: Granular control over hyperparameters (Scale Factor, Min Neighbors, Min Size, Canny thresholds, Blur kernel size). Available via Settings UI.
- Dark/Light Theme: Respects system theme by default, and can be forced via Settings.
- Network & Photo Tab: Allows manual processing of gallery photos, and provides local HTTP server metrics.
- API Docs & GUI: View the active Network dashboard to see bridged images and requests.

How to Use:
1. Open the App. 
2. Use the Hamburger Menu (top-left) to navigate between different tools.
3. Network & Photo: Select an image from your device and apply various OpenCV effects manually.
4. Live Camera: Switch between front & back cameras. Use the toggle to turn the OpenCV Engine On/Off (if off, raw previews are shown). 
5. Settings: Customize OpenCV variables exactly as needed. Adjust Dark/Light mode here.

Notes on Performance:
Changing "Face Min Size" or "Scale Factor" will drastically affect performance vs accuracy. Lower values mean higher accuracy but heavier processing.

Built iteratively, designed for stability without distracting elements.
