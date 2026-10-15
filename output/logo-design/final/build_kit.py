from pathlib import Path
import re,json,shutil,xml.etree.ElementTree as ET
p=Path(__file__).resolve().parent
builder=p.parent/'shooter-titles/build_concepts_v2.py'
ns={'__file__':str(builder)};exec(builder.read_text().split('for key,accent,slant,depth')[0],ns)
shape=ns['shape'];letters=ns['letters'];dark='#121923';white='#f1f5f7';orange='#ff8a18'
def svg(body,title,width=640,height=256):return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width} {height}" role="img"><title>{title}</title>{body}</svg>'
def write(name,body,width=640,height=256):
 (p/'source'/name).write_text(svg(body,'Voxel Strike — Block Impact',width,height))
# Approved stacked title: preserve the construction, outline and extrusion exactly.
shutil.copy2(p.parent/'shooter-titles/title-a-v2.svg',p/'source/voxel-strike-stacked-color.svg')
flat=shape('VOXEL',88,24,1.44,6,dark)+shape('STRIKE',42,126,1.48,6,orange)+'<path fill="#ff8a18" d="M280 13H293V26H280Z M297 8H306V17H297Z"/>'
write('voxel-strike-stacked-light.svg',flat)
mono=shape('VOXEL',88,24,1.44,6,'#111111')+shape('STRIKE',42,126,1.48,6,'#111111')+'<path fill="#111111" d="M280 13H293V26H280Z M297 8H306V17H297Z"/>'
write('voxel-strike-stacked-mono.svg',mono)
# Horizontal companion, for wide headers.
horizontal=shape('VOXEL',32,90,1.12,8,white)+shape('STRIKE',420,90,1.12,8,orange)
write('voxel-strike-horizontal-color.svg',horizontal,870)
write('voxel-strike-horizontal-light.svg',horizontal.replace(white,dark),870)
write('voxel-strike-horizontal-mono.svg',horizontal.replace(white,'#111111').replace(orange,'#111111'),870)
# The application's emblem is taken from the approved title's X and ejected voxels.
x=f'<path fill="{white}" d="{letters["X"]}" transform="translate(50 54) scale(2.6)"/>'
chips='<path fill="#ff8a18" d="M144 20H174V50H144Z M184 10H200V26H184Z"/>'
write('voxel-strike-symbol-color.svg',x+chips,256)
small=x+'<path fill="#ff8a18" d="M146 18H176V48H146Z"/>'
write('voxel-strike-symbol-small.svg',small,256)
# Slightly thinner reverse artwork to keep the white letter strokes optically balanced.
rev=''
for word,xx,yy,scale in [('VOXEL',88,24,1.44),('STRIKE',42,126,1.48)]:rev+=shape(word,xx,yy,scale,6,'#ffffff',-.45)
rev+='<path fill="#ffffff" d="M280 13H293V26H280Z M297 8H306V17H297Z"/>'
write('voxel-strike-stacked-reversed.svg',rev)
# Android adaptive layers, kept in the existing 108-unit authoring space.
body=ET.fromstring(svg(small,'symbol',256));paths=[]
for el in body:
 if el.tag.endswith('path'):
  d=el.attrib['d'];transform=el.attrib.get('transform','')
  if transform:
   # Bake the X's 2.6 scale and translation into its path coordinates.
   poly=ns['loops'](d);d=' '.join('M'+'L'.join(f'{50+a*2.6:.2f} {54+b*2.6:.2f}' for a,b in loop)+'Z' for loop in poly)
  paths.append((el.attrib['fill'],d))
android_header='<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">'
for name,monochrome in [('ic_launcher_foreground.xml',False),('ic_launcher_monochrome.xml',True)]:
 content=android_header+'<group android:scaleX="0.24" android:scaleY="0.24" android:translateX="23.28" android:translateY="26.4">'
 for color,d in paths:content+=f'<path android:fillColor="{"#FFFFFF" if monochrome else color}" android:pathData="{d}"/>'
 content+='</group></vector>'
 (p/'android'/name).write_text(content)
(p/'android/ic_launcher_background.xml').write_text(android_header+'<path android:fillColor="#121923" android:pathData="M0 0H108V108H0Z"/></vector>')
spec={'brand':'Voxel Strike','brief':'Мобільний аркадний шутер з блоковими світами, швидким рухом і руйнуванням оточення.','adjectives':['Блоковий','Швидкий','Контрастний'],'criteria':'Читабельний титул у меню та впізнавана іконка Android.','brand_color':orange,'tile_color':dark,'greyscale':False,'final':True,'industry':'consumer-app','mockups':['app-icon','website','social','sticker','tshirt','favicon-tab'],'nav':['Гра','Карти','Завантажити'],'deck_title':'Block Impact','concepts':[{'name':'Block Impact','symbol':'source/voxel-strike-symbol-small.svg','symbol_on_tile':'source/voxel-strike-symbol-small.svg','symbol_on_dark':'source/voxel-strike-symbol-small.svg','lockup':'source/voxel-strike-stacked-light.svg','lockup_on_dark':'source/voxel-strike-stacked-color.svg','stacked':'source/voxel-strike-stacked-light.svg','stacked_on_tile':'source/voxel-strike-stacked-color.svg','idea':'Масивний ігровий титул із воксельними уламками над X.','rationale':['Світлий VOXEL і помаранчевий STRIKE відповідають оформленню меню.','Авторські векторні літери не залежать від шрифтів.','Компактний X переносить характер титулу в іконку.']}],'recommendation':'Затверджений варіант A — Block Impact.','next_steps':[]}
(p/'presentation-spec.json').write_text(json.dumps(spec,ensure_ascii=False,indent=2))
print('Masters and Android layers ready')
