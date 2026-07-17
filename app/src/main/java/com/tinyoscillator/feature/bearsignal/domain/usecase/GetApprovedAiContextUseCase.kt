package com.tinyoscillator.feature.bearsignal.domain.usecase

import com.tinyoscillator.feature.bearsignal.domain.model.AiContextSectionKey
import com.tinyoscillator.feature.bearsignal.domain.model.ApprovedAiContext
import com.tinyoscillator.feature.bearsignal.domain.repository.AiContextRepository

/**
 * §4.7 승인 캐시 조회 — 화면 렌더 시 섹션별 승인 콘텐츠를 로드한다(P7-3 소비). 캐시가 없는 섹션은
 * 반환 맵에서 생략되며, 호출측은 정적 fallback([com.tinyoscillator.feature.bearsignal.domain.model.BearSignalStaticContent])으로
 * 대체한다(§4.7 "캐시 없으면 정적 fallback 그대로").
 */
class GetApprovedAiContextUseCase(private val repository: AiContextRepository) {

    suspend operator fun invoke(): Map<AiContextSectionKey, ApprovedAiContext> = repository.getApproved()
}
