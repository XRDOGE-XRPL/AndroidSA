import com.android.build.api.dsl.ApplicationExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    val androidSaGoogleMavenUrl = project.findProperty("androidsa.google.maven.url") as String?
        ?: System.getenv("ANDROIDSA_GOOGLE_MAVEN_URL")
    repositories {
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
    dependencies {
        classpath("com.android.tools.build:gradle:8.5.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.24")
    }
}

apply(plugin = "com.android.application")
apply(plugin = "org.jetbrains.kotlin.android")

extensions.configure<ApplicationExtension>("android") {
    namespace = "com.xrdoge.xrpl.androidsa"
    compileSdk = 34
    ndkVersion = "27.3.13750724"

    defaultConfig {
        applicationId = "com.xrdoge.xrpl.androidsa"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++20"
            }
        }
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.5"
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")

    add("implementation", composeBom)
    add("androidTestImplementation", composeBom)

    add("implementation", "androidx.core:core-ktx:1.13.1")
    add("implementation", "androidx.activity:activity-compose:1.9.0")
    add("implementation", "androidx.compose.material3:material3")
    add("implementation", "androidx.compose.ui:ui")
    add("implementation", "androidx.compose.ui:ui-tooling-preview")

    add("testImplementation", "junit:junit:4.13.2")
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "17"
}
