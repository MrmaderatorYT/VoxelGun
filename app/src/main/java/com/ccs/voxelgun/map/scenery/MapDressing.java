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
            case "warehouse", "facility" -> IndustrialDressing.build(b, map.id());
            case "harbor_yard", "arctic_station", "metro_junction", "canyon_outpost",
                 "reactor_core", "rooftop_district", "bamboo_temple", "stone_quarry",
                 "orbital_dock", "flooded_plaza", "train_depot", "volcano_forge",
                 "ice_citadel", "solar_farm", "bunker_13" -> ExpansionDressing.build(b, map.id());
            default -> { }
        }
        ClassicArenaDressing.build(b, map.id());
    }
}
