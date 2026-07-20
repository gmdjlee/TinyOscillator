package com.tinyoscillator.core.di

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [DatabaseModule.provideAppDatabase] 마이그레이션 실패 안전망 (P1a-2,
 * TASK_code_review_improvements.md).
 *
 * Room의 `build()`는 lazy — 과거에는 try 블록이 `buildDatabase()` 호출만 감쌌기 때문에
 * 마이그레이션 에러가 첫 DAO 쿼리 시점에야 발생해 catch 경로(백업→삭제→재생성)에 도달할 수
 * 없었다. 본 테스트는 다운그레이드 불가 상태(버전 999)의 기존 DB 파일을 미리 만들어 두고
 * `provideAppDatabase`를 호출해, eager 검증(`openHelper.writableDatabase` 즉시 open)이
 * catch 경로를 실제로 발화시켜 예외 없이 재생성된 DB를 반환하는지 검증한다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class DatabaseModuleRecoveryTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `다운그레이드 불가 DB 파일이 있어도 예외 없이 백업 후 재생성된다`() {
        val dbFile = context.getDatabasePath("tiny_oscillator.db")
        dbFile.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { legacyDb ->
            legacyDb.version = 999
        }

        val db = DatabaseModule.provideAppDatabase(context)

        try {
            val writable = db.openHelper.writableDatabase
            assertTrue("재생성된 DB가 열려 있어야 한다", writable.isOpen)
            assertTrue(
                "재생성된 DB 버전은 다운그레이드 불가 상태(999)보다 낮아야 한다(정상 재생성)",
                writable.version < 999
            )
        } finally {
            db.close()
        }
    }
}
