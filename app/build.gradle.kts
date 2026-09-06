plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // Embedded CPython for the python_run tool (spec §3 / P2).
    alias(libs.plugins.chaquopy)
}

android {
    namespace = "com.zenwayne.zenagent"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zenwayne.zenagent"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        vectorDrawables { useSupportLibrary = true }
        // The agentflow engine is arm64-only, and Chaquopy requires an
        // explicit ABI list so it packages just the matching runtime.
        ndk { abiFilters += listOf("arm64-v8a") }
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    // The agentflow AAR bundles jni/arm64-v8a/*.so — ensure they land in the APK.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

// python_run ships the standard library only (spec §8): no `pip` block, so the
// APK grows by just the interpreter + stdlib. numpy/pandas remain a possible
// later build flavor. Sources live in app/src/main/python/.
chaquopy {
    defaultConfig {
        // Track the build host's default `python3`: Chaquopy requires a
        // buildPython of the same major.minor as the target runtime, and
        // pinning a version the machine lacks fails configuration outright.
        version = "3.14"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    // SAF DocumentFile tree for the authorized /shared root (P3).
    implementation(libs.androidx.documentfile)
    debugImplementation(libs.androidx.ui.tooling)
    // ZenAgent on-device inference bridge (agentflow DSL + arm64 JNI lib).
    implementation(files("libs/agentflow-android.aar"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
