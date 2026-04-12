package com.tinyoscillator.presentation.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 알고리즘 해설 바텀시트 콘텐츠.
 * 9개 분석 엔진의 원리, 입력 데이터, 결과 해석 방법을 설명한다.
 */
@Composable
fun AlgorithmGuideContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "분석 알고리즘 해설",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "TinyOscillator는 9개의 통계 엔진을 병렬 실행하여 종합 확률을 산출합니다. " +
                "각 엔진은 독립적으로 작동하며, 하나가 실패해도 나머지 결과가 반환됩니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider()

        algorithms.forEach { algo ->
            AlgorithmCard(algo)
        }

        // 종합 해석 가이드
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("결과 해석 가이드", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                InterpretationRow("0.65 이상", "강세 신호 — 상승 가능성이 높다고 판단")
                InterpretationRow("0.35~0.65", "중립 — 방향성 불확실, 관망 권장")
                InterpretationRow("0.35 이하", "약세 신호 — 하락 가능성이 높다고 판단")
                Spacer(Modifier.height(4.dp))
                Text(
                    "앙상블 점수는 시장 레짐(HMM)에 따라 가중 합산됩니다. " +
                        "변동성이 높은 구간에서는 모멘텀 엔진의 비중이 줄고, 안정 구간에서는 늘어납니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun AlgorithmCard(algo: AlgorithmInfo) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        algo.icon, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(algo.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "접기" else "펼치기"
                )
            }
            Text(algo.oneLiner, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (expanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                InfoSection("작동 원리", algo.principle)
                InfoSection("입력 데이터", algo.input)
                InfoSection("출력", algo.output)
                InfoSection("해석 방법", algo.interpretation)
            }
        }
    }
}

@Composable
private fun InfoSection(title: String, content: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary)
        Text(content, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun InterpretationRow(range: String, description: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(range, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(80.dp))
        Text(description, style = MaterialTheme.typography.bodySmall)
    }
}

private data class AlgorithmInfo(
    val name: String,
    val icon: ImageVector,
    val oneLiner: String,
    val principle: String,
    val input: String,
    val output: String,
    val interpretation: String
)

