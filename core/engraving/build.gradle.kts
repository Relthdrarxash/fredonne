plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core:music"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    // La machine de CI est en anglais : on fixe la langue de la JVM en français.
    systemProperty("user.language", "fr")
}
