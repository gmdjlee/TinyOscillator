package com.tinyoscillator.core.testing.annotations

/**
 * JVM 단위 테스트, < 100ms, Emulator 없음.
 * JUnit4 @Category 마커 인터페이스 — Gradle useJUnit { includeCategories } 에서 필터 가능.
 */
interface FastTest

/**
 * Emulator / Chaquopy / 네트워크 필요.
 * JUnit4 @Category 마커 인터페이스.
 */
interface SlowTest
