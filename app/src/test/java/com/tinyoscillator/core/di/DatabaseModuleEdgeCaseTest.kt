package com.tinyoscillator.core.di

import androidx.room.migration.Migration
import org.junit.Assert.*
import org.junit.Test

/**
 * DatabaseModule edge case tests.
 *
 * Tests migration configuration and database builder setup.
 */
class DatabaseModuleEdgeCaseTest {

    @Test
    fun `DatabaseModule은 MIGRATION_1_2 필드를 포함한다`() {
        val field = DatabaseModule::class.java.getDeclaredField("MIGRATION_1_2")
        field.isAccessible = true
        val migration = field.get(DatabaseModule)
        assertNotNull(migration)
        assertTrue("Should be a Migration instance", migration is Migration)
    }

    @Test
    fun `MIGRATION_1_2는 version 1에서 2로 마이그레이션한다`() {
        val field = DatabaseModule::class.java.getDeclaredField("MIGRATION_1_2")
        field.isAccessible = true
        val migration = field.get(DatabaseModule) as Migration
        assertEquals(1, migration.startVersion)
        assertEquals(2, migration.endVersion)
    }

    @Test
    fun `provideAppDatabase 메서드는 @Singleton 어노테이션을 가진다`() {
        val method = DatabaseModule::class.java.getDeclaredMethod(
            "provideAppDatabase", android.content.Context::class.java
        )
        val singleton = method.getAnnotation(javax.inject.Singleton::class.java)
        assertNotNull("provideAppDatabase should have @Singleton", singleton)
    }

    @Test
    fun `provideStockMasterDao 메서드가 존재한다`() {
        val methods = DatabaseModule::class.java.methods
        val daoMethod = methods.find { it.name == "provideStockMasterDao" }
        assertNotNull("provideStockMasterDao should exist", daoMethod)
    }

    @Test
    fun `provideFinancialCacheDao 메서드가 존재한다`() {
        val methods = DatabaseModule::class.java.methods
        val daoMethod = methods.find { it.name == "provideFinancialCacheDao" }
        assertNotNull("provideFinancialCacheDao should exist", daoMethod)
    }

    @Test
    fun `provideAnalysisCacheDao 메서드가 존재한다`() {
        val methods = DatabaseModule::class.java.methods
        val daoMethod = methods.find { it.name == "provideAnalysisCacheDao" }
        assertNotNull("provideAnalysisCacheDao should exist", daoMethod)
    }

    @Test
    fun `provideAnalysisHistoryDao 메서드가 존재한다`() {
        val methods = DatabaseModule::class.java.methods
        val daoMethod = methods.find { it.name == "provideAnalysisHistoryDao" }
        assertNotNull("provideAnalysisHistoryDao should exist", daoMethod)
    }
}
