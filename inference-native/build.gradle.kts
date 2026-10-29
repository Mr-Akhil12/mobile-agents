plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android) }
val lfmAbi = providers.gradleProperty("lfmAbi").getOrElse("arm64-v8a")
require(lfmAbi in setOf("arm64-v8a", "x86_64")) { "Unsupported lfmAbi" }
android {
    namespace = "dev.lfm.nativebridge"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    ndkVersion = "28.0.13004108"
    defaultConfig {
        minSdk = 28
        ndk { abiFilters += lfmAbi }
        externalNativeBuild { cmake {
            arguments += "-DCMAKE_BUILD_TYPE=Release"
            if (providers.gradleProperty("lfmCcache").getOrElse("false").toBoolean()) {
                arguments += listOf("-DCMAKE_C_COMPILER_LAUNCHER=ccache", "-DCMAKE_CXX_COMPILER_LAUNCHER=ccache")
            }
            cppFlags += "-std=c++17"
        } }
    }
    externalNativeBuild { cmake { path = file("CMakeLists.txt"); version = "3.22.1" } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}

dependencyLocking { lockAllConfigurations() }
