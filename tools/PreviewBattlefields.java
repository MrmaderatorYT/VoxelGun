import com.ccs.voxelstrike.map.GameMap;
import com.ccs.voxelstrike.map.Maps;
import com.ccs.voxelstrike.map.scenery.Landmark;
import com.ccs.voxelstrike.voxel.Materials;
import com.ccs.voxelstrike.voxel.VoxelWorld;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Desktop visual QA of the real generated worlds; run against compiled app Java classes. */
public final class PreviewBattlefields {
    public static void main(String[] args) throws Exception {
        File directory = new File(args.length > 0 ? args[0] : "output/battlefield-redesign");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException(directory.toString());
        for (int index = Maps.firstBattlefieldIndex(); index < Maps.count(); index++) {
            GameMap map = Maps.create(index);
            VoxelWorld world = map.build(9L);
            try {
                BufferedImage plan = plan(world, map);
                ImageIO.write(plan, "png", new File(directory, map.id() + "-plan.png"));
                ImageIO.write(isometric(world, map), "png", new File(directory, map.id() + "-iso.png"));
                System.out.println(map.id() + ": " + map.scenery().landmarks().size() + " landmarks, "
                        + map.stairFlights().size() + " stair flights");
            } finally { world.release(); }
        }
    }

    private static BufferedImage plan(VoxelWorld world, GameMap map) {
        int scale = 2, margin = 32, header = 90;
        BufferedImage image = new BufferedImage(world.sizeX() * scale + 2 * margin,
                world.sizeZ() * scale + header + 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0x17202a)); g.fillRect(0, 0, image.getWidth(), image.getHeight());
        title(g, map, "Generated geometry · overhead plan");
        for (int z = 0; z < world.sizeZ(); z++) for (int x = 0; x < world.sizeX(); x++) {
            int y = top(world, x, z), rgb = Materials.rgb(world.get(x, y, z) & 255);
            float shade = .84f + Math.min(.16f, y * .006f);
            int neighbour = top(world, Math.max(0, x - 1), Math.max(0, z - 1));
            if (y > neighbour) shade *= .83f;
            g.setColor(color(rgb, shade));
            g.fillRect(margin + x * scale, header + z * scale, scale, scale);
        }
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        for (Landmark l : map.scenery().landmarks()) {
            int x = margin + (int)l.x * scale, z = header + (int)l.z * scale;
            g.setColor(new Color(0x10202a)); g.fillRoundRect(x - 7, z - 24,
                    g.getFontMetrics().stringWidth(l.name) + 16, 28, 8, 8);
            g.setColor(new Color(0xffe0a0)); g.drawString(l.name, x, z - 5);
        }
        g.setColor(new Color(0x55aaff)); g.fillOval(margin + 13 * scale, header + 280 * scale, 14, 14);
        g.setColor(new Color(0xff7766)); g.fillOval(margin + 620 * scale, header + 280 * scale, 14, 14);
        g.setColor(new Color(0xc1c9d1)); g.setFont(new Font("SansSerif", Font.PLAIN, 15));
        g.drawString("640 × 576 · blue / red deployments · seed 9 · actual map generator", margin, image.getHeight() - 28);
        g.dispose(); return image;
    }

    private static BufferedImage isometric(VoxelWorld world, GameMap map) {
        BufferedImage image = new BufferedImage(1500, 1000, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0x17202a)); g.fillRect(0, 0, 1500, 1000);
        title(g, map, "Generated geometry · isometric overview");
        float s = 1.08f, v = .53f, h = 3.8f;
        for (int z = 0; z < world.sizeZ(); z++) for (int x = 0; x < world.sizeX(); x++) {
            int y = top(world, x, z) + 1, rgb = Materials.rgb(world.get(x, y - 1, z) & 255);
            float px = 720 + (x - z) * s, py = 150 + (x + z) * v - y * h;
            int east = x + 1 < world.sizeX() ? top(world, x + 1, z) + 1 : 0;
            int south = z + 1 < world.sizeZ() ? top(world, x, z + 1) + 1 : 0;
            if (east < y) {
                g.setColor(color(rgb, .64f));
                g.fillPolygon(new int[]{Math.round(px),Math.round(px+s),Math.round(px+s),Math.round(px)},
                        new int[]{Math.round(py+v),Math.round(py),Math.round(py+(y-east)*h),Math.round(py+v+(y-east)*h)},4);
            }
            if (south < y) {
                g.setColor(color(rgb, .48f));
                g.fillPolygon(new int[]{Math.round(px-s),Math.round(px),Math.round(px),Math.round(px-s)},
                        new int[]{Math.round(py),Math.round(py+v),Math.round(py+v+(y-south)*h),Math.round(py+(y-south)*h)},4);
            }
            g.setColor(color(rgb, 1));
            g.fillPolygon(new int[]{Math.round(px),Math.round(px+s),Math.round(px),Math.round(px-s)},
                    new int[]{Math.round(py-v),Math.round(py),Math.round(py+v),Math.round(py)},4);
        }
        g.setColor(new Color(0xc1c9d1)); g.setFont(new Font("SansSerif", Font.PLAIN, 16));
        g.drawString("CPU geometry preview; lighting and small scenery details use the in-game renderer.",32,965);
        g.dispose(); return image;
    }

    private static int top(VoxelWorld world, int x, int z) {
        for (int y = world.sizeY() - 1; y >= 0; y--) if (world.get(x,y,z) != 0) return y;
        return 0;
    }
    private static Color color(int rgb, float gain) {
        return new Color(Math.min(255, (int)((rgb >> 16 & 255) * gain)),
                Math.min(255, (int)((rgb >> 8 & 255) * gain)), Math.min(255,(int)((rgb & 255) * gain)));
    }
    private static void title(Graphics2D g, GameMap map, String subtitle) {
        g.setFont(new Font("SansSerif", Font.BOLD, 27)); g.setColor(new Color(0xf1f5f8));
        g.drawString(map.displayName(), 32, 39);
        g.setFont(new Font("SansSerif", Font.PLAIN, 16)); g.setColor(new Color(0xacbac8));
        g.drawString(subtitle, 32, 67);
    }
}
