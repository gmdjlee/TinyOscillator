package com.tinyoscillator.presentation.common.skeleton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tinyoscillator.core.ui.modifier.ShimmerBox
import com.tinyoscillator.core.ui.modifier.ShimmerLine

/** 종목 분석 화면 (오실레이터 탭) 로딩 스켈레톤 */
@Composable
fun OscillatorScreenSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 기간 선택 버튼 행
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(5) {
                ShimmerLine(width = 56.dp, height = 32.dp, cornerRadius = 16.dp)
            }
        }
        // 캔들 차트 영역
        ShimmerBox(height = 240.dp, cornerRadius = 8.dp)
        // 신호 요약 카드
        ShimmerBox(height = 64.dp, cornerRadius = 12.dp)
        // 신호 분석 행
        repeat(3) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShimmerLine(width = 80.dp, height = 12.dp)
                ShimmerLine(height = 6.dp, cornerRadius = 3.dp, modifier = Modifier.weight(1f))
                ShimmerLine(width = 40.dp, height = 12.dp)
            }
        }
    }
}

/** AI 분석 화면 (확률 분석 결과) 로딩 스켈레톤 */
@Composable
fun AnalysisScreenSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 종목명 + 현재가 헤더
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ShimmerLine(width = 120.dp, height = 20.dp)
                ShimmerLine(width = 80.dp, height = 14.dp)
            }
            ShimmerLine(width = 80.dp, height = 28.dp)
        }
        // 앙상블 점수 카드
        ShimmerBox(height = 80.dp, cornerRadius = 12.dp)
        // 알고리즘 기여도
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ShimmerLine(width = 120.dp, height = 16.dp)
                repeat(4) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ShimmerLine(width = 72.dp, height = 12.dp)
                        ShimmerLine(height = 6.dp, cornerRadius = 3.dp, modifier = Modifier.weight(1f))
                        ShimmerLine(width = 32.dp, height = 12.dp)
                    }
                }
            }
        }
        // 차트 영역
        ShimmerBox(height = 200.dp, cornerRadius = 12.dp)
    }
}

/** 워치리스트 리스트 아이템 스켈레톤 */
@Composable
fun WatchlistItemSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ShimmerLine(width = 24.dp, height = 24.dp, cornerRadius = 4.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ShimmerLine(width = 100.dp, height = 14.dp)
            ShimmerLine(width = 60.dp, height = 11.dp)
        }
        ShimmerLine(width = 56.dp, height = 36.dp, cornerRadius = 4.dp)
        ShimmerLine(width = 56.dp, height = 36.dp, cornerRadius = 4.dp)
    }
}

/** 스크리너 결과 아이템 스켈레톤 */
@Composable
fun ScreenerResultSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ShimmerLine(width = 110.dp, height = 15.dp)
            ShimmerLine(width = 160.dp, height = 11.dp)
        }
        ShimmerLine(width = 48.dp, height = 28.dp, cornerRadius = 6.dp)
    }
}

/** 수익률 비교 화면 스켈레톤 */
@Composable
fun ComparisonSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 차트 영역
        ShimmerBox(height = 240.dp, cornerRadius = 8.dp)
        // 알파/베타 요약
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShimmerBox(height = 56.dp, cornerRadius = 8.dp, modifier = Modifier.weight(1f))
            ShimmerBox(height = 56.dp, cornerRadius = 8.dp, modifier = Modifier.weight(1f))
        }
        // 신호 이력 차트
        ShimmerBox(height = 160.dp, cornerRadius = 8.dp)
    }
}

/** 시장 분석(공포탐욕/DeMark) 스켈레톤 */
@Composable
fun MarketAnalysisSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 게이지 영역
        ShimmerBox(height = 120.dp, cornerRadius = 12.dp)
        // 차트 영역
        ShimmerBox(height = 200.dp, cornerRadius = 8.dp)
        // 지표 행
        repeat(3) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShimmerLine(width = 80.dp, height = 12.dp)
                ShimmerLine(height = 8.dp, cornerRadius = 4.dp, modifier = Modifier.weight(1f))
                ShimmerLine(width = 36.dp, height = 12.dp)
            }
        }
    }
}

/** 차트 화면 (DeMark/펀더멘탈/재무 등) 범용 스켈레톤 */
@Composable
fun ChartScreenSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ShimmerLine(width = 140.dp, height = 18.dp)
        ShimmerBox(height = 280.dp, cornerRadius = 8.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(4) {
                ShimmerLine(width = 60.dp, height = 28.dp, cornerRadius = 14.dp)
            }
        }
    }
}
