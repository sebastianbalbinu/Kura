plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.sebastianbalbinu.kura"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.sebastianbalbinu.kura"
        minSdk = 26
        targetSdk = 36
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "0.1." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    signingConfigs {
        create("kura") {
            // La llave vive en los secretos de GitHub, nunca en el repo.
            val ks = file(System.getenv("KURA_KEYSTORE_PATH") ?: "none.jks")
            if (ks.exists()) {
                storeFile = ks
                // La contraseña no es el secreto: sin el archivo de la llave no sirve de nada.
                storePassword = "kura-firma"
                keyAlias = "kura"
                keyPassword = "kura-firma"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val kura = signingConfigs.getByName("kura")
            signingConfig = if (kura.storeFile != null) kura else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    packaging {
        jniLibs { useLegacyPackaging = true }
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    val bom = platform("androidx.compose:compose-bom:2025.06.01")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // IA dentro del celular (Qwen 3.5 en formato .litertlm)
    implementation("com.google.ai.edge.litertlm:litertlm-android:latest.release")
}
