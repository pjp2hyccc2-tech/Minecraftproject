package com.eternalrealm.guild.house;

import com.eternalrealm.core.ModInfo;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One guild-house design of the catalogue ({@code data/eternalrealm/guildhouse/designs.json}): its id (also the
 * estate design, the blueprint folder {@code estates/guild/<id>} and the theme of its gate), its tier (1 cosy … 5
 * grand), its price in gold, the two colours of its portal and the size of the built house.
 *
 * @param baseColor   0xRRGGBB, the portal's main colour
 * @param accentColor 0xRRGGBB, the shimmer on top of it
 */
public record GuildHouseDesign(String id, int tier, long price, int baseColor, int accentColor, int width, int depth,
                               int height) {
    public static final int MIN_TIER = 1;
    public static final int MAX_TIER = 5;
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,32}");

    public GuildHouseDesign {
        Objects.requireNonNull(id, "id");
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("bad design id '" + id + "'");
        }
        if (tier < MIN_TIER || tier > MAX_TIER) {
            throw new IllegalArgumentException(id + ": tier out of range: " + tier);
        }
        if (price <= 0 || price > GuildHouseRules.MAX_PRICE) {
            throw new IllegalArgumentException(id + ": price out of range: " + price);
        }
        if ((baseColor & ~0xFFFFFF) != 0 || (accentColor & ~0xFFFFFF) != 0) {
            throw new IllegalArgumentException(id + ": colours must be 0xRRGGBB");
        }
        if (width <= 0 || depth <= 0 || height <= 0 || width > 512 || depth > 512 || height > 384) {
            throw new IllegalArgumentException(id + ": bad size " + width + "x" + depth + "x" + height);
        }
    }

    /** Translation key of the design's name. */
    public String nameKey() {
        return "guildhouse." + ModInfo.MOD_ID + ".design." + id;
    }

    /** Translation key of the design's two-line description. */
    public String descriptionKey() {
        return nameKey() + ".desc";
    }

    /** Shared storage the house holds: {@link GuildHouseRules#storageChests} chests of 27 slots. */
    public int storageSlots() {
        return GuildHouseRules.storageChests(tier) * 27;
    }
}
