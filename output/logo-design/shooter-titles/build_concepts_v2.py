from pathlib import Path
import re,math,json
p=Path(__file__).resolve().parent
raw=(p.parent/'concepts/concept-a-lockup-v2.svg').read_text()
glyphs=re.findall(r'<path fill="#111111" fill-rule="evenodd"[^>]*d="([^"]+)"',raw)
letters=dict(zip('VOXELSTRIKE',glyphs))
letters['X']='M0 0H18L30 20.78L42 0H60L41.53 32L60 64H42L30 43.22L18 64H0L18.47 32Z'
def loops(d):
 t=re.findall(r'[MHVLZ]|-?\d+(?:\.\d+)?',d);out=[];poly=[];i=0;cmd='';x=y=0
 while i<len(t):
  if t[i].isalpha():cmd=t[i];i+=1
  if cmd=='Z':
   if poly:out.append(poly);poly=[]
   cmd='';continue
  if cmd in ['M','L']:x=float(t[i]);y=float(t[i+1]);i+=2
  elif cmd=='H':x=float(t[i]);i+=1
  elif cmd=='V':y=float(t[i]);i+=1
  else:raise ValueError(t)
  poly.append((x,y))
  if cmd=='M':cmd='L'
 return out

def offset(poly,r):
 n=len(poly);area=sum(poly[i][0]*poly[(i+1)%n][1]-poly[(i+1)%n][0]*poly[i][1] for i in range(n));sign=1 if area>0 else -1;edges=[]
 for i in range(n):
  a=poly[i];b=poly[(i+1)%n];dx=b[0]-a[0];dy=b[1]-a[1];length=math.hypot(dx,dy);ox=sign*dy/length*r;oy=-sign*dx/length*r
  edges.append(((a[0]+ox,a[1]+oy),(b[0]+ox,b[1]+oy)))
 out=[]
 for i in range(n):
  a,b=edges[i-1];c,d=edges[i];u=(b[0]-a[0],b[1]-a[1]);v=(d[0]-c[0],d[1]-c[1]);den=u[0]*v[1]-u[1]*v[0]
  if abs(den)<1e-6:out.append(c);continue
  k=((c[0]-a[0])*v[1]-(c[1]-a[1])*v[0])/den;out.append((a[0]+k*u[0],a[1]+k*u[1]))
 return out

def shape(word,x,y,s,gap,fill,outline=0,shear=0,shift=(0,0)):
 parts=[]
 for ch in word:
  polys=loops(letters[ch]);paths=[]
  for i,poly in enumerate(polys):
   if outline:poly=offset(poly,outline if i==0 else -outline)
   pts=[(x+px*s+shear*(64-py)*s+shift[0],y+py*s+shift[1]) for px,py in poly]
   paths.append('M'+'L'.join(f'{a:.2f} {b:.2f}' for a,b in pts)+'Z')
  parts.append(f'<path fill="{fill}" fill-rule="evenodd" d="{" ".join(paths)}"/>')
  x+=(48 if ch=='I' else 60)*s+gap
 return ''.join(parts)
DARK='#121923';WHITE='#f1f5f7'
for key,accent,slant,depth in [('a','#ff8a18',0,12),('b','#34d5de',0,0),('c','#b9f142',.268,8)]:
 line1=('VOXEL',88 if key!='c' else 60,24,1.44,6)
 line2=('STRIKE',42 if key!='c' else 14,126,1.48,6)
 body=''
 for line in [line1,line2]:
  if depth:body+=shape(*line,DARK,4,slant,(depth*.5,depth))
  body+=shape(*line,DARK,4,slant)
 for i,line in enumerate([line1,line2]):body+=shape(*line,(accent if i==1 else WHITE) if key!='b' else (WHITE if i==1 else accent),0,slant)
 if key=='a':body+='<path fill="#ff8a18" d="M280 13H293V26H280Z M297 8H306V17H297Z"/>'
 if key=='b':body+='<path fill="#34d5de" d="M42 230H262V238H42Z M368 230H588V238H368Z"/>'
 if key=='c':body+='<path fill="#b9f142" d="M14 130H38L30 138H6Z M8 151H22L14 159H0Z"/>'
 if key=='c':body='<g transform="translate(20 0)">'+body+'</g>'
 svg=f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 640 256" role="img"><title>Voxel Strike — shooter title {key.upper()}</title>{body}</svg>'
 (p/f'title-{key}-v2.svg').write_text(svg)
 # Plain one-colour companion is for inspecting the underlying title construction.
 mono=shape(*line1,'#111111',0,slant)+shape(*line2,'#111111',0,slant)
 if key=='c':mono='<g transform="translate(20 0)">'+mono+'</g>'
 (p/f'title-{key}-mono-v2.svg').write_text(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 640 256"><title>Voxel Strike — mono title {key.upper()}</title>{mono}</svg>')
labels=[('A · BLOCK IMPACT','Масивний блоковий титул · рекомендую'),('B · ARCADE ARENA','Контрастний аркадний титул'),('C · SPEED STRIKE','Нахил і рух для швидкого шутера')]
parts=['<rect width="1680" height="830" fill="#090e16"/><text x="55" y="68" fill="#f1f5f7" font-family="Arial,sans-serif" font-size="34" font-weight="bold">VOXEL STRIKE — мобільний аркадний шутер</text><text x="55" y="108" fill="#9aaabd" font-family="Arial,sans-serif" font-size="20">Ігровий титул для меню · блокові літери · три напрями</text>']
for i,(name,note) in enumerate(labels):
 x=40+i*545;parts.append(f'<rect x="{x}" y="150" width="525" height="620" rx="18" fill="#111b29" stroke="{"#ff8a18" if i==0 else "#293b50"}" stroke-width="2"/>')
 svg=(p/f'title-{"abc"[i]}-v2.svg').read_text();svg=svg.replace('viewBox="0 0 640 256"',f'viewBox="0 0 640 256" x="{x+10}" y="200" width="505" height="235"')
 parts.append(svg)
 # A realistic small-menu width makes text legibility assessable.
 small=(p/f'title-{"abc"[i]}-v2.svg').read_text().replace('viewBox="0 0 640 256"',f'viewBox="0 0 640 256" x="{x+45}" y="475" width="230" height="92"');parts.append(small)
 parts.append(f'<text x="{x+30}" y="610" fill="#f1f5f7" font-family="Arial,sans-serif" font-size="25" font-weight="bold">{name}</text><text x="{x+30}" y="648" fill="#9aaabd" font-family="Arial,sans-serif" font-size="17">{note}</text><text x="{x+30}" y="704" fill="#667c94" font-family="Arial,sans-serif" font-size="14">Векторні літери · жодних шрифтових залежностей</text>')
(p/'shooter-concepts.svg').write_text('<svg xmlns="http://www.w3.org/2000/svg" width="1680" height="830" viewBox="0 0 1680 830">'+''.join(parts)+'</svg>')
(p/'updated-brief.json').write_text(json.dumps({'user_clarification':'A mobile shooter like Krunker','direction':'Game title first; energetic block lettering with strong outline and menu contrast','recommendation':'A / Block Impact','status':'Concept checkpoint; no in-app replacement yet'},ensure_ascii=False,indent=2))
