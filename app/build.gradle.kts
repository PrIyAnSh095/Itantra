plugins {
    id("com.android.application")
}

android {
    namespace = "com.itantra.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.itantra.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
    }

    androidResources {
        noCompress += listOf("onnx", "tflite")
    }
}

dependencies {
    // ONNX Runtime for STT, TTS, and Translation
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.16.3")

    // TensorFlow Lite for Silero VAD
    implementation("org.tensorflow:tensorflow-lite:2.16.1")

    // Coroutines for background execution
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // WorkManager for background model tasks
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Standard AndroidX & Material UI
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.cardview:cardview:1.0.0")

    // Architecture Components
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-ktx:1.8.2")

    // Unit test
    testImplementation("junit:junit:4.13.2")
}
