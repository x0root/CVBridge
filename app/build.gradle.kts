import java.net.URL
import java.io.InputStream

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.termuxcvbridge.xyzabc"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }

  packaging {
    resources {
      excludes += "META-INF/INDEX.LIST"
      excludes += "META-INF/io.netty.versions.properties"
    }
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  // implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  
  // OpenCV
  implementation(libs.opencv)
  
  // Ktor Server
  implementation(libs.ktor.server.core)
  implementation(libs.ktor.server.netty)
  implementation(libs.ktor.server.cors)
  implementation(libs.ktor.server.status.pages)
  implementation(libs.ktor.server.content.negotiation)
  implementation(libs.ktor.serialization.kotlinx.json)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

tasks.register("downloadAssetsAndModels") {
    val xmlDest = file("src/main/assets/haarcascade_frontalface_default.xml")
    val imgDest = file("src/main/assets/lena.jpg")
    inputs.property("xmlUrl", "https://cdn.jsdelivr.net/gh/opencv/opencv@master/data/haarcascades/haarcascade_frontalface_default.xml")
    inputs.property("imgUrl", "https://cdn.jsdelivr.net/gh/opencv/opencv@master/samples/data/lena.jpg")
    outputs.files(xmlDest, imgDest)
    doLast {
        if (!xmlDest.parentFile.exists()) {
            xmlDest.parentFile.mkdirs()
        }
        // 1. Download XML Face Cascade Model
        val xmlMirrors = listOf(
            "https://cdn.jsdelivr.net/gh/opencv/opencv@master/data/haarcascades/haarcascade_frontalface_default.xml",
            "https://raw.githubusercontent.com/opencv/opencv/master/data/haarcascades/haarcascade_frontalface_default.xml"
        )
        var xmlSuccess = false
        for (urlStr in xmlMirrors) {
            try {
                println("Downloading face cascade model from: $urlStr")
                val url = URL(urlStr)
                url.openStream().use { input ->
                    xmlDest.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                println("Success! Face cascade model saved to assets.")
                xmlSuccess = true
                break
            } catch (e: Exception) {
                println("Failed to download from $urlStr: ${e.message}")
            }
        }

        // 2. Download Lena test face image
        val imgMirrors = listOf(
            "https://cdn.jsdelivr.net/gh/opencv/opencv@master/samples/data/lena.jpg",
            "https://raw.githubusercontent.com/opencv/opencv/master/samples/data/lena.jpg"
        )
        var imgSuccess = false
        for (urlStr in imgMirrors) {
            try {
                println("Downloading Lena sample face image from: $urlStr")
                val url = URL(urlStr)
                url.openStream().use { input ->
                    imgDest.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                println("Success! Lena face sample image saved to assets.")
                imgSuccess = true
                break
            } catch (e: Exception) {
                println("Failed to download image from $urlStr: ${e.message}")
            }
        }

        if (!xmlSuccess) {
            if (xmlDest.exists()) xmlDest.delete()
            throw RuntimeException("Could not download Haar Cascade XML model")
        }
        if (!imgSuccess) {
            if (imgDest.exists()) imgDest.delete()
            throw RuntimeException("Could not download Lena face sample image")
        }
    }
}

tasks.named("preBuild") {
    dependsOn("downloadAssetsAndModels")
}

