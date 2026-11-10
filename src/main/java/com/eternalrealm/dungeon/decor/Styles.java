package com.eternalrealm.dungeon.decor;

import com.eternalrealm.dungeon.DungeonTheme;

import java.util.Random;

/** The style of each theme. */
final class Styles {
    private Styles() {
    }

    static Style of(DungeonTheme theme, Canvas canvas, Random random) {
        return switch (theme) {
            case UNDERGROUND_CITY -> new CityStyle(canvas, random);
            case FOREST -> new ForestStyle(canvas, random);
            case DESERT_TEMPLE -> new TempleStyle(canvas, random);
            case ICE_PRISON -> new PrisonStyle(canvas, random);
            case ICE_CASTLE -> new CastleStyle(canvas, random);
            case VOLCANO -> new VolcanoStyle(canvas, random);
            case SKY_RUINS -> new SkyStyle(canvas, random);
            case DARK_TOWER -> new TowerStyle(canvas, random);
            case END_THRONE -> new ThroneStyle(canvas, random);
        };
    }
}
