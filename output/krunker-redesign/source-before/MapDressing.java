package com.ccs.voxelgun.map.scenery;

import com.ccs.voxelgun.map.GameMap;
import com.ccs.voxelgun.map.MapBuilder;

/** Routing only; each art direction lives in its own small authoring class. */
public final class MapDressing {
    private MapDressing() {}
    public static void apply(GameMap map, MapBuilder b) {
        switch (map.id()) {
            case "village" -> VillageDressing.build(b);
            case "battlefield_alps" -> AlpineDressing.build(b);
            case "battlefield_city" -> CityBattlefieldDressing.build(b);
            case "rooftop_district" -> UrbanDressing.build(b, false);
            case "warehouse", "facility", "harbor_yard", "reactor_core", "train_depot",
                 "orbital_dock", "volcano_forge", "bunker_13" -> IndustrialDressing.build(b, map.id());
            default -> { }
        }
    }
}
