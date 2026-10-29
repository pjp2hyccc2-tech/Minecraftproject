package com.huntcraft.entity.monster;

import com.huntcraft.core.DamageKind;
import com.huntcraft.core.Element;

/** MH hitzone values (0..100). Raw damage is multiplied by value/100. */
public record Hitzone(int cut, int blunt, int shot, int fire, int water, int thunder, int ice, int dragon) {

    public static Hitzone of(int cut, int blunt, int shot, int fire, int water, int thunder, int ice, int dragon) {
        return new Hitzone(cut, blunt, shot, fire, water, thunder, ice, dragon);
    }

    public int raw(DamageKind kind) {
        return switch (kind) {
            case CUT -> cut;
            case BLUNT -> blunt;
            case SHOT -> shot;
        };
    }

    public int element(Element e) {
        return switch (e) {
            case FIRE -> fire;
            case WATER -> water;
            case THUNDER -> thunder;
            case ICE -> ice;
            case DRAGON -> dragon;
            default -> 0;
        };
    }

    /** Softer hitzone used after a part has been broken. */
    public Hitzone softened(int bonus) {
        return new Hitzone(Math.min(100, cut + bonus), Math.min(100, blunt + bonus), Math.min(100, shot + bonus), fire, water, thunder, ice, dragon);
    }
}
