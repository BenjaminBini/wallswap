import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Kept out of this public repo: set it in ~/.gradle/gradle.properties or ORG_GRADLE_PROJECT_unsplashAccessKey.
val unsplashAccessKey: String = providers.gradleProperty("unsplashAccessKey").orNull?.takeIf { it.isNotBlank() }
    ?: error("Missing Gradle property unsplashAccessKey (your Unsplash Access Key)")

android {
    namespace = "com.benjaminbini.wallswap"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.benjaminbini.wallswap"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "UNSPLASH_ACCESS_KEY", "\"$unsplashAccessKey\"")
    }

    // Committed key so every build (local or CI) can update the installed app in place.
    signingConfigs {
        create("shared") {
            storeFile = file("wallswap.keystore")
            storePassword = "wallswap"
            keyAlias = "wallswap"
            keyPassword = "wallswap"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("shared")
        }
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.work:work-runtime-ktx:2.10.5")
}
