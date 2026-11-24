import com.android.build.api.variant.ApplicationAndroidComponentsExtension

// No version here: the root build script puts AGP on the classpath (see its buildscript block).
plugins {
    id("com.android.application")
}

val gdxVersion = libs.versions.gdx.get()
val abis = listOf("armeabi-v7a", "arm64-v8a", "x86_64")

val natives: Configuration = configurations.create("natives")

android {
    namespace = "dev.mursal.dewfield"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.mursal.dewfield"
        minSdk = 24
        targetSdk = 36
        versionCode = 4
        versionName = "0.4.0"
        ndk { abiFilters += abis }
    }

    signingConfigs {
        // Release signing comes from environment variables so no secret lives in the repo.
        // Without them the release build falls back to the debug key (fine for sideloading).
        create("release") {
            val storePath = System.getenv("DEWFIELD_KEYSTORE")
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = System.getenv("DEWFIELD_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("DEWFIELD_KEY_ALIAS")
                keyPassword = System.getenv("DEWFIELD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (System.getenv("DEWFIELD_KEYSTORE") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf("META-INF/*.kotlin_module", "META-INF/versions/**")
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.gdx.backend.android)
    for (abi in abis) {
        natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-$abi")
        natives("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-$abi")
    }
}

/** Unpacks the libGDX native .so files from their jars into <abi>/ folders for packaging. */
abstract class UnpackNatives : DefaultTask() {
    @get:InputFiles
    abstract val nativeJars: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val fs: FileSystemOperations

    @get:Inject
    abstract val archives: ArchiveOperations

    @TaskAction
    fun unpack() {
        fs.delete { delete(outputDir) }
        for (jar in nativeJars.files) {
            val abi = jar.name.substringAfter("natives-").removeSuffix(".jar")
            fs.copy {
                from(archives.zipTree(jar)) { include("*.so") }
                into(outputDir.dir(abi))
            }
        }
    }
}

val unpackNatives = tasks.register<UnpackNatives>("unpackNatives") {
    nativeJars.from(natives)
    outputDir.set(layout.buildDirectory.dir("gdx-natives"))
}

extensions.getByType<ApplicationAndroidComponentsExtension>().onVariants { variant ->
    variant.sources.jniLibs?.addGeneratedSourceDirectory(unpackNatives, UnpackNatives::outputDir)
    variant.sources.assets?.addStaticSourceDirectory(rootProject.file("assets").path)
}
