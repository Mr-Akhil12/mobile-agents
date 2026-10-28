plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android) }
android {
    namespace = "dev.lfm.nativebridge"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    ndkVersion = "28.0.13004108"
    defaultConfig {
        minSdk = 28
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild { cmake { arguments += "-DCMAKE_BUILD_TYPE=Release"; cppFlags += "-std=c++17" } }
    }
    externalNativeBuild { cmake { path = file("CMakeLists.txt"); version = "3.22.1" } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}

dependencyLocking { lockAllConfigurations() }
