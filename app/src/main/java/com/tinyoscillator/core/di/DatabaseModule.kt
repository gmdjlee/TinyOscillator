package com.tinyoscillator.core.di

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tinyoscillator.core.database.AppDatabase
import com.tinyoscillator.core.database.migration.AppDatabaseMigrations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
import java.io.File
import javax.inject.Singleton

/**
 * AppDatabase 인스턴스를 생성하고 마이그레이션/백업 정책을 적용하는 모듈.
 *
 * Phase 4.5에서 800+ 줄 단일 파일을 세 파일로 분리:
 *  - 본 파일: `AppDatabase` 빌더 + 마이그레이션 실패 시 백업 로직
 *  - [AppDatabaseMigrations]: 전체 25개 마이그레이션 정의
 *  - [DaoModule]: Hilt DAO 프로바이더 22개
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return try {
            buildDatabase(context)
        } catch (e: Exception) {
            Timber.e(e, "Database 빌더 생성 실패 → 포트폴리오 백업 후 재생성 시도")
            backupPortfolioData(context)
            context.deleteDatabase("tiny_oscillator.db")
            buildDatabase(context)
        }
    }

    private fun buildDatabase(context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "tiny_oscillator.db"
        )
            .addMigrations(*AppDatabaseMigrations.ALL)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    Timber.d("Database opened successfully (version %d)", db.version)
                }
            })
            .build()

    /**
     * 마이그레이션 실패 시 포트폴리오 데이터를 CSV 파일로 백업.
     * 포트폴리오는 사용자가 직접 입력한 데이터이므로 API로 복구할 수 없다.
     */
    private fun backupPortfolioData(context: Context) {
        val dbFile = context.getDatabasePath("tiny_oscillator.db")
        if (!dbFile.exists()) return

        try {
            val db = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
            val backupDir = File(context.filesDir, "migration_backup")
            backupDir.mkdirs()
            val timestamp = System.currentTimeMillis()

            val tables = listOf("portfolios", "portfolio_holdings", "portfolio_transactions")
            for (table in tables) {
                try {
                    db.rawQuery("SELECT * FROM $table", null).use { cursor ->
                        if (cursor.count == 0) return@use
                        val file = File(backupDir, "${table}_$timestamp.csv")
                        val cols = cursor.columnNames
                        val sb = StringBuilder()
                        sb.appendLine(cols.joinToString(","))
                        while (cursor.moveToNext()) {
                            val row = cols.indices.joinToString(",") { i ->
                                val v = cursor.getString(i) ?: ""
                                "\"${v.replace("\"", "\"\"")}\""
                            }
                            sb.appendLine(row)
                        }
                        file.writeText(sb.toString())
                        Timber.i("포트폴리오 백업 완료: %s (%d건)", file.name, cursor.count)
                    }
                } catch (_: Exception) {
                    Timber.w("테이블 %s 백업 실패 (존재하지 않을 수 있음)", table)
                }
            }
            db.close()
        } catch (e: Exception) {
            Timber.e(e, "포트폴리오 백업 실패 — DB 파일을 열 수 없음")
        }
    }
}
