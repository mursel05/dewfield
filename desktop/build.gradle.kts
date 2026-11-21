import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

val gdxVersion = libs.versions.gdx.get()

dependencies {
    implementation(project(":core"))
    implementation(libs.gdx.backend.lwjgl3)
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
    implementation("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-desktop")
}

application {
    mainClass.set("dev.mursal.dewfield.desktop.DesktopLauncherKt")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.file("assets")
    // Pass extra arguments with: ./gradlew :desktop:run --args="--tour out/"
}
