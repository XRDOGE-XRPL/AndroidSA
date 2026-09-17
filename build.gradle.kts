plugins {
    base
}

val nativeValidationDir = layout.buildDirectory.dir("native-validation")

val nativeHostCheck by tasks.registering {
    group = "verification"
    description = "Runs the repository's native host validation path"
    doLast {
        val buildDir = nativeValidationDir.get().asFile
        buildDir.mkdirs()

        exec {
            commandLine("cmake", "-S", file("app/src/main/cpp").absolutePath, "-B", buildDir.absolutePath, "-DANDROIDSA_ENABLE_NATIVE_TESTS=ON")
        }
        exec {
            commandLine("cmake", "--build", buildDir.absolutePath, "--target", "client_state_test")
        }
        exec {
            commandLine("ctest", "--test-dir", buildDir.absolutePath, "--output-on-failure")
        }
    }
}

tasks.named("build") {
    dependsOn(":app:build")
}

tasks.named("check") {
    dependsOn(":app:check")
}

tasks.register("releaseValidation") {
    group = "verification"
    description = "Runs the repo's required release validation path: native host tests, JVM unit tests, and assemble"
    dependsOn(nativeHostCheck)
    dependsOn(":app:testDebugUnitTest")
    dependsOn(":app:assemble")
}
