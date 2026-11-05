import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(kotlin("test-junit"))
}

tasks.test {
    // Phase0ScoreTest reads OCR dumps and hand-labelled truth from samples/.
    systemProperty("samples.dir", rootProject.file("samples").absolutePath)
    inputs.files(fileTree(rootProject.file("samples")) { include("ocr*/**", "truth/**") }).withPropertyName("samples")
    providers.gradleProperty("dump").orNull?.let { systemProperty("dump", it.ifEmpty { "true" }) }
    testLogging { showStandardStreams = true }
}
