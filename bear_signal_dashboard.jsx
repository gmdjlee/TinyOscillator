import React, { useState, useMemo } from "react";

/*
  주도주 붕괴 판단 계기판  (Leader-Collapse Judgment Console)
  ------------------------------------------------------------------
  Source of every rule/threshold: 신영증권「주도주의 물리학」(2026.6.30).
  All logic below is a direct operationalization of that report only —
  no external indicators (VIX, yield-curve, put/call, etc.) are added.

  Model = 3 thermometers (선행 신호) + 1 trigger (금리 방아쇠) + 1 amplifier (집중).
    · 신호1 주변부 압착        (Ch.IV-1, 도표 46~48)
    · 신호2 변동성 무게중심     (Ch.IV-2, 도표 49~50)
    · 신호3 IPO 질            (Ch.IV-3, 도표 51~53)
    · 신호4 금리 방아쇠 [GATE] (Ch.IV-4, 도표 54~57)
    · 집중 증폭 계수 [AMP]     (유형4, 도표 44)
  국면(phase) = 선행 신호 점등 여부 × 금리 방아쇠 상태 × 집중 증폭.
*/

// ------------------------------------------------------------------
// palette / type
// ------------------------------------------------------------------
const C = {
  bg: "#0A0E13",
  panel: "#131A22",
  panel2: "#0F151C",
  line: "#243040",
  lineSoft: "#1A2430",
  text: "#E7EEF5",
  mut: "#7E8C9B",
  mut2: "#586675",
  go: "#25B368",     // 안전 · 신영 그린 계열
  amber: "#E0A93B",  // 주의
  orange: "#E8823A", // 임박
  red: "#E5484D",    // 격발
  accent: "#3DA9E0",
};
const MONO = "ui-monospace, SFMono-Regular, Menlo, 'Roboto Mono', monospace";
const SANS =
  "-apple-system, BlinkMacSystemFont, 'Apple SD Gothic Neo', 'Malgun Gothic', 'Noto Sans KR', sans-serif";

const LEVELS = ["안전", "주의", "경고", "위험"];
const LEVEL_C = [C.go, C.amber, C.orange, C.red];
const GATE_STATES = ["정상화 구간", "경계 접근", "임계 접근", "긴축 돌입"];

// ------------------------------------------------------------------
// baseline = the report's own snapshot (2026.6.30)
// ------------------------------------------------------------------
// 도표 48 · 최근 국가별 주가 수익률 (누적 %, 2026.6.26 기준). r = [-12M, -6M, -3M, -1M]
const PERIODS = [
  { k: "12m", t: "-12개월", i: 0 },
  { k: "6m", t: "-6개월", i: 1 },
  { k: "3m", t: "-3개월", i: 2 },
  { k: "1m", t: "-1개월", i: 3 },
];
const MARKETS = [
  { name: "코스피", r: ["+173.1", "+103.7", "+54.0", "+4.5"], lead: true },
  { name: "닛케이", r: ["+75.2", "+36.7", "+29.4", "+6.7"] },
  { name: "대만", r: ["+98.2", "+56.1", "+33.7", "+2.4"] },
  { name: "다우", r: ["+19.6", "+6.5", "+12.9", "+2.8"] },
  { name: "CAC40", r: ["+11.0", "+3.5", "+7.9", "+2.6"] },
  { name: "호주", r: ["+2.5", "+0.0", "+2.8", "+1.2"] },
  { name: "유로", r: ["+18.6", "+8.2", "+11.8", "+2.6"] },
  { name: "FTSE", r: ["+20.3", "+6.5", "+5.4", "+0.2"] },
  { name: "태국", r: ["+39.4", "+22.5", "+6.9", "-0.7"] },
  { name: "베트남", r: ["+37.1", "+8.2", "+13.8", "-0.7"] },
  { name: "상하이", r: ["+16.8", "+1.6", "+3.6", "-2.8"] },
  { name: "S&P", r: ["+19.8", "+6.1", "+13.5", "-2.2"] },
  { name: "DAX", r: ["+4.3", "+1.4", "+9.1", "-2.0"] },
  { name: "나스닥", r: ["+25.4", "+7.2", "+18.2", "-5.1"] },
  { name: "인도", r: ["-5.8", "-7.6", "+3.2", "+0.6"] },
  { name: "멕시코", r: ["+17.0", "+2.4", "+0.2", "-2.8"] },
  { name: "브라질", r: ["+26.4", "+8.0", "-5.2", "-1.9"] },
  { name: "인니", r: ["-14.5", "-30.9", "-17.7", "-3.8"] },
  { name: "항생", r: ["-6.8", "-12.2", "-8.8", "-11.4"] },
  { name: "RTS", r: ["-17.1", "-16.4", "-13.7", "-17.6"] },
];

const BASELINE = {
  // 신호1 주변부 압착 — 도표48 표에서 자동 산출
  markets: MARKETS, // 국가별 수익률 (편집 가능)
  s1_period: "1m", // 이탈 판정 기준 기간 (리포트: 최근으로 좁힐수록 이탈 수 ↑)
  // 신호2 변동성 무게중심 (직전 6개월, ±3σ 기준) — 도표50
  s2_up: 14,
  s2_down: 12,
  s2_deepening: true, // 하락 깊이 심화 조짐
  // 신호3 IPO 질
  s3_lossRatio: 45, // 적자기업 상장 비중 % (평상 20~40, 버블 ~80) — 도표51
  s3_etf: "up", // Renaissance IPO ETF 방향 up|flat|down (도표52 회복 중)
  s3_bigDeal: "pending", // 대어 소화 smooth|pending|failed (SpaceX 완료, OpenAI/Anthropic 대기)
  // 신호4 금리 방아쇠
  s4_rate: 3.75, // 기준금리 상단 % (임계 4.5)
  s4_dir: "hike", // ease | hold | hike (Warsh 매파 전환)
  s4_credit: 38, // 신용잔고 조원 (2023말 17.5)
  s4_marginCall: false, // 반대매매 임박 (담보 유지비율 140% 근접)
  // 집중 증폭
  amp_semiExport: 23.1, // 반도체 수출 비중 % (2026 1Q)
  amp_kospi2: 56, // 삼성전자+SK하이닉스 코스피 비중 %
  amp_buffer: true, // 완충 산업 건재 (자동차·기계·석유 등)
};

