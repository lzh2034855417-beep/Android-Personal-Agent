import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val apaVersionName = "0.1.1"
val signingProperties = Properties()
val signingPropertiesFile = rootProject.file("keystore.properties")

if (signingPropertiesFile.isFile) {
    signingPropertiesFile.inputStream().use(signingProperties::load)
}
val hasReleaseSigningProperties = signingPropertiesFile.isFile && signingProperties.getProperty("storeFile") != null
val requestedReleaseTask = gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }

if (requestedReleaseTask && !hasReleaseSigningProperties) {
    throw GradleException("Release signing requires a local keystore.properties file")
}

android {
    namespace = "com.aegis.apa"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.aegis.apa"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = apaVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            signingProperties.getProperty("storeFile")?.let { storeFilePath ->
                signingConfig = signingConfigs.create("release") {
                    storeFile = file(storeFilePath)
                    storePassword = signingProperties.getProperty("storePassword")
                    keyAlias = signingProperties.getProperty("keyAlias")
                    keyPassword = signingProperties.getProperty("keyPassword")
                }
            }
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set("APA-v$apaVersionName-${variant.name}.apk")
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("de.boehrsi:devicemarketingnames:0.7.1")
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
