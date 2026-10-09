plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

val miCatalogoApiBaseUrl = providers.gradleProperty("miCatalogoApiBaseUrl")
    // Production is the safe default for device builds. Local emulator work can override this
    // with -PmiCatalogoApiBaseUrl=http://10.0.2.2:8000/.
    .orElse("https://micatalogo.bsolutions.dev/")
    .map { url -> if (url.endsWith('/')) url else "$url/" }

// Release credentials remain outside the source tree and are supplied through
// Gradle properties or environment variables by the release build environment.
val releaseKeystoreFile = providers.gradleProperty("miCatalogoKeystoreFile")
    .orElse(providers.environmentVariable("MICATALOGO_KEYSTORE_FILE"))
    .orNull
val releaseKeystorePassword = providers.gradleProperty("miCatalogoKeystorePassword")
    .orElse(providers.environmentVariable("MICATALOGO_KEYSTORE_PASSWORD"))
    .orNull
val releaseKeyAlias = providers.gradleProperty("miCatalogoKeyAlias")
    .orElse(providers.environmentVariable("MICATALOGO_KEY_ALIAS"))
    .orNull
val releaseKeyPassword = providers.gradleProperty("miCatalogoKeyPassword")
    .orElse(providers.environmentVariable("MICATALOGO_KEY_PASSWORD"))
    .orNull
val hasReleaseSigning = listOf(
    releaseKeystoreFile,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    sourceSets.getByName("test").resources.srcDir("$rootDir/docs/api-contracts")
    sourceSets.getByName("androidTest").assets.srcDir("$rootDir/docs/api-contracts")
    if (providers.gradleProperty("miCatalogoSummaryUiCheck").orNull == "true") {
        sourceSets.getByName("androidTest").java.setSrcDirs(
            listOf("src/androidTest/java/com/example/bspos/presentation/dashboard", "src/androidTest/java/com/example/bspos/presentation/sales", "src/androidTest/java/com/example/bspos/presentation/pos", "src/androidTest/java/com/example/bspos/presentation/quote", "src/androidTest/java/com/example/bspos/presentation/dayclose")
        )
        sourceSets.getByName("androidTest").kotlin.setSrcDirs(
            listOf("src/androidTest/java/com/example/bspos/presentation/dashboard", "src/androidTest/java/com/example/bspos/presentation/sales", "src/androidTest/java/com/example/bspos/presentation/pos", "src/androidTest/java/com/example/bspos/presentation/quote", "src/androidTest/java/com/example/bspos/presentation/dayclose")
        )
    }
    namespace = "com.example.bspos"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.bsolutions.micatalogo"
        minSdk = 26
        targetSdk = 37
        versionCode = 84
        versionName = "1.0.83"
        buildConfigField("String", "MICATALOGO_API_BASE_URL", "\"${miCatalogoApiBaseUrl.get()}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = file(releaseKeystoreFile!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            if (providers.gradleProperty("miCatalogoOfflineCheck").orNull == "true") {
                applicationIdSuffix = ".offlinecheck"
                versionNameSuffix = "-offlinecheck"
            }
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
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

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.windowsizeclass)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.work)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // DataStore
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.biometric)

    // Coil
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)

    // POS barcode scanner
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.google.mlkit.barcode)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // MiCatalogo API foundation
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
