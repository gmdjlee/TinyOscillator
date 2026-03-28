package com.tinyoscillator.data.local.llm

import com.tinyoscillator.domain.repository.LlmRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * llama.cpp JNI 래퍼를 통한 LLM Repository 구현
 *
 * - native 메서드: nativeLoadModel, nativeGenerate, nativeUnload
 * - ChatML 포맷 프롬프트 지원
 * - Flow<String>으로 토큰 단위 스트리밍
 * - 인퍼런스 스레드 수: min(availableProcessors-1, 4)
 *
 * JNI C++ 코드는 Phase 5에서 구현. 여기서는 Kotlin 인터페이스와 JNI 선언까지만.
 */
@Singleton
class LlmRepositoryImpl @Inject constructor() : LlmRepository {

    companion object {
        private const val TAG = "LlmRepository"

        init {
            try {
                System.loadLibrary("llama_jni")
                Timber.d("llama_jni 라이브러리 로드 성공")
            } catch (e: UnsatisfiedLinkError) {
                Timber.w("llama_jni 라이브러리 로드 실패 (NDK 빌드 필요): ${e.message}")
            }
        }
    }

    private val _isModelLoaded = MutableStateFlow(false)
    override val isModelLoaded: Flow<Boolean> = _isModelLoaded.asStateFlow()

    private var modelHandle: Long = 0
    private val nThreads = minOf(Runtime.getRuntime().availableProcessors() - 1, 4).coerceAtLeast(1)

    override suspend fun loadModel(modelPath: String) {
        withContext(Dispatchers.Default) {
            Timber.d("모델 로드 시작: $modelPath (threads=$nThreads)")
            try {
                modelHandle = nativeLoadModel(modelPath, nThreads)
                _isModelLoaded.value = modelHandle != 0L
                Timber.d("모델 로드 ${if (_isModelLoaded.value) "성공" else "실패"}")
            } catch (e: UnsatisfiedLinkError) {
                Timber.w("JNI 미구현 — Phase 5에서 구현 예정")
                _isModelLoaded.value = false
            }
        }
    }

    override suspend fun unloadModel() {
        withContext(Dispatchers.Default) {
            if (modelHandle != 0L) {
                try {
                    nativeUnload(modelHandle)
                } catch (e: UnsatisfiedLinkError) {
                    Timber.w("JNI 미구현")
                }
                modelHandle = 0
                _isModelLoaded.value = false
                Timber.d("모델 해제 완료")
            }
        }
    }

    override fun generate(
        prompt: String,
        maxTokens: Int,
        temperature: Float
    ): Flow<String> = flow {
        if (modelHandle == 0L) {
            emit("[모델이 로드되지 않았습니다]")
            return@flow
        }

        try {
            // 토큰을 수집할 리스트 (nativeGenerate가 콜백으로 전달)
            val tokens = mutableListOf<String>()
            val callback = object : TokenCallback {
                override fun onToken(text: String) {
                    tokens.add(text)
                }
            }

            // nativeGenerate는 전체 생성을 수행하고 콜백으로 토큰 전달
            nativeGenerate(modelHandle, prompt, maxTokens, temperature, callback)

            // 수집된 토큰을 하나씩 emit
            for (token in tokens) {
                emit(token)
            }
        } catch (e: UnsatisfiedLinkError) {
            emit("[NDK 빌드 필요 — llama.cpp 소스를 app/src/main/cpp/llama.cpp/에 배치하세요]")
        }
    }.flowOn(Dispatchers.Default)

    override suspend fun generateComplete(
        prompt: String,
        maxTokens: Int,
        temperature: Float
    ): String {
        val sb = StringBuilder()
        generate(prompt, maxTokens, temperature).collect { token ->
            sb.append(token)
        }
        return sb.toString()
    }

    // ─── JNI Native Methods ───

    /**
     * GGUF 모델 로드
     * @return 모델 핸들 (0이면 실패)
     */
    private external fun nativeLoadModel(modelPath: String, nThreads: Int): Long

    /**
     * 텍스트 생성 (1 토큰씩)
     * @return true면 더 생성할 토큰이 있음
     */
    private external fun nativeGenerate(
        modelHandle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        callback: TokenCallback
    ): Boolean

    /**
     * 모델 해제
     */
    private external fun nativeUnload(modelHandle: Long)

    /** 토큰 콜백 인터페이스 */
    interface TokenCallback {
        fun onToken(text: String)
    }
}
