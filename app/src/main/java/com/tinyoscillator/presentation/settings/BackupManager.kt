package com.tinyoscillator.presentation.settings

import android.content.Context
import android.net.Uri
import com.tinyoscillator.core.database.AppDatabase
import com.tinyoscillator.core.database.entity.EtfEntity
import com.tinyoscillator.core.database.entity.EtfHoldingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.cancellation.CancellationException

private val backupJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
}

// region API Backup Models

@Serializable
data class KiwoomApiBackup(
    val appKey: String,
    val secretKey: String,
    val mode: String
)

@Serializable
data class KisApiBackup(
    val appKey: String,
    val appSecret: String,
    val mode: String
)

@Serializable
data class KrxApiBackup(
    val id: String,
    val password: String
)

@Serializable
data class ApiBackup(
    val version: Int = 1,
    val type: String, // "kiwoom", "kis", "krx", "all_api"
    val kiwoom: KiwoomApiBackup? = null,
    val kis: KisApiBackup? = null,
    val krx: KrxApiBackup? = null
)

// endregion

// region ETF Backup Models

@Serializable
data class EtfBackupEntry(
    val ticker: String,
    val name: String,
    val isinCode: String,
    val indexName: String? = null,
    val totalFee: Double? = null,
    val updatedAt: Long = 0
)

@Serializable
data class EtfHoldingBackupEntry(
    val etfTicker: String,
    val stockTicker: String,
    val date: String,
    val stockName: String,
    val weight: Double? = null,
    val shares: Long = 0,
    val amount: Long = 0
)

@Serializable
data class EtfDataBackup(
    val version: Int = 1,
    val type: String = "etf_data",
    val startDate: String? = null,
    val endDate: String? = null,
    val etfs: List<EtfBackupEntry>,
    val holdings: List<EtfHoldingBackupEntry>
)

// endregion

object BackupManager {

    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val GCM_TAG_BITS = 128
    private const val PBKDF2_ITERATIONS = 210000
    private const val KEY_BITS = 256

    fun encrypt(plainText: String, password: String): ByteArray {
        val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
        val keySpec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS)
        val secretKey = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(keySpec)
        val aesKey = SecretKeySpec(secretKey.encoded, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return salt + iv + cipherText
    }

    fun decrypt(encrypted: ByteArray, password: String): String {
        val salt = encrypted.copyOfRange(0, SALT_SIZE)
        val iv = encrypted.copyOfRange(SALT_SIZE, SALT_SIZE + IV_SIZE)
        val cipherText = encrypted.copyOfRange(SALT_SIZE + IV_SIZE, encrypted.size)
        val keySpec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS)
        val secretKey = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(keySpec)
        val aesKey = SecretKeySpec(secretKey.encoded, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }

    // region API Backup/Restore

