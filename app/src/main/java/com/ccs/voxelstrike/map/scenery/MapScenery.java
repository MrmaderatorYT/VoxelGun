package com.ccs.voxelstrike.map.scenery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Build-time lists; exposed read-only to the renderer and simulation. */
public final class MapScenery {
    private final List<DecorBox> boxes = new ArrayList<>();
    private final List<Landmark> landmarks = new ArrayList<>();
    private final List<DecorBox> boxView = Collections.unmodifiableList(boxes);
    private final List<Landmark> landmarkView = Collections.unmodifiableList(landmarks);
    public List<DecorBox> boxes() { return boxView; }
    public List<Landmark> landmarks() { return landmarkView; }
    public void clear() { boxes.clear(); landmarks.clear(); }
    public void box(float x, float y, float z, float w, float h, float d, int material) {
        boxes.add(new DecorBox(x, y, z, w, h, d, material));
    }
    public void landmark(String name, float x, float z, float radius) {
        landmarks.add(new Landmark(name, x, z, radius));
    }
    public String area(float x, float z) {
        Landmark found = null;
        float closest = Float.MAX_VALUE;
        for (int i = 0; i < landmarks.size(); i++) {
            Landmark l = landmarks.get(i);
            float dx = x - l.x, dz = z - l.z, distance = dx * dx + dz * dz;
            if (distance <= l.radius * l.radius && distance < closest) { found = l; closest = distance; }
        }
        return found == null ? "" : found.name;
    }
}
