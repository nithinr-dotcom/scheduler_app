plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Firebase (Google Sign-In + cloud backup) switches on once app/google-services.json exists.
// Without it the app still runs fully offline.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.wac.worktrack"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.wac.worktrack"
        minSdk = 26
        targetSdk = 37
        versionCode = 3
        versionName = "3.0"
    }

    buildTypes {
        getByName("release") {
            // R8 strips unused code (most of material-icons-extended) and unused resources.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

// Room writes each database version's schema here, so migrations can be checked against it.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    // Installs the Compose libraries' baseline profiles, so cold start isn't run interpreted.
    implementation("androidx.profileinstaller:profileinstaller:1.4.0")

    // Compose UI
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.10.2")
    implementation("androidx.fragment:fragment-ktx:1.9.1")

    // App lock (fingerprint / face / screen-lock PIN)
    implementation("androidx.biometric:biometric:1.1.0")

    // Location reminders (geofences)
    implementation("com.google.android.gms:play-services-location:21.4.0")

    // Health Connect (read-only steps, sleep, workouts)
    implementation("androidx.health.connect:connect-client:1.1.0")

    // Local database
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    // Background work (daily cloud backup)
    implementation("androidx.work:work-runtime-ktx:2.12.0")

    // Firebase + Google Sign-In + Google Tasks (read-only) authorization
    implementation(platform("com.google.firebase:firebase-bom:35.1.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.1")
    implementation("com.google.android.gms:play-services-auth:22.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")

    testImplementation("junit:junit:4.13.2")
}
