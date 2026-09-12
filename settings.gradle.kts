pluginManagement {
    repositories {
        maven(url = "https://dl.google.com/dl/android/maven2/")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven(url = "https://dl.google.com/dl/android/maven2/")
        google()
        mavenCentral()
    }
}

rootProject.name = "AndroidSA"
include(":app")
