package com.tinyoscillator.core.ui.debug

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import com.tinyoscillator.BuildConfig

/**
 * 리컴포지션 횟수를 Logcat에 기록하는 디버그 전용 유틸.
 * release 빌드에서는 BuildConfig.DEBUG 가드로 완전히 no-op.
 *
 * 사용법:
 *   @Composable fun MyCard(...) {
 *       LogRecompositions("MyCard")
 *       // ... 실제 UI
 *   }
 *
 * Logcat 필터: tag="Recompose"
 */
@Composable
fun LogRecompositions(tag: String) {
    if (BuildConfig.DEBUG) {
        val count = remember { mutableIntStateOf(0) }
        SideEffect {
            count.intValue++
            Log.d("Recompose", "$tag: ${count.intValue}")
        }
    }
}