// ------------------------------------------------------------------
// scoring — pure functions, 0=안전 1=주의 2=경고 3=위험
// ------------------------------------------------------------------
function scoreS1(count, depth) {
  // 리포트: 닷컴 정점 직전 1개월 = 7개국 이탈. 진짜 천장 = 이탈 수 + 낙폭 동시 확대.
  const many = count >= 7; // 닷컴 정점 직전 수준 이상
  if (!many) return depth === "deep" ? 1 : 0;
  if (depth === "shallow") return 1;
  if (depth === "deepening") return 2;
  return 3;
}
function scoreS2(up, down, deepening) {
  // 리포트: 큰 하락일 빈도 > 큰 상승일 빈도로 역전되는 순간이 천장. 현재 ~6:4 상승 우세.
  const r = up === 0 ? 9 : down / up; // 하락/상승
  if (r > 1.0) return 3;
  if (r >= 0.95) return 2;
  if (r >= 0.7) return deepening ? 1 : 0;
  return 0;
}
function scoreS3(loss, etf, big) {
  let lv = 0;
  if (loss >= 80) lv = 3; // 닷컴·2021 버블 수준
  else if (loss >= 60) lv = 2;
  else if (loss >= 45) lv = 1;
  if (etf === "down") lv = Math.max(lv, 2); // ETF가 지수보다 먼저 꺾임 (2021 사례)
  if (big === "failed") lv = Math.max(lv, 3); // 대어 상장 실패 = 검증 칼날
  else if (big === "pending") lv = Math.max(lv, 1);
  return lv;
}
function scoreGate(rate, dir, credit, margin) {
  // 리포트: 임계 4.5%(진짜 긴축) / 현재 3.75%. 신용 급증 = 방아쇠의 국산화.
  let lv;
  if (rate >= 4.5) lv = 3;
  else if (rate >= 4.0) lv = 2;
  else lv = dir === "hike" ? 1 : 0;
  if (margin) lv = Math.max(lv, 2);
  else if (credit >= 35) lv = Math.max(lv, 1); // 2023말 17.5조 → 2배 이상
  return lv;
}
function amplifier(semi, kospi2, buffer) {
  // 리포트: 집중은 방아쇠가 아니라 충격에 곱해지는 계수. 완충 산업 건재 시 핀란드형 파국 회피.
  let a = 1.0;
  if (semi >= 20) a += 0.15;
  if (kospi2 >= 50) a += 0.15;
  if (!buffer) a += 0.2; // 완충 부재 → 지수·경제 전이(핀란드형)
  return Math.min(a, 1.6);
}

// count of negative markets in one period column
function countNeg(markets, colIdx) {
  return markets.reduce((n, m) => {
    const v = parseFloat(m.r[colIdx]);
    return n + (!isNaN(v) && v < 0 ? 1 : 0);
  }, 0);
}
// derive 신호1 inputs from 도표48 table for the selected period
function analyzeMarkets(markets, periodKey) {
  const col = (PERIODS.find((p) => p.k === periodKey) || PERIODS[3]).i;
  let neg = 0;
  let worstNew = 0; // 신규 이탈(12M 플러스 → 최근 마이너스)의 최저 낙폭
  markets.forEach((m) => {
    const v = parseFloat(m.r[col]);
    const v12 = parseFloat(m.r[0]);
    if (!isNaN(v) && v < 0) {
      neg++;
      if (!isNaN(v12) && v12 > 0) worstNew = Math.min(worstNew, v);
    }
  });
  // 리포트: 이탈 국가들의 낙폭 자체가 아직 얕음(만성 약세국 제외). 신규 이탈의 깊이로 판정.
  let depth = "shallow";
  if (worstNew <= -12) depth = "deep";
  else if (worstNew <= -6) depth = "deepening";
  return { neg, worstNew, depth, col };
}

function composite(s) {
  const ma = analyzeMarkets(s.markets, s.s1_period);
  const s1 = scoreS1(ma.neg, ma.depth);
  const s2 = scoreS2(s.s2_up, s.s2_down, s.s2_deepening);
  const s3 = scoreS3(s.s3_lossRatio, s.s3_etf, s.s3_bigDeal);
  const gate = scoreGate(s.s4_rate, s.s4_dir, s.s4_credit, s.s4_marginCall);
  const amp = amplifier(s.amp_semiExport, s.amp_kospi2, s.amp_buffer);
  const lead = s1 + s2 + s3; // 0..9
  const leadPct = Math.round((lead / 9) * 100);
  const warn = [s1, s2, s3].filter((x) => x >= 2).length; // 경고 이상 신호 수

  let phase; // GREEN | AMBER | ORANGE | RED
  if (gate >= 3 && warn >= 1)
    phase = "RED"; // 결정타(긴축 돌입) + 선행 경고 = 톱니바퀴 격발
  else if (gate >= 2 || (lead >= 6 && gate >= 1))
    phase = "ORANGE"; // 방아쇠 임박
  else if (lead >= 3 || gate >= 1) phase = "AMBER"; // 신호 점등 · 방아쇠 대기
  else phase = "GREEN"; // 안정
  return { s1, s2, s3, gate, amp, lead, leadPct, warn, phase, ma };
}

const PHASE_META = {
  GREEN: {
    c: C.go,
    label: "안정 국면",
    sub: "신호등 소등 · 상승 지속 가능",
    desc:
      "선행 신호가 켜지지 않았고 금리 방아쇠도 정상화 구간이다. 쏠림 자체는 위험이 아니라는 리포트의 전제가 그대로 유효한 상태.",
  },
  AMBER: {
    c: C.amber,
    label: "신호 점등 · 방아쇠 대기",
    sub: "선행 신호는 켜졌으나 결정타(금리)는 미발동",
    desc:
      "주변부 균열·양방향 변동성·레버리지 급증으로 신호등엔 불이 켜졌다. 다만 정상화를 넘어선 금리 인상과 적자기업 IPO 급증이라는 결정타는 아직 당겨지지 않았다. — 리포트의 현 진단.",
  },
  ORANGE: {
    c: C.orange,
    label: "방아쇠 임박",
    sub: "금리 임계 접근 · 선행 신호 경고 다수",
    desc:
      "금리가 정상화 한계선(≈4.5%)에 다가서거나 선행 신호가 경고 이상으로 다수 점등됐다. 심리 균열이 실물 공백과 세트로 엮이기 직전 구간.",
  },
  RED: {
    c: C.red,
    label: "약세장 격발",
    sub: "긴축 돌입 + 선행 신호 위험 = 톱니바퀴 결합",
    desc:
      "금리가 진짜 긴축으로 인식되며 멀티플·유동성·수요를 동시에 마비시킨다. 심리 압축과 실물 둔화가 맞물려 파괴력이 기하급수적으로 증폭되는 국면.",
  },
};

