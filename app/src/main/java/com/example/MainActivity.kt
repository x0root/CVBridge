package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import kotlinx.coroutines.launch
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var openCVProcessor: OpenCVProcessor
    private var networkServer: NetworkServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        openCVProcessor = OpenCVProcessor(this)
        LogManager.init(applicationContext)

        setContent {
            val sharedPref = remember { getSharedPreferences("AppPrefs", Context.MODE_PRIVATE) }
            var themeMode by remember { mutableStateOf(sharedPref.getInt("themeMode", 0)) } // 0=System, 1=Light, 2=Dark
            
            val darkTheme = when (themeMode) {
                1 -> false
                2 -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = darkTheme) {
                var serverStatus by remember { mutableStateOf("Initializing OpenCV...") }
                var isReady by remember { mutableStateOf(false) }
                var faceModelStatusMessage by remember { mutableStateOf("Checking...") }
                var remoteImage by remember { mutableStateOf<Bitmap?>(null) }

                val latestCameraFrameRef = remember { java.util.concurrent.atomic.AtomicReference<Bitmap?>(null) }
                
                val sharedPref = remember { getSharedPreferences("AppPrefs", Context.MODE_PRIVATE) }
                var maxImageSizeMB by remember { mutableStateOf(sharedPref.getInt("maxImageSizeMB", 5)) }
                var maxImageDimension by remember { mutableStateOf(sharedPref.getInt("maxImageDimension", 1024)) }
                var cvScaleFactor by remember { mutableStateOf(sharedPref.getFloat("cvScaleFactor", 1.15f)) }
                var cvMinNeighbors by remember { mutableStateOf(sharedPref.getInt("cvMinNeighbors", 3)) }
                var cvMinSize by remember { mutableStateOf(sharedPref.getInt("cvMinSize", 24)) }
                var cvCannyT1 by remember { mutableStateOf(sharedPref.getFloat("cvCannyT1", 100f)) }
                var cvCannyT2 by remember { mutableStateOf(sharedPref.getFloat("cvCannyT2", 200f)) }
                var cvBlurSize by remember { mutableStateOf(sharedPref.getInt("cvBlurSize", 15)) }

                var engineOn by remember { mutableStateOf(sharedPref.getBoolean("engineOn", false)) }

                LaunchedEffect(Unit) {
                    networkServer = NetworkServer(
                        openCVProcessor = openCVProcessor,
                        getLatestCameraFrame = { latestCameraFrameRef.get() },
                        getMaxImageSizeMB = { maxImageSizeMB },
                        getMaxDimension = { maxImageDimension },
                        onShowImage = { bitmap -> remoteImage = bitmap }
                    )
                    
                    // Initialize fast
                    openCVProcessor.initialize(onFaceModelStatusUpdate = {
                        faceModelStatusMessage = openCVProcessor.faceModelStatusMessage
                    })
                    
                    isReady = openCVProcessor.isInitialized
                    faceModelStatusMessage = openCVProcessor.faceModelStatusMessage
                }

                LaunchedEffect(engineOn, isReady) {
                    if (isReady) {
                        try {
                            if (engineOn) {
                                networkServer?.start()
                                val serviceIntent = android.content.Intent(this@MainActivity, ServerService::class.java)
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    startForegroundService(serviceIntent)
                                } else {
                                    startService(serviceIntent)
                                }
                                val localIp = getLocalIpAddress()
                                serverStatus = if (localIp != null) {
                                    "Engine Online\n\nAccess Dashboard on:\n• Local: http://127.0.0.1:8080\n• Wi-Fi / LAN: http://$localIp:8080\n"
                                } else {
                                    "Engine Online\n\nAccess Dashboard on:\n• http://127.0.0.1:8080 (Localhost)"
                                }
                            } else {
                                networkServer?.stop()
                                val serviceIntent = android.content.Intent(this@MainActivity, ServerService::class.java).apply {
                                    action = "STOP"
                                }
                                startService(serviceIntent)
                                serverStatus = "Engine Offline\n\nToggle on to start network processing"
                            }
                        } catch (e: Exception) {
                            serverStatus = "Server connection error."
                        }
                    } else {
                        serverStatus = "OpenCV Initializing or Failed"
                        networkServer?.stop()
                    }
                }

                MainScreen(
                    statusMessage = serverStatus,
                    isReady = isReady,
                    faceModelStatusMessage = faceModelStatusMessage,
                    onRetryFaceModelDownload = {
                        lifecycleScope.launch(Dispatchers.IO) {
                            openCVProcessor.downloadAndLoadFaceModel(onFaceModelStatusUpdate = {
                                faceModelStatusMessage = openCVProcessor.faceModelStatusMessage
                            })
                        }
                    },
                    processor = openCVProcessor,
                    remoteImage = remoteImage,
                    onFrameUpdate = { latestCameraFrameRef.set(it) },
                    maxImageSizeMB = maxImageSizeMB,
                    onMaxImageSizeMBChange = {
                        maxImageSizeMB = it
                        sharedPref.edit().putInt("maxImageSizeMB", it).apply()
                    },
                    maxImageDimension = maxImageDimension,
                    onMaxImageDimensionChange = {
                        maxImageDimension = it
                        sharedPref.edit().putInt("maxImageDimension", it).apply()
                    },
                    cvParams = CvParams(cvScaleFactor, cvMinNeighbors, cvMinSize, cvCannyT1, cvCannyT2, cvBlurSize),
                    onCvParamChange = { p ->
                        cvScaleFactor = p.scaleFactor
                        cvMinNeighbors = p.minNeighbors
                        cvMinSize = p.minSize
                        cvCannyT1 = p.cannyT1
                        cvCannyT2 = p.cannyT2
                        cvBlurSize = p.blurSize
                        sharedPref.edit().apply {
                            putFloat("cvScaleFactor", p.scaleFactor)
                            putInt("cvMinNeighbors", p.minNeighbors)
                            putInt("cvMinSize", p.minSize)
                            putFloat("cvCannyT1", p.cannyT1)
                            putFloat("cvCannyT2", p.cannyT2)
                            putInt("cvBlurSize", p.blurSize)
                        }.apply()
                    },
                    themeMode = themeMode,
                    onThemeModeChange = { mode ->
                        themeMode = mode
                        sharedPref.edit().putInt("themeMode", mode).apply()
                    },
                    engineOn = engineOn,
                    onEngineOnChange = { on ->
                        engineOn = on
                        sharedPref.edit().putBoolean("engineOn", on).apply()
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        networkServer?.stop()
    }
}

fun getLocalIpAddress(): String? {
    try {
        val interfaces = java.net.NetworkInterface.getNetworkInterfaces() ?: return null
        val list = java.util.Collections.list(interfaces)
        for (networkInterface in list) {
            val addresses = java.util.Collections.list(networkInterface.inetAddresses)
            for (address in addresses) {
                if (!address.isLoopbackAddress) {
                    val sAddr = address.hostAddress ?: continue
                    val isIPv4 = sAddr.indexOf(':') < 0
                    if (isIPv4) {
                        return sAddr
                    }
                }
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("IPAddress", "Error getting local IP address", e)
    }
    return null
}

data class CvParams(
    val scaleFactor: Float = 1.15f,
    val minNeighbors: Int = 3,
    val minSize: Int = 24,
    val cannyT1: Float = 100f,
    val cannyT2: Float = 200f,
    val blurSize: Int = 15
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    statusMessage: String,
    isReady: Boolean,
    faceModelStatusMessage: String,
    onRetryFaceModelDownload: () -> Unit,
    processor: OpenCVProcessor,
    remoteImage: Bitmap?,
    onFrameUpdate: (Bitmap) -> Unit,
    maxImageSizeMB: Int,
    onMaxImageSizeMBChange: (Int) -> Unit,
    maxImageDimension: Int,
    onMaxImageDimensionChange: (Int) -> Unit,
    cvParams: CvParams,
    onCvParamChange: (CvParams) -> Unit,
    themeMode: Int,
    onThemeModeChange: (Int) -> Unit,
    engineOn: Boolean,
    onEngineOnChange: (Boolean) -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Network & Photo", "Live Camera", "API Docs", "Settings", "Live Logs")

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(12.dp))
                Text(
                    "CVBridge",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                HorizontalDivider()
                tabs.forEachIndexed { index, title ->
                    NavigationDrawerItem(
                        label = { Text(title) },
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(tabs[selectedTab]) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Open Sidebar")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            },
            modifier = modifier.fillMaxSize()
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                when (selectedTab) {
                    0 -> NetworkPhotoTab(
                        statusMessage = statusMessage,
                        isReady = isReady,
                        faceModelStatusMessage = faceModelStatusMessage,
                        onRetryFaceModelDownload = onRetryFaceModelDownload,
                        processor = processor,
                        maxImageDimension = maxImageDimension,
                        params = cvParams,
                        engineOn = engineOn,
                        onEngineOnChange = onEngineOnChange
                    )
                    1 -> LiveDetectionScreen(processor, isReady, onFrameUpdate, cvParams, engineOn)
                    2 -> ApiDocsTab(remoteImage)
                    3 -> SettingsTab(
                        maxSize = maxImageSizeMB,
                        onSizeChange = onMaxImageSizeMBChange,
                        maxDim = maxImageDimension,
                        onDimChange = onMaxImageDimensionChange,
                        params = cvParams,
                        onParamsChange = onCvParamChange,
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                        engineOn = engineOn,
                        onEngineOnChange = onEngineOnChange
                    )
                    4 -> LogTab()
                }
            }
        }
    }
}

@Composable
fun SettingsTab(
    maxSize: Int,
    onSizeChange: (Int) -> Unit,
    maxDim: Int,
    onDimChange: (Int) -> Unit,
    params: CvParams,
    onParamsChange: (CvParams) -> Unit,
    themeMode: Int,
    onThemeModeChange: (Int) -> Unit,
    engineOn: Boolean,
    onEngineOnChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("App Preferences", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                
                Text(text = "Dark/Light Mode")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = themeMode == 0, onClick = { onThemeModeChange(0) })
                    Text("System")
                    Spacer(modifier = Modifier.width(8.dp))
                    RadioButton(selected = themeMode == 1, onClick = { onThemeModeChange(1) })
                    Text("Light")
                    Spacer(modifier = Modifier.width(8.dp))
                    RadioButton(selected = themeMode == 2, onClick = { onThemeModeChange(2) })
                    Text("Dark")
                }
            }
        }
        
        HorizontalDivider()

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("OpenCV Parameters", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                
                Text(text = "Face Scale Factor: ${"%.2f".format(params.scaleFactor)}")
                Slider(
                    value = params.scaleFactor,
                    onValueChange = { onParamsChange(params.copy(scaleFactor = it)) },
                    valueRange = 1.01f..1.5f
                )
                
                Text(text = "Face Min Neighbors: ${params.minNeighbors}")
                Slider(
                    value = params.minNeighbors.toFloat(),
                    onValueChange = { onParamsChange(params.copy(minNeighbors = it.toInt())) },
                    valueRange = 1f..10f,
                    steps = 8
                )
                
                Text(text = "Face Min Size (px): ${params.minSize}")
                Slider(
                    value = params.minSize.toFloat(),
                    onValueChange = { onParamsChange(params.copy(minSize = it.toInt())) },
                    valueRange = 10f..100f
                )
                
                Text(text = "Canny Threshold 1: ${params.cannyT1.toInt()}")
                Slider(
                    value = params.cannyT1,
                    onValueChange = { onParamsChange(params.copy(cannyT1 = it)) },
                    valueRange = 10f..300f
                )
                
                Text(text = "Canny Threshold 2: ${params.cannyT2.toInt()}")
                Slider(
                    value = params.cannyT2,
                    onValueChange = { onParamsChange(params.copy(cannyT2 = it)) },
                    valueRange = 10f..300f
                )
                
                Text(text = "Blur Kernel Size: ${params.blurSize}")
                Slider(
                    value = params.blurSize.toFloat(),
                    onValueChange = { onParamsChange(params.copy(blurSize = it.toInt())) },
                    valueRange = 3f..45f,
                    steps = 21 // odd sizes
                )
                
                Button(onClick = { onParamsChange(CvParams()) }) {
                    Text("Reset OpenCV Settings")
                }
            }
        }
        
        HorizontalDivider()

        Text(
            text = "API Constraints",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text("Limit image size and dimensions to prevent memory errors.")

        var localSize by remember(maxSize) { mutableStateOf(maxSize.toString()) }
        var localDim by remember(maxDim) { mutableStateOf(maxDim.toString()) }

        OutlinedTextField(
            value = localSize,
            onValueChange = { localSize = it },
            label = { Text("Max Payload Size (MB)") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = localDim,
            onValueChange = { localDim = it },
            label = { Text("Max Image Dimension (px)") },
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    val newSize = localSize.toIntOrNull()?.coerceAtLeast(1) ?: 5
                    val newDim = localDim.toIntOrNull()?.coerceAtLeast(100) ?: 1024
                    localSize = newSize.toString()
                    localDim = newDim.toString()
                    onSizeChange(newSize)
                    onDimChange(newDim)
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Save Settings")
            }
            OutlinedButton(
                onClick = {
                    onSizeChange(5)
                    onDimChange(1024)
                    localSize = "5"
                    localDim = "1024"
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Restore Default")
            }
        }
    }
}

