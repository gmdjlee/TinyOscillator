<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>박병창 매매의 기술 — 완전 정리</title>
<style>
:root {
  --bg: #f7f6f3;
  --card: #ffffff;
  --border: #e5e4e0;
  --text: #1c1c1a;
  --muted: #6b6b67;
  --faint: #a0a09a;
  --buy-bg: #eaf3de; --buy-text: #2e5a0e; --buy-border: #b8d98a;
  --sell-bg: #fce9e9; --sell-text: #8b2020; --sell-border: #f0a0a0;
  --warn-bg: #fef3e2; --warn-text: #7a4a00; --warn-border: #f5c878;
  --info-bg: #e8f2fd; --info-text: #1050a0; --info-border: #90c0f0;
  --purple-bg: #eeecfe; --purple-text: #3a2fa0; --purple-border: #b8b0f0;
  --radius: 12px; --radius-sm: 8px;
}
* { box-sizing: border-box; margin: 0; padding: 0; }
body { font-family: -apple-system, BlinkMacSystemFont, 'Apple SD Gothic Neo', 'Noto Sans KR', sans-serif;
  background: var(--bg); color: var(--text); font-size: 14px; line-height: 1.65; }
.page { max-width: 860px; margin: 0 auto; padding: 28px 16px 60px; }

/* ── Header ── */
.page-header { margin-bottom: 28px; }
.page-title { font-size: 24px; font-weight: 700; letter-spacing: -0.3px; }
.page-subtitle { font-size: 13px; color: var(--muted); margin-top: 5px; }
.page-meta { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 10px; }
.meta-chip { font-size: 11px; padding: 3px 10px; border-radius: 20px; background: var(--card);
border: 1px solid var(--border); color: var(--muted); }

