package com.eternalrealm.estate;

import com.eternalrealm.core.ModInfo;

import java.util.Optional;

/**
 * The rooms of the Starlight Manor (星灯りの館) a player can rent from the innkeeper, cheapest first. Each is a design
 * {@code estates/room/<id>} (tools/estates/rooms.py); a dearer room has every storage piece of the cheaper ones (by
 * name), so moving up keeps everything exactly where it was. Prices follow size and furnishing (for scale: the horse
 * costs 1,500 G at Lv10, the griffin 12,000 G, the arcbike 40,000 G).
 */
public enum RoomDesign {
    /** 屋根裏部屋: a snug attic under the roof slope. */
    GARRET("garret", 1_000L),
    /** 運河の見える部屋: a panelled room with a bay window over the Grand Canal. */
    CANAL("canal", 5_000L),
    /** 書斎の間: a two-storey library room with a gallery of shelves. */
    LIBRARY("library", 12_000L),
    /** 星見の塔の部屋: a round tower room on two levels, windows all round and a telescope. */
    TOWER("tower", 25_000L),
    /** 貴賓のスイート: salon, bedchamber, armoury and vault, a balcony over the canal. */
    GRAND("grand", 50_000L);

    private final String id;
    private final long price;

    RoomDesign(String id, long price) {
        this.id = id;
        this.price = price;
    }

    /** The design id (the blueprint folder {@code estates/room/<id>}). */
    public String id() {
        return id;
    }

    public long price() {
        return price;
    }

    public String translationKey() {
        return "estate." + ModInfo.MOD_ID + ".room." + id;
    }

    public String descriptionKey() {
        return translationKey() + ".description";
    }

    public EstateKey key(java.util.UUID owner) {
        return new EstateKey(EstateKey.ROOM, owner, id);
    }

    public static Optional<RoomDesign> byId(String id) {
        for (RoomDesign design : values()) {
            if (design.id.equals(id)) {
                return Optional.of(design);
            }
        }
        return Optional.empty();
    }
}
