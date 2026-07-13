# TASK_bear_signal_console.md — v1.1 → v1.2 병합 패치

이번 세션 spec의 신규 3요소를 기존 명세에 흡수한다. **기존 §7 수용·부록 번호를 건드리지 않도록** 삽입 위치를 설계했다. 아래 3개 블록을 지정 위치에 넣고 헤더 버전을 v1.2로 올린 뒤, `tinyoscillator_leadercollapse_spec.md`는 `archive/`로 이동한다(삭제 금지, 근거 보존).

## 병합 요약
| 패치 | 삽입 위치 | 내용 |
|---|---|---|
| A | §3 서두 (신설 §3.0) | 임계치 출처를 `bear_thresholds.json`으로 전환 |
| B | §4 하위 (신설 §4.6) | 스냅샷 계약 (수집↔엔진↔Room↔React 경계) |
| C | §4 계획표 + 신설 상세 | Phase 3.5 — Room 이력·전이 감지 |

## 헤더 변경
```
- 문서 버전: v1.1  →  v1.2
```

## Changelog (v1.2) — 명세 상단 이력에 추가
```
- v1.2: 임계치 외부화(bear_thresholds.json, §3.0), 스냅샷 계약 신설(§4.6),
        Room 이력·전이 감지 Phase 3.5 추가. 세션 spec 흡수 후 아카이브.
```

---

## PATCH A — §3.0 임계치 출처 (신설, §3 맨 앞에 삽입)

> §3의 스코어링 로직·경계는 불변이다. 단, 숫자 임계치는 코드 상수가 아니라 `bear_thresholds.json`에서 로드한다. 기존 스켈레톤의 하드코딩 상수를 아래 `BearThresholds`로 대체하고, 스코어링 엔진은 이를 생성자 주입으로 받는다. 테스트는 리터럴/테스트 JSON으로 직접 구성한다(안드로이드 무의존 유지).

```kotlin
// domain — 프레임워크 무의존. bear_thresholds.json과 1:1.
data class BearThresholds(
    val version: String, val basis: String,
    val s1: S1, val s2: S2, val s3: S3, val gate: Gate, val amp: Amp, val phase: PhaseCfg,
) {
    data class S1(val manyCountries: Int, val deepPct: Double, val deepeningPct: Double)
    data class S2(val redLine: Double, val warnLine: Double, val watchLine: Double)
    data class S3(val loss1: Double, val loss2: Double, val loss3: Double)
    data class Gate(val critical: Double, val approach: Double, val creditWarn: Double)
    data class Amp(val semiExport: Double, val kospi2: Double,
                   val wSemi: Double, val wKospi2: Double, val wNoBuffer: Double, val cap: Double)
    data class PhaseCfg(val leadOrange: Int, val leadAmber: Int)
}

// 스코어링 엔진은 임계치를 주입받는다 (기존 SSOT 로직 그대로, 상수만 t.* 참조)
class SignalScoring(private val t: BearThresholds) { /* scoreS1..composite: t.s1.manyCountries 등 */ }
```

```kotlin
// data — assets/bear_thresholds.json 로드 (Hilt @Provides @Singleton)
class ThresholdsProvider(private val context: Context, private val json: Json) {
    fun load(): BearThresholds =
        context.assets.open("bear_thresholds.json").bufferedReader().use {
            json.decodeFromString(it.readText())
        }
}
```

파일 배치: `app/src/main/assets/bear_thresholds.json` (React용 사본과 값 동일 유지).

---

## PATCH B — §4.6 스냅샷 계약 (신설)

> 수집 계층 · 엔진 · Room · React 뷰어를 잇는 단일 경계. 이 스키마를 먼저 고정한다. `inputs`는 React 골든 입력과 완전 동형(계산 결과가 아닌 원입력만 담는다). Room 직렬화도 이 스키마를 그대로 쓴다(별도 규약 금지).

### 스키마 `bear-snapshot/1`
```json
{
  "schema": "bear-snapshot/1",
  "as_of": "2026-07-11",
  "generator": "tinyoscillator | routine | manual",
  "config_basis": "신영 2026.6.30",
  "inputs": {
    "markets": [{ "name": "코스피", "r": [173.1, 103.7, 54.0, 4.5] }],
    "s1_period": "1m",
    "s2_up": 14, "s2_down": 12, "s2_deepening": true,
    "s3_lossRatio": 45, "s3_etf": "up", "s3_bigDeal": "pending",
    "s4_rate": 3.75, "s4_dir": "hike", "s4_credit": 38, "s4_marginCall": false,
    "amp_semiExport": 23.1, "amp_kospi2": 56, "amp_buffer": true,
    "axes": { "commodity": 1, "capex": 2, "chase": 1, "duration": 2, "contract": 2 }
  },
  "field_meta": {
    "s2_up":   { "source": "SNAPSHOT", "as_of": "2026-07-11", "origin": "kotlin_krx:KS11" },
    "s4_rate": { "source": "AUTO",     "as_of": "2026-06-18", "origin": "FRED:DFEDTARU" },
    "s4_credit": { "source": "MANUAL", "as_of": "2026-07-04", "origin": "user" }
  }
}
```

