package com.example.udpservice.test

/**
 * Test Stage Annotations
 *
 * The test suite has 3 stages:
 *
 * ## Stage 1: Unit Tests (JVM)
 * Location: src/test/
 * Run with: ./gradlew :udp-service:test
 * No annotation needed - these run on JVM without device.
 *
 * ## Stage 2: Emulator + Device Tests
 * Location: src/androidTest/ (no annotation)
 * Run with: ./gradlew :udp-service:connectedAndroidTest
 * These tests work on both emulator and physical device.
 * Examples: UDP reception, database persistence
 *
 * ## Stage 3: Device-Only Tests
 * Location: src/androidTest/ with @DeviceOnly annotation
 * Run with: ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.annotation=com.example.udpservice.test.DeviceOnly
 * Exclude in CI: ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.notAnnotation=com.example.udpservice.test.DeviceOnly
 *
 * Use @DeviceOnly for tests that require:
 * - Real WiFi network (actual IP addresses, not 10.0.2.x)
 * - Boot receiver testing
 * - Battery/power behavior
 * - Device-specific hardware features
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class DeviceOnly
