pluginManagement {
    plugins {
        id("com.android.application") version "8.5.2"
        id("org.jetbrains.kotlin.android") version "1.9.24"
    }
    repositories {
        val androidSaGoogleMavenUrl = providers.gradleProperty("androidsa.google.maven.url")
            .orElse(providers.environmentVariable("ANDROIDSA_GOOGLE_MAVEN_URL"))
            .orNull
        if (androidSaGoogleMavenUrl.isNullOrBlank()) {
            google {
                content {
                    includeGroupByRegex("androidx.*")
                    includeGroupByRegex("com\\.android.*")
                    includeGroupByRegex("com\\.google.*")
                }
            }
        } else {
            maven(url = uri(androidSaGoogleMavenUrl)) {
                name = "AndroidSaGoogleMirror"
                isAllowInsecureProtocol = androidSaGoogleMavenUrl.startsWith("http://")
                content {
                    includeGroupByRegex("androidx.*")
                    includeGroupByRegex("com\\.android.*")
                    includeGroupByRegex("com\\.google.*")
                }
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            val pluginVersion = requested.version ?: return@eachPlugin
            when (requested.id.id) {
                "com.android.application" -> {
                    useModule("com.android.tools.build:gradle:$pluginVersion")
                }
                "org.jetbrains.kotlin.android" -> {
                    useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:$pluginVersion")
                }
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        val androidSaGoogleMavenUrl = providers.gradleProperty("androidsa.google.maven.url")
            .orElse(providers.environmentVariable("ANDROIDSA_GOOGLE_MAVEN_URL"))
            .orNull
        if (androidSaGoogleMavenUrl.isNullOrBlank()) {
            google {
                content {
                    includeGroupByRegex("androidx.*")
                    includeGroupByRegex("com\\.android.*")
                    includeGroupByRegex("com\\.google.*")
                }
            }
        } else {
            maven(url = uri(androidSaGoogleMavenUrl)) {
                name = "AndroidSaGoogleMirror"
                isAllowInsecureProtocol = androidSaGoogleMavenUrl.startsWith("http://")
                content {
                    includeGroupByRegex("androidx.*")
                    includeGroupByRegex("com\\.android.*")
                    includeGroupByRegex("com\\.google.*")
                }
            }
        }
        mavenCentral()
    }
}

rootProject.name = "AndroidSA"
include(":app")
