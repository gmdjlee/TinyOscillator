"""주체별 매물대 v2 — 일봉/주봉 기반, 기간 지정, 스윙 앵커, 상하방 집중도"""
import numpy as np, datetime as dt

R = open('rows.csv').read().strip().split('\n')
COLS = ['date','open','high','low','close','vol',
        'p_bv','p_bp','p_sv','p_sp','f_bv','f_bp','f_sv','f_sp','o_bv','o_bp','o_sv','o_sp']
INV = {'개인':'p','외국인':'f','기관':'o'}

recs=[]
for line in R:
    v=line.split(',')
    d=dict(zip(COLS,[v[0]]+[float(x) for x in v[1:]]))
    recs.append(d)
recs.sort(key=lambda r:r['date'])

def to_weekly(rs):
    """일봉 레코드를 주봉으로 집계. 투자자 물량과 거래대금은 단순 합산."""
    out={}
    for r in rs:
        y,w,_=dt.date(int(r['date'][:4]),int(r['date'][4:6]),int(r['date'][6:])).isocalendar()
        k=(y,w)
        if k not in out:
            out[k]=dict(r); out[k]['date']=r['date']
        else:
            a=out[k]
            a['high']=max(a['high'],r['high']); a['low']=min(a['low'],r['low'])
            a['close']=r['close']; a['vol']+=r['vol']
            for p in INV.values():
                for f in ['_bv','_bp','_sv','_sp']: a[p+f]+=r[p+f]
    return [out[k] for k in sorted(out)]

def swing_points(rs,k=5):
    hi=[i for i in range(k,len(rs)-k) if rs[i]['high']==max(x['high'] for x in rs[i-k:i+k+1])]
    lo=[i for i in range(k,len(rs)-k) if rs[i]['low'] ==min(x['low']  for x in rs[i-k:i+k+1])]
    return hi,lo

def profile(rs, nbins=20, decay='prorata'):
    lo_e=min(r['low'] for r in rs); hi_e=max(r['high'] for r in rs)
    edges=np.linspace(lo_e,hi_e,nbins+1); c=(edges[:-1]+edges[1:])/2
    H={k:np.zeros(nbins) for k in INV}; TOT=np.zeros(nbins); resid={k:0.0 for k in INV}
    def kern(vwap,lo,hi):
        s=max((hi-lo)/4.0,(hi_e-lo_e)/(nbins*4))
        w=np.exp(-0.5*((c-vwap)/s)**2); w[(c<lo-(edges[1]-edges[0]))|(c>hi+(edges[1]-edges[0]))]=0
        return w/w.sum() if w.sum()>0 else np.eye(nbins)[np.argmin(abs(c-vwap))]
    for r in rs:
        TOT+=r['vol']*kern((r['high']+r['low']+r['close'])/3,r['low'],r['high'])
        for name,p in INV.items():
            bv,bp,sv,sp=r[p+'_bv'],r[p+'_bp'],r[p+'_sv'],r[p+'_sp']
            H[name]+=bv*kern(bp*1e6/bv,r['low'],r['high'])
            if decay=='prorata':
                take=min(sv,H[name].sum()); resid[name]+=sv-take
                if H[name].sum()>0: H[name]-=H[name]*(take/H[name].sum())
            else:
                H[name]-=sv*kern(sp*1e6/sv,r['low'],r['high'])
    return edges,c,H,TOT,resid

def report(rs,label,nbins=20):
    edges,c,H,TOT,resid=profile(rs,nbins)
    cur=rs[-1]['close']; adv=np.mean([r['vol'] for r in rs])
    print(f"\n=== {label} | {rs[0]['date']}~{rs[-1]['date']} ({len(rs)}봉) 현재가 {cur:,.0f} ===")
    print(f"전체 POC {c[TOT.argmax()]:,.0f}원  구간폭 {edges[1]-edges[0]:,.0f}원")
    for name in INV:
        h=H[name]; up=h[c>cur].sum(); dn=h[c<=cur].sum()
        ti=np.argsort(-np.where(c>cur,h,0))[:2]; bi=np.argsort(-np.where(c<=cur,h,0))[:2]
        print(f"[{name}] 잔존 {h.sum()/1e4:8.0f}만주 | 상방 {up/1e4:7.0f} 하방 {dn/1e4:7.0f} "
              f"| 저항 {' '.join(f'{c[i]/1e4:.1f}만({h[i]/1e4:.0f})' for i in ti if h[i]>0)}"
              f" | 지지 {' '.join(f'{c[i]/1e4:.1f}만({h[i]/1e4:.0f})' for i in bi if h[i]>0)}")
    tot=sum(H[k] for k in INV); up=tot[c>cur].sum()
    print(f"3주체 합산 상방 {up/1e4:.0f}만주 = 일평균거래량 {adv/1e4:.0f}만주의 {up/adv:.1f}일분")
    return edges,c,H,TOT,cur

hi,lo=swing_points(recs,k=5)
print("일봉 스윙 고점:",[(recs[i]['date'],int(recs[i]['high'])) for i in hi])
print("일봉 스윙 저점:",[(recs[i]['date'],int(recs[i]['low'])) for i in lo])
report(recs,"① 전체 기간(일봉)")
if hi: report(recs[hi[-1]:],"② 직전 스윙 고점 이후(일봉)")
if lo: report(recs[lo[-1]:],"③ 직전 스윙 저점 이후(일봉)")
wk=to_weekly(recs); report(wk,"④ 전체 기간(주봉 표시, 일봉 배분)",nbins=12)

print("\n----- 상각 방식 비교(직전 스윙 고점 이후 14봉) -----")
sub=recs[hi[-1]:]
for mode in ['prorata','net']:
    e,c,H,T,rs_=profile(sub,20,mode)
    print(mode, {k:round(H[k].sum()/1e4) for k in INV})
print("\n----- 고정 구간폭(5,000원) 전체기간, 순매수 누적 방식 -----")
e,c,H,T,_=profile(recs,20,'net')
cur=recs[-1]['close']
for name in INV:
    h=H[name]
    print(name, "상방", round(h[c>cur].sum()/1e4), "하방", round(h[c<=cur].sum()/1e4))
np.save('c.npy',c); np.save('Hp.npy',H['개인']); np.save('Hf.npy',H['외국인']); np.save('Ho.npy',H['기관'])
print("bins:", [f"{x/1e4:.1f}" for x in c])
print("개인:", [round(x/1e4) for x in H['개인']])
print("외국인:", [round(x/1e4) for x in H['외국인']])
print("기관:", [round(x/1e4) for x in H['기관']])
