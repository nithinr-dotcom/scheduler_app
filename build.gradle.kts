buildscript {
    dependencies {
        // AGP 9 has built-in Kotlin; pin a newer Kotlin so the Compose compiler plugin matches.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
    }
}

plugins {
    id("com.android.application") version "9.2.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    id("com.google.devtools.ksp") version "2.3.12" apply false
    id("com.google.gms.google-services") version "4.5.0" apply false
}
