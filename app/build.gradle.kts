plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.identify"
    compileSdk = 35
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.example.identify"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += listOf("arm64-v8a") }
        externalNativeBuild {
            cmake {
                // Native code is ALWAYS Release. Debug native builds make inference many times slower.
                arguments += listOf(
                    "-DCMAKE_BUILD_TYPE=Release",
                    "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
                )
            }
        }
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }

    buildFeatures { viewBinding = true }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity:1.9.0")
    implementation("androidx.fragment:fragment:1.8.2")
    implementation("androidx.core:core:1.13.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.navigation:navigation-fragment:2.7.7")
    implementation("androidx.navigation:navigation-ui:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.0")
    implementation("androidx.lifecycle:lifecycle-livedata:2.8.0")
    implementation("androidx.room:room-runtime:2.6.1")
    annotationProcessor("androidx.room:room-compiler:2.6.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation(project(":core"))
    testImplementation("junit:junit:4.13.2")
}
