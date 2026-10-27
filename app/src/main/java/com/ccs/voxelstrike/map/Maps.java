package com.ccs.voxelstrike.map;

import com.ccs.voxelstrike.map.maps.CandyCastle;
import com.ccs.voxelstrike.map.maps.BattlefieldMaps;
import com.ccs.voxelstrike.map.maps.DustYard;
import com.ccs.voxelstrike.map.maps.ExpansionArena;
import com.ccs.voxelstrike.map.maps.PirateCove;
import com.ccs.voxelstrike.map.maps.SkyIslands;
import com.ccs.voxelstrike.map.maps.Facility;
import com.ccs.voxelstrike.map.maps.NeonArena;
import com.ccs.voxelstrike.map.maps.Village;
import com.ccs.voxelstrike.map.maps.Warehouse;

/** Catalog of playable maps, in menu order. */
public final class Maps {
    private static final int CLASSIC_COUNT = 8;
    private static final int BATTLEFIELD_START = CLASSIC_COUNT + ExpansionArena.count();

    private Maps() {}

    public static GameMap create(int index) {
        if (isBattlefield(index)) {
            return BattlefieldMaps.create(index - BATTLEFIELD_START);
        }
        if (index >= CLASSIC_COUNT && index < BATTLEFIELD_START) {
            return ExpansionArena.create(index - CLASSIC_COUNT);
        }
        switch (index) {
            case 1:
                return new Warehouse();
            case 2:
                return new NeonArena();
            case 3:
                return new Village();
            case 4:
                return new Facility();
            case 5:
                return new PirateCove();
            case 6:
                return new SkyIslands();
            case 7:
                return new CandyCastle();
            case 0:
            default:
                return new DustYard();
        }
    }

    public static int count() {
        return BATTLEFIELD_START + BattlefieldMaps.COUNT;
    }

    /** First catalog slot reserved for the large Battle Field-only maps. */
    public static int firstBattlefieldIndex() {
        return BATTLEFIELD_START;
    }

    public static boolean isBattlefield(int index) {
        return index >= BATTLEFIELD_START && index < count();
    }

    public static String tags(int index) {
        if (isBattlefield(index)) {
            return BattlefieldMaps.tags(index - BATTLEFIELD_START);
        }
        if (index >= CLASSIC_COUNT && index < BATTLEFIELD_START) {
            return ExpansionArena.tags(index - CLASSIC_COUNT);
        }
        String[] tags = {"Mixed range / Courtyards", "Close quarters / Cover",
                "Close quarters / Symmetric", "Flanking / Rooftops", "Interiors / Corridors",
                "Mixed range / Ship decks", "Vertical / Bridges / Jump pads", "Vertical / Battlements"};
        return tags[Math.max(0, Math.min(tags.length - 1, index))];
    }

    public static com.ccs.voxelstrike.game.GameMode recommended(int index) {
        if (isBattlefield(index)) {
            return com.ccs.voxelstrike.game.GameMode.BATTLE_FIELD;
        }
        if (index >= CLASSIC_COUNT && index < BATTLEFIELD_START) {
            return ExpansionArena.recommended(index - CLASSIC_COUNT);
        }
        return switch (index) {
            case 1, 2 -> com.ccs.voxelstrike.game.GameMode.KING_OF_THE_HILL;
            case 3, 4 -> com.ccs.voxelstrike.game.GameMode.BOMB_PLANT;
            case 5, 6 -> com.ccs.voxelstrike.game.GameMode.CRYSTAL_HEIST;
            default -> com.ccs.voxelstrike.game.GameMode.CAPTURE_POINT;
        };
    }

    public static String name(int index) {
        return create(index).displayName();
    }

    public static String subtitle(int index) {
        return create(index).subtitle();
    }

    public static int indexOf(String id) {
        for (int i = 0; i < count(); i++) {
            if (create(i).id().equals(id)) {
                return i;
            }
        }
        return 0;
    }
}
