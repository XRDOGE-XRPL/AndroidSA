plugins {
    base
}

val compileSdkVersion = 34
val minSdkVersion = 26
val targetSdkVersion = 34
val supportedAbis = listOf("arm64-v8a", "armeabi-v7a")

val requiredFoundationFiles = listOf(
    "src/main/AndroidManifest.xml",
    "src/main/java/com/xrdoge/androidsa/MainActivity.kt",
    "src/main/java/com/xrdoge/androidsa/NativeBridge.kt",
    "src/main/java/com/xrdoge/androidsa/ui/AppRuntimeState.kt",
    "src/main/res/values/strings.xml",
    "src/main/res/values/themes.xml",
    "src/main/cpp/CMakeLists.txt",
    "src/main/cpp/native-lib.cpp",
    "src/main/cpp/native/network/RpcClient.hpp",
    "src/main/cpp/native/network/RpcClient.cpp",
    "src/main/cpp/native/logging/NativeLogger.hpp",
    "src/main/cpp/native/logging/NativeLogger.cpp"
)

val validateProjectFoundation = tasks.register("validateProjectFoundation") {
    group = "verification"
    description = "Validates the initial Android and native project foundation files."

    doLast {
        require(compileSdkVersion >= 34) {
            "compileSdkVersion must be at least 34"
        }
        require(minSdkVersion >= 26) {
            "minSdkVersion must be at least 26"
        }
        require(targetSdkVersion >= compileSdkVersion) {
            "targetSdkVersion must be greater than or equal to compileSdkVersion"
        }
        require(supportedAbis.containsAll(listOf("arm64-v8a", "armeabi-v7a"))) {
            "supportedAbis must include arm64-v8a and armeabi-v7a"
        }

        val missing = requiredFoundationFiles.filterNot {
            layout.projectDirectory.file(it).asFile.exists()
        }

        if (missing.isNotEmpty()) {
            throw GradleException(
                "Missing project foundation files:\n - ${missing.joinToString("\n - ")}"
            )
        }
    }
}

tasks.register("describeAndroidModule") {
    group = "help"
    description = "Prints the expected Android module configuration."

    doLast {
        println("compileSdk=$compileSdkVersion")
        println("minSdk=$minSdkVersion")
        println("targetSdk=$targetSdkVersion")
        println("abis=${supportedAbis.joinToString(",")}")
    }
}

tasks.named("assemble") {
    dependsOn(validateProjectFoundation)
}

tasks.named("check") {
    dependsOn(validateProjectFoundation)
}