### 값 출처 우선순위
```
MANUAL 〉 SNAPSHOT 〉 AUTO 〉 BASELINE(기준값)
```
사용자 수동 조정은 언제나 자동 수집을 이긴다(무인 갱신이 사람 판단을 덮지 않는다). *(§4.5 웹 수집의 WEB 출처는 AUTO에 속한다.)*

```kotlin
enum class ValueSource { MANUAL, SNAPSHOT, AUTO, BASELINE }  // ordinal = 우선순위(작을수록 강함)
data class FieldSource(val source: ValueSource, val asOf: LocalDate?, val origin: String?)
```

---

## PATCH C — Phase 3.5 (신설): Room 이력 · 국면 전이 감지

### C-1. §4 구현 계획표에 행 추가
| Phase | 내용 | 등급 | 마커 |
|---|---|---|---|
| **3.5** | **Room 스냅샷 이력 영속 + 국면/방아쇠 전이 감지 + 스파크라인·전이 로그** | — | **P3.5** |

배치: Phase 3([C]/[D]) 완료 후, Phase 4(웹/LLM 갱신) 전. 근거 — 리포트 판정 논리("이탈 수+낙폭 동시 확대", "빈도 역전 순간", "임계 접근")가 전부 변화율 개념이므로, 자동 수집(P1~P2)이 갖춰진 직후 시계열 축적을 시작해야 실측 전이를 볼 수 있다.

### C-2. 상세 명세 (신설 하위 절)
```kotlin
// data/local — 일 단위 upsert (inputsJson·fieldMetaJson은 §4.6 스키마 그대로)
@Entity(tableName = "bear_snapshot")
data class BearSnapshotEntity(
    @PrimaryKey val day: String,            // "YYYY-MM-DD"
    val phase: String, val lead: Int, val gate: Int,
    val s1: Int, val s2: Int, val s3: Int, val amp: Double,
    val configBasis: String, val inputsJson: String, val fieldMetaJson: String,
    val createdAt: Long,
)

@Dao
interface BearSnapshotDao {
    @Upsert suspend fun upsert(e: BearSnapshotEntity)
    @Query("SELECT * FROM bear_snapshot ORDER BY day DESC LIMIT 1")
    fun observeLatest(): Flow<BearSnapshotEntity?>
    @Query("SELECT * FROM bear_snapshot WHERE day BETWEEN :from AND :to ORDER BY day ASC")
    fun observeRange(from: String, to: String): Flow<List<BearSnapshotEntity>>
    @Query("SELECT * FROM bear_snapshot ORDER BY day DESC LIMIT 1")
    suspend fun latest(): BearSnapshotEntity?
}
```

```kotlin
// domain/usecase — 연속 스냅샷에서 국면·방아쇠 전이 파생
class DetectTransitionsUseCase {
    operator fun invoke(series: List<BearSnapshot>): List<Transition> = buildList {
        for (i in 1 until series.size) {
            val a = series[i - 1]; val b = series[i]
            if (a.phase != b.phase) add(Transition(b.asOf, PhaseChange(a.phase, b.phase)))
            if (b.gate > a.gate)   add(Transition(b.asOf, GateAdvance(b.gate)))
        }
    }
}
```

구현 범위:
- `SnapshotRepository`(upsertToday·observeLatest·observeRange·latestOrNull) + Impl + Hilt 바인딩. 기존 `AppDatabase`에 DAO 추가, 마이그레이션 버전 +1.
- 세션 진입 시 `state:latest` 복원(WEB/MANUAL 출처·수동값 유지). 최신 스냅샷 `as_of`가 로컬보다 오래되면 갱신 제안(승인 원칙 유지).
- 프레젠테이션: `Sparkline`(lead·gate 60~90일) + `TransitionLog`("6/30 GREEN→AMBER · 방아쇠 경계 접근"). Canvas로 경량 구현.

### C-3. §7 수용 기준에 추가 (부록 B 파리티 9블록은 불변 — 이력·전이는 React 대비 신규 능력)
```
- 이력 영속: 앱 재시작 후에도 일자별 스냅샷 유지(upsert·range 조회 통과).
- 전이 감지: 국면·방아쇠 전이 로그 표시, in-memory DB 테스트 통과.
```

### C-4. PROGRESS.md 마커 순서
```
P0 → P1 → P2 → P3 → P3.5 → P4 → P5(5-1,5-2) → LOOP_COMPLETE
```

---

## 병합 후 정리
- `PHASE_RUNBOOK.md`에 Phase 3.5 프롬프트 1개 추가(자족형: TASK §4.6·C-2 참조, 전제 P3, 완료 P3.5).
- `tinyoscillator_leadercollapse_spec.md` → `archive/`.
- `bear_thresholds.json`을 리포지토리 루트 + `app/src/main/assets/`에 배치.
