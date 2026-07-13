package com.tinyoscillator.core.database.migration

import android.app.Application
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v36→v37 마이그레이션 테스트 — `bear_snapshot` 테이블 신설이 기존 데이터를 보존하는지, 신규
 * 테이블 스키마가 올바른지 검증한다 (TASK_bear_signal_console.md §6.1 Phase 3.5-1 하드 게이트
 * "마이그레이션 테스트(기존→신 스키마 데이터 보존)").
 *
 * `AppDatabaseMigrations`의 `MIGRATION_N_M` 객체들은 파일-private이라 외부에서 이름으로 직접
 * 참조할 수 없다 — 공개 API인 [AppDatabaseMigrations.ALL] 배열에서 `startVersion`/`endVersion`으로
 * 대상 마이그레이션을 찾아 raw [SupportSQLiteDatabase]에 직접 적용한다. Room의 `MigrationTestHelper`
 * (스키마 asset 배선 필요)를 쓰지 않고도, 실제 프로덕션 마이그레이션 객체 자체를 실행해 "기존 행
 * 보존"과 "신규 테이블 컬럼 정확성"을 결정적으로 검증할 수 있다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class Migration36To37Test {

    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    private fun migration36To37() =
        AppDatabaseMigrations.ALL.first { it.startVersion == 36 && it.endVersion == 37 }

    @Before
    fun setup() {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(36) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // v36 시점에 이미 존재하던 테이블 하나(bear_signal_auto_cache)만 재현한다 —
                    // 이 테스트의 관심사는 "기존 데이터 보존 + 신규 테이블 생성"이지 전체 스키마 재현이 아니다.
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `bear_signal_auto_cache` (" +
                            "`indicator_key` TEXT NOT NULL, " +
                            "`value` REAL NOT NULL, " +
                            "`source` TEXT NOT NULL, " +
                            "`updated_at` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`indicator_key`))"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    // 이 테스트는 onUpgrade 경로가 아니라 마이그레이션 객체를 직접 호출해 검증한다.
                }
            })
            .build()
        helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        db = helper.writableDatabase
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `마이그레이션 후 기존 테이블의 데이터가 보존된다`() {
        db.execSQL(
            "INSERT INTO bear_signal_auto_cache (indicator_key, value, source, updated_at) " +
                "VALUES ('s2_up3', 14.0, 'AUTO', 1000)"
        )

        migration36To37().migrate(db)

        db.query("SELECT * FROM bear_signal_auto_cache WHERE indicator_key = 's2_up3'").use {
            assertTrue(it.moveToFirst())
            assertEquals(14.0, it.getDouble(it.getColumnIndexOrThrow("value")), 1e-9)
            assertEquals("AUTO", it.getString(it.getColumnIndexOrThrow("source")))
        }
    }

    @Test
    fun `마이그레이션 후 bear_snapshot 테이블이 기대한 컬럼으로 생성된다`() {
        migration36To37().migrate(db)

        db.execSQL(
            "INSERT INTO bear_snapshot " +
                "(day, phase, lead, gate, s1, s2, s3, amp, configBasis, inputsJson, fieldMetaJson, createdAt) " +
                "VALUES ('2026-07-11', 'AMBER', 3, 1, 1, 1, 1, 1.3, '신영 2026.6.30', '{}', '{}', 1000)"
        )

        db.query("SELECT * FROM bear_snapshot WHERE day = '2026-07-11'").use {
            assertTrue(it.moveToFirst())
            assertEquals("AMBER", it.getString(it.getColumnIndexOrThrow("phase")))
            assertEquals(3, it.getInt(it.getColumnIndexOrThrow("lead")))
            assertEquals(1.3, it.getDouble(it.getColumnIndexOrThrow("amp")), 1e-9)
            assertEquals(1000L, it.getLong(it.getColumnIndexOrThrow("createdAt")))
        }
    }

    @Test
    fun `bear_snapshot의 day는 기본키라 동일값 재삽입시 유일성이 유지된다`() {
        migration36To37().migrate(db)

        db.execSQL(
            "INSERT OR REPLACE INTO bear_snapshot " +
                "(day, phase, lead, gate, s1, s2, s3, amp, configBasis, inputsJson, fieldMetaJson, createdAt) " +
                "VALUES ('2026-07-11', 'AMBER', 3, 1, 1, 1, 1, 1.3, '신영 2026.6.30', '{}', '{}', 1000)"
        )
        db.execSQL(
            "INSERT OR REPLACE INTO bear_snapshot " +
                "(day, phase, lead, gate, s1, s2, s3, amp, configBasis, inputsJson, fieldMetaJson, createdAt) " +
                "VALUES ('2026-07-11', 'RED', 8, 3, 3, 3, 2, 1.6, '신영 2026.6.30', '{}', '{}', 2000)"
        )

        db.query("SELECT * FROM bear_snapshot").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("RED", cursor.getString(cursor.getColumnIndexOrThrow("phase")))
        }
    }
}