// ------------------------------------------------------------------
// static reference — 약세장 3유형 (붕괴 챕터) + 모니터링 체크리스트 (한국 대입)
// ------------------------------------------------------------------
const TYPES = [
  {
    n: "유형 1",
    title: "경쟁 · 역전",
    axis: "이익 훼손",
    recovery: "회복 가능성 최저",
    recoveryC: C.red,
    theory: "크리스텐슨 파괴적 혁신 · 슘페터 창조적 파괴",
    cases: "노키아(스마트폰 오판, 5년 내 한 자릿수) · 인텔(파운드리 주도권 상실, 2025 배당 중단) · 팬택 · LG 스마트폰",
    why: "전방 수요는 견조하나 기술 전환 실패·후발 추격으로 1위 자리를 내줌. 점유율→가격결정력→마진이 영구 손상.",
    monitor: [
      "CXMT·YMTC 양산 규모 / 수율 안정화 / 세대 격차 축소",
      "마이크론 HBM 점유율 변화 · 차세대(HBM4) 진척도",
      "미국 정부 보조금·지분 참여 강도",
      "엔비디아 공급망 다변화 정책 속도",
    ],
  },
  {
    n: "유형 2",
    title: "전방 수요 · 사이클",
    axis: "이익 훼손",
    recovery: "회복 가능성 중간",
    recoveryC: C.amber,
    theory: "거미집 이론 · 챈슬러 자본 사이클",
    cases: "한진해운(발틱운임 12,000→700, 4년 연속 적자, 2017 파산) · 에릭손(통신버블, 고점比 −90%) · 국내 조선",
    why: "기업 경쟁력은 유지되나 전방 수요·가격 사이클이 붕괴. 호황기 증설이 부메랑(시차의 함정). 사이클 반전 시 재기 가능하나 버틸 체력 필요.",
    monitor: [
      "HBM 연간 계약 유지 여부 · 평균판매가(ASP) 변화",
      "범용 D램·낸드 고정거래가 / 현물가 추이",
      "북미 AI 데이터센터 전력 확보 현황 · 완공 일정",
      "글로벌 PC·스마트폰 실제 출하량 전망",
    ],
  },
  {
    n: "유형 3",
    title: "밸류에이션 · 금리",
    axis: "멀티플 수축",
    recovery: "펀더멘털 생존 · 인내 필요",
    recoveryC: C.accent,
    theory: "고든·윌리엄스 배당할인모형(고듀레이션 자산)",
    cases: "니프티 피프티(PER 40+ → −70~90%) · 시스코(닷컴 고점 회복에 25년) · 코카콜라(70년대 멀티플 압축)",
    why: "이익은 견고한데 금리 상승·위험선호 후퇴로 멀티플이 순식간에 압축. 듀레이션 긴 성장주일수록 타격이 치명적. — 리포트가 꼽은 최유력 경로.",
    monitor: [
      "엔비디아 실적 달성률 · 향후 CAPEX 가이던스",
      "미국 장기·실질 금리 환경",
      "연준(워시)·한국은행 금리 경로 및 정상화 한계선",
    ],
  },
];

// ------------------------------------------------------------------
// UI primitives
// ------------------------------------------------------------------
function Eyebrow({ children, color = C.mut }) {
  return (
    <div
      style={{
        fontFamily: MONO,
        fontSize: 10.5,
        letterSpacing: 2,
        textTransform: "uppercase",
        color,
        fontWeight: 600,
      }}
    >
      {children}
    </div>
  );
}

function Panel({ children, style }) {
  return (
    <div
      style={{
        background: C.panel,
        border: `1px solid ${C.line}`,
        borderRadius: 10,
        padding: 18,
        ...style,
      }}
    >
      {children}
    </div>
  );
}

function LevelPill({ level }) {
  return (
    <span
      style={{
        fontFamily: MONO,
        fontSize: 11,
        fontWeight: 700,
        color: LEVEL_C[level],
        border: `1px solid ${LEVEL_C[level]}`,
        borderRadius: 999,
        padding: "2px 9px",
        letterSpacing: 1,
      }}
    >
      {LEVELS[level]}
    </span>
  );
}

// 4-segment gauge (온도계)
function Gauge({ level, labels = LEVELS, colors = LEVEL_C }) {
  return (
    <div style={{ display: "flex", gap: 3, marginTop: 4 }}>
      {labels.map((lb, i) => {
        const on = i <= level;
        const cur = i === level;
        return (
          <div key={i} style={{ flex: 1 }}>
            <div
              style={{
                height: 8,
                borderRadius: 2,
                background: on ? colors[level] : C.lineSoft,
                opacity: on ? (cur ? 1 : 0.42) : 1,
                boxShadow: cur ? `0 0 10px ${colors[level]}66` : "none",
                transition: "all .18s",
              }}
            />
            <div
              style={{
                fontFamily: MONO,
                fontSize: 9,
                marginTop: 4,
                textAlign: "center",
                color: cur ? colors[level] : C.mut2,
                fontWeight: cur ? 700 : 400,
              }}
            >
              {lb}
            </div>
          </div>
        );
      })}
    </div>
  );
}

