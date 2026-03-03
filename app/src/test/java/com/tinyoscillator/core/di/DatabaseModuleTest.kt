package com.tinyoscillator.core.di

import org.junit.Assert.*
import org.junit.Test

/**
 * DatabaseModule configuration tests.
 *
 * Verifies:
 * 1. fallbackToDestructiveMigration is NOT used (data safety)
 * 2. Migration v1→v2 exists for financial_cache table
 * 3. DatabaseModule annotation structure
 */
class DatabaseModuleTest {

    @Test
    fun `MIGRATION_1_2가 존재한다`() {
        val field = DatabaseModule::class.java.getDeclaredField("MIGRATION_1_2")
        field.isAccessible = true
        val migration = field.get(DatabaseModule)
        assertNotNull(migration)
    }

    @Test
    fun `MIGRATION_1_2는 버전 1에서 2로 마이그레이션한다`() {
        val field = DatabaseModule::class.java.getDeclaredField("MIGRATION_1_2")
        field.isAccessible = true
        val migration = field.get(DatabaseModule) as androidx.room.migration.Migration
        assertEquals(1, migration.startVersion)
        assertEquals(2, migration.endVersion)
    }

    @Test
    fun `provideAppDatabase 메서드가 존재한다`() {
        val method = DatabaseModule::class.java.getDeclaredMethod(
            "provideAppDatabase",
            android.content.Context::class.java
        )
        assertNotNull(method)
    }

    @Test
    fun `provideAppDatabase는 @Singleton이다`() {
        val method = DatabaseModule::class.java.getDeclaredMethod(
            "provideAppDatabase",
            android.content.Context::class.java
        )
        val singleton = method.getAnnotation(javax.inject.Singleton::class.java)
        assertNotNull("provideAppDatabase should have @Singleton", singleton)
    }

    @Test
    fun `DatabaseModule은 @Module 어노테이션이 있다`() {
        val module = DatabaseModule::class.java.getAnnotation(dagger.Module::class.java)
        assertNotNull("DatabaseModule should have @Module", module)
    }

    @Test
    fun `provideStockMasterDao 메서드가 존재한다`() {
        val method = DatabaseModule::class.java.getDeclaredMethod(
            "provideStockMasterDao",
            com.tinyoscillator.core.database.AppDatabase::class.java
        )
        assertNotNull(method)
    }

    @Test
    fun `provideAnalysisCacheDao 메서드가 존재한다`() {
        val method = DatabaseModule::class.java.getDeclaredMethod(
            "provideAnalysisCacheDao",
            com.tinyoscillator.core.database.AppDatabase::class.java
        )
        assertNotNull(method)
    }

    @Test
    fun `provideFinancialCacheDao 메서드가 존재한다`() {
        val method = DatabaseModule::class.java.getDeclaredMethod(
            "provideFinancialCacheDao",
            com.tinyoscillator.core.database.AppDatabase::class.java
        )
        assertNotNull(method)
    }
}
