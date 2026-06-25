import os, numpy as np
from PIL import Image
ASSETS="app/src/main/assets/SPRITES/NPC"; DEBUG="tools/_debug4"
TH=30; PT,PB,PS=0.10,0.06,0.08; HFRAC=0.45
ANIM={"idle":("Idle","i"),"walk":("Walk","w"),"run":("Run","r"),"special":("Special","s")}
CONFIG={
 "pap1":{"sheet":"sprites_paparazzi_n1-Photoroom.png","char":"PaparazziN1","prefix":"pn1","regions":{
   "idle":(0.0,0.03,0.21,0.25,3),"walk":(0.21,0.03,0.82,0.25,8),
   "run":(0.0,0.27,0.82,0.49,8),"special":(0.32,0.70,0.72,0.89,4)}},   # TOMAR FOTO
 "pap5":{"sheet":"sprites_paparazzi_n5-Photoroom.png","char":"PaparazziN5","prefix":"pn5","regions":{
   "idle":(0.0,0.03,0.21,0.25,3),"walk":(0.22,0.03,0.78,0.25,6),
   "run":(0.0,0.27,0.82,0.49,8),"special":(0.33,0.64,0.72,0.82,4)}},  # TOMAR FOTO
 "cdmx":{"sheet":"sprites_policias_cdmx-Photoroom.png","char":"PoliciaCDMX","prefix":"pcd","regions":{
   "idle":(0.0,0.02,0.22,0.23,4),"walk":(0.23,0.02,0.80,0.23,6),
   "run":(0.0,0.24,0.80,0.46,8),"special":(0.30,0.47,0.58,0.66,3)}},  # DISPARAR
 "gra":{"sheet":"sprites_policias_granaderos-Photoroom.png","char":"Granaderos","prefix":"gra","regions":{
   "idle":(0.0,0.02,0.21,0.21,4),"walk":(0.21,0.02,0.78,0.21,6),
   "run":(0.0,0.22,0.80,0.41,8),"special":(0.0,0.60,0.30,0.78,4)}},   # GOLPEAR CON ESCUDO
 "pmd":{"sheet":"sprites_paramedico-Photoroom.png","char":"Paramedico","prefix":"pmd","regions":{
   "idle":(0.0,0.02,0.22,0.23,4),"walk":(0.22,0.02,0.80,0.23,6),
   "run":(0.0,0.24,0.80,0.46,8),"special":(0.33,0.47,0.72,0.66,4)}},  # USAR DESFIBRILADOR
}
def split_n(mask,k):
    prof=mask.sum(axis=0).astype(float); cols=np.where(prof>0)[0]
    if len(cols)==0: return []
    a,b=cols[0],cols[-1]+1
    if k<=1: return [(a,b)]
    step=(b-a)/k; win=max(2,int(step*0.40)); cuts=[a]
    for i in range(1,k):
        c=int(a+i*step); lo,hi=max(a+1,c-win),min(b-1,c+win)
        if hi>lo: c=lo+int(np.argmin(prof[lo:hi]))
        cuts.append(c)
    cuts.append(b); return [(cuts[i],cuts[i+1]) for i in range(k)]
def gap_runs(mask,min_gap):
    col=mask.any(axis=0); w=len(col); runs=[]; x=0
    while x<w:
        if col[x]:
            x0=x
            while x<w and col[x]: x+=1
            xe=x; g=0
            while xe<w and not col[xe]: g+=1; xe+=1
            if 0<g<min_gap and xe<w: x=xe; continue
            runs.append((x0,x))
        else: x+=1
    return runs
def main_block(cell,mask):
    rows=mask.any(axis=1); runs=[]; y=0; Hh=len(rows)
    while y<Hh:
        if rows[y]:
            yy=y
            while y<Hh and rows[y]: y+=1
            runs.append((yy,y))
        else: y+=1
    if not runs: return None
    a,b=max(runs,key=lambda r:r[1]-r[0]); sub=cell[a:b]; sm=mask[a:b]
    xs=np.where(sm.any(axis=0))[0]
    return None if len(xs)==0 else sub[:,xs[0]:xs[-1]+1]
def emit(cr,prefix,lt,idx,od,mont):
    fh,fw=cr.shape[:2]; top,bot,side=int(fh*PT),int(fh*PB),int(fw*PS)
    cv=np.zeros((fh+top+bot,fw+2*side,4),np.uint8); cv[top:top+fh,side:side+fw]=cr
    Image.fromarray(cv,"RGBA").save(os.path.join(od,f"{prefix}_{lt}_{idx}.webp"),"WEBP",quality=95,method=6)
    mont.append(Image.fromarray(cv,"RGBA"))
os.makedirs(DEBUG,exist_ok=True)
for key,cfg in CONFIG.items():
    img=Image.open(os.path.join(ASSETS,cfg["sheet"])).convert("RGBA"); W,H=img.size
    arr=np.array(img); mask=arr[:,:,3]>TH; print(f"== {cfg['char']} ==")
    for anim,(x0,y0,x1,y1,cnt) in cfg["regions"].items():
        sd,lt=ANIM[anim]; od=os.path.join(ASSETS,cfg["char"],sd); os.makedirs(od,exist_ok=True)
        reg=arr[int(y0*H):int(y1*H),int(x0*W):int(x1*W)]; rmask=mask[int(y0*H):int(y1*H),int(x0*W):int(x1*W)]
        Rh=reg.shape[0]; mont=[]; idx=0
        if anim=="special":
            figs=[(a,b) for (a,b) in gap_runs(rmask,max(3,int(reg.shape[1]*0.012)))
                  if (lambda ys: len(ys) and (ys[-1]-ys[0]+1)>=HFRAC*Rh)(np.where(rmask[:,a:b].any(axis=1))[0])]
            if figs:
                single=min(b-a for a,b in figs)
                for (a,b) in figs:
                    n=max(1,round((b-a)/(single*1.25)))
                    for (sa,sb) in (split_n(rmask[:,a:b],n) if n>1 else [(0,b-a)]):
                        cr=main_block(reg[:,a+sa:a+sb],rmask[:,a+sa:a+sb])
                        if cr is None or cr.shape[0]<5: continue
                        idx+=1; emit(cr,cfg["prefix"],lt,idx,od,mont)
        else:
            for (cx0,cx1) in split_n(rmask,cnt):
                cr=main_block(reg[:,cx0:cx1],rmask[:,cx0:cx1])
                if cr is None or cr.shape[0]<5: continue
                idx+=1; emit(cr,cfg["prefix"],lt,idx,od,mont)
        print(f"  {anim:8s} {idx}/{cnt}")
        if mont:
            mh=max(m.height for m in mont); mw=sum(m.width for m in mont)
            mo=Image.new("RGBA",(mw,mh),(120,120,120,255)); x=0
            for m in mont: mo.paste(m,(x,mh-m.height),m); x+=m.width
            mo.convert("RGB").save(os.path.join(DEBUG,f"{key}_{anim}.png"))
print("LISTO")
