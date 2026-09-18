pluginManagement {
    repositories {
        val androidSaGoogleMavenUrl = providers.gradleProperty("androidsa.google.maven.url")
            .orElse(providers.environmentVariable("ANDROIDSA_GOOGLE_MAVEN_URL"))
            .orNull
        val androidSaLocalMavenRepo = providers.gradleProperty("androidsa.local.maven.repo")
            .orElse(providers.environmentVariable("ANDROIDSA_LOCAL_MAVEN_REPO"))
            .orNull

        if (!androidSaLocalMavenRepo.isNullOrBlank()) {
            maven(url = uri(androidSaLocalMavenRepo)) {
                name = "AndroidSaLocalMavenRepo"
                content {
                    includeGroupByRegex("androidx.*")
                    includeGroupByRegex("com\\.android.*")
                    includeGroupByRegex("com\\.google.*")
                }
            }
        }

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
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        val androidSaGoogleMavenUrl = providers.gradleProperty("androidsa.google.maven.url")
            .orElse(providers.environmentVariable("ANDROIDSA_GOOGLE_MAVEN_URL"))
            .orNull
        val androidSaLocalMavenRepo = providers.gradleProperty("androidsa.local.maven.repo")
            .orElse(providers.environmentVariable("ANDROIDSA_LOCAL_MAVEN_REPO"))
            .orNull

        if (!androidSaLocalMavenRepo.isNullOrBlank()) {
            maven(url = uri(androidSaLocalMavenRepo)) {
                name = "AndroidSaLocalMavenRepo"
                content {
                    includeGroupByRegex("androidx.*")
                    includeGroupByRegex("com\\.android.*")
                    includeGroupByRegex("com\\.google.*")
                }
            }
        }

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
        mavenLocal()
        mavenCentral()
    }
}

rootProject.name = "AndroidSA"
include(":app")
