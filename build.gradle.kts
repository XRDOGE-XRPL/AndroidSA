plugins {
    base
}

group = "com.xrdoge.androidsa"
version = "0.1.0"

tasks.named("build") {
    dependsOn(":app:build")
}

tasks.named("check") {
    dependsOn(":app:check")
}
