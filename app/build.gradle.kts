import java.util.Base64
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import java.security.MessageDigest

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
// Licensed font resources are stored losslessly as gzip/base64 for connector-safe transport.
// Decode offline before resource discovery. No runtime download or executable payload.
val fontHashes = mapOf(
    "nunito_medium" to "74920a51bc96148840daa512e1e7f132bc4acc422389a558f85df9602769bbce",
    "nunito_regular" to "ce4a6ed4e751214fe08c58e1f7e42b0dd2119cafa2f0bf8aa06f77ab63e9ec6c",
    "nunito_semibold" to "8697328550b10d787f15eecdbbf8bff1339c5f3423a89cb8eb0ad7e2e0d1ea24"
)
fontHashes.forEach { (name, expected) ->
    val encoded = file("font-source/$name.ttf.gz.b64").readText()
    val bytes = GZIPInputStream(ByteArrayInputStream(Base64.getMimeDecoder().decode(encoded))).use { it.readBytes() }
    val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    require(actual == expected) { "Font resource checksum mismatch: $name" }
    val target = file("src/main/res/font/$name.ttf")
    target.parentFile.mkdirs()
    if (!target.exists() || !target.readBytes().contentEquals(bytes)) target.writeBytes(bytes)
}

android {
    namespace = "com.workflow.operaciones"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.workflow.operaciones"
        minSdk = 26
        targetSdk = 35
        versionCode = 102
        versionName = "1.0.2-controls"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        create("production") {
            System.getenv("WORKFLOW_KEYSTORE_PATH")?.let { storeFile = file(it) }
            storePassword = System.getenv("WORKFLOW_STORE_PASSWORD")
            keyAlias = System.getenv("WORKFLOW_KEY_ALIAS")
            keyPassword = System.getenv("WORKFLOW_KEY_PASSWORD")
        }
    }
    buildTypes {
        debug { applicationIdSuffix = ".review"; versionNameSuffix = "-debug" }
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("production")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
}
