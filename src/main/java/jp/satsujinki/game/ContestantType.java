package jp.satsujinki.game;

import net.minecraft.util.RandomSource;

/** The contestant archetypes. Each one has two skins and its own personality. All contestants are adults. */
public enum ContestantType {
    SALARYMAN("salaryman", false, Personality.TIMID, 20.0F, 0.30D, 1.00F),
    OFFICE_LADY("office_lady", true, Personality.TIMID, 18.0F, 0.31D, 1.00F),
    YANKEE("yankee", false, Personality.FIGHTER, 24.0F, 0.32D, 0.95F),
    GYARU("gyaru", true, Personality.LOUD, 18.0F, 0.32D, 1.08F),
    OTAKU("otaku", false, Personality.PANICKY, 18.0F, 0.28D, 1.10F),
    OJISAN("ojisan", false, Personality.WHINY, 22.0F, 0.26D, 0.85F),
    MACHO("macho", false, Personality.FIGHTER, 34.0F, 0.30D, 0.82F),
    PRESIDENT("president", false, Personality.BRIBER, 20.0F, 0.27D, 0.92F);

    public static final int SKINS_PER_TYPE = 2;

    public final String id;
    public final boolean female;
    public final Personality personality;
    public final float health;
    public final double speed;
    public final float voicePitch;

    ContestantType(String id, boolean female, Personality personality, float health, double speed, float voicePitch) {
        this.id = id;
        this.female = female;
        this.personality = personality;
        this.health = health;
        this.speed = speed;
        this.voicePitch = voicePitch;
    }

    public static ContestantType byId(int i) {
        ContestantType[] v = values();
        return i >= 0 && i < v.length ? v[i] : SALARYMAN;
    }

    public static ContestantType random(RandomSource r) {
        return values()[r.nextInt(values().length)];
    }

    public static int skinCount() {
        return values().length * SKINS_PER_TYPE;
    }

    public enum Personality {
        TIMID(0.18F, 1.0F),
        FIGHTER(0.05F, 0.7F),
        LOUD(0.12F, 1.1F),
        PANICKY(0.25F, 1.4F),
        WHINY(0.20F, 1.1F),
        BRIBER(0.10F, 0.9F);

        public final float baseFear;
        public final float fearGain;

        Personality(float baseFear, float fearGain) {
            this.baseFear = baseFear;
            this.fearGain = fearGain;
        }
    }
}
