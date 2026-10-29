package com.huntcraft.entity.monster;

import com.huntcraft.core.Element;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Static data for a monster species: parts/hitzones, status thresholds, rewards, size range, notes info.
 * Build with {@link #builder(String, boolean)}.
 */
public final class MonsterProfile {
    public final String id;
    public final boolean large;
    public final float healthLow;
    public final float healthHigh;
    public final float damageHighMult;
    public final float defenseHigh;
    public final float minScale, maxScale;
    public final Map<String, PartDef> parts;
    public final List<PartDef> hitboxParts;
    public final StatusThresholds status;
    public final RewardTable carves;
    public final RewardTable tailCarves;
    public final RewardTable captureRewards;
    public final Map<String, RewardTable> breakRewards;
    public final int carveCount;
    public final float captureThreshold;
    public final float rageThreshold;
    public final float flinchThreshold;
    public final Element mainElement;
    /** Weakness stars 0..3 per element, shown in hunter notes. */
    public final Map<Element, Integer> weakness;
    public final int questZenny;
    public final int questHrp;
    /** Category key for notes: flying_wyvern, fanged_beast, leviathan, brute_wyvern, elder_dragon, small... */
    public final String category;

    private MonsterProfile(Builder b) {
        this.id = b.id;
        this.large = b.large;
        this.healthLow = b.healthLow;
        this.healthHigh = b.healthHigh;
        this.damageHighMult = b.damageHighMult;
        this.defenseHigh = b.defenseHigh;
        this.minScale = b.minScale;
        this.maxScale = b.maxScale;
        this.parts = Collections.unmodifiableMap(b.parts);
        List<PartDef> hb = new ArrayList<>();
        for (PartDef p : b.parts.values()) if (p.hasHitbox) hb.add(p);
        this.hitboxParts = Collections.unmodifiableList(hb);
        this.status = b.status;
        this.carves = b.carves;
        this.tailCarves = b.tailCarves;
        this.captureRewards = b.captureRewards;
        this.breakRewards = Collections.unmodifiableMap(b.breakRewards);
        this.carveCount = b.carveCount;
        this.captureThreshold = b.captureThreshold;
        this.rageThreshold = b.rageThreshold;
        this.flinchThreshold = b.flinchThreshold;
        this.mainElement = b.mainElement;
        this.weakness = Collections.unmodifiableMap(b.weakness);
        this.questZenny = b.questZenny;
        this.questHrp = b.questHrp;
        this.category = b.category;
    }

    public static Builder builder(String id, boolean large) {
        return new Builder(id, large);
    }

    public PartDef part(String name) {
        PartDef p = parts.get(name);
        return p != null ? p : parts.get("body");
    }

    public Component displayName() {
        return Component.translatable("entity.huntcraft." + id);
    }

    public Component description() {
        return Component.translatable("notes.huntcraft." + id + ".desc");
    }

    public static final class Builder {
        private final String id;
        private final boolean large;
        private float healthLow = 400, healthHigh = 800, damageHighMult = 1.6F, defenseHigh = 0.8F;
        private float minScale = 0.9F, maxScale = 1.2F;
        private final Map<String, PartDef> parts = new LinkedHashMap<>();
        private StatusThresholds status = StatusThresholds.LARGE_DEFAULT;
        private RewardTable carves = new RewardTable();
        private RewardTable tailCarves = new RewardTable();
        private RewardTable captureRewards = new RewardTable();
        private final Map<String, RewardTable> breakRewards = new LinkedHashMap<>();
        private int carveCount = 3;
        private float captureThreshold = 0.25F;
        private float rageThreshold = 0.12F;
        private float flinchThreshold = 0.06F;
        private Element mainElement = Element.NONE;
        private final Map<Element, Integer> weakness = new EnumMap<>(Element.class);
        private int questZenny = 1500, questHrp = 60;
        private String category = "small";

        private Builder(String id, boolean large) {
            this.id = id;
            this.large = large;
            if (!large) {
                status = StatusThresholds.SMALL_DEFAULT;
                carveCount = 1;
                minScale = 0.95F;
                maxScale = 1.05F;
            }
        }

        /** Max health for low rank and high rank (both capped at 1024 by vanilla). */
        public Builder health(float low, float high) {
            this.healthLow = low;
            this.healthHigh = high;
            return this;
        }

        /** High rank damage multiplier and damage-taken multiplier (defense rate). */
        public Builder highRank(float damageMult, float defenseRate) {
            this.damageHighMult = damageMult;
            this.defenseHigh = defenseRate;
            return this;
        }

        public Builder scale(float min, float max) {
            this.minScale = min;
            this.maxScale = max;
            return this;
        }

        public Builder part(PartDef part) {
            parts.put(part.name, part);
            return this;
        }

        public Builder status(StatusThresholds s) {
            this.status = s;
            return this;
        }

        public Builder carves(RewardTable t) {
            this.carves = t;
            return this;
        }

        public Builder tailCarves(RewardTable t) {
            this.tailCarves = t;
            return this;
        }

        public Builder captureRewards(RewardTable t) {
            this.captureRewards = t;
            return this;
        }

        public Builder breakReward(String partName, RewardTable t) {
            breakRewards.put(partName, t);
            return this;
        }

        public Builder carveCount(int n) {
            this.carveCount = n;
            return this;
        }

        public Builder captureThreshold(float f) {
            this.captureThreshold = f;
            return this;
        }

        /** Fraction of max HP of accumulated damage that triggers enrage. */
        public Builder rageThreshold(float f) {
            this.rageThreshold = f;
            return this;
        }

        /** Fraction of max HP of accumulated damage that triggers a flinch. */
        public Builder flinchThreshold(float f) {
            this.flinchThreshold = f;
            return this;
        }

        public Builder element(Element e) {
            this.mainElement = e;
            return this;
        }

        public Builder weakness(Element e, int stars) {
            this.weakness.put(e, stars);
            return this;
        }

        public Builder questReward(int zenny, int hrp) {
            this.questZenny = zenny;
            this.questHrp = hrp;
            return this;
        }

        public Builder category(String c) {
            this.category = c;
            return this;
        }

        public MonsterProfile build() {
            if (!parts.containsKey("body")) part(PartDef.body(Hitzone.of(40, 40, 35, 10, 10, 10, 10, 10)));
            return new MonsterProfile(this);
        }
    }
}
