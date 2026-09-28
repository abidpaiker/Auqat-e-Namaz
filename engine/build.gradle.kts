// :engine — pure Kotlin/JVM. No Android, no third-party dependencies.
// Everything the app needs to compute is here, and everything here is unit-tested.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

// Compile with whatever JDK Android Studio uses (17 or newer), but emit Java 17 bytecode
// so it matches the :app module. No specific JDK version needs to be installed.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
    testLogging { events("failed"); showStandardStreams = true }
}