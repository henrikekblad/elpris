plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseSigningValues = mapOf(
    "storePath" to System.getenv("SIGNING_STORE_PATH"),
    "storePassword" to System.getenv("SIGNING_STORE_PASSWORD"),
    "keyAlias" to System.getenv("SIGNING_KEY_ALIAS"),
    "keyPassword" to System.getenv("SIGNING_KEY_PASSWORD")
)
val hasReleaseSigning = releaseSigningValues.values.all { !it.isNullOrBlank() }

android {
    namespace = "se.sensnology.elpris"
    compileSdk = 36
    buildFeatures { buildConfig = true }
    bundle { language { enableSplit = false } }

    defaultConfig {
        applicationId = "se.sensnology.elpris"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "1.5.1"
    }

    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseSigningValues.getValue("storePath")!!)
                storePassword = releaseSigningValues.getValue("storePassword")
                keyAlias = releaseSigningValues.getValue("keyAlias")
                keyPassword = releaseSigningValues.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
}
