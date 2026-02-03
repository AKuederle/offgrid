plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.example.udpcli.MainKt")
}

dependencies {
    // Reliable UDP Transport
    implementation(project(":reliable-udp"))

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // CLI argument parsing
    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.6")

    // Unit Testing - JUnit 5
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.1")

    // MockK for mocking
    testImplementation("io.mockk:mockk:1.13.8")

    // Coroutines testing
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}

tasks.test {
    useJUnitPlatform()
}

// Distribution tasks for CLI installation
tasks.named<Jar>("jar") {
    manifest {
        attributes["Main-Class"] = "com.example.udpcli.MainKt"
    }
}

// Create a fat JAR with all dependencies
tasks.register<Jar>("fatJar") {
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(sourceSets.main.get().output)

    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    })

    manifest {
        attributes["Main-Class"] = "com.example.udpcli.MainKt"
    }
}
