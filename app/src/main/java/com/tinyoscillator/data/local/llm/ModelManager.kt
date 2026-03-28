package com.tinyoscillator.data.local.llm

import android.app.ActivityManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GGUF 모델 관리자
 *
 * - 추천 모델 목록 (크기/RAM 요구사항)
 * - 디바이스 RAM 감지 → 적합 모델 추천
 * - 모델 다운로드 진행률 Flow
 * - 내부 저장소 캐시 관리
 */
@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient
) {

    companion object {
        private const val MODEL_DIR = "llm_models"
        private const val BUFFER_SIZE = 8192
    }

    /** 추천 모델 목록 */
    val recommendedModels = listOf(
        ModelInfo(
            name = "Qwen2.5-1.5B-Instruct",
            fileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            url = "", // 사용자가 직접 모델 URL 설정
            sizeBytes = 1_100_000_000L,
            minRamGb = 3,
            description = "경량 모델 (1.5B) — 빠른 추론, 기본 분석"
        ),
        ModelInfo(
            name = "Qwen2.5-3B-Instruct",
            fileName = "qwen2.5-3b-instruct-q4_k_m.gguf",
            url = "",
            sizeBytes = 2_000_000_000L,
            minRamGb = 4,
            description = "중간 모델 (3B) — 균형잡힌 성능"
        ),
        ModelInfo(
            name = "Qwen2.5-7B-Instruct",
            fileName = "qwen2.5-7b-instruct-q4_k_m.gguf",
            url = "",
            sizeBytes = 4_400_000_000L,
            minRamGb = 6,
            description = "고성능 모델 (7B) — 상세 분석"
        )
    )

    /**
     * 디바이스 RAM 감지 (GB)
     */
    fun getDeviceRamGb(): Int {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return (memInfo.totalMem / (1024L * 1024 * 1024)).toInt()
    }

    /**
     * 디바이스에 적합한 모델 추천
     */
    fun getRecommendedModel(): ModelInfo {
        val ramGb = getDeviceRamGb()
        return recommendedModels
            .filter { it.minRamGb <= ramGb }
            .maxByOrNull { it.sizeBytes }
            ?: recommendedModels.first()
    }

    /**
     * 모델 파일 존재 확인
     */
    fun isModelDownloaded(model: ModelInfo): Boolean {
        val file = getModelFile(model)
        return file.exists() && file.length() == model.sizeBytes
    }

    /**
     * 모델 파일 경로
     */
    fun getModelFile(model: ModelInfo): File {
        val dir = File(context.filesDir, MODEL_DIR)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, model.fileName)
    }

    /**
     * 모델 다운로드 (진행률 Flow)
     */
    fun downloadModel(model: ModelInfo): Flow<DownloadProgress> = flow {
        require(model.url.isNotBlank()) { "모델 URL이 설정되지 않았습니다." }

        val file = getModelFile(model)
        emit(DownloadProgress.Started(model.name))

        try {
            val request = Request.Builder().url(model.url).build()
            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                emit(DownloadProgress.Error("다운로드 실패: HTTP ${response.code}"))
                return@flow
            }

            val body = response.body ?: run {
                emit(DownloadProgress.Error("빈 응답"))
                return@flow
            }

            val totalBytes = body.contentLength()
            var downloadedBytes = 0L

            file.outputStream().use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        val progress = if (totalBytes > 0)
                            downloadedBytes.toFloat() / totalBytes else 0f
                        emit(DownloadProgress.Downloading(progress, downloadedBytes, totalBytes))
                    }
                }
            }

            emit(DownloadProgress.Completed(file.absolutePath))
            Timber.d("모델 다운로드 완료: ${file.absolutePath}")

        } catch (e: Exception) {
            file.delete()
            emit(DownloadProgress.Error("다운로드 오류: ${e.message}"))
            Timber.e(e, "모델 다운로드 실패")
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 캐시된 모델 목록
     */
    suspend fun getCachedModels(): List<CachedModel> = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, MODEL_DIR)
        if (!dir.exists()) return@withContext emptyList()

        dir.listFiles()
            ?.filter { it.extension == "gguf" }
            ?.map { file ->
                val modelInfo = recommendedModels.find { it.fileName == file.name }
                CachedModel(
                    filePath = file.absolutePath,
                    fileName = file.name,
                    sizeBytes = file.length(),
                    modelName = modelInfo?.name ?: file.nameWithoutExtension
                )
            } ?: emptyList()
    }

    /**
     * 모델 파일 삭제
     */
    suspend fun deleteModel(model: ModelInfo) = withContext(Dispatchers.IO) {
        val file = getModelFile(model)
        if (file.exists()) {
            file.delete()
            Timber.d("모델 삭제: ${file.absolutePath}")
        }
    }
}

/** 모델 정보 */
data class ModelInfo(
    val name: String,
    val fileName: String,
    val url: String,
    val sizeBytes: Long,
    val minRamGb: Int,
    val description: String = ""
)

/** 다운로드 진행 상태 */
sealed class DownloadProgress {
    data class Started(val modelName: String) : DownloadProgress()
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : DownloadProgress()
    data class Completed(val filePath: String) : DownloadProgress()
    data class Error(val message: String) : DownloadProgress()
}

/** 캐시된 모델 */
data class CachedModel(
    val filePath: String,
    val fileName: String,
    val sizeBytes: Long,
    val modelName: String
)
