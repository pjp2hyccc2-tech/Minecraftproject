package com.huntcraft.quest;

import com.huntcraft.core.Rank;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/** Static quest definition (shared by client and server). Build with {@link #builder}. */
public final class QuestDef {
    public enum Type {
        /** 狩猟: slay or capture the target large monster(s). */
        HUNT,
        /** 捕獲: capture the target (slaying it fails the quest). */
        CAPTURE,
        /** 討伐: slay N small monsters. */
        SLAY,
        /** 採取/納品: deliver items to the delivery box at the base camp. */
        GATHER;

        public String key() {
            return "questtype.huntcraft." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** A target: monster registry id (hunt/capture/slay) or an item to deliver (gather). */
    public record Target(String id, @Nullable Supplier<? extends Item> item, int count) {
        public boolean isItem() {
            return item != null;
        }

        public Component name() {
            if (item != null) return new ItemStack(item.get()).getHoverName();
            return Component.translatable("entity.huntcraft." + id);
        }
    }

    /** A fixed (sub) reward. */
    public record Reward(Supplier<? extends Item> item, int count) {
    }

    public final String id;
    public final Type type;
    public final int stars;
    public final Rank rank;
    public final QuestMap map;
    public final List<Target> targets;
    public final int timeLimitMinutes;
    public final int zenny;
    public final int hrp;
    public final int hrReq;
    public final boolean key;
    public final boolean urgent;
    public final int rolls;
    public final List<Reward> rewards;

    private QuestDef(Builder b) {
        this.id = b.id;
        this.type = b.type;
        this.stars = b.stars;
        this.rank = b.stars >= 4 ? Rank.HIGH : Rank.LOW;
        this.map = b.map;
        this.targets = Collections.unmodifiableList(new ArrayList<>(b.targets));
        this.timeLimitMinutes = b.time;
        this.zenny = b.zenny;
        this.hrp = b.hrp;
        this.hrReq = b.hr;
        this.key = b.key;
        this.urgent = b.urgent;
        this.rolls = b.rolls > 0 ? b.rolls : defaultRolls(b);
        this.rewards = Collections.unmodifiableList(new ArrayList<>(b.rewards));
    }

    private static int defaultRolls(Builder b) {
        return switch (b.type) {
            case HUNT, CAPTURE -> Math.max(3, (b.targets.size() > 1 ? 4 : 5) + (b.stars - 1) / 2);
            case SLAY, GATHER -> 3 + (b.stars + 1) / 2;
        };
    }

    public static Builder builder(String id, Type type, int stars, QuestMap map) {
        return new Builder(id, type, stars, map);
    }

    public int timeLimitTicks() {
        return timeLimitMinutes * 60 * 20;
    }

    /** Contract fee paid on accepting (refunded when cancelled before departure). */
    public int contractFee() {
        return Math.max(50, (zenny / 10) / 10 * 10);
    }

    public boolean isHighRank() {
        return rank == Rank.HIGH;
    }

    public String titleKey() {
        return "quest.huntcraft." + id;
    }

    public String descKey() {
        return "quest.huntcraft." + id + ".desc";
    }

    public String clientKey() {
        return "quest.huntcraft." + id + ".client";
    }

    public Component title() {
        return Component.translatable(titleKey());
    }

    /** "炎翼竜イグニヴァルド 1頭の狩猟" style objective line for a target. */
    public Component objective(Target t) {
        return Component.translatable("quest.huntcraft.objective." + type.name().toLowerCase(Locale.ROOT), t.name(), t.count());
    }

    public static final class Builder {
        private final String id;
        private final Type type;
        private final int stars;
        private final QuestMap map;
        private final List<Target> targets = new ArrayList<>();
        private final List<Reward> rewards = new ArrayList<>();
        private int time = 50;
        private int zenny = 500;
        private int hrp = 50;
        private int hr = 1;
        private boolean key;
        private boolean urgent;
        private int rolls;

        private Builder(String id, Type type, int stars, QuestMap map) {
            this.id = id;
            this.type = type;
            this.stars = stars;
            this.map = map;
        }

        public Builder target(String monsterId, int count) {
            targets.add(new Target(monsterId, null, count));
            return this;
        }

        public Builder deliver(Supplier<? extends Item> item, int count) {
            targets.add(new Target("item", item, count));
            return this;
        }

        public Builder reward(int zenny, int hrp) {
            this.zenny = zenny;
            this.hrp = hrp;
            return this;
        }

        public Builder hr(int hr) {
            this.hr = hr;
            return this;
        }

        public Builder key() {
            this.key = true;
            return this;
        }

        public Builder urgent() {
            this.urgent = true;
            return this;
        }

        public Builder time(int minutes) {
            this.time = minutes;
            return this;
        }

        public Builder rolls(int n) {
            this.rolls = n;
            return this;
        }

        public Builder item(Supplier<? extends Item> item, int count) {
            rewards.add(new Reward(item, count));
            return this;
        }

        public QuestDef build() {
            if (targets.isEmpty()) throw new IllegalStateException("quest " + id + " has no target");
            return new QuestDef(this);
        }
    }
}
