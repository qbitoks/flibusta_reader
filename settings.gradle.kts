pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        // Google-hosted Maven Central mirror: far faster than repo1 on some networks.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Google-hosted Maven Central mirror: far faster than repo1 on some networks.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        // sherpa-onnx (offline Piper voices) is published only as a GitHub-release AAR mirrored by JitPack.
        maven("https://jitpack.io") {
            content { includeGroup("com.github.k2-fsa.sherpa-onnx") }
        }
    }
}

rootProject.name = "Folio"

include(":app")
include(":baselineprofile")
include(":core:model")
include(":core:format")
include(":core:layout")
include(":core:data")
include(":core:opds")
include(":core:designsystem")
include(":core:speech")
include(":feature:library")
include(":feature:reader")
include(":feature:catalog")
