from pathlib import Path
import re
from PIL import Image, ImageDraw
root = Path(__file__).resolve().parents[2]
source = (root/'app/src/main/java/com/ccs/voxelgun/model/Models.java').read_text()
materials = (root/'app/src/main/java/com/ccs/voxelgun/voxel/Materials.java').read_text()
body = source.split('private static VoxelModel rifle() {',1)[1].split('\n    }',1)[0]
sx,sy,sz = map(int,re.search(r'new VoxelModel\((\d+), (\d+), (\d+)\)',body).groups())
colors = {}
for name,r,g,b in re.findall(r'define\((\w+), ([\d.]+)f, ([\d.]+)f, ([\d.]+)f', materials):
    colors[name] = tuple(int(float(v)*255) for v in (r,g,b))
voxels = {}
for match in re.finditer(r'model.box\((\d+), (\d+), (\d+), (\d+), (\d+), (\d+), Materials\.(\w+)\)',body):
    x0,y0,z0,x1,y1,z1 = map(int,match.groups()[:6])
    for z in range(z0,z1+1):
        for y in range(y0,y1+1):
            for x in range(x0,x1+1): voxels[x,y,z] = colors[match[7]]
# Same exposed faces, projection and shading as CosmeticPreview.drawIsometric.
polygons=[]
for diagonal in range(sx+sz-1):
    for y in range(sy):
        for x in range(max(0,diagonal-sz+1),min(diagonal,sx-1)+1):
            z=diagonal-x
            if (x,y,z) not in voxels: continue
            px=(x-z)*.5; py=(x+z)*.25-y*.75
            faces=[((x,y+1,z),1,[(px,py-.75),(px+.5,py-.5),(px,py-.25),(px-.5,py-.5)]),
                   ((x+1,y,z),.72,[(px,py-.25),(px+.5,py-.5),(px+.5,py+.25),(px,py+.5)]),
                   ((x,y,z+1),.5,[(px,py-.25),(px-.5,py-.5),(px-.5,py+.25),(px,py+.5)])]
            for neighbor,gain,points in faces:
                if neighbor not in voxels: polygons.append((points,tuple(int(c*gain) for c in voxels[x,y,z])))
points=[p for face,_ in polygons for p in face]
minx,miny=min(p[0] for p in points),min(p[1] for p in points)
maxx,maxy=max(p[0] for p in points),max(p[1] for p in points)
size=1024; aa=3; scale=min(864/(maxx-minx),864/(maxy-miny))
image=Image.new('RGBA',(size*aa,size*aa)); draw=ImageDraw.Draw(image)
for face,color in polygons:
    draw.polygon([((512+(x-(minx+maxx)/2)*scale)*aa,(512+(y-(miny+maxy)/2)*scale)*aa) for x,y in face],fill=color+(255,))
image.resize((size,size),Image.Resampling.LANCZOS).save(Path(__file__).with_name('voxel-strike-actual-weapon-logo.png'))
