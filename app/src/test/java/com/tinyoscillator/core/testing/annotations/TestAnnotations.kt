package com.tinyoscillator.core.testing.annotations

/** JVM 단위 테스트, < 100ms, Emulator 없음 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class FastTest

/** Emulator / Chaquopy / 네트워크 필요 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class SlowTest