private val algorithms = listOf(
    AlgorithmInfo(
        name = "나이브 베이즈",
        icon = Icons.Default.Calculate,
        oneLiner = "과거 패턴에서 조건부 확률로 상승/하락/횡보 분류",
        principle = "베이즈 정리를 활용하여 각 특성(시가총액, 외국인 순매수, 기관 순매수 등)이 " +
            "상승/하락/횡보 각 클래스에 속할 확률을 독립적으로 계산하고 곱한 결과를 정규화합니다.",
        input = "일별 종가, 시가총액, 외국인/기관 순매수",
        output = "상승·하락·횡보 각각의 사후확률 (합계 1.0)",
        interpretation = "상승 확률이 가장 높으면 강세, 하락이 높으면 약세로 판단합니다. " +
            "세 클래스 확률이 비슷하면 불확실성이 높다는 의미입니다."
    ),
    AlgorithmInfo(
        name = "로지스틱 회귀",
        icon = Icons.AutoMirrored.Filled.TrendingUp,
        oneLiner = "다변량 특성에 가중치를 부여하여 상승 확률 점수화",
        principle = "시그모이드 함수를 통해 여러 입력 특성의 가중 합을 0~1 확률로 변환합니다. " +
            "경사하강법으로 가중치를 학습하며, 수렴 미달 시 조기 종료합니다.",
        input = "종가 변화율, 거래량, 외국인/기관 순매수, 기술적 지표",
        output = "0~100 점수 + 가장 영향력 높은 특성명",
        interpretation = "점수 65 이상이면 강세, 35 이하면 약세입니다. " +
            "주요 기여 특성(예: momentum)이 함께 표시되어 어떤 요인이 판단을 이끌었는지 확인할 수 있습니다."
    ),
    AlgorithmInfo(
        name = "HMM 레짐 분석",
        icon = Icons.Default.Layers,
        oneLiner = "은닉 마르코프 모델로 현재 시장 국면(레짐) 판별",
        principle = "관측 불가능한 시장 상태(레짐)가 주가 변동을 생성한다고 가정합니다. " +
            "4개 레짐(강세/약세/고변동/저변동)의 전이 확률을 학습하여 현재 상태를 추정합니다.",
        input = "일별 수익률, 변동성",
        output = "4개 레짐별 확률 (합계 1.0) + 현재 레짐명",
        interpretation = "'저변동상승' 레짐 확률이 높으면 안정적 상승 국면, " +
            "'고변동하락' 확률이 높으면 급락 위험을 시사합니다. " +
            "앙상블에서는 레짐에 따라 다른 엔진의 가중치가 조절됩니다."
    ),
    AlgorithmInfo(
        name = "패턴 스캔",
        icon = Icons.Default.GridView,
        oneLiner = "차트 패턴(이중바닥, 삼각수렴 등)을 탐지하고 과거 승률 제공",
        principle = "최근 가격 시계열에서 전형적인 차트 패턴(이중 바닥/천장, 삼각수렴, " +
            "헤드앤숄더 등)을 규칙 기반으로 탐지합니다. 탐지된 패턴의 과거 수익률/승률을 계산합니다.",
        input = "일별 고가, 저가, 종가",
        output = "활성 패턴 수, 패턴별 승률 및 기대수익률",
        interpretation = "활성 패턴이 3개 이상이고 최고 승률이 70% 이상이면 신뢰할 수 있습니다. " +
            "상승 패턴과 하락 패턴이 동시에 활성화되면 혼조세를 의미합니다."
    ),
    AlgorithmInfo(
        name = "가중 시그널",
        icon = Icons.Default.Balance,
        oneLiner = "RSI, MACD, 볼린저밴드 등 기술적 지표의 가중 합산 점수",
        principle = "다수의 기술적 지표(RSI, MACD, 볼린저밴드, 이동평균 교차 등)를 각각 " +
            "매수/매도 신호로 변환한 뒤, 레짐 상태에 따른 가중치로 합산합니다.",
        input = "일별 OHLCV (시가, 고가, 저가, 종가, 거래량)",
        output = "0~100 종합 점수 + 방향(강세/약세/중립)",
        interpretation = "점수 65 이상 = 강세, 35 이하 = 약세. " +
            "여러 지표가 같은 방향을 가리키면 신뢰도가 높습니다."
    ),
    AlgorithmInfo(
        name = "상관관계 분석",
        icon = Icons.AutoMirrored.Filled.CompareArrows,
        oneLiner = "시장 지수·환율 등과의 롤링 상관계수로 동조화 측정",
        principle = "대상 종목과 KOSPI/KOSDAQ 지수, 환율, 거래량 등과의 " +
            "롤링 상관계수를 계산합니다. 시차(-5~+5일)를 두고 가장 높은 상관을 찾습니다.",
        input = "종목 종가, 시장 지수, 거래량",
        output = "상관계수 r ∈ [-1, 1] + 최적 시차 lag ∈ [-5, 5]",
        interpretation = "r > 0.7이면 시장 추종, r < -0.5면 역행. " +
            "lag가 양수면 시장이 선행, 음수면 종목이 선행합니다."
    ),
    AlgorithmInfo(
        name = "베이지안 업데이트",
        icon = Icons.Default.Update,
        oneLiner = "새 데이터가 들어올 때마다 사전확률을 갱신하여 사후확률 도출",
        principle = "사전확률(50%)에서 시작하여, 매일의 새로운 데이터(수익률, 거래량 변화)를 " +
            "관측할 때마다 베이즈 정리로 확률을 갱신합니다. 최근 데이터일수록 강하게 반영됩니다.",
        input = "일별 종가 변화율, 거래량 변화율",
        output = "사전확률 → 사후확률 (0.001~0.999) + 변화량",
        interpretation = "사전 대비 사후가 크게 증가(예: 62%→84%)하면 최근 데이터가 강하게 " +
            "상승을 지지한다는 의미입니다. 변화가 작으면 새 정보가 기존 판단을 바꾸지 못한 것입니다."
    ),
    AlgorithmInfo(
        name = "수급 분석",
        icon = Icons.Default.SwapVert,
        oneLiner = "외국인·기관의 순매수 패턴에서 매수/매도 우위 판별",
        principle = "외국인과 기관투자자의 순매수 금액과 연속성을 분석합니다. " +
            "일정 기간 누적 순매수와 거래량 대비 비율로 매수 우위 정도를 산출합니다.",
        input = "외국인 순매수, 기관 순매수, 거래량",
        output = "매수우위점수 (0~1) + 강도(강/중/약)",
        interpretation = "매수우위 0.7 이상이면 수급이 강하게 상승을 지지합니다. " +
            "0.3 이하면 매도 압력이 우세합니다."
    ),
    AlgorithmInfo(
        name = "DART 이벤트",
        icon = Icons.AutoMirrored.Filled.Article,
        oneLiner = "기업 공시(DART) 이벤트의 주가 영향을 이벤트 스터디로 분석",
        principle = "DART 공시(유상증자, 자사주 매입, 배당 등)를 감지하고, " +
            "이벤트 전후 비정상 수익률(CAR)을 OLS 회귀로 계산합니다. " +
            "[-5, +20]일 이벤트 윈도우를 사용합니다.",
        input = "DART 공시 목록, 일별 주가, KOSPI 지수",
        output = "신호점수 (0~1) + 이벤트 유형 + 비정상수익률",
        interpretation = "양의 CAR + 긍정적 이벤트(자사주 매입 등)면 강세 신호. " +
            "음의 CAR + 부정적 이벤트(유상증자 등)면 약세 신호입니다."
    )
)
