pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "dewfield"

include(":core", ":desktop", ":tools")

// The Android module needs the Android SDK and Google's Maven repository.
// It is included only when an SDK is configured, so the game, tests and desktop
// build still work on machines (and CI jobs) without one.
// Set ANDROID_HOME to an empty value to leave the module out on purpose (the CI test job does).
val androidSdk = listOf(System.getenv("ANDROID_HOME"), System.getenv("ANDROID_SDK_ROOT"))
    .firstOrNull { it != null }
    ?: file("local.properties").takeIf { it.exists() }?.readLines()
        ?.firstOrNull { it.startsWith("sdk.dir=") }?.substringAfter("=")
if (!androidSdk.isNullOrBlank() && file(androidSdk).isDirectory) {
    include(":android")
}