function Seg({ value, options, onChange }) {
  return (
    <div style={{ display: "flex", gap: 4, flexWrap: "wrap" }}>
      {options.map((o) => {
        const on = o.v === value;
        return (
          <button
            key={o.v}
            onClick={() => onChange(o.v)}
            style={{
              fontFamily: SANS,
              fontSize: 12,
              padding: "5px 10px",
              borderRadius: 6,
              cursor: "pointer",
              border: `1px solid ${on ? C.accent : C.line}`,
              background: on ? "rgba(61,169,224,0.14)" : "transparent",
              color: on ? C.text : C.mut,
              fontWeight: on ? 600 : 400,
            }}
          >
            {o.t}
          </button>
        );
      })}
    </div>
  );
}

function Stepper({ value, onChange, step = 1, min = 0, max = 999, unit = "" }) {
  const set = (v) => onChange(Math.max(min, Math.min(max, +v.toFixed(2))));
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
      <button onClick={() => set(value - step)} style={stepBtn}>
        −
      </button>
      <div
        style={{
          fontFamily: MONO,
          fontSize: 15,
          fontWeight: 700,
          color: C.text,
          minWidth: 62,
          textAlign: "center",
        }}
      >
        {value}
        <span style={{ fontSize: 10, color: C.mut, marginLeft: 2 }}>{unit}</span>
      </div>
      <button onClick={() => set(value + step)} style={stepBtn}>
        +
      </button>
    </div>
  );
}
const stepBtn = {
  width: 26,
  height: 26,
  borderRadius: 6,
  border: `1px solid ${C.line}`,
  background: C.panel2,
  color: C.text,
  fontSize: 16,
  cursor: "pointer",
  lineHeight: 1,
};

// responsive 3-column layout used by the signal / type sections
const grid3 = {
  display: "grid",
  gridTemplateColumns: "repeat(auto-fit, minmax(280px, 1fr))",
  gap: 14,
};

// country-return table cells
const thBase = {
  padding: "6px 3px",
  textAlign: "center",
  fontSize: 10.5,
  borderBottom: `1px solid ${C.line}`,
  whiteSpace: "nowrap",
};
const tdBase = {
  padding: "3px 4px",
  textAlign: "center",
  borderBottom: `1px solid ${C.lineSoft}`,
};

function Field({ label, children, hint }) {
  return (
    <div style={{ marginTop: 12 }}>
      <div style={{ fontFamily: SANS, fontSize: 12.5, color: C.mut, marginBottom: 6 }}>
        {label}
        {hint && (
          <span style={{ color: C.mut2, marginLeft: 6, fontSize: 11 }}>{hint}</span>
        )}
      </div>
      {children}
    </div>
  );
}

function Source({ children }) {
  return (
    <div
      style={{
        marginTop: 14,
        paddingTop: 10,
        borderTop: `1px dashed ${C.lineSoft}`,
        fontFamily: SANS,
        fontSize: 11,
        color: C.mut2,
        lineHeight: 1.55,
      }}
    >
      <span style={{ color: C.mut, fontWeight: 600 }}>근거 </span>
      {children}
    </div>
  );
}

// signal container with header
function SignalPanel({ tag, title, level, levelLabels, children }) {
  return (
    <Panel>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
        <div>
          <Eyebrow>{tag}</Eyebrow>
          <div style={{ fontFamily: SANS, fontSize: 16, fontWeight: 700, color: C.text, marginTop: 3 }}>
            {title}
          </div>
        </div>
        {levelLabels ? (
          <span
            style={{
              fontFamily: MONO,
              fontSize: 11,
              fontWeight: 700,
              color: LEVEL_C[level],
              border: `1px solid ${LEVEL_C[level]}`,
              borderRadius: 999,
              padding: "2px 9px",
            }}
          >
            {levelLabels[level]}
          </span>
        ) : (
          <LevelPill level={level} />
        )}
      </div>
      <div style={{ marginTop: 10 }}>
        <Gauge level={level} labels={levelLabels || LEVELS} />
      </div>
      {children}
    </Panel>
  );
}

// ------------------------------------------------------------------
// main
// ------------------------------------------------------------------
const freshState = () => ({
  ...BASELINE,
  markets: BASELINE.markets.map((m) => ({ ...m, r: [...m.r] })),
});

