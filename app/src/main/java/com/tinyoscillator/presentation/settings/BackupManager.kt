package com.tinyoscillator.presentation.settings

import android.content.Context
import android.net.Uri
import com.tinyoscillator.core.database.AppDatabase

/**
 * 백업/복원 퍼사드. 암호화·모델·Export·Import 모듈로 분할되어 있으며,
 * 기존 외부 호출부(`BackupSettingsSection`, 테스트)와의 호환을 위해 동일 시그니처를 유지한다.
 *
 * - 암호화/복호화 ↦ [BackupEncryption]
 * - 직렬화 모델 ↦ `BackupModels.kt`
 * - DB → JSON/TSV Export ↦ [BackupExporter]
 * - JSON → DB Import ↦ [BackupImporter]
 */
object BackupManager {

    // region Encryption facade

    fun encrypt(plainText: String, password: String): ByteArray =
        BackupEncryption.encrypt(plainText, password)

    fun decrypt(encrypted: ByteArray, password: String): String =
        BackupEncryption.decrypt(encrypted, password)

    // endregion

    // region Export facade

    suspend fun exportApiBackup(
        context: Context,
        uri: Uri,
        type: String,
        password: String
    ): Result<Int> = BackupExporter.exportApiBackup(context, uri, type, password)

    suspend fun exportEtfData(
        context: Context,
        uri: Uri,
        db: AppDatabase,
        startDate: String?,
        endDate: String?
    ): Result<Int> = BackupExporter.exportEtfData(context, uri, db, startDate, endDate)

    suspend fun exportPortfolioData(
        context: Context,
        uri: Uri,
        db: AppDatabase
    ): Result<Int> = BackupExporter.exportPortfolioData(context, uri, db)

    suspend fun exportConsensusData(
        context: Context,
        uri: Uri,
        db: AppDatabase
    ): Result<Int> = BackupExporter.exportConsensusData(context, uri, db)

    suspend fun exportFearGreedData(
        context: Context,
        uri: Uri,
        db: AppDatabase
    ): Result<Int> = BackupExporter.exportFearGreedData(context, uri, db)

    suspend fun exportAllDataForAnalysis(
        context: Context,
        uri: Uri,
        db: AppDatabase,
        onProgress: (String) -> Unit = {}
    ): Result<Int> = BackupExporter.exportAllDataForAnalysis(context, uri, db, onProgress)

    // endregion

    // region Import facade

    suspend fun importApiBackup(
        context: Context,
        uri: Uri,
        password: String
    ): Result<String> = BackupImporter.importApiBackup(context, uri, password)

    suspend fun importEtfData(
        context: Context,
        uri: Uri,
        db: AppDatabase
    ): Result<String> = BackupImporter.importEtfData(context, uri, db)

    suspend fun importPortfolioData(
        context: Context,
        uri: Uri,
        db: AppDatabase
    ): Result<String> = BackupImporter.importPortfolioData(context, uri, db)

    suspend fun importConsensusData(
        context: Context,
        uri: Uri,
        db: AppDatabase
    ): Result<String> = BackupImporter.importConsensusData(context, uri, db)

    suspend fun importFearGreedData(
        context: Context,
        uri: Uri,
        db: AppDatabase
    ): Result<String> = BackupImporter.importFearGreedData(context, uri, db)

    // endregion

    // region TSV helpers (test API — must remain as BackupManager members)

    internal fun formatTimestamp(millis: Long): String = formatTimestampInternal(millis)

    internal fun Any?.toTsv(): String = this.toTsvInternal()

    // endregion
}
