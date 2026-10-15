import com.ccs.voxelgun.map.*;
import com.ccs.voxelgun.map.maps.MovementCourse;
import com.ccs.voxelgun.map.scenery.DecorBox;
import com.ccs.voxelgun.voxel.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;

/** Desktop QA of actual generated geometry and decor. This is NOT the Android renderer. */
public final class PreviewMapCatalog {
    private static final int W = 800, H = 480;
    public static void main(String[] args) throws Exception {
        File directory = new File(args[0]);
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException(directory.toString());
        BufferedImage contact = new BufferedImage(1600, 7 * 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D cg = contact.createGraphics();
        cg.setColor(new Color(0x162131)); cg.fillRect(0,0,contact.getWidth(),contact.getHeight());
        try (PrintWriter stats = new PrintWriter(new File(directory, "catalog.csv"))) {
            stats.println("id,solidAboveGround,decor,landmarks,stairs");
            for (int index = 0; index <= Maps.count(); index++) {
                GameMap map = index == Maps.count() ? new MovementCourse() : Maps.create(index);
                VoxelWorld world = map.build(9);
                try {
                    BufferedImage overview = overview(map, world);
                    ImageIO.write(overview, "png", new File(directory, map.id() + "-overview.png"));
                    cg.drawImage(overview, index % 4 * 400, index / 4 * 300, 400, 300, null);
                    ImageIO.write(perspective(map, world), "png", new File(directory, map.id() + "-view.png"));
                    int solid = 0;
                    for (int z=4;z<world.sizeZ()-4;z++) for(int y=3;y<world.sizeY();y++)
                        for(int x=4;x<world.sizeX()-4;x++) if(world.solid(x,y,z)) solid++;
                    stats.println(map.id()+","+solid+","+map.scenery().boxes().size()+","+
                            map.scenery().landmarks().size()+","+map.stairFlights().size());
                    System.out.println(map.id()+": "+solid+" solid, "+map.scenery().boxes().size()+" decor");
                } finally { world.release(); }
            }
        }
        cg.dispose(); ImageIO.write(contact,"png",new File(directory,"all-maps-overview.png"));
    }

    private static BufferedImage overview(GameMap map, VoxelWorld world) {
        BufferedImage image = new BufferedImage(800,600,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics(); g.setColor(new Color(0x162131));g.fillRect(0,0,800,600);
        int sx=world.sizeX(), sz=world.sizeZ();
        int ceiling=switch(map.id()) {
            case "warehouse" -> 19;
            case "neon_arena" -> 13;
            case "facility" -> 9;
            case "bunker_13" -> 10;
            default -> world.sizeY()-1;
        };
        int[] heights=new int[sx*sz];
        for(int z=0;z<sz;z++) for(int x=0;x<sx;x++) {
            for(int y=ceiling;y>=0;y--) if(world.get(x,y,z)!=0){heights[x+sx*z]=y+1;break;}
        }
        float scale=700f/(sx+sz), vertical=scale*.54f, rise=scale*1.5f;
        float ox=50+sz*scale, oy=110;
        for(int z=0;z<sz;z++) for(int x=0;x<sx;x++) {
            int y=heights[x+sx*z]; if(y==0) continue;
            int rgb=Materials.rgb(world.get(x,y-1,z)&255);
            float px=ox+(x-z)*scale,py=oy+(x+z)*vertical-y*rise;
            int east=x+1<sx?heights[x+1+sx*z]:0, south=z+1<sz?heights[x+sx*(z+1)]:0;
            for(int layer=Math.min(east,south);layer<y;layer++) {
                int material=world.get(x,layer,z)&255;if(material==0)continue;
                float sy=py+(y-layer-1)*rise;
                if(east<=layer){g.setColor(tint(Materials.rgb(material),.72));poly(g,px,sy+vertical,px+scale,sy,px+scale,sy+rise,px,sy+vertical+rise);}
                if(south<=layer){g.setColor(tint(Materials.rgb(material),.55));poly(g,px-scale,sy,px,sy+vertical,px,sy+vertical+rise,px-scale,sy+rise);}
            }
            g.setColor(tint(rgb,1.03));poly(g,px,py-vertical,px+scale,py,px,py+vertical,px-scale,py);
        }
        g.setColor(new Color(0xf1f5f9));g.setFont(new Font("SansSerif",Font.BOLD,26));g.drawString(map.displayName(),26,38);
        g.setFont(new Font("SansSerif",Font.PLAIN,14));g.setColor(new Color(0xa9bacd));
        g.drawString("Actual world geometry | seed 9 | "+sx+" x "+sz+
                (ceiling<world.sizeY()-1?" | roof cutaway":""),26,62);
        g.drawString("Desktop geometry preview · lighting differs from Android",26,575);
        g.dispose();return image;
    }
    private static void poly(Graphics2D g,float... p){int[] x=new int[p.length/2],y=new int[x.length];for(int i=0;i<x.length;i++){x[i]=Math.round(p[i*2]);y[i]=Math.round(p[i*2+1]);}g.fillPolygon(x,y,x.length);}

    private static double[] camera(String id) {
        return switch(id) {
            case "dust_yard" -> new double[]{64,12,37,49,7,9};
            case "warehouse" -> new double[]{38,5,52,30,6,44};
            case "neon_arena" -> new double[]{64,5,42,64,13,27};
            case "village" -> new double[]{63,5,22,48,7,18};
            case "facility" -> new double[]{28,5,96,38,6,88};
            case "pirate_cove" -> new double[]{29,5,82,43,7,79};
            case "sky_islands" -> new double[]{60,30,51,54,31,55};
            case "candy_castle" -> new double[]{28,5,28,49,8,14};
            case "harbor_yard" -> new double[]{30,5,43,18,7,20};
            case "arctic_station" -> new double[]{32,7,43,19,8,20};
            case "flooded_plaza" -> new double[]{49,5,24,16,7,16};
            case "battlefield_city" -> new double[]{89,7,343,97,10,311};
            case "battlefield_farmland" -> new double[]{236,7,114,187,8,133};
            case "battlefield_alps" -> new double[]{156,7,107,193,9,129};
            case "movement_course_v1" -> new double[]{38,6,59,68,7,71};
            default -> new double[]{28,6,44,22,7,22};
        };
    }

    private record Hit(double t, int material, double nx, double ny, double nz) {}
    private static BufferedImage perspective(GameMap map,VoxelWorld world) {
        BufferedImage out=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);
        double[] c=camera(map.id());
        // Move a blocked camera vertically out of authored geometry, never change the map.
        while(world.solid((int)c[0],(int)c[1],(int)c[2])&&c[1]<world.sizeY()-2)c[1]++;
        double fx=c[3]-c[0],fy=c[4]-c[1],fz=c[5]-c[2], fl=Math.sqrt(fx*fx+fy*fy+fz*fz);
        final double vx=fx/fl,vy=fy/fl,vz=fz/fl;
        double rl=Math.sqrt(vx*vx+vz*vz);
        final double rx=-vz/rl,rz=vx/rl,ux=-vy*rz,uy=vx*rz-vz*rx,uz=vy*rx;
        MapEnvironment e=map.environment();
        HashMap<Integer,ArrayList<DecorBox>> bins=new HashMap<>();
        for(DecorBox b:map.scenery().boxes()) for(int z=(int)b.z/16;z<=(int)(b.z+b.depth)/16;z++)
            for(int x=(int)b.x/16;x<=(int)(b.x+b.width)/16;x++)bins.computeIfAbsent(x+z*100,k->new ArrayList<>()).add(b);
        IntStream.range(0,H).parallel().forEach(py->{
            for(int px=0;px<W;px++) {
                double a=(2*(px+.5)/W-1)*.8, b=(1-2*(py+.5)/H)*.8*H/W;
                double dx=vx+rx*a+ux*b,dy=vy+uy*b,dz=vz+rz*a+uz*b;
                double len=Math.sqrt(dx*dx+dy*dy+dz*dz);dx/=len;dy/=len;dz/=len;
                Hit hit=trace(world,c[0],c[1],c[2],dx,dy,dz,420,true);
                double max=hit==null?420:hit.t;
                HashSet<Integer> seen=new HashSet<>();
                // Quarter-chunk samples include endpoints; candidate boxes are indexed by every
                // touched chunk, so thin attached signs and cornices are included as well.
                for(double t=0;t<=max+4;t+=4){
                    double pxw=c[0]+dx*Math.min(t,max),pzw=c[2]+dz*Math.min(t,max);
                    int key=Math.floorDiv((int)Math.floor(pxw),16)+100*Math.floorDiv((int)Math.floor(pzw),16);
                    if(!seen.add(key))continue;
                    var boxes=bins.get(key);if(boxes==null)continue;
                    for(DecorBox box:boxes){Hit h=boxHit(c,dx,dy,dz,box);if(h!=null&&h.t<max){hit=h;max=h.t;}}
                }
                int sky=sky(e,dy);
                if(hit==null){out.setRGB(px,py,sky);continue;}
                double x=c[0]+dx*hit.t,y=c[1]+dy*hit.t,z=c[2]+dz*hit.t;
                int rgb=Materials.rgb(hit.material);
                double sun=Math.max(0,hit.nx*e.sunX+hit.ny*e.sunY+hit.nz*e.sunZ);
                Hit shade=sun>.05?trace(world,x+hit.nx*.03,y+hit.ny*.03,z+hit.nz*.03,e.sunX,e.sunY,e.sunZ,100,false):null;
                double gain=.56+.20*Math.max(0,hit.ny)+(shade==null?.31:.02)*sun;
                int col=tint(rgb,gain).getRGB();
                double fog=Math.min(.32,Math.max(0,(hit.t-55)/650));
                out.setRGB(px,py,mix(col,sky,fog));
            }
        });
        Graphics2D g=out.createGraphics();g.setColor(new Color(15,23,35,215));g.fillRect(0,H-48,W,48);
        g.setColor(Color.WHITE);g.setFont(new Font("SansSerif",Font.BOLD,18));g.drawString(map.displayName(),18,H-25);
        g.setColor(new Color(0xc0ccda));g.setFont(new Font("SansSerif",Font.PLAIN,11));
        g.drawString("Actual generated geometry + decor | desktop preview, not an Android screenshot",18,H-9);g.dispose();return out;
    }

    private static Hit boxHit(double[] c,double dx,double dy,double dz,DecorBox b){
        double[] origin={c[0],c[1],c[2]},dir={dx,dy,dz},lo={b.x,b.y,b.z},hi={b.x+b.width,b.y+b.height,b.z+b.depth};
        double near=0,far=1e6,sign=0;int axis=0;
        for(int i=0;i<3;i++){
            if(Math.abs(dir[i])<1e-9){if(origin[i]<lo[i]||origin[i]>hi[i])return null;continue;}
            double a=(lo[i]-origin[i])/dir[i],d=(hi[i]-origin[i])/dir[i];
            double first=Math.min(a,d),last=Math.max(a,d);
            if(first>near){near=first;axis=i;sign=dir[i]>0?-1:1;}
            far=Math.min(far,last);if(near>far)return null;
        }
        return near>.001?new Hit(near,b.material&255,axis==0?sign:0,axis==1?sign:0,axis==2?sign:0):null;
    }
    private static Hit trace(VoxelWorld w,double ox,double oy,double oz,double dx,double dy,double dz,double max,boolean transparent){
        int x=(int)Math.floor(ox),y=(int)Math.floor(oy),z=(int)Math.floor(oz);
        int sx=dx>=0?1:-1,sy=dy>=0?1:-1,sz=dz>=0?1:-1;
        double ax=Math.abs(1/dx),ay=Math.abs(1/dy),az=Math.abs(1/dz);
        double tx=(dx>=0?x+1-ox:ox-x)*ax,ty=(dy>=0?y+1-oy:oy-y)*ay,tz=(dz>=0?z+1-oz:oz-z)*az;
        double t=0,nx=0,ny=0,nz=0;
        while(t<max){
            if(!w.inside(x,y,z))return null;
            int material=w.get(x,y,z)&255;
            if(material!=0&&(transparent||Materials.opaque(material)))return new Hit(t,material,nx,ny,nz);
            if(tx<=ty&&tx<=tz){t=tx;tx+=ax;x+=sx;nx=-sx;ny=0;nz=0;}
            else if(ty<=tz){t=ty;ty+=ay;y+=sy;ny=-sy;nx=0;nz=0;}
            else{t=tz;tz+=az;z+=sz;nz=-sz;nx=0;ny=0;}
        }
        return null;
    }
    private static int sky(MapEnvironment e,double dy){double t=Math.min(1,Math.max(0,dy)*1.8);return new Color((float)(e.horizonR*(1-t)+e.zenithR*t),(float)(e.horizonG*(1-t)+e.zenithG*t),(float)(e.horizonB*(1-t)+e.zenithB*t)).getRGB();}
    private static int mix(int a,int b,double t){return new Color((int)((a>>16&255)*(1-t)+(b>>16&255)*t),(int)((a>>8&255)*(1-t)+(b>>8&255)*t),(int)((a&255)*(1-t)+(b&255)*t)).getRGB();}
    private static Color tint(int rgb,double g){return new Color(Math.min(255,(int)((rgb>>16&255)*g)),Math.min(255,(int)((rgb>>8&255)*g)),Math.min(255,(int)((rgb&255)*g)));}
}
