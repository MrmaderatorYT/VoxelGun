package com.ccs.voxelstrike.map.scenery;

import com.ccs.voxelstrike.voxel.MeshData;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Startup-only cuboid meshing: six merged faces per box, one draw per 32 m palette batch. */
public final class DecorationMesher {
    private static final int[][] FACES = {{1,3,7,5}, {0,4,6,2}, {2,6,7,3}, {0,1,5,4}, {4,5,7,6}, {0,2,3,1}};
    private static final float[][] NORMALS = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
    private DecorationMesher() {}
    public static DecorationBatch[] build(MapScenery scenery) {
        Map<Long,List<DecorBox>> cells = new TreeMap<>();
        for (DecorBox b : scenery.boxes()) {
            long cx = (long) Math.floor((b.x + b.width * .5f) / 32f);
            long cz = (long) Math.floor((b.z + b.depth * .5f) / 32f);
            long key = cx << 32 | (cz & 0xffffffffL);
            cells.computeIfAbsent(key, ignored -> new ArrayList<>()).add(b);
        }
        DecorationBatch[] result = new DecorationBatch[cells.size()];
        int cursor = 0;
        for (List<DecorBox> boxes : cells.values()) result[cursor++] = mesh(boxes);
        return result;
    }
    private static DecorationBatch mesh(List<DecorBox> boxes) {
        int vertices = boxes.size() * 24, indices = boxes.size() * 36;
        FloatBuffer v = ByteBuffer.allocateDirect(vertices * 8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        IntBuffer ix = ByteBuffer.allocateDirect(indices * 4).order(ByteOrder.nativeOrder()).asIntBuffer();
        float[] bounds = {Float.MAX_VALUE,Float.MAX_VALUE,Float.MAX_VALUE,-Float.MAX_VALUE,-Float.MAX_VALUE,-Float.MAX_VALUE};
        int base = 0;
        for (DecorBox b : boxes) {
            bounds[0] = Math.min(bounds[0], b.x); bounds[1] = Math.min(bounds[1], b.y); bounds[2] = Math.min(bounds[2], b.z);
            bounds[3] = Math.max(bounds[3], b.x + b.width); bounds[4] = Math.max(bounds[4], b.y + b.height); bounds[5] = Math.max(bounds[5], b.z + b.depth);
            for (int face = 0; face < 6; face++) {
                for (int corner : FACES[face]) {
                    v.put(b.x + ((corner & 1) != 0 ? b.width : 0));
                    v.put(b.y + ((corner & 2) != 0 ? b.height : 0));
                    v.put(b.z + ((corner & 4) != 0 ? b.depth : 0));
                    v.put(NORMALS[face]); v.put((float) b.material); v.put(.95f);
                }
                ix.put(base).put(base + 1).put(base + 2).put(base).put(base + 2).put(base + 3);
                base += 4;
            }
        }
        v.flip(); ix.flip();
        return new DecorationBatch(new MeshData(v, ix, vertices, indices), bounds);
    }
}
