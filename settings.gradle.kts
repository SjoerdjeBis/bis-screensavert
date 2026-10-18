pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Voor libadb-android: zelf koppelen via Draadloze foutopsporing.
        maven("https://jitpack.io")
    }
}

rootProject.name = "BisScreenSaver"
include(":app")