fun scaleBitmapToLimit(bitmap: Bitmap, maxDim: Int): Bitmap {
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

fun decodeUriToBitmap(context: Context, uri: Uri, maxDim: Int): Bitmap? {
    return try {
        // Read stream into byte array exactly once to bypass any content provider stream-reopen limitations
        val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.readBytes()
        } ?: return null
        
        val options = android.graphics.BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        
        var inSampleSize = 1
        if (options.outWidth > maxDim || options.outHeight > maxDim) {
            val halfHeight = options.outHeight / 2
            val halfWidth = options.outWidth / 2
            while ((halfHeight / inSampleSize) >= maxDim && (halfWidth / inSampleSize) >= maxDim) {
                inSampleSize *= 2
            }
        }
        
        val decodeOptions = android.graphics.BitmapFactory.Options().apply {
            inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = true
        }
        
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
        
        bitmap?.let {
            if (it.width > maxDim || it.height > maxDim) {
                scaleBitmapToLimit(it, maxDim)
            } else {
                if (!it.isMutable) {
                    it.copy(Bitmap.Config.ARGB_8888, true)
                } else {
                    it
                }
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("decodeUriToBitmap", "Error decoding uri", e)
        null
    }
}

fun createPlaceholderBitmap(): Bitmap {
    val size = 512
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint()
    
    // Background Grid
    paint.color = android.graphics.Color.rgb(30, 41, 59) // slate-800
    canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
    
    // Grid lines
    paint.color = android.graphics.Color.rgb(51, 65, 85) // slate-700
    paint.strokeWidth = 2f
    for (i in 0..size step 64) {
        canvas.drawLine(i.toFloat(), 0f, i.toFloat(), size.toFloat(), paint)
        canvas.drawLine(0f, i.toFloat(), size.toFloat(), i.toFloat(), paint)
    }
    
    // Draw a prominent red circle (excellent for Edge and Blur testing)
    paint.color = android.graphics.Color.rgb(239, 68, 68) // red-500
    paint.style = android.graphics.Paint.Style.FILL
    canvas.drawCircle(size * 0.3f, size * 0.4f, 60f, paint)
    
    // Draw a prominent yellow rectangle
    paint.color = android.graphics.Color.rgb(234, 179, 8) // yellow-500
    canvas.drawRect(size * 0.55f, size * 0.25f, size * 0.85f, size * 0.55f, paint)
    
    // Draw a mock face outline to test Face Detection (two eyes and mouth)
    paint.color = android.graphics.Color.rgb(59, 130, 246) // blue-500
    canvas.drawCircle(size * 0.5f, size * 0.75f, 50f, paint)
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(size * 0.45f, size * 0.72f, 8f, paint)
    canvas.drawCircle(size * 0.55f, size * 0.72f, 8f, paint)
    paint.color = android.graphics.Color.BLACK
    canvas.drawRect(size * 0.47f, size * 0.78f, size * 0.53f, size * 0.80f, paint)
    
    // Text Banner top
    paint.color = android.graphics.Color.rgb(15, 23, 42) // dark slate
    canvas.drawRect(0f, 0f, size.toFloat(), 60f, paint)
    
    paint.color = android.graphics.Color.WHITE
    paint.textSize = 22f
    paint.textAlign = android.graphics.Paint.Align.CENTER
    paint.style = android.graphics.Paint.Style.FILL
    canvas.drawText("OpenCV Test Sample Active", size / 2f, 40f, paint)
    
    return bitmap
}

@Composable
fun CopyableCodeBlock(
    title: String,
    code: String,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var isCopied by remember { mutableStateOf(false) }

    LaunchedEffect(isCopied) {
        if (isCopied) {
            kotlinx.coroutines.delay(2000)
            isCopied = false
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium
                )
                TextButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(code))
                        isCopied = true
                        Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(if (isCopied) "Copied!" else "Copy")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = code,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
    }
}

@Composable
fun ApiDocsTab(remoteImage: Bitmap?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Python Integration & Remote GUI",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        Text(
            text = "Zero-Install OpenCV for Termux: Execute OpenCV operations from Python simply by using 'requests'. No 'pip install opencv' needed! The app acts as your full OpenCV engine and GUI.",
            style = MaterialTheme.typography.bodyMedium
        )

        remoteImage?.let {
            Text("Latest Displayed Image (from our 'imshow' replacement):", fontWeight = FontWeight.SemiBold)
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Remotely Displayed Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .background(Color.Black, RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Fit
            )
        }

        CopyableCodeBlock(
            title = "Python Script using ONLY `requests`",
            code = """
import requests
import time

# ZERO cv2 installation needed! 
# The Android app does all the heavy OpenCV processing natively.

print("--- 1. PROCESS LOCAL IMAGE ---")
with open('my_image.jpg', 'rb') as f:
    img_data = f.read()

# Send to the app's OpenCV engine: detect faces, edges, blur, etc.
resp = requests.post(
    'http://127.0.0.1:8080/process?task=edges',
    data=img_data,
    headers={'Content-Type': 'image/jpeg'}
)
if resp.status_code == 200:
    with open('output.jpg', 'wb') as f:
        f.write(resp.content)
    print("Saved output to output.jpg!")


print("\n--- 2. PROCESS LIVE CAMERA STREAM ---")
print("Streaming live edges directly from your phone camera...")
while True:
    try:
        # Get frame, process Canny Edges, and get result in one call!
        resp = requests.get('http://127.0.0.1:8080/camera/process?task=edges')
        
        if resp.status_code == 200:
            # Display it on the App GUI tab!
            requests.post(
                'http://127.0.0.1:8080/imshow',
                data=resp.content,
                headers={'Content-Type': 'image/jpeg'}
            )
        time.sleep(0.1) # Small delay to limit frame rate
    except Exception as e:
        print("Camera bridge not ready.")
        time.sleep(1)
            """.trimIndent()
        )
    }
}

