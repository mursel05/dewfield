import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Build-time tools: packs the rendered art into a texture atlas.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(libs.gdx.tools)
}

tasks.register<JavaExec>("packAtlas") {
    group = "art"
    description = "Packs tools/art/out/*.png into assets/atlas/game.atlas"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("dev.mursal.dewfield.tools.PackAtlasKt")
    args(rootProject.file("tools/art/out").path, rootProject.file("assets/atlas").path)
}
