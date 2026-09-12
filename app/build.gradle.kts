plugins {
    base
}

val requiredFoundationFiles = listOf(
    "src/main/AndroidManifest.xml",
    "src/main/java/com/xrdoge/androidsa/MainActivity.kt",
    "src/main/res/values/strings.xml",
    "src/main/res/values/themes.xml"
)

val validateProjectFoundation = tasks.register("validateProjectFoundation") {
    group = "verification"
    description = "Validates the initial Android project foundation files."

    doLast {
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

tasks.named("assemble") {
    dependsOn(validateProjectFoundation)
}

tasks.named("check") {
    dependsOn(validateProjectFoundation)
}