@Composable
fun NetworkPhotoTab(
    statusMessage: String,
    isReady: Boolean,
    faceModelStatusMessage: String,
    onRetryFaceModelDownload: () -> Unit,
    processor: OpenCVProcessor,
    maxImageDimension: Int,
    params: CvParams,
    engineOn: Boolean,
    onEngineOnChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var processedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (sourceBitmap == null) {
            withContext(Dispatchers.IO) {
                try {
                    context.assets.open("man.jpg").use { inputStream ->
                        val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                        if (bitmap != null) {
                            val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                            withContext(Dispatchers.Main) {
                                sourceBitmap = mutableBitmap
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                sourceBitmap = createPlaceholderBitmap()
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("NetworkPhotoTab", "Failed to load default lena face from assets", e)
                    withContext(Dispatchers.Main) {
                        sourceBitmap = createPlaceholderBitmap()
                    }
                }
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val bitmap = decodeUriToBitmap(context, it, maxImageDimension)
                    if (bitmap != null) {
                        withContext(Dispatchers.Main) {
                            sourceBitmap = bitmap
                            processedBitmap = null
                            Toast.makeText(context, "Image loaded successfully!", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Could not decode selected image.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("NetworkPhotoTab", "Failed to pick/decode image", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to load image. It might be too large or invalid.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "CVBridge",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (engineOn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "OpenCV Processing Engine",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Switch(checked = engineOn, onCheckedChange = onEngineOnChange)
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        
        CopyableCodeBlock(
            title = "Termux Usage Example:",
            code = "curl -X POST --data-binary @image.jpg \\\n\"http://127.0.0.1:8080/process?task=edges\" > output.jpg"
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        
        Text(text = "App Sample Tester", style = MaterialTheme.typography.titleLarge)

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = { imagePickerLauncher.launch("image/*") },
                modifier = Modifier.weight(1f),
                enabled = engineOn
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Pick Image", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Select Image")
            }
            
            OutlinedButton(
                onClick = {
                    sourceBitmap = createPlaceholderBitmap()
                    processedBitmap = null
                    Toast.makeText(context, "Loaded test pattern!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
                enabled = engineOn
            ) {
                Text("Use Test Pattern")
            }
        }
        
        if (!engineOn) {
            Text(
                text = "Turn on the OpenCV engine to test sample patterns.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (sourceBitmap != null) {
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val tasks = listOf("Detect Heads", "Detect People", "Grayscale", "Blur", "Edges")
                tasks.forEach { task ->
                    Button(
                        onClick = {
                            isProcessing = true
                            coroutineScope.launch(Dispatchers.Default) {
                                val result = if (!engineOn) sourceBitmap!! else when (task) {
                                    "Detect Heads" -> processor.detectFaces(sourceBitmap!!, params.scaleFactor.toDouble(), params.minNeighbors, params.minSize)
                                    "Detect People" -> processor.detectPeople(sourceBitmap!!, params.scaleFactor.toDouble())
                                    "Grayscale" -> processor.toGrayscale(sourceBitmap!!)
                                    "Blur" -> processor.applyBlur(sourceBitmap!!, params.blurSize)
                                    "Edges" -> processor.detectEdges(sourceBitmap!!, params.cannyT1.toDouble(), params.cannyT2.toDouble())
                                    else -> sourceBitmap!!
                                }
                                withContext(Dispatchers.Main) {
                                    processedBitmap = result
                                    isProcessing = false
                                }
                            }
                        },
                        enabled = isReady && !isProcessing
                    ) {
                        Text(task)
                    }
                }
            }

            if (isProcessing) {
                CircularProgressIndicator()
            }

            val displayBitmap = processedBitmap ?: sourceBitmap
            displayBitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Preview Image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(Color.Black, RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

@Composable
fun LogTab() {
    val logs by LogManager.logs.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Network Processing Logs", style = MaterialTheme.typography.titleLarge)
            Row {
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString(LogManager.getAllLogs()))
                    Toast.makeText(context, "Copied logs to clipboard", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Copy")
                }
                TextButton(onClick = {
                    LogManager.clear()
                    Toast.makeText(context, "Logs cleared", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        
        Card(
            modifier = Modifier.fillMaxSize(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val scrollState = rememberScrollState()
            
            // Auto scroll to bottom when logs change
            LaunchedEffect(logs.size) {
                scrollState.animateScrollTo(scrollState.maxValue)
            }
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp)
            ) {
                if (logs.isEmpty()) {
                    Text(
                        "No logs available yet. Make network requests to see logs here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp)
                    )
                } else {
                    logs.forEach { log ->
                        val isError = log.contains("[ERROR]")
                        Text(
                            text = log,
                            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
