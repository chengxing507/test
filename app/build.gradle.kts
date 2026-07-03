plugins {
    id("com.android.application")
}

android {
    namespace = "com.train12306"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.train12306"
        minSdk = 21
        targetSdk = 33
        versionCode = 2
        versionName = "2.0"

        vectorDrawables {
            useSupportLibrary = false
        }
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    // OkHttp + Gson 替代原生的 HttpURLConnection
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // AndroidX 支持库
    implementation("androidx.appcompat:appcompat:1.6.1")
}
