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

val qaProfile = providers.gradleProperty("miCatalogoQaProfile").orNull
val qaRoot = "src/androidTest/java/com/example/bspos"
val qaShared = "$qaRoot/data/local/tenant"
val qaFolders = when (qaProfile) {
    null, "all" -> listOf("src/androidTest/java")
    "local" -> listOf("$qaRoot/data/local", "$qaRoot/domain")
    "tenant" -> listOf(qaShared, "$qaRoot/presentation/orders")
    "ui" -> listOf("$qaRoot/presentation", qaShared)
    "live" -> listOf("$qaRoot/data/micatalogo", "$qaRoot/presentation/emulatorqa", qaShared)
    else -> error("Unknown miCatalogoQaProfile: $qaProfile")
}

android {
    sourceSets.getByName("test").resources.srcDir("$rootDir/docs/api-contracts")
    sourceSets.getByName("androidTest").assets.srcDir("$rootDir/docs/api-contracts")
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
    if (qaProfile != null) {
        check(listOf("miCatalogoEmulatorUiCheck", "miCatalogoTenantCheck", "miCatalogoSummaryUiCheck")
            .none { providers.gradleProperty(it).orNull == "true" }) { "Choose one QA profile; do not combine legacy filters." }
        val folders = qaFolders + "src/androidTest/java/com/example/bspos/qa"
        sourceSets.getByName("androidTest").java.setSrcDirs(folders)
        sourceSets.getByName("androidTest").kotlin.setSrcDirs(folders)
    }
    if (providers.gradleProperty("miCatalogoEmulatorUiCheck").orNull == "true") {
        val folders=listOf("src/androidTest/java/com/example/bspos/presentation", "src/androidTest/java/com/example/bspos/data/local/tenant")
        sourceSets.getByName("androidTest").java.setSrcDirs(folders)
        sourceSets.getByName("androidTest").kotlin.setSrcDirs(folders)
    }
    if (providers.gradleProperty("miCatalogoTenantCheck").orNull == "true") {
        sourceSets.getByName("androidTest").java.setSrcDirs(
            listOf("src/androidTest/java/com/example/bspos/data/local/tenant", "src/androidTest/java/com/example/bspos/presentation/orders")
        )
        sourceSets.getByName("androidTest").kotlin.setSrcDirs(
            listOf("src/androidTest/java/com/example/bspos/data/local/tenant", "src/androidTest/java/com/example/bspos/presentation/orders")
        )
    }
    if (providers.gradleProperty("miCatalogoSummaryUiCheck").orNull == "true") {
        sourceSets.getByName("androidTest").java.setSrcDirs(
            listOf("src/androidTest/java/com/example/bspos/presentation/dashboard", "src/androidTest/java/com/example/bspos/presentation/sales", "src/androidTest/java/com/example/bspos/presentation/pos", "src/androidTest/java/com/example/bspos/presentation/quote", "src/androidTest/java/com/example/bspos/presentation/dayclose", qaShared)
        )
        sourceSets.getByName("androidTest").kotlin.setSrcDirs(
            listOf("src/androidTest/java/com/example/bspos/presentation/dashboard", "src/androidTest/java/com/example/bspos/presentation/sales", "src/androidTest/java/com/example/bspos/presentation/pos", "src/androidTest/java/com/example/bspos/presentation/quote", "src/androidTest/java/com/example/bspos/presentation/dayclose", qaShared)
        )
    }
    namespace = "com.example.bspos"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.bsolutions.micatalogo"
        minSdk = 26
        targetSdk = 37
        versionCode = 122
        versionName = "1.0.121"
        buildConfigField("String", "MICATALOGO_API_BASE_URL", "\"${miCatalogoApiBaseUrl.get()}\"")

        testInstrumentationRunner = if (qaProfile != null) "com.example.bspos.qa.IsolatedQaRunner" else "androidx.test.runner.AndroidJUnitRunner"
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
                applicationIdSuffix = when {
                    qaProfile != null -> ".offlinecheck.syncqa"
                    providers.gradleProperty("miCatalogoEmulatorUiCheck").orNull == "true" -> ".offlinecheck.emulatorqa"
                    providers.gradleProperty("miCatalogoTenantCheck").orNull == "true" -> ".offlinecheck.tenancy"
                    else -> ".offlinecheck"
                }
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

if (qaProfile != null) {
    val sourceFiles = files(qaFolders.map { fileTree(it) { include("**/*.kt") } }).asFileTree
    val manifestDirectory = layout.buildDirectory.dir("generated/qaManifest").get().asFile
    android.sourceSets.getByName("androidTest").assets.srcDir(manifestDirectory)
    val profileName = qaProfile
    val manifestTask = tasks.register("generateQaTestManifest") {
        inputs.files(sourceFiles)
        inputs.property("profile", profileName)
        outputs.dir(manifestDirectory)
        doLast {
            val declared = sourceFiles.files.distinct().flatMap { source ->
                val text = source.readText()
                val pkg = Regex("(?m)^package\\s+([\\w.]+)").find(text)?.groupValues?.get(1)
                val klass = Regex("\\bclass\\s+(\\w+Test)\\b").find(text)?.groupValues?.get(1)
                if (pkg == null || klass == null) emptyList() else
                    Regex("@Test(?:\\([^)]*\\))?\\s+fun\\s+(\\w+)").findAll(text)
                        .map { "$pkg.$klass#${it.groupValues[1]}" }.toList()
            }.distinct().sorted()
            check(declared.isNotEmpty()) { "QA profile contains no tests" }
            val required = when (profileName) {
                "all" -> listOf("CatalogDatabaseTest", "TenantMigrationTest", "MixedCreditSaleTest", "PosSaleLiveIntegrationTest", "DecantsWorkspaceTest")
                "local" -> listOf("CatalogDatabaseTest", "CompleteSaleUseCaseTest", "TenantMigrationTest")
                "tenant" -> listOf("TenantMigrationTest", "TenantSyncTest", "OrdersEntryTest")
                "ui" -> listOf("MixedCreditSaleTest", "OrdersEntryTest", "DecantsWorkspaceTest")
                "live" -> listOf("PosSaleLiveIntegrationTest", "InventoryImportLiveIntegrationTest", "EmulatorJourneyTest")
                else -> emptyList()
            }
            required.forEach { klass -> check(declared.any { it.contains(".$klass#") }) { "Missing expected QA class: $klass" } }
            manifestDirectory.mkdirs()
            manifestDirectory.resolve("qa-test-manifest.txt").writeText(declared.joinToString("\n"))
            println("QA profile $profileName: ${declared.size} declared tests; compilation is mandatory")
        }
    }
    tasks.matching { it.name == "mergeDebugAndroidTestAssets" }.configureEach { dependsOn(manifestTask) }
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
