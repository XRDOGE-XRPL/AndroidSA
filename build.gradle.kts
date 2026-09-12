plugins {
    base
}

tasks.named("build") {
    dependsOn(":app:build")
}

tasks.named("check") {
    dependsOn(":app:check")
}