export default function App() {
  const [s, setS] = useState(freshState);
  const r = useMemo(() => composite(s), [s]);
  const set = (k) => (v) => setS((p) => ({ ...p, [k]: v }));
  // edit one return cell (row i, period column j) immutably
  const setCell = (i, j, val) =>
    setS((p) => ({
      ...p,
      markets: p.markets.map((row, ri) =>
        ri === i ? { ...row, r: row.r.map((c, cj) => (cj === j ? val : c)) } : row
      ),
    }));
  const pm = PHASE_META[r.phase];

  return (
    <div
      style={{
        fontFamily: SANS,
        background: C.bg,
        color: C.text,
        minHeight: "100vh",
        padding: "22px 20px 60px",
      }}
    >
      <div style={{ maxWidth: 1080, margin: "0 auto" }}>
        {/* HEADER */}
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-end", flexWrap: "wrap", gap: 12 }}>
          <div>
            <Eyebrow color={C.go}>BEAR-SIGNAL CONSOLE · 신영증권「주도주의 물리학」기반</Eyebrow>
            <h1 style={{ fontSize: 27, fontWeight: 800, margin: "8px 0 4px", letterSpacing: -0.5 }}>
              주도주 붕괴 판단 계기판
            </h1>
            <div style={{ fontSize: 13, color: C.mut, maxWidth: 640, lineHeight: 1.5 }}>
              주가 = <b style={{ color: C.text }}>이익</b> × <b style={{ color: C.text }}>멀티플</b>. 셋의 온도계(선행
              신호)와 하나의 방아쇠(금리)로 약세장 전환을 추적한다. 값을 갱신하면 국면이 즉시 재계산된다.
            </div>
          </div>
          <button
            onClick={() => setS(freshState())}
            style={{
              fontFamily: MONO,
              fontSize: 12,
              padding: "8px 14px",
              borderRadius: 8,
              border: `1px solid ${C.line}`,
              background: C.panel,
              color: C.mut,
              cursor: "pointer",
            }}
          >
            ↺ 리포트 기준값(2026.6.30)
          </button>
        </div>

        {/* VERDICT BAND */}
        <div
          style={{
            marginTop: 18,
            background: `linear-gradient(180deg, ${C.panel} 0%, ${C.panel2} 100%)`,
            border: `1px solid ${pm.c}55`,
            borderRadius: 12,
            padding: 20,
            display: "grid",
            gridTemplateColumns: "minmax(230px,1.1fr) minmax(230px,1fr) 150px",
            gap: 20,
            alignItems: "center",
          }}
        >
          {/* traffic light */}
          <div style={{ display: "flex", alignItems: "center", gap: 16 }}>
            <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
              {["RED", "ORANGE", "AMBER", "GREEN"].map((p) => {
                const on = p === r.phase;
                return (
                  <div
                    key={p}
                    style={{
                      width: 18,
                      height: 18,
                      borderRadius: "50%",
                      background: on ? PHASE_META[p].c : "#20293300",
                      border: `2px solid ${PHASE_META[p].c}${on ? "" : "44"}`,
                      boxShadow: on ? `0 0 14px ${PHASE_META[p].c}` : "none",
                    }}
                  />
                );
              })}
            </div>
            <div>
              <Eyebrow color={pm.c}>현 국면</Eyebrow>
              <div style={{ fontSize: 22, fontWeight: 800, color: pm.c, margin: "4px 0 2px" }}>
                {pm.label}
              </div>
              <div style={{ fontSize: 12.5, color: C.mut }}>{pm.sub}</div>
            </div>
          </div>

          {/* readouts */}
          <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
            <div>
              <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 4 }}>
                <span style={{ fontSize: 11, color: C.mut, fontFamily: MONO }}>선행 신호 점수 (온도계 3종)</span>
                <span style={{ fontFamily: MONO, fontWeight: 700, color: pm.c }}>{r.leadPct}/100</span>
              </div>
              <div style={{ height: 8, background: C.lineSoft, borderRadius: 4, overflow: "hidden" }}>
                <div style={{ width: `${r.leadPct}%`, height: "100%", background: pm.c, transition: "width .2s" }} />
              </div>
            </div>
            <div style={{ display: "flex", gap: 16 }}>
              <Readout k="금리 방아쇠" v={GATE_STATES[r.gate]} c={LEVEL_C[r.gate]} />
              <Readout k="집중 증폭" v={`×${r.amp.toFixed(2)}`} c={r.amp >= 1.3 ? C.orange : C.mut} />
              <Readout k="경고↑ 신호" v={`${r.warn} / 3`} c={r.warn >= 2 ? C.red : C.mut} />
            </div>
          </div>

          {/* mini radar */}
          <Radar s1={r.s1} s2={r.s2} s3={r.s3} gate={r.gate} color={pm.c} />
        </div>

        <div style={{ marginTop: 10, fontSize: 12.5, color: C.mut, lineHeight: 1.6, padding: "0 2px" }}>
          {pm.desc}
        </div>

        {/* SECTION: 선행 신호 3종 */}
        <SectionTitle n="온도계" t="선행 신호 3종 — 위험선호가 어디까지 식었나" />
        <div style={grid3}>
          {/* 신호1 */}
          <SignalPanel tag="신호 1 · 주변부 압착" title="주변부부터 식어가는가" level={r.s1}>
            <Field label="이탈 판정 기준 기간" hint="관찰 기간을 좁힐수록 이탈 수 ↑">
              <Seg
                value={s.s1_period}
                onChange={set("s1_period")}
                options={PERIODS.map((p) => ({ v: p.k, t: p.t }))}
              />
            </Field>
            <div style={{ display: "flex", gap: 18, marginTop: 14, flexWrap: "wrap" }}>
              <Readout k="이탈 지수 수" v={`${r.ma.neg} / 20`} c={r.ma.neg >= 7 ? C.orange : C.mut} />
              <Readout
                k="신규 이탈 최저 낙폭"
                v={`${r.ma.worstNew.toFixed(1)}%`}
                c={r.ma.worstNew <= -6 ? C.orange : C.mut}
              />
              <Readout
                k="낙폭 판정"
                v={{ shallow: "얕음", deepening: "심화 중", deep: "깊음" }[r.ma.depth]}
                c={LEVEL_C[r.s1]}
              />
            </div>
            <div style={{ fontFamily: MONO, fontSize: 10.5, color: C.mut2, marginTop: 9 }}>
              닷컴 정점 직전 1개월 = 7개국 이탈 · 아래 도표 48 표에서 자동 산출
            </div>
            <Source>
              1등 주도주는 끝까지 버티므로 신호는 <b style={{ color: C.mut }}>변방에서 먼저</b> 온다. 이탈 국가 수
              <b style={{ color: C.mut }}>와 낙폭이 동시에 확대</b>되는 순간이 진짜 천장. 낙폭은 만성 약세국을 뺀 신규
              이탈(12M 플러스 → 최근 마이너스) 기준 (도표 46~48).
            </Source>
          </SignalPanel>

          {/* 신호2 */}
          <SignalPanel tag="신호 2 · 변동성 무게중심" title="급락이 급등을 앞지르나" level={r.s2}>
            <Field label="직전 6개월 ±3σ 급변일 (상승 / 하락)">
              <div style={{ display: "flex", gap: 18 }}>
                <Stepper value={s.s2_up} onChange={set("s2_up")} min={0} max={60} unit="상승" />
                <Stepper value={s.s2_down} onChange={set("s2_down")} min={0} max={60} unit="하락" />
              </div>
              <div style={{ fontFamily: MONO, fontSize: 11, color: C.mut2, marginTop: 6 }}>
                하락/상승 = {(s.s2_up ? s.s2_down / s.s2_up : 0).toFixed(2)} · 1.0 초과 시 천장 신호
              </div>
            </Field>
            <Field label="한 번 밀릴 때 하락 깊이가 심화되는가">
              <Seg
                value={s.s2_deepening ? "y" : "n"}
                onChange={(v) => set("s2_deepening")(v === "y")}
                options={[
                  { v: "n", t: "아니오" },
                  { v: "y", t: "예 (비대칭 조짐)" },
                ]}
              />
            </Field>
            <Source>
              변동성 크기가 아니라 <b style={{ color: C.mut }}>시소의 무게중심</b>이 관건. 큰 하락일 빈도가 큰 상승일
              빈도를 앞지르면 천장. 현재는 6:4 상승 우세 (도표 49~50).
            </Source>
          </SignalPanel>

          {/* 신호3 */}
          <SignalPanel tag="신호 3 · IPO 질" title="위험선호의 거울" level={r.s3}>
            <Field label="적자기업 상장 비중" hint="평상 20~40% · 버블 ~80%">
              <Stepper value={s.s3_lossRatio} onChange={set("s3_lossRatio")} step={5} min={0} max={100} unit="%" />
            </Field>
            <Field label="Renaissance IPO ETF 방향">
              <Seg
                value={s.s3_etf}
                onChange={set("s3_etf")}
                options={[
                  { v: "up", t: "상승/회복" },
                  { v: "flat", t: "횡보" },
                  { v: "down", t: "하락 전환" },
                ]}
              />
            </Field>
            <Field label="대어(OpenAI·Anthropic 등) 공모 소화">
              <Seg
                value={s.s3_bigDeal}
                onChange={set("s3_bigDeal")}
                options={[
                  { v: "smooth", t: "원활" },
                  { v: "pending", t: "대기" },
                  { v: "failed", t: "실패/삐끗" },
                ]}
              />
            </Field>
            <Source>
              양이 아니라 질. 적자기업 비중과 신주 발행이 치솟으면 위험선호 과열 (Baker&Wurgler 2000). ETF는 지수보다
              먼저 꺾이고(2021), 대형 IPO의 흥행 여부가 거품을 먼저 검증 (도표 51~53).
            </Source>
          </SignalPanel>
        </div>

        {/* 신호 1 상세 — 도표 48 국가별 수익률 (편집 가능 · 신호 1 자동 산출) */}
        <div style={{ marginTop: 14 }}>
          <Panel style={{ padding: 0, overflow: "hidden" }}>
            <div
              style={{
                display: "flex",
                justifyContent: "space-between",
                alignItems: "baseline",
                flexWrap: "wrap",
                gap: 8,
                padding: "14px 16px 10px",
              }}
            >
              <div>
                <Eyebrow>신호 1 상세 · 도표 48</Eyebrow>
                <div style={{ fontSize: 14.5, fontWeight: 700, marginTop: 3 }}>국가별 주가 수익률 비교</div>
              </div>
              <div style={{ fontFamily: MONO, fontSize: 10.5, color: C.mut2, textAlign: "right", lineHeight: 1.5 }}>
                누적 %, 2026.6.26 기준 · 값 편집 가능
                <br />
                기간(행 머리) 클릭 = 신호 1 이탈 판정 기간 선택
              </div>
            </div>
            <div style={{ overflowX: "auto", padding: "0 16px 12px" }}>
              <table style={{ borderCollapse: "collapse", fontFamily: MONO, fontSize: 11.5, minWidth: 900 }}>
                <thead>
                  <tr>
                    <th style={{ ...thBase, textAlign: "left", minWidth: 62 }}>기간</th>
                    {s.markets.map((m, i) => (
                      <th
                        key={i}
                        style={{ ...thBase, color: m.lead ? C.go : C.mut, fontWeight: m.lead ? 800 : 600 }}
                      >
                        {m.name}
                        {m.lead && <span style={{ display: "block", fontSize: 8, color: C.go }}>주도</span>}
                      </th>
                    ))}
                    <th style={{ ...thBase, color: C.mut, borderLeft: `1px solid ${C.line}` }}>이탈</th>
                  </tr>
                </thead>
                <tbody>
                  {PERIODS.map((p) => {
                    const sel = p.k === s.s1_period;
                    const neg = countNeg(s.markets, p.i);
                    return (
                      <tr key={p.k} style={{ background: sel ? "rgba(61,169,224,0.10)" : "transparent" }}>
                        <td
                          onClick={() => set("s1_period")(p.k)}
                          style={{
                            ...tdBase,
                            textAlign: "left",
                            cursor: "pointer",
                            fontWeight: 700,
                            color: sel ? C.accent : C.text,
                            borderLeft: `2px solid ${sel ? C.accent : "transparent"}`,
                            whiteSpace: "nowrap",
                          }}
                        >
                          {p.t}
                        </td>
                        {s.markets.map((m, i) => {
                          const v = parseFloat(m.r[p.i]);
                          const cc = isNaN(v) ? C.mut : v < 0 ? C.red : v > 0 ? C.go : C.mut2;
                          return (
                            <td key={i} style={{ ...tdBase, padding: "2px 3px" }}>
                              <input
                                value={m.r[p.i]}
                                onChange={(e) => setCell(i, p.i, e.target.value)}
                                style={{
                                  width: 44,
                                  background: "transparent",
                                  border: "1px solid transparent",
                                  borderRadius: 4,
                                  color: cc,
                                  fontFamily: MONO,
                                  fontSize: 11.5,
                                  textAlign: "right",
                                  padding: "2px 3px",
                                  outline: "none",
                                }}
                                onFocus={(e) => (e.target.style.border = `1px solid ${C.accent}88`)}
                                onBlur={(e) => (e.target.style.border = "1px solid transparent")}
                              />
                            </td>
                          );
                        })}
                        <td
                          style={{
                            ...tdBase,
                            borderLeft: `1px solid ${C.line}`,
                            fontWeight: 800,
                            color: neg >= 7 ? C.orange : neg >= 4 ? C.amber : C.mut,
                          }}
                        >
                          {neg}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            <div
              style={{
                padding: "0 16px 14px",
                display: "flex",
                gap: 16,
                fontFamily: MONO,
                fontSize: 10.5,
                color: C.mut2,
                flexWrap: "wrap",
              }}
            >
              <span>
                <span style={{ color: C.go }}>■</span> 플러스
              </span>
              <span>
                <span style={{ color: C.red }}>■</span> 마이너스(이탈)
              </span>
              <span>
                <span style={{ color: C.accent }}>■</span> 선택된 판정 기간 → 신호 1 반영
              </span>
              <span style={{ color: C.mut2 }}>
                리포트: 6·3개월 각 4개국 → 1개월 급증. 관찰 기간을 좁힐수록 주변부 균열이 확산한다.
              </span>
            </div>
          </Panel>
        </div>

        {/* SECTION: 방아쇠 + 증폭 */}
        <SectionTitle n="방아쇠 · 증폭기" t="금리(결정타)와 집중(증폭 계수)" />
        <div style={{ display: "grid", gridTemplateColumns: "1.4fr 1fr", gap: 14 }}>
          {/* GATE */}
          <SignalPanel
            tag="신호 4 · 금리 [ 결정타 ]"
            title="세 신호를 하락으로 바꾸는 방아쇠"
            level={r.gate}
            levelLabels={GATE_STATES}
          >
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 }}>
              <Field label="기준금리 상단" hint="현재 3.75 · 임계 4.5">
                <Stepper value={s.s4_rate} onChange={set("s4_rate")} step={0.25} min={0} max={8} unit="%" />
              </Field>
              <Field label="정책 방향 (연준·한은)">
                <Seg
                  value={s.s4_dir}
                  onChange={set("s4_dir")}
                  options={[
                    { v: "ease", t: "완화" },
                    { v: "hold", t: "동결" },
                    { v: "hike", t: "인상" },
                  ]}
                />
              </Field>
              <Field label="신용잔고" hint="2023말 17.5조">
                <Stepper value={s.s4_credit} onChange={set("s4_credit")} step={1} min={0} max={80} unit="조" />
              </Field>
              <Field label="반대매매 임박 (담보 140% 근접)">
                <Seg
                  value={s.s4_marginCall ? "y" : "n"}
                  onChange={(v) => set("s4_marginCall")(v === "y")}
                  options={[
                    { v: "n", t: "아니오" },
                    { v: "y", t: "예" },
                  ]}
                />
              </Field>
            </div>
            <Source>
              금리는 유동성·수요·할인율 세 기둥을 동시에 마비시키는 상위 결정타. 정상화(≤임계)까지는 오히려 상승,
              한계선(≈4.5%)을 넘으면 진짜 긴축으로 인식. 매수 주체가 국내 신용으로 이동하며 방아쇠가 한국은행으로
              국산화 (도표 54~57).
            </Source>
          </SignalPanel>

          {/* AMP */}
          <Panel>
            <Eyebrow>유형 4 · 집중 [ 증폭 계수 ]</Eyebrow>
            <div style={{ fontSize: 16, fontWeight: 700, marginTop: 3 }}>충격을 키우는 계수</div>
            <div
              style={{
                fontFamily: MONO,
                fontSize: 34,
                fontWeight: 800,
                color: r.amp >= 1.3 ? C.orange : C.text,
                margin: "8px 0 2px",
              }}
            >
              ×{r.amp.toFixed(2)}
            </div>
            <div style={{ fontSize: 12, color: C.mut, marginBottom: 4 }}>
              방아쇠가 아니라 이미 당겨진 충격에 곱해지는 값
            </div>
            <Field label="반도체 수출 비중" hint="2023 15.6 → 2026 1Q 23.1">
              <Stepper value={s.amp_semiExport} onChange={set("amp_semiExport")} step={0.5} min={0} max={60} unit="%" />
            </Field>
            <Field label="삼성+SK 코스피 비중">
              <Stepper value={s.amp_kospi2} onChange={set("amp_kospi2")} step={1} min={0} max={90} unit="%" />
            </Field>
            <Field label="완충 산업 건재 (자동차·기계·석유)">
              <Seg
                value={s.amp_buffer ? "y" : "n"}
                onChange={(v) => set("amp_buffer")(v === "y")}
                options={[
                  { v: "y", t: "건재" },
                  { v: "n", t: "부재(핀란드형)" },
                ]}
              />
            </Field>
            <Source>
              분산된 시장은 한 산업이 무너져도 다른 축이 받쳐 낙폭을 희석하지만, 완충이 없으면 주도 산업 충격 = 지수 =
              경제 전이(노키아·핀란드). 한국은 자동차 9.8·기계 7.2·석유제품 7.1% 등 완충 존재 (도표 44).
            </Source>
          </Panel>
        </div>

        {/* SECTION: 약세장 3유형 */}
        <SectionTitle n="유형 진단" t="주도주 하락세 판단 — 약세장 3유형과 회복 가능성" />
        <div style={grid3}>
          {TYPES.map((t, i) => {
            const active = i === 2 && r.gate >= 1; // 유형3(금리)이 현재 활성 방아쇠
            return (
              <Panel key={t.n} style={{ borderColor: active ? `${C.accent}77` : C.line }}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
                  <div>
                    <Eyebrow color={active ? C.accent : C.mut}>{t.n}</Eyebrow>
                    <div style={{ fontSize: 16, fontWeight: 700, marginTop: 3 }}>{t.title}</div>
                  </div>
                  <span style={{ fontFamily: MONO, fontSize: 10, color: C.mut2, border: `1px solid ${C.line}`, borderRadius: 5, padding: "2px 6px" }}>
                    {t.axis}
                  </span>
                </div>
                {active && (
                  <div style={{ fontFamily: MONO, fontSize: 10.5, color: C.accent, marginTop: 6, fontWeight: 700 }}>
                    ● 현재 활성 방아쇠 (리포트 최유력)
                  </div>
                )}
                <div style={{ fontSize: 12.5, color: C.mut, lineHeight: 1.55, marginTop: 8 }}>{t.why}</div>
                <div style={{ marginTop: 10, fontSize: 12, fontWeight: 700, color: t.recoveryC }}>{t.recovery}</div>
                <div style={{ fontSize: 11.5, color: C.mut2, marginTop: 3 }}>이론 · {t.theory}</div>
                <div style={{ fontSize: 11.5, color: C.mut2, marginTop: 3, lineHeight: 1.5 }}>사례 · {t.cases}</div>
                <div style={{ marginTop: 12, paddingTop: 10, borderTop: `1px dashed ${C.lineSoft}` }}>
                  <div style={{ fontFamily: MONO, fontSize: 10, color: C.mut, letterSpacing: 1, marginBottom: 6 }}>
                    모니터링 체크리스트
                  </div>
                  {t.monitor.map((m, k) => (
                    <div key={k} style={{ display: "flex", gap: 7, fontSize: 11.5, color: C.mut, marginBottom: 5, lineHeight: 1.4 }}>
                      <span style={{ color: t.recoveryC }}>▪</span>
                      <span>{m}</span>
                    </div>
                  ))}
                </div>
              </Panel>
            );
          })}
        </div>

        {/* SECTION: 역사 검증 */}
        <SectionTitle n="역사 검증" t="최악의 조합 — 3충격 동시 결합 (일본 1980s)" />
        <Panel style={{ borderColor: `${C.red}44` }}>
          <div style={{ fontSize: 13, color: C.mut, lineHeight: 1.65, maxWidth: 900 }}>
            세 유형은 독립적으로만 오지 않는다. 1980년대 일본 메모리 산업은 세 충격을 <b style={{ color: C.text }}>시차를 두고 겹쳐</b> 맞으며 무너졌다 —
            ① <b style={{ color: C.accent }}>플라자 합의 엔고 + 버블 붕괴·금리</b>(멀티플·유형3), ② <b style={{ color: C.amber }}>PC 전환·다운사이클</b>(전방수요·유형2), ③ <b style={{ color: C.red }}>한국·대만 추격</b>(경쟁·유형1). 결국 엘피다가 2013년 마이크론에 피인수. 지금 한국이 서 있는 자리가 1988년 일본과 겹치지 않는지 감시해야 할 3대 지표:
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(3,1fr)", gap: 12, marginTop: 14 }}>
            {[
              ["매크로", "환율 지형·보호무역 장벽 — 인위적 환율 변동성 및 SCM 재편 방어막"],
              ["경쟁", "중국 레거시 잠식·미국 자국 지원 — 역사이클 CAPEX 유입 속도·치킨게임 재발"],
              ["포트폴리오", "단일 품목 의존 탈피 — HBM 맞춤형·파운드리·시스템LSI 다변화 성과"],
            ].map(([h, b], i) => (
              <div key={i} style={{ background: C.panel2, border: `1px solid ${C.line}`, borderRadius: 8, padding: 12 }}>
                <div style={{ fontFamily: MONO, fontSize: 10, color: C.mut, letterSpacing: 1 }}>{h}</div>
                <div style={{ fontSize: 12, color: C.text, marginTop: 6, lineHeight: 1.5 }}>{b}</div>
              </div>
            ))}
          </div>
        </Panel>

        {/* FOOTER */}
        <div style={{ marginTop: 22, paddingTop: 16, borderTop: `1px solid ${C.line}`, fontSize: 11, color: C.mut2, lineHeight: 1.7 }}>
          <div style={{ fontFamily: MONO, color: C.mut, marginBottom: 6 }}>지표 ↔ 리포트 매핑</div>
          신호1 도표46~48 · 신호2 도표49~50 · 신호3 도표51~53 · 신호4(금리) 도표54~57 · 집중 증폭 도표44 · 약세장 3유형 도표26~35 · 역사 검증 도표58.
          <br />
          <span style={{ color: C.mut2 }}>
            모든 임계치·사례·이론은 신영증권「주도주의 물리학」(2026.6.30) 원문에만 근거한다. 국면 판정 규칙(가중·상태
            기계)은 리포트 서술을 코드화한 것으로 투자 판단의 보조 도구이며, 종목 선택·투자 시기의 최종 책임 근거로
            사용될 수 없다.
          </span>
        </div>
      </div>
    </div>
  );
}

