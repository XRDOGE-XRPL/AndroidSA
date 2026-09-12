buildscript {
    repositories {
        val androidSaGoogleMavenUrl = findProperty("androidsa.google.maven.url") as String?
            ?: System.getenv("ANDROIDSA_GOOGLE_MAVEN_URL")
        if (androidSaGoogleMavenUrl.isNullOrBlank()) {
            google()
        } else {
            maven(url = uri(androidSaGoogleMavenUrl)) {
                name = "AndroidSaGoogleMirror"
                isAllowInsecureProtocol = androidSaGoogleMavenUrl.startsWith("http://")
            }
        }
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.5.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.24")
    }
}

plugins {
    base
}

tasks.named("build") {
    dependsOn(":app:build")
}

tasks.named("check") {
    dependsOn(":app:check")
}
