package com.example.udpservice.test

/**
 * Marks a test as requiring a physical device.
 *
 * Tests annotated with @DeviceOnly will be excluded from CI emulator runs.
 * Use this for tests that require:
 * - Real WiFi network (actual IP addresses)
 * - Boot receiver testing
 * - Battery/power behavior
 * - Device-specific hardware features
 *
 * To run only device tests:
 * ```
 * ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.annotation=com.example.udpservice.test.DeviceOnly
 * ```
 *
 * To exclude device tests (CI):
 * ```
 * ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.notAnnotation=com.example.udpservice.test.DeviceOnly
 * ```
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class DeviceOnly
