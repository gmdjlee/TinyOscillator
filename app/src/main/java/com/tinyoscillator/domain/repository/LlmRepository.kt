package com.tinyoscillator.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * LLM 추론 Repository 인터페이스.
 *
 * llama.cpp JNI 래퍼를 통해 로컬 GGUF 모델로 추론을 수행한다.
 * 토큰 단위 스트리밍을 Flow<String>으로 제공.
 */
interface LlmRepository {

    /** 모델 로드 상태 */
    val isModelLoaded: Flow<Boolean>

    /** 모델 로드 (GGUF 파일 경로) */
    suspend fun loadModel(modelPath: String)

    /** 모델 해제 */
    suspend fun unloadModel()

    /**
     * 텍스트 생성 (스트리밍)
     *
     * @param prompt ChatML 포맷 프롬프트
     * @param maxTokens 최대 생성 토큰 수
     * @param temperature 생성 온도 (0.0~2.0)
     * @return 토큰 단위 스트리밍 Flow
     */
    fun generate(
        prompt: String,
        maxTokens: Int = 512,
        temperature: Float = 0.7f
    ): Flow<String>

    /**
     * 텍스트 생성 (전체 완료 후 반환)
     *
     * @param prompt ChatML 포맷 프롬프트
     * @param maxTokens 최대 생성 토큰 수
     * @param temperature 생성 온도 (0.0~2.0)
     * @return 전체 생성 텍스트
     */
    suspend fun generateComplete(
        prompt: String,
        maxTokens: Int = 512,
        temperature: Float = 0.7f
    ): String
}
