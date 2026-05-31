plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("com.squareup.sqldelight")
}

android {
    namespace = "com.jdcr.kmpdatabase"
    compileSdk = 35

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

sqldelight {
    database("AppDatabase") {
        packageName = "tasksq"
    }
}

dependencies {
    api("com.github.ljwx:jdcrlog:1.2.3")
    implementation(libs.sqldelight.runtime)
    implementation(libs.sqldelight.android.driver) // Android 专用驱动
//    implementation("com.squareup.sqldelight:native-driver:2.0.1") // ios
}