    suspend fun exportApiBackup(context: Context, uri: Uri, type: String, password: String): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                val backup = when (type) {
                    "kiwoom" -> {
                        val config = loadKiwoomConfig(context)
                        ApiBackup(
                            type = "kiwoom",
                            kiwoom = KiwoomApiBackup(config.appKey, config.secretKey, config.investmentMode.name)
                        )
                    }
                    "kis" -> {
                        val config = loadKisConfig(context)
                        ApiBackup(
                            type = "kis",
                            kis = KisApiBackup(config.appKey, config.appSecret, config.investmentMode.name)
                        )
                    }
                    "krx" -> {
                        val creds = loadKrxCredentials(context)
                        ApiBackup(
                            type = "krx",
                            krx = KrxApiBackup(creds.id, creds.password)
                        )
                    }
                    else -> {
                        val kiwoomConfig = loadKiwoomConfig(context)
                        val kisConfig = loadKisConfig(context)
                        val krxCreds = loadKrxCredentials(context)
                        ApiBackup(
                            type = "all_api",
                            kiwoom = KiwoomApiBackup(kiwoomConfig.appKey, kiwoomConfig.secretKey, kiwoomConfig.investmentMode.name),
                            kis = KisApiBackup(kisConfig.appKey, kisConfig.appSecret, kisConfig.investmentMode.name),
                            krx = KrxApiBackup(krxCreds.id, krxCreds.password)
                        )
                    }
                }
                val json = backupJson.encodeToString(backup)
                val encryptedBytes = encrypt(json, password)
                context.contentResolver.openOutputStream(uri)?.use { it.write(encryptedBytes) }
                Result.success(1)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun importApiBackup(context: Context, uri: Uri, password: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val encryptedBytes = context.contentResolver.openInputStream(uri)?.use {
                    it.readBytes()
                } ?: return@withContext Result.failure(Exception("파일을 읽을 수 없습니다"))

                val json = try {
                    decrypt(encryptedBytes, password)
                } catch (_: Exception) {
                    return@withContext Result.failure(Exception("비밀번호가 올바르지 않거나 파일이 손상되었습니다"))
                }

                val backup = backupJson.decodeFromString<ApiBackup>(json)
                val restoredParts = mutableListOf<String>()

                backup.kiwoom?.let { kw ->
                    val mode = com.tinyoscillator.core.api.InvestmentMode.entries.find { it.name == kw.mode }
                        ?: com.tinyoscillator.core.api.InvestmentMode.MOCK
                    val prefs = getEncryptedPrefsForBackup(context)
                    prefs.edit()
                        .putString("kiwoom_app_key", kw.appKey)
                        .putString("kiwoom_secret_key", kw.secretKey)
                        .putString("kiwoom_mode", mode.name)
                        .apply()
                    restoredParts.add("Kiwoom")
                }
                backup.kis?.let { kis ->
                    val mode = com.tinyoscillator.core.api.InvestmentMode.entries.find { it.name == kis.mode }
                        ?: com.tinyoscillator.core.api.InvestmentMode.MOCK
                    val prefs = getEncryptedPrefsForBackup(context)
                    prefs.edit()
                        .putString("kis_app_key", kis.appKey)
                        .putString("kis_app_secret", kis.appSecret)
                        .putString("kis_mode", mode.name)
                        .apply()
                    restoredParts.add("KIS")
                }
                backup.krx?.let { krx ->
                    saveKrxCredentials(context, KrxCredentials(krx.id, krx.password))
                    restoredParts.add("KRX")
                }

                Result.success("${restoredParts.joinToString(", ")} 복원 완료")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // endregion

    // region ETF Data Backup/Restore

    suspend fun exportEtfData(
        context: Context,
        uri: Uri,
        db: AppDatabase,
        startDate: String?,
        endDate: String?
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val dao = db.etfDao()
            val etfs = dao.getAllEtfsList().map {
                EtfBackupEntry(it.ticker, it.name, it.isinCode, it.indexName, it.totalFee, it.updatedAt)
            }
            val holdings = if (startDate != null && endDate != null) {
                dao.getHoldingsByDateRange(startDate, endDate)
            } else {
                dao.getAllHoldings()
            }.map {
                EtfHoldingBackupEntry(it.etfTicker, it.stockTicker, it.date, it.stockName, it.weight, it.shares, it.amount)
            }
            val backup = EtfDataBackup(
                startDate = startDate,
                endDate = endDate,
                etfs = etfs,
                holdings = holdings
            )
            val json = backupJson.encodeToString(backup)
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            Result.success(holdings.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importEtfData(context: Context, uri: Uri, db: AppDatabase): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val json = context.contentResolver.openInputStream(uri)?.use {
                    it.bufferedReader().readText()
                } ?: return@withContext Result.failure(Exception("파일을 읽을 수 없습니다"))

                val backup = backupJson.decodeFromString<EtfDataBackup>(json)
                val dao = db.etfDao()

                val etfEntities = backup.etfs.map {
                    EtfEntity(it.ticker, it.name, it.isinCode, it.indexName, it.totalFee, it.updatedAt)
                }
                val holdingEntities = backup.holdings.map {
                    EtfHoldingEntity(it.etfTicker, it.stockTicker, it.date, it.stockName, it.weight, it.shares, it.amount)
                }

                if (etfEntities.isNotEmpty()) dao.insertEtfs(etfEntities)
                // Insert in chunks to avoid SQLite variable limit
                holdingEntities.chunked(500).forEach { chunk ->
                    dao.insertHoldings(chunk)
                }

                Result.success("ETF ${etfEntities.size}개, 보유종목 ${holdingEntities.size}건 복원 완료")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // endregion

    private fun getEncryptedPrefsForBackup(context: Context): android.content.SharedPreferences {
        val masterKey = androidx.security.crypto.MasterKey.Builder(context.applicationContext)
            .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
            .build()
        return androidx.security.crypto.EncryptedSharedPreferences.create(
            context.applicationContext,
            "api_settings_encrypted",
            masterKey,
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}