// small readout
function Readout({ k, v, c }) {
  return (
    <div>
      <div style={{ fontSize: 10.5, color: C.mut, fontFamily: MONO }}>{k}</div>
      <div style={{ fontFamily: MONO, fontSize: 15, fontWeight: 700, color: c, marginTop: 2 }}>{v}</div>
    </div>
  );
}

// section title
function SectionTitle({ n, t }) {
  return (
    <div style={{ margin: "28px 0 12px", display: "flex", alignItems: "baseline", gap: 12 }}>
      <span style={{ fontFamily: MONO, fontSize: 10.5, color: C.go, letterSpacing: 2, textTransform: "uppercase" }}>
        {n}
      </span>
      <span style={{ fontSize: 15, fontWeight: 700, color: C.text }}>{t}</span>
      <div style={{ flex: 1, height: 1, background: C.line }} />
    </div>
  );
}

// mini radar (SVG) — 4 axes: s1,s2,s3,gate on 0..3
function Radar({ s1, s2, s3, gate, color }) {
  const vals = [s1, s2, s3, gate];
  const labels = ["주변부", "변동성", "IPO", "금리"];
  const cx = 62,
    cy = 62,
    R = 42;
  const pt = (i, v) => {
    const a = -Math.PI / 2 + (i * Math.PI) / 2; // 4 axes, top-clockwise
    const rr = (v / 3) * R;
    return [cx + rr * Math.cos(a), cy + rr * Math.sin(a)];
  };
  const poly = vals.map((v, i) => pt(i, v).join(",")).join(" ");
  return (
    <svg width="124" height="124" style={{ margin: "0 auto", display: "block" }}>
      {[1, 2, 3].map((g) => (
        <polygon
          key={g}
          points={[0, 1, 2, 3].map((i) => pt(i, g).join(",")).join(" ")}
          fill="none"
          stroke={C.lineSoft}
          strokeWidth="1"
        />
      ))}
      {[0, 1, 2, 3].map((i) => {
        const [x, y] = pt(i, 3);
        return <line key={i} x1={cx} y1={cy} x2={x} y2={y} stroke={C.lineSoft} strokeWidth="1" />;
      })}
      <polygon points={poly} fill={`${color}33`} stroke={color} strokeWidth="1.6" />
      {vals.map((v, i) => {
        const [x, y] = pt(i, v);
        return <circle key={i} cx={x} cy={y} r="2.4" fill={color} />;
      })}
      {labels.map((lb, i) => {
        const [x, y] = pt(i, 3.55);
        return (
          <text
            key={i}
            x={x}
            y={y}
            fill={C.mut2}
            fontSize="8"
            fontFamily={MONO}
            textAnchor="middle"
            dominantBaseline="middle"
          >
            {lb}
          </text>
        );
      })}
    </svg>
  );
}
