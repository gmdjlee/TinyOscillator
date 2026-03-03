package com.tinyoscillator.core.di

import com.tinyoscillator.core.api.KisApiClient
import com.tinyoscillator.core.api.KiwoomApiClient
import com.tinyoscillator.domain.usecase.CalcOscillatorUseCase
import org.junit.Assert.*
import org.junit.Test
import javax.inject.Singleton

/**
 * AppModule DI configuration tests.
 */
class AppModuleTest {

    @Test
    fun `provideOkHttpClient 메서드가 존재한다`() {
        val method = AppModule::class.java.methods.find { it.name == "provideOkHttpClient" }
        assertNotNull("provideOkHttpClient should exist", method)
    }

    @Test
    fun `provideOkHttpClient는 Singleton 어노테이션을 가진다`() {
        val method = AppModule::class.java.methods.find { it.name == "provideOkHttpClient" }!!
        val singleton = method.getAnnotation(Singleton::class.java)
        assertNotNull("provideOkHttpClient should have @Singleton", singleton)
    }

    @Test
    fun `provideKiwoomApiClient 메서드가 존재한다`() {
        val method = AppModule::class.java.methods.find { it.name == "provideKiwoomApiClient" }
        assertNotNull("provideKiwoomApiClient should exist", method)
    }

    @Test
    fun `provideKisApiClient 메서드가 존재한다`() {
        val method = AppModule::class.java.methods.find { it.name == "provideKisApiClient" }
        assertNotNull("provideKisApiClient should exist", method)
    }

    @Test
    fun `provideJson 메서드가 존재한다`() {
        val method = AppModule::class.java.methods.find { it.name == "provideJson" }
        assertNotNull("provideJson should exist", method)
    }

    @Test
    fun `provideCalcOscillatorUseCase 메서드가 존재한다`() {
        val method = AppModule::class.java.methods.find { it.name == "provideCalcOscillatorUseCase" }
        assertNotNull("provideCalcOscillatorUseCase should exist", method)
    }

    @Test
    fun `provideFinancialRepository 메서드가 존재한다`() {
        val method = AppModule::class.java.methods.find { it.name == "provideFinancialRepository" }
        assertNotNull("provideFinancialRepository should exist", method)
    }

    @Test
    fun `AppModule은 object 선언이다`() {
        // object 선언은 INSTANCE 필드를 가진다
        val instanceField = AppModule::class.java.getDeclaredField("INSTANCE")
        assertNotNull("AppModule should be an object (has INSTANCE)", instanceField)
    }

    @Test
    fun `provideCalcOscillatorUseCase는 새 인스턴스를 반환한다`() {
        val useCase = AppModule.provideCalcOscillatorUseCase()
        assertNotNull(useCase)
        assertTrue(useCase is CalcOscillatorUseCase)
    }

    @Test
    fun `provideJson은 Json 인스턴스를 반환한다`() {
        val json = AppModule.provideJson()
        assertNotNull(json)
    }

    @Test
    fun `provideOkHttpClient는 OkHttpClient를 반환한다`() {
        val client = AppModule.provideOkHttpClient()
        assertNotNull(client)
        // 30초 타임아웃 확인
        assertEquals(30_000, client.connectTimeoutMillis)
        assertEquals(30_000, client.readTimeoutMillis)
        assertEquals(30_000, client.writeTimeoutMillis)
    }

    @Test
    fun `provideKiwoomApiClient는 OkHttpClient를 받는다`() {
        val method = AppModule::class.java.methods.find { it.name == "provideKiwoomApiClient" }!!
        assertEquals(1, method.parameterCount)
    }

    @Test
    fun `provideFinancialRepository는 3개 파라미터를 받는다`() {
        val method = AppModule::class.java.methods.find { it.name == "provideFinancialRepository" }!!
        assertEquals(3, method.parameterCount)
    }
}