/* ── Tabs ── */
.tab-bar { display: flex; gap: 5px; flex-wrap: wrap; margin-bottom: 20px; }
.tab-btn { font-size: 12px; font-weight: 500; padding: 6px 14px; border-radius: 20px;
border: 1px solid var(--border); cursor: pointer; background: var(--card);
color: var(--muted); transition: all 0.15s; }
.tab-btn:hover { border-color: #aaa; color: var(--text); }
.tab-btn.on { background: var(--text); color: #fff; border-color: var(--text); }
.tab-panel { display: none; }
.tab-panel.on { display: block; }

/* ── Section ── */
.sec-title { font-size: 11px; font-weight: 700; letter-spacing: 0.08em; text-transform: uppercase;
color: var(--faint); border-bottom: 1px solid var(--border); padding-bottom: 6px; margin-bottom: 14px; }

/* ── Cards ── */
.card { background: var(--card); border: 1px solid var(--border); border-radius: var(--radius);
padding: 16px 18px; margin-bottom: 12px; }
.card-title { font-size: 14px; font-weight: 700; margin-bottom: 10px; display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }

/* ── Badges ── */
.badge { font-size: 11px; font-weight: 700; padding: 3px 9px; border-radius: 20px; white-space: nowrap; }
.b-buy { background: var(--buy-bg); color: var(--buy-text); border: 1px solid var(--buy-border); }
.b-sell { background: var(--sell-bg); color: var(--sell-text); border: 1px solid var(--sell-border); }
.b-info { background: var(--info-bg); color: var(--info-text); border: 1px solid var(--info-border); }
.b-warn { background: var(--warn-bg); color: var(--warn-text); border: 1px solid var(--warn-border); }
.b-purple { background: var(--purple-bg); color: var(--purple-text); border: 1px solid var(--purple-border); }
.b-neutral { background: var(--bg); color: var(--muted); border: 1px solid var(--border); }

/* ── Rules list ── */
.rule-list { list-style: none; }
.rule-item { display: flex; gap: 11px; align-items: flex-start; padding: 10px 0;
border-bottom: 1px solid #f2f1ed; }
.rule-item:last-child { border-bottom: none; }
.step-circle { width: 24px; height: 24px; border-radius: 50%; font-size: 11px; font-weight: 700;
display: flex; align-items: center; justify-content: center; flex-shrink: 0; margin-top: 1px; }
.sc-buy { background: var(--buy-bg); color: var(--buy-text); }
.sc-sell { background: var(--sell-bg); color: var(--sell-text); }
.sc-gray { background: var(--bg); color: var(--muted); }
.sc-info { background: var(--info-bg); color: var(--info-text); }
.sc-warn { background: var(--warn-bg); color: var(--warn-text); }
.rule-body { flex: 1; }
.rule-name { font-size: 13px; font-weight: 700; color: var(--text); }
.rule-desc { font-size: 12px; color: var(--muted); margin-top: 3px; line-height: 1.55; }
.rule-tags { display: flex; gap: 5px; flex-wrap: wrap; margin-top: 6px; }
.rtag { font-size: 11px; padding: 2px 8px; border-radius: 12px;
background: var(--bg); border: 1px solid var(--border); color: var(--muted); }

/* ── MA Zone Visual ── */
.ma-zone-row { display: flex; align-items: stretch; margin: 10px 0 4px; font-size: 12px; font-weight: 700; }
.mz { flex: 1; text-align: center; padding: 7px 4px; }
.mz-buy { background: var(--buy-bg); color: var(--buy-text); border-radius: var(--radius-sm) 0 0 var(--radius-sm); }
.mz-warn { background: var(--warn-bg); color: var(--warn-text); }
.mz-sell { background: var(--sell-bg); color: var(--sell-text); border-radius: 0 var(--radius-sm) var(--radius-sm) 0; }
.mz-arr { display: flex; align-items: center; color: var(--faint); padding: 0 2px; font-size: 10px; }

/* ── Signal Grid ── */
.sig-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-bottom: 12px; }
.sig-box { border-radius: var(--radius-sm); padding: 14px 15px; border: 1px solid; }
.sg-buy { background: var(--buy-bg); border-color: var(--buy-border); }
.sg-sell { background: var(--sell-bg); border-color: var(--sell-border); }
.sg-info { background: var(--info-bg); border-color: var(--info-border); }
.sg-warn { background: var(--warn-bg); border-color: var(--warn-border); }
.sig-type { font-size: 10px; font-weight: 700; letter-spacing: 0.05em; margin-bottom: 4px; opacity: 0.7; }
.sig-name { font-size: 14px; font-weight: 700; margin-bottom: 8px; }
.sig-conditions { list-style: none; }
.sig-conditions li { font-size: 12px; line-height: 1.5; padding: 2px 0; }
.sig-conditions li::before { content: "▸ "; opacity: 0.6; }
.sig-result { font-size: 12px; font-weight: 700; margin-top: 8px; padding-top: 8px; border-top: 1px solid rgba(0,0,0,0.08); }

/* ── Concept Grid ── */
.con-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 9px; margin-bottom: 14px; }
.con-box { background: var(--card); border: 1px solid var(--border); border-radius: var(--radius-sm); padding: 12px 13px; }
.con-num { font-size: 10px; font-weight: 700; color: var(--faint); margin-bottom: 3px; }
.con-name { font-size: 13px; font-weight: 700; margin-bottom: 5px; }
.con-desc { font-size: 11px; color: var(--muted); line-height: 1.5; }

/* ── Principle Pill ── */
.pp { border-left: 3px solid; border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
padding: 11px 14px; margin-bottom: 9px; }
.pp-buy { background: #f2f8ea; border-color: #72b830; }
.pp-sell { background: #fef0f0; border-color: #e05050; }
.pp-info { background: var(--info-bg); border-color: #5090d8; }
.pp-purple { background: var(--purple-bg); border-color: #6060d0; }
.pp-warn { background: var(--warn-bg); border-color: #d8a020; }
.pp-name { font-size: 13px; font-weight: 700; margin-bottom: 4px; }
.pp-desc { font-size: 12px; color: var(--muted); line-height: 1.6; }

/* ── Time Blocks ── */
.time-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-bottom: 12px; }
.time-card { border: 1px solid var(--border); border-radius: var(--radius-sm); padding: 13px 15px; background: var(--card); }
.time-header { font-size: 12px; font-weight: 700; margin-bottom: 10px; display: flex; align-items: center; gap: 6px; }
.time-row { display: flex; gap: 6px; margin-top: 8px; }
.tblock { border-radius: 6px; padding: 7px 12px; font-size: 11px; flex: 1; text-align: center; }
.tb-buy { background: var(--buy-bg); color: var(--buy-text); }
.tb-sell { background: var(--sell-bg); color: var(--sell-text); }
.tb-label { font-weight: 700; font-size: 10px; margin-bottom: 2px; }

/* ── Table ── */
.comp-table { width: 100%; border-collapse: collapse; font-size: 12px; }
.comp-table th { background: var(--bg); font-size: 11px; font-weight: 700; color: var(--muted);
padding: 8px 12px; text-align: left; border-bottom: 2px solid var(--border); }
.comp-table td { padding: 9px 12px; border-bottom: 1px solid #f0efe9; vertical-align: top; }
.comp-table tr:last-child td { border-bottom: none; }
.comp-table .buy-cell { color: var(--buy-text); font-weight: 600; }
.comp-table .sell-cell { color: var(--sell-text); font-weight: 600; }

/* ── Philosophy Box ── */
.philo-box { background: linear-gradient(135deg, #1c1c1a 0%, #2d2d28 100%);
border-radius: var(--radius); padding: 20px 22px; margin-bottom: 12px; color: #f0efe8; }
.philo-quote { font-size: 16px; font-weight: 700; line-height: 1.5; margin-bottom: 8px; }
.philo-sub { font-size: 12px; color: #a0a090; line-height: 1.6; }

/* ── 50% Rule Visual ── */
.fifty-visual { background: var(--bg); border-radius: var(--radius-sm); padding: 14px;
margin: 10px 0; display: flex; align-items: center; justify-content: center; gap: 20px; }
.candle-wrap { display: flex; flex-direction: column; align-items: center; gap: 6px; }
.candle-label { font-size: 11px; color: var(--muted); font-weight: 600; }
.candle-body { display: flex; flex-direction: column; align-items: center; gap: 1px; }
.candle-wick { width: 2px; height: 12px; background: #888; border-radius: 1px; }
.candle-rect { width: 28px; border-radius: 3px; position: relative; }
.candle-line { position: absolute; width: 100%; border-top: 2px dashed rgba(0,0,0,0.3); left: 0; }
.yang { background: #e05050; height: 60px; }
.eum { background: #185fa5; height: 60px; }
.rule-arrow { font-size: 20px; color: var(--faint); }
.rule-result { text-align: center; }
.rr-title { font-size: 12px; font-weight: 700; margin-bottom: 4px; }
.rr-desc { font-size: 11px; color: var(--muted); }

/* ── Tags row ── */
.tag-row { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 9px; }
.tag { font-size: 11px; padding: 3px 9px; border-radius: 14px; background: var(--bg);
border: 1px solid var(--border); color: var(--muted); }

/* ── Divider ── */
hr { border: none; border-top: 1px solid var(--border); margin: 16px 0; }

/* ── Footer ── */
.foot { font-size: 11px; color: var(--faint); text-align: right; margin-top: 18px; padding-top: 10px; border-top: 1px solid var(--border); }

/* ── Flow Visual ── */
.flow-row { display: flex; align-items: center; gap: 0; margin: 12px 0; font-size: 12px; }
.flow-box { flex: 1; padding: 8px 6px; text-align: center; font-weight: 600; }
.flow-arr { color: var(--faint); font-size: 12px; padding: 0 2px; }
.fb-1 { background: #f0f8e8; color: #2e5a0e; border-radius: 6px 0 0 6px; }
.fb-2 { background: #fef8e8; color: #7a4a00; }
.fb-3 { background: #feeaea; color: #8b2020; border-radius: 0 6px 6px 0; }
.fb-sub { font-size: 10px; font-weight: 400; opacity: 0.8; display: block; margin-top: 2px; }

/* ── Responsive ── */
@media(max-width:520px){
.sig-grid,.con-grid,.time-grid { grid-template-columns: 1fr; }
.flow-box { padding: 6px 3px; font-size: 11px; }
}
</style>
</head>
<body>
<div class="page">

  <!-- ── Header ── -->
  <div class="page-header">
    <div class="page-title">📊 박병창의 매매의 기술 — 완전 정리</div>
    <div class="page-subtitle">교보증권 박병창 | 30년 실전 트레이딩 원칙 체계</div>
    <div class="page-meta">
      <span class="meta-chip">📚 매매의 기술 (포레스트북스, 2021)</span>
      <span class="meta-chip">🎯 매수 3원칙 · 매도 2원칙</span>
      <span class="meta-chip">📡 16가지 상황별 타이밍</span>
      <span class="meta-chip">⚡ 장중 시그널 4유형</span>
    </div>
  </div>

  <!-- ── Tabs ── -->
  <div class="tab-bar">
    <button class="tab-btn on" onclick="show('philosophy',this)">🦁 기본철학</button>
    <button class="tab-btn" onclick="show('buy',this)">🟢 매수신호</button>
    <button class="tab-btn" onclick="show('sell',this)">🔴 매도신호</button>
    <button class="tab-btn" onclick="show('fifty',this)">📏 50% 룰</button>
    <button class="tab-btn" onclick="show('signals',this)">⚡ 장중시그널</button>
    <button class="tab-btn" onclick="show('concepts',this)">🔬 6대개념</button>
    <button class="tab-btn" onclick="show('timing',this)">⏰ 시간대전략</button>
    <button class="tab-btn" onclick="show('skills',this)">🛠 실전스킬</button>
    <button class="tab-btn" onclick="show('compare',this)">📋 비교표</button>
  </div>

  <!-- ════════════ 기본 철학 ════════════ -->
  <div id="tab-philosophy" class="tab-panel on">
    <div class="philo-box">
      <div class="philo-quote">"주식은 타이밍이다.<br>황소와 곰의 힘겨루기를 읽는 자가 이긴다."</div>
      <div class="philo-sub">30년 실전에서 깨달은 한 가지 — 주가 움직임의 원리는 불변한다.<br>황소(Bull)가 가격을 올려서 매수할 때 시장은 오르고, 곰(Bear)이 가격을 내려서 매도할 때 시장은 내린다.<br>그 힘겨루기의 세기가 거래량이고, 거래량이 만드는 변동성이 트레이더의 기회다.</div>
    </div>

    <div class="sec-title">시장 메커니즘 이해</div>
    <div class="card">
      <div class="card-title">황소 vs 곰의 힘겨루기 구조</div>
      <ul class="rule-list">
        <li class="rule-item">
          <div class="step-circle sc-buy">🐂</div>
          <div class="rule-body">
            <div class="rule-name">황소(Bull) — 매수 주도 세력</div>
            <div class="rule-desc">가격을 올려서라도 매수. 강한 상승 의지. 매수 호가를 높이며 진입. 순간체결량이 상방으로 연속 발생. → 주가 상승·거래량 급증.</div>
          </div>
        </li>
        <li class="rule-item">
          <div class="step-circle sc-sell">🐻</div>
          <div class="rule-body">
            <div class="rule-name">곰(Bear) — 매도 주도 세력</div>
            <div class="rule-desc">가격을 내려서라도 매도. 매도 호가를 낮추며 진입. 매수 호가 잔량 감소. 순간체결량이 하방으로 연속 발생. → 주가 하락·거래량 급증.</div>
          </div>
        </li>
        <li class="rule-item">
          <div class="step-circle sc-info">📊</div>
          <div class="rule-body">
            <div class="rule-name">거래량 — 힘겨루기의 세기</div>
            <div class="rule-desc">힘겨루기가 치열할수록 거래량 급증 → 가격 변동성 확대 → 트레이더의 매매 기회 발생. <b>가격 움직임 = 거래량 움직임 = 가격 변동성 = 거래량 변동성.</b></div>
          </div>
        </li>
      </ul>
    </div>

    <div class="sec-title">이동평균선 위치 기반 매매 구조</div>
    <div class="flow-row">
      <div class="flow-box fb-1">5일선 위<span class="fb-sub">강세 추세</span></div>
      <div class="flow-arr">▶</div>
      <div class="flow-box fb-2">5일선~20일선<span class="fb-sub">조정·눌림목</span></div>
      <div class="flow-arr">▶</div>
      <div class="flow-box fb-3">20일선 아래<span class="fb-sub">약세 추세</span></div>
    </div>
    <div style="font-size:11px;color:var(--faint);margin-top:4px;">현재 주가 위치 → 어떤 매수·매도 원칙을 적용할지 결정</div>

    <div class="card" style="margin-top:14px;">
      <div class="card-title">핵심 투자 원칙</div>
      <ul class="rule-list">
        <li class="rule-item"><div class="step-circle sc-gray">1</div><div class="rule-body"><div class="rule-name">이유를 찾기 전에 차트에 먼저 반응하라</div><div class="rule-desc">시세 급락 시 원인 탐색 지연 → 기회 소실 또는 손실 확대. 차트가 뉴스보다 먼저 답을 알고 있다.</div></div></li>
        <li class="rule-item"><div class="step-circle sc-gray">2</div><div class="rule-body"><div class="rule-name">시장에 순응하라 (달리는 말에 올라타라)</div><div class="rule-desc">전문 트레이더도 "시장에 순응"이 제1원칙. 역추세 거래는 고난도 기술 요구. 추세 추종이 기본.</div></div></li>
        <li class="rule-item"><div class="step-circle sc-gray">3</div><div class="rule-body"><div class="rule-name">자신의 성향에 맞는 상황만 선택해 원칙을 고수</div><div class="rule-desc">16가지 상황 중 자신에게 맞는 것만 선택. 매수 3원칙 + 매도 2원칙을 자기 맞춤으로 체화.</div></div></li>
      </ul>
    </div>
  </div>

  <!-- ════════════ 매수 신호 ════════════ -->
  <div id="tab-buy" class="tab-panel">
    <div class="sec-title">매수 신호 정의와 구분</div>

    <div class="card">
      <div class="card-title"><span class="badge b-buy">매수 제1원칙</span> 5일 이동평균선 위에 있을 때</div>
      <ul class="rule-list">
        <li class="rule-item">
          <div class="step-circle sc-buy">📌</div>
          <div class="rule-body">
            <div class="rule-name">신호 정의</div>
            <div class="rule-desc">주가가 5일선 위에 위치하며 상승 추세 유지 중. '달리는 말에 올라타는' 추세 추종 매수. 강세 종목에서 거래량이 터지며 재상승할 때가 핵심 진입 타이밍.</div>
          </div>
        </li>
        <li class="rule-item">
          <div class="step-circle sc-buy">✅</div>
          <div class="rule-body">
            <div class="rule-name">진입 조건 (AND 조건)</div>
            <div class="rule-desc">① 5일선 위 주가 위치 확인<br>② 거래량 급증 + 순간체결량 상방 연속 발생<br>③ 양봉 형성 중 or 전일 양봉의 50% 이상 유지<br>④ 매수 호가 잔량 증가 확인</div>
          </div>
        </li>
        <li class="rule-item">
          <div class="step-circle sc-sell">⛔</div>
          <div class="rule-body">
            <div class="rule-name">손절 조건</div>
            <div class="rule-desc">5일선 이탈 즉시 손절 원칙 적용. 이탈 후 재진입 시도 없이 다음 기회 대기.</div>
            <div class="rule-tags"><span class="rtag">강세종목 탄력 매매</span><span class="rtag">연속 급등 초기 단계</span></div>
          </div>
        </li>
      </ul>
    </div>

    <div class="card">
      <div class="card-title"><span class="badge b-buy">매수 제2원칙</span> 5일선~20일선 사이에 있을 때 (눌림목)</div>
      <ul class="rule-list">
        <li class="rule-item">
          <div class="step-circle sc-buy">📌</div>
          <div class="rule-body">
            <div class="rule-name">신호 정의</div>
            <div class="rule-desc">상승 후 5일선 아래로 조정받아 5일~20일선 사이에서 횡보 또는 반등 시도 중. 가장 안전하고 활용도 높은 매수 타이밍. 눌림목 매수의 교과서적 진입.</div>
          </div>
        </li>
        <li class="rule-item">
          <div class="step-circle sc-buy">✅</div>
          <div class="rule-body">
            <div class="rule-name">진입 조건</div>
            <div class="rule-desc">① 20일선이 상승하는 상태에서 주가가 조정<br>② 20일선 근처에서 거래량 감소 후 재차 매수세 유입<br>③ 매도 호가 잔량 감소 전환 + 매수 호가 잔량 증가<br>④ 양봉 출현 or 전일 음봉 50% 이상 회복</div>
          </div>
        </li>
        <li class="rule-item">
          <div class="step-circle sc-sell">⛔</div>
          <div class="rule-body">
            <div class="rule-name">손절 조건</div>
            <div class="rule-desc">20일선 이탈 시 손절. 20일선 붕괴는 단기 추세 전환 신호로 해석.</div>
            <div class="rule-tags"><span class="rtag">눌림목 반등</span><span class="rtag">가장 활용도 높음</span><span class="rtag">리스크 중간</span></div>
          </div>
        </li>
      </ul>
    </div>

    <div class="card">
      <div class="card-title"><span class="badge b-warn">매수 제3원칙</span> 20일선 아래에 있을 때 (역발상 저가 매수)</div>
      <ul class="rule-list">
        <li class="rule-item">
          <div class="step-circle sc-warn">⚠️</div>
          <div class="rule-body">
            <div class="rule-name">신호 정의</div>
            <div class="rule-desc">20일선 아래의 약세 추세 중 세력 매집 또는 급반등 징후 포착. 고난도·고위험 전략. 반드시 복합 신호 확인 필수. 아무나 사용해서는 안 되는 원칙.</div>
          </div>
        </li>
        <li class="rule-item">
          <div class="step-circle sc-buy">✅</div>
          <div class="rule-body">
            <div class="rule-name">진입 조건 (엄격한 AND 조건)</div>
            <div class="rule-desc">① 거래량 폭증 (평소 대비 3배 이상) + 순간체결량 급등<br>② 하락 중 주가 멈춤 (음봉 후 아랫꼬리 출현)<br>③ 매도 호가 잔량 급감 → 매수 호가 잔량 역전환<br>④ 특정 가격에 대량 매도 물량 소화 후 반등<br>⑤ 장중 시그널 ④번 유형 확인</div>
          </div>
        </li>
        <li class="rule-item">
          <div class="step-circle sc-sell">⛔</div>
          <div class="rule-body">
            <div class="rule-name">주의사항</div>
            <div class="rule-desc">성급한 반발 매수 절대 금지. 조건 미충족 시 무조건 관망. 분할 진입으로 리스크 분산.</div>
            <div class="rule-tags"><span class="rtag">고위험</span><span class="rtag">경험자 한정</span><span class="rtag">분할매수 필수</span></div>
          </div>
        </li>
      </ul>
    </div>

    <div class="card">
      <div class="card-title" style="font-size:13px;">매수 신호 판단 공통 체크리스트</div>
      <div class="pp pp-buy">
        <div class="pp-name">📊 거래량·순간체결량 확인</div>
        <div class="pp-desc">가격이 움직일 때 거래량이 반드시 동반되어야 유효한 신호. 특히 순간체결량이 급증해야 중요한 타이밍. 거래량 없는 상승은 가짜 신호일 가능성 높음.</div>
      </div>
      <div class="pp pp-buy">
        <div class="pp-name">📋 호가 잔량 변화 방향</div>
        <div class="pp-desc">매수 시: 매수 호가 잔량 증가 + 매도 호가 잔량 감소 확인. 반대 흐름이면 함정 가능성.</div>
      </div>
      <div class="pp pp-buy">
        <div class="pp-name">⏱ 멈춤 신호 포착 후 진입</div>
        <div class="pp-desc">하락 중 거래량 급증 + 주가 멈춤 → 방향 전환 예고. 멈춤 확인 후 진입이 안전. 먼저 움직이면 안 됨.</div>
      </div>
    </div>
  </div>

  <!-- ════════════ 매도 신호 ════════════ -->
  <div id="tab-sell" class="tab-panel">
    <div class="sec-title">매도 신호 정의와 구분</div>

    <div class="card">
      <div class="card-title"><span class="badge b-sell">매도 제1원칙</span> 5일선 위에 있을 때 — 수익 실현 매도</div>
      <ul class="rule-list">
        <li class="rule-item">
          <div class="step-circle sc-sell">📌</div>
          <div class="rule-body">
            <div class="rule-name">신호 정의</div>
            <div class="rule-desc">상승 추세 중 주가가 5일선 위에서 상투권 형성 또는 추세 전환 징후. 거래량과 주가 움직임의 '멈춤' 신호 포착이 핵심. 이 시점을 놓치면 눌림목 매도로 넘어감.</div>
          </div>
        </li>
        <li class="rule-item">
          <div class="step-circle sc-sell">🔴</div>
          <div class="rule-body">
            <div class="rule-name">매도 조건 (신호 발생 시 즉시 대응)</div>
            <div class="rule-desc">① 고점 부근에서 거래량 급증 후 주가 멈춤 (상투 시그널)<br>② 순간체결량 감소 전환<br>③ 매수 호가 잔량 급감 + 매도 호가 잔량 증가<br>④ 음봉 발생 or 전일 양봉의 50% 이하로 하락<br>⑤ 장중 시그널 ③번 유형 발생</div>
          </div>
        </li>
        <li class="rule-item">
          <div class="step-circle sc-info">💡</div>
          <div class="rule-body">
            <div class="rule-name">고점 터닝 신호 식별</div>
            <div class="rule-desc">분명한 시그널이 온다. 이유를 찾기보다 차트 시그널을 먼저 보라. 카카오(고점), 삼성전자(고점) 등 대형주도 예외 없이 터닝 신호를 준다.</div>
            <div class="rule-tags"><span class="rtag">연속급등 후 필수 확인</span><span class="rtag">거래량 급증+멈춤</span></div>
          </div>
        </li>
      </ul>
    </div>

    <div class="card">
      <div class="card-title"><span class="badge b-sell">매도 제2원칙</span> 5일선~20일선 사이에 있을 때 — 손절·비중 축소</div>
      <ul class="rule-list">
        <li class="rule-item">
          <div class="step-circle sc-sell">📌</div>
          <div class="rule-body">
            <div class="rule-name">신호 정의</div>
            <div class="rule-desc">5일선 이탈 후 5~20일선 사이에서 반등 실패 또는 20일선 접근 중 추가 하락 징후. 추세 훼손의 초기 신호. 손절 원칙을 엄격히 적용하는 구간.</div>
          </div>
        </li>
        <li class="rule-item">
          <div class="step-circle sc-sell">🔴</div>
          <div class="rule-body">
            <div class="rule-name">매도 조건</div>
            <div class="rule-desc">① 5일선 이탈 후 반등 시도 중 재차 음봉 발생<br>② 20일선 지지 테스트 중 거래량 증가 + 하락<br>③ 매수 호가 잔량이 회복되지 않는 경우<br>④ 전일 음봉의 50%를 회복 못하는 약세 지속<br>⑤ 장중 시그널 ②번 유형(반등 실패 확인)</div>
          </div>
        </li>
        <li class="rule-item">
          <div class="step-circle sc-info">💡</div>
          <div class="rule-body">
            <div class="rule-name">대응 전략</div>
            <div class="rule-desc">20일선 붕괴 전 선제적 매도 또는 비중 축소. 버티기보다 재진입이 유리. 손절 후 눌림목 재매수 기회 탐색.</div>
            <div class="rule-tags"><span class="rtag">20일선 붕괴 전 선제대응</span><span class="rtag">손절 후 재진입 고려</span></div>
          </div>
        </li>
      </ul>
    </div>

    <div class="card">
      <div class="card-title" style="font-size:13px;">매도 신호 판단 공통 원칙</div>
      <div class="pp pp-sell">
        <div class="pp-name">🛑 매도는 수익과 손실 두 경우 모두에 적용</div>
        <div class="pp-desc">수익 실현 매도(제1원칙)와 손절 매도(제2원칙)를 명확히 구분. 어느 상황인지 먼저 파악 후 해당 원칙 적용.</div>
      </div>
      <div class="pp pp-sell">
        <div class="pp-name">📉 거래량 급증 후 주가 멈춤 = 매도 시그널</div>
        <div class="pp-desc">상승 추세 중 거래량이 급증했음에도 주가가 더 오르지 않으면 고점 신호. 이 '멈춤' 신호 포착이 매도 타이밍의 핵심.</div>
      </div>
      <div class="pp pp-sell">
        <div class="pp-name">⏰ 하락 추세 시 오전 10시 전 매도 원칙</div>
        <div class="pp-desc">시장이 하락 추세일 때는 오전 10시 전 변동성을 이용한 매도 우선. 오후 2시 이후까지 기다리면 저가에 팔 위험.</div>
      </div>
    </div>
  </div>

  <!-- ════════════ 50% 룰 ════════════ -->
  <div id="tab-fifty" class="tab-panel">
    <div class="sec-title">봉 해석과 50% 룰</div>
    <div class="card">
      <div class="card-title"><span class="badge b-buy">황소의 50% 룰</span> — 전일 양봉 기준</div>
      <div style="font-size:13px;color:var(--muted);margin-bottom:10px;">전일 양봉의 중간값(= 시가+종가 ÷ 2)이 당일 핵심 기준선</div>
      <div style="background:var(--buy-bg);border-radius:8px;padding:12px;margin-bottom:10px;">
        <div style="font-size:12px;font-weight:700;color:var(--buy-text);margin-bottom:4px;">✅ 매수 신호</div>
        <div style="font-size:12px;color:var(--buy-text);">당일 주가가 전일 양봉 중간값 <b>위에서 유지·상승</b> → 황소의 기세 지속 → 매수 진입</div>
      </div>
      <div style="background:var(--sell-bg);border-radius:8px;padding:12px;margin-bottom:10px;">
        <div style="font-size:12px;font-weight:700;color:var(--sell-text);margin-bottom:4px;">🔴 매도·관망 신호</div>
        <div style="font-size:12px;color:var(--sell-text);">당일 주가가 전일 양봉 중간값 <b>아래로 이탈</b> → 황소 기세 약화 → 관망 또는 매도</div>
      </div>
      <div class="tag-row">
        <span class="tag">전일 양봉 중간값 = 당일 핵심 지지선</span>
        <span class="tag">거래량 동반 여부 반드시 확인</span>
        <span class="tag">지지 확인 후 진입 원칙</span>
      </div>
    </div>

    <div class="card">
      <div class="card-title"><span class="badge b-sell">곰의 50% 룰</span> — 전일 음봉 기준</div>
      <div style="font-size:13px;color:var(--muted);margin-bottom:10px;">전일 음봉의 중간값(= 시가+종가 ÷ 2)이 당일 핵심 저항선</div>
      <div style="background:var(--buy-bg);border-radius:8px;padding:12px;margin-bottom:10px;">
        <div style="font-size:12px;font-weight:700;color:var(--buy-text);margin-bottom:4px;">✅ 매수 고려</div>
        <div style="font-size:12px;color:var(--buy-text);">전일 음봉 중간값을 <b>거래량 동반 상향 돌파</b> → 곰의 기세 약화 → 매수 가능</div>
      </div>
      <div style="background:var(--sell-bg);border-radius:8px;padding:12px;margin-bottom:10px;">
        <div style="font-size:12px;font-weight:700;color:var(--sell-text);margin-bottom:4px;">🔴 매도·관망 신호</div>
        <div style="font-size:12px;color:var(--sell-text);">전일 음봉 중간값 <b>아래에서 반등 실패</b> → 곰의 기세 지속 → 관망 또는 매도</div>
      </div>
      <div class="tag-row">
        <span class="tag">전일 음봉 중간값 = 당일 핵심 저항선</span>
        <span class="tag">저항 돌파 실패 = 매도 신호</span>
        <span class="tag">돌파 시 거래량 필수 확인</span>
      </div>
    </div>

    <div class="card">
      <div class="card-title" style="font-size:13px;">매물대 분석 — 이퀴볼륨 차트</div>
      <ul class="rule-list">
        <li class="rule-item">
          <div class="step-circle sc-info">📊</div>
          <div class="rule-body">
            <div class="rule-name">매물대 = 집중 거래 가격대</div>
            <div class="rule-desc">특정 가격대에 거래가 집중된 구간. 이퀴볼륨 차트로 시각적 확인. 매물대가 강할수록 지지·저항도 강함.</div>
          </div>
        </li>
        <li class="rule-item">
          <div class="step-circle sc-warn">⚡</div>
          <div class="rule-body">
            <div class="rule-name">이례적 호가 누적 = 세력 의도 파악 포인트</div>
            <div class="rule-desc">특정 가격에 이례적으로 대량 매도 호가 쌓임 → 세력이 물량 소화 후 매집 가능. 대량 체결로 소화 완료 + 주가 상승 시 돌파 매수 기회.</div>
          </div>
        </li>
      </ul>
    </div>
  </div>

  <!-- ════════════ 장중 시그널 ════════════ -->
  <div id="tab-signals" class="tab-panel">
    <div class="sec-title">장중 매매 시그널 — 4가지 유형 정의</div>
    <div style="font-size:12px;color:var(--muted);margin-bottom:14px;background:var(--bg);padding:10px 14px;border-radius:8px;">
      <b>핵심 원리:</b> 가격 움직임 = 거래량 움직임 = 가격 변동성 = 거래량 변동성<br>
      상승·하락 초기에 순간체결량이 급격히 활발해지고, 어느 순간 서서히 '멈춤'이 나타난다. 이 멈춤 포착이 핵심.
    </div>

    <div class="sig-grid">
      <div class="sig-box sg-buy">
        <div class="sig-type">유형 ① — 매수 신호</div>
        <div class="sig-name" style="color:var(--buy-text);">강세 추세 진입형</div>
        <ul class="sig-conditions">
          <li>주가 상승 + 거래량 동시 급증</li>
          <li>순간체결량 상방 연속 발생</li>
          <li>매수 호가 잔량 지속 증가</li>
          <li>매도 호가 잔량 감소</li>
          <li>5일선 위 or 양봉 중간값 유지</li>
        </ul>
        <div class="sig-result" style="color:var(--buy-text);">→ 강력 매수 신호. 추세 추종 진입.</div>
      </div>
      <div class="sig-box sg-info">
        <div class="sig-type">유형 ② — 매수 신호</div>
        <div class="sig-name" style="color:var(--info-text);">눌림·횡보 반등형</div>
        <ul class="sig-conditions">
          <li>소폭 조정 후 거래량 감소 안정</li>
          <li>재차 순간체결량 상방 발생</li>
          <li>매도 호가 잔량 감소 전환</li>
          <li>매수 호가 잔량 증가 전환</li>
          <li>음봉의 50% 이상 당일 회복</li>
        </ul>
        <div class="sig-result" style="color:var(--info-text);">→ 눌림목 매수 타이밍. 안전한 진입.</div>
      </div>
      <div class="sig-box sg-sell">
        <div class="sig-type">유형 ③ — 매도 신호</div>
        <div class="sig-name" style="color:var(--sell-text);">상투·하락 전환형</div>
        <ul class="sig-conditions">
          <li>고점 부근 거래량 급증 후 멈춤</li>
          <li>순간체결량 감소 전환</li>
          <li>매수 호가 잔량 급감</li>
          <li>매도 호가 잔량 증가</li>
          <li>음봉 발생 or 양봉 중간값 이탈</li>
        </ul>
        <div class="sig-result" style="color:var(--sell-text);">→ 즉시 매도·청산 신호.</div>
      </div>
      <div class="sig-box sg-warn">
        <div class="sig-type">유형 ④ — 매수 기회</div>
        <div class="sig-name" style="color:var(--warn-text);">세력 물량 소화형</div>
        <ul class="sig-conditions">
          <li>특정 가격 이례적 매도 호가 누적</li>
          <li>대량 순간체결로 매물 소화</li>
          <li>매도 잔량 소화 후 주가 상승</li>
          <li>거래량 폭증 동반 필수</li>
          <li>소화 완료 확인 후 진입</li>
        </ul>
        <div class="sig-result" style="color:var(--warn-text);">→ 돌파 확인 후 매수. 세력 매집 추정.</div>
      </div>
    </div>

    <div class="card">
      <div class="card-title" style="font-size:13px;">3요소 복합 확인 원칙</div>
      <ul class="rule-list">
        <li class="rule-item"><div class="step-circle sc-buy">1</div><div class="rule-body"><div class="rule-name">가격 움직임의 방향·속도</div><div class="rule-desc">상승/하락 중인지, 속도가 빨라지는지 느려지는지. 속도 감소 = 추세 약화 신호.</div></div></li>
        <li class="rule-item"><div class="step-circle sc-buy">2</div><div class="rule-body"><div class="rule-name">순간체결량 급증 여부</div><div class="rule-desc">순간체결이 연속 발생하며 거래량이 급증하는지. 상승·하락 초기에 활발 → 멈춤 신호 포착.</div></div></li>
        <li class="rule-item"><div class="step-circle sc-buy">3</div><div class="rule-body"><div class="rule-name">호가 잔량 변화 방향</div><div class="rule-desc">가격 하락 중 매도 호가 잔량 증가 / 매수 호가 잔량 감소. 멈춤 순간 반대 움직임 전환 시 방향 전환 신호.</div></div></li>
      </ul>
    </div>
  </div>

  <!-- ════════════ 6대 개념 ════════════ -->
  <div id="tab-concepts" class="tab-panel">
    <div class="sec-title">매매 타이밍 판단의 6대 핵심 개념</div>
    <div class="con-grid">
      <div class="con-box">
        <div class="con-num">CONCEPT 01</div>
        <div class="con-name">⏱ 시간</div>
        <div class="con-desc">변동성은 장 초반(9~10시)과 마감 직전(2~3시)에 집중. 시간대별 시장 심리 패턴 파악이 매매의 출발점.</div>
      </div>
      <div class="con-box">
        <div class="con-num">CONCEPT 02</div>
        <div class="con-name">💰 가격</div>
        <div class="con-desc">이동평균선(5·20일) 위치. 50% 룰 기준가. 전고점·전저점. 매물대 가격. 지지·저항 가격대.</div>
      </div>
      <div class="con-box">
        <div class="con-num">CONCEPT 03</div>
        <div class="con-name">📊 거래량</div>
        <div class="con-desc">순간체결량 급증 = 핵심 타이밍 기준. 거래량 미동반 가격 변동은 신뢰도 낮음. 거래량 있어야 진짜 신호.</div>
      </div>
      <div class="con-box">
        <div class="con-num">CONCEPT 04</div>
        <div class="con-name">⏸ 움직임·멈춤</div>
        <div class="con-desc">가격+거래량 동시 움직임 후 '멈춤' = 방향 전환 예고. 멈춤 후 호가 역전환 확인이 진입 타이밍.</div>
      </div>
      <div class="con-box">
        <div class="con-num">CONCEPT 05</div>
        <div class="con-name">⚡ 속도</div>
        <div class="con-desc">급등·급락의 각도와 속도. 속도 감소 = 추세 약화 신호. 속도 가속 = 추세 강화. 속도 변화를 실시간 감지.</div>
      </div>
      <div class="con-box">
        <div class="con-num">CONCEPT 06</div>
        <div class="con-name">🎯 지지·저항·돌파</div>
        <div class="con-desc">이전 고점·저점·이동평균. 거래량 동반 돌파 = 유효. 거래량 없는 돌파 = 가짜. 돌파 후 눌림 시 진입.</div>
      </div>
    </div>

    <hr>
    <div class="sec-title">추세·패턴·종목 선정</div>
    <div class="pp pp-info">
      <div class="pp-name">📈 추세 분석</div>
      <div class="pp-desc">상승·하락·횡보 추세 유형별 대응 차별화. 추세 방향에 순응하는 매매가 기본 원칙. 추세 반전 신호 감지 시 포지션 전환.</div>
    </div>
    <div class="pp pp-info">
      <div class="pp-name">🔄 패턴 분석</div>
      <div class="pp-desc">주가는 심리에 의해 일정한 패턴을 반복. 반복 패턴 인식으로 다음 방향 예측. 직접 차트를 보며 경험으로 체득하는 것이 가장 효과적.</div>
    </div>
    <div class="pp pp-purple">
      <div class="pp-name">🔍 종목 선정</div>
      <div class="pp-desc">장 시작 전: 전일 급등·강세 종목 스크리닝. 장중: HTS 실시간 필터링. 시장 추세 방향 먼저 확인 후 해당 방향 종목 선정이 원칙.</div>
    </div>
  </div>

  <!-- ════════════ 시간대 전략 ════════════ -->
  <div id="tab-timing" class="tab-panel">
    <div class="sec-title">시장 추세별 시간대 매매 전략</div>
    <div style="font-size:12px;color:var(--muted);margin-bottom:14px;padding:10px;background:var(--bg);border-radius:8px;">
      2000년대 데이트레이딩 1세대로 하루 10번 이상 거래하며 체득한 시간대별 패턴. 주가는 심리에 의해 일정 패턴을 반복.
    </div>

    <div class="time-grid">
      <div class="time-card">
        <div class="time-header"><span class="badge b-sell">하락 추세 시장</span></div>
        <div class="time-row">
          <div class="tblock tb-sell"><div class="tb-label">오전 10시 전</div>매도 우선</div>
          <div class="tblock tb-buy"><div class="tb-label">오후 2시 이후</div>매수 탐색</div>
        </div>
        <div style="font-size:11px;color:var(--muted);margin-top:8px;">매수 신호 미확인 시 다음 날 이연 원칙.</div>
      </div>
      <div class="time-card">
        <div class="time-header"><span class="badge b-buy">상승 추세 시장</span></div>
        <div class="time-row">
          <div class="tblock tb-buy"><div class="tb-label">오전 10시 전</div>매수 우선</div>
          <div class="tblock tb-sell"><div class="tb-label">오후 2시 이후</div>매도·수익실현</div>
        </div>
        <div style="font-size:11px;color:var(--muted);margin-top:8px;">고점에서 반드시 시그널 먼저 확인 후 매도.</div>
      </div>
    </div>

    <div class="card">
      <div class="card-title" style="font-size:13px;">장중 시간대별 특성</div>
      <ul class="rule-list">
        <li class="rule-item"><div class="step-circle sc-sell">🌅</div><div class="rule-body"><div class="rule-name">장 초반 (9:00~10:00)</div><div class="rule-desc">전일 뉴스·공시 반영. 변동성 최대 구간. 하락 추세일 때 매도 기회. 상승 추세일 때 매수 기회. 신호 없으면 관망.</div></div></li>
        <li class="rule-item"><div class="step-circle sc-info">🌤</div><div class="rule-body"><div class="rule-name">장중 (10:00~14:00)</div><div class="rule-desc">상대적으로 안정된 흐름. 눌림목 매수·반등 확인 구간. 방향성 결정 후 진입 적기. 급등·급락 종목 탐색.</div></div></li>
        <li class="rule-item"><div class="step-circle sc-buy">🌆</div><div class="rule-body"><div class="rule-name">장 마감 직전 (14:00~15:30)</div><div class="rule-desc">변동성 재차 확대. 상승 추세일 때 매도·수익실현 구간. 하락 추세일 때 매수 탐색. 시초가 갭 예측 고려.</div></div></li>
      </ul>
    </div>
  </div>

  <!-- ════════════ 실전 스킬 ════════════ -->
  <div id="tab-skills" class="tab-panel">
    <div class="sec-title">실전 트레이딩 스킬</div>
    <div class="pp pp-buy">
      <div class="pp-name">🚀 강세 종목 탄력 매매</div>
      <div class="pp-desc">강한 상승 모멘텀 종목의 탄력을 이용한 단기 매매. 거래량 동반 확인 필수. 5일선 이탈 즉시 손절. 탄력이 유지되는 동안만 보유. '달리는 말에 올라타기'의 실전 적용.</div>
    </div>
    <div class="pp pp-buy">
      <div class="pp-name">📈 연속 급등 종목 매매 타이밍</div>
      <div class="pp-desc">2~3일 연속 급등 후 반드시 상투 확인: 거래량 급증 + 주가 멈춤 → 고점 매도. 재차 거래량 급증 + 순간체결 상방 발생 시에만 추가 매수 고려. 추격 매수는 함정.</div>
    </div>
    <div class="pp pp-sell">
      <div class="pp-name">📉 연속 급락 종목 매매 타이밍</div>
      <div class="pp-desc">급락 중 성급한 반발 매수 절대 금지. 거래량 폭증 + 주가 멈춤 → 세력 매집 가능성 탐색. ④번 유형 시그널(대량 매도 소화 후 반등) 확인 필수. 확인 후 분할 진입.</div>
    </div>
    <div class="pp pp-info">
      <div class="pp-name">📋 거래량 활용 매매</div>
      <div class="pp-desc">일별 거래량이 아닌 순간체결량 급증에 주목. 거래량 급증 = 세력 개입 신호. 거래량 없는 가격 변동 무시. 거래량이 주가 방향의 확신 지표.</div>
    </div>
    <div class="pp pp-warn">
      <div class="pp-name">🛡 리스크 관리 원칙</div>
      <div class="pp-desc">손절선 명확히 설정 후 진입. 분할 매수로 평균 단가 관리. 한 종목에 집중 투자 금지. 매수 신호 없으면 현금 보유. 시장 추세 역행 매매 자제.</div>
    </div>

    <hr>
    <div class="sec-title">MP+ 시스템 트레이딩 기반 원칙</div>
    <div class="card">
      <div class="card-title" style="font-size:13px;">박병창 자체 개발 MP+ 시스템의 핵심 로직</div>
      <ul class="rule-list">
        <li class="rule-item"><div class="step-circle sc-info">①</div><div class="rule-body"><div class="rule-name">시장 추세 방향 먼저 결정</div><div class="rule-desc">코스피/코스닥 지수 방향 + 시간대 파악 → 매수/매도 중 어느 방향으로 매매할지 결정.</div></div></li>
        <li class="rule-item"><div class="step-circle sc-info">②</div><div class="rule-body"><div class="rule-name">조건에 맞는 종목 필터링</div><div class="rule-desc">HTS 실시간 거래량 급증 + 가격 변동 조건 스크리닝. 16가지 상황 중 해당 유형 분류.</div></div></li>
        <li class="rule-item"><div class="step-circle sc-info">③</div><div class="rule-body"><div class="rule-name">진입·청산 원칙 자동 적용</div><div class="rule-desc">매수 3원칙·매도 2원칙 + 장중 시그널 4유형 복합 판단. 감정 배제, 원칙 기계적 적용.</div></div></li>
      </ul>
    </div>
  </div>

  <!-- ════════════ 비교표 ════════════ -->
  <div id="tab-compare" class="tab-panel">
    <div class="sec-title">매수 vs 매도 신호 비교표</div>
    <div class="card" style="padding:0;overflow:hidden;">
      <table class="comp-table" style="border-radius:12px;overflow:hidden;">
        <thead>
          <tr>
            <th style="width:18%">구분</th>
            <th style="width:22%">조건</th>
            <th style="width:30%">🟢 매수 신호</th>
            <th style="width:30%">🔴 매도 신호</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><b>이동평균선</b></td>
            <td>5일선 위</td>
            <td class="buy-cell">추세 추종 매수 (제1원칙)</td>
            <td class="sell-cell">상투 확인 시 수익 실현 (제1원칙)</td>
          </tr>
          <tr>
            <td></td>
            <td>5일~20일선 사이</td>
            <td class="buy-cell">눌림목 반등 매수 (제2원칙)</td>
            <td class="sell-cell">반등 실패 시 손절 (제2원칙)</td>
          </tr>
          <tr>
            <td></td>
            <td>20일선 아래</td>
            <td class="buy-cell">세력 매집 확인 후 (제3원칙·고위험)</td>
            <td>—</td>
          </tr>
          <tr>
            <td><b>거래량</b></td>
            <td>순간체결량</td>
            <td class="buy-cell">상방 연속 급증</td>
            <td class="sell-cell">급증 후 감소 전환 (멈춤)</td>
          </tr>
          <tr>
            <td><b>호가 잔량</b></td>
            <td>매수 호가</td>
            <td class="buy-cell">증가</td>
            <td class="sell-cell">급감</td>
          </tr>
          <tr>
            <td></td>
            <td>매도 호가</td>
            <td class="buy-cell">감소</td>
            <td class="sell-cell">증가</td>
          </tr>
          <tr>
            <td><b>50% 룰</b></td>
            <td>전일 양봉 기준</td>
            <td class="buy-cell">중간값 위 유지 → 매수</td>
            <td class="sell-cell">중간값 이탈 → 매도</td>
          </tr>
          <tr>
            <td></td>
            <td>전일 음봉 기준</td>
            <td class="buy-cell">중간값 상향 돌파 → 매수</td>
            <td class="sell-cell">중간값 아래 반등 실패 → 매도</td>
          </tr>
          <tr>
            <td><b>장중시그널</b></td>
            <td>유형 ①</td>
            <td class="buy-cell">강세 추세 진입 → 강력 매수</td>
            <td>—</td>
          </tr>
          <tr>
            <td></td>
            <td>유형 ②</td>
            <td class="buy-cell">눌림 반등 → 안전 매수</td>
            <td>—</td>
          </tr>
          <tr>
            <td></td>
            <td>유형 ③</td>
            <td>—</td>
            <td class="sell-cell">상투 전환 → 즉시 매도</td>
          </tr>
          <tr>
            <td></td>
            <td>유형 ④</td>
            <td class="buy-cell">물량 소화 후 돌파 매수</td>
            <td>—</td>
          </tr>
          <tr>
            <td><b>시간대</b></td>
            <td>하락 추세 시</td>
            <td class="buy-cell">오후 2시 이후 탐색</td>
            <td class="sell-cell">오전 10시 전 우선 매도</td>
          </tr>
          <tr>
            <td></td>
            <td>상승 추세 시</td>
            <td class="buy-cell">오전 10시 전 우선 매수</td>
            <td class="sell-cell">오후 2시 이후 수익 실현</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="foot">📚 출처: 박병창 저 『돈을 부르는 매매의 기술』(포레스트북스, 2021) 및 관련 강의 내용 종합 정리</div>
  </div>

</div>
<script>
function show(id, btn) {
  document.querySelectorAll('.tab-panel').forEach(p => p.classList.remove('on'));
  document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('on'));
  document.getElementById('tab-' + id).classList.add('on');
  btn.classList.add('on');
}
</script>
</body>
</html>