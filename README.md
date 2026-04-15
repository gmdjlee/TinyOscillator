# 확률적 기대값 분석 엔진 — Claude Code 구현 패키지

## 파일 구성

```
프로젝트 루트/
├── CLAUDE.md              ← 프로젝트 컨텍스트 (기존에 병합)
├── TASK.md                ← Phase별 작업 목록
├── PROMPT.md              ← Phase별 Claude Code 실행 프롬프트
├── PROGRESS.md            ← 진행 상태 추적 (자동 업데이트)
├── QUICKSTART.md          ← 실행 가이드 (PowerShell 명령어)
├── LLM_SYSTEM_PROMPT.txt  ← 런타임 LLM 시스템 프롬프트 템플릿
│                            (→ app/src/main/assets/prompts/ 에 배치)
└── .claude/
    ├── agents/
    │   ├── algorithm-engine.md    ← 알고리즘 구현 서브에이전트
    │   └── llm-integration.md    ← LLM 통합 서브에이전트
    └── hooks/
        └── pre-commit-check.md   ← 코드 품질 검증 규칙
```

## 실행 순서 요약

1. 파일들을 프로젝트 루트에 배치
2. QUICKSTART.md의 "사전 준비" 섹션 실행
3. Phase 1 → Phase 2 → Phase 3 → Phase 4 순서로 Claude Code 실행
4. 각 Phase 완료 후 PROGRESS.md 확인

## 핵심 아키텍처 결정

| 결정 | 근거 |
|------|------|
| 7개 알고리즘 모두 순수 Kotlin | 외부 ML 라이브러리 = APK 비대화 + 호환성 이슈 |
| HMM은 Heuristic/Static 방식 | 완전 학습은 모바일에서 불필요, 사전 정의 파라미터로 충분 |
| LLM temperature = 0.3 | 분석 보고서는 일관성이 중요 |
| ChatML 포맷 | Qwen3, Phi-4 등 대부분 호환 |
| 프롬프트 < 4,000 tokens | 소형 모델 컨텍스트의 12~25% |
| 7개 엔진 병렬 실행 | 합산 ~50ms로 LLM 대기 시간에 영향 없음 |
