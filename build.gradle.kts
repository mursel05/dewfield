// The Android Gradle plugin has to live in the same class loader as the Kotlin Gradle plugin:
// AGP's built-in Kotlin support creates Kotlin plugin types that refer back to AGP classes.
// The plugins block below puts the Kotlin plugin on the root classpath, so AGP goes there too,
// but only when settings included :android. Machines without an Android SDK (and the CI test job)
// then never need Google's Maven repository.
buildscript {
    if (findProject(":android") != null) {
        repositories {
            google()
            mavenCentral()
        }
        dependencies {
            classpath(libs.android.gradle.plugin)
        }
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
