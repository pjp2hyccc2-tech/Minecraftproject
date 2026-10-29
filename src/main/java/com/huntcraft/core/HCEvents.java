package com.huntcraft.core;

import com.huntcraft.entity.monster.HuntMonster;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.Event;
import org.jetbrains.annotations.Nullable;

/** Custom events posted on MinecraftForge.EVENT_BUS. */
public final class HCEvents {
    private HCEvents() {
    }

    /**
     * A monster was slain (large: when it becomes a carcass; small: on death) or captured.
     * Quest module listens to count targets.
     */
    public static class MonsterHuntedEvent extends Event {
        private final HuntMonster monster;
        @Nullable
        private final Player player;
        private final boolean captured;

        public MonsterHuntedEvent(HuntMonster monster, @Nullable Player player, boolean captured) {
            this.monster = monster;
            this.player = player;
            this.captured = captured;
        }

        public HuntMonster getMonster() { return monster; }

        @Nullable
        public Player getPlayer() { return player; }

        public boolean isCaptured() { return captured; }
    }

    /** A part was broken / severed. */
    public static class MonsterPartBrokenEvent extends Event {
        private final HuntMonster monster;
        private final String part;
        @Nullable
        private final Player player;

        public MonsterPartBrokenEvent(HuntMonster monster, String part, @Nullable Player player) {
            this.monster = monster;
            this.part = part;
            this.player = player;
        }

        public HuntMonster getMonster() { return monster; }

        public String getPart() { return part; }

        @Nullable
        public Player getPlayer() { return player; }
    }

    /** A hunter gathered from a gathering point (posted by the quest module). */
    public static class GatherEvent extends Event {
        private final Player player;
        private final net.minecraft.world.item.ItemStack stack;

        public GatherEvent(Player player, net.minecraft.world.item.ItemStack stack) {
            this.player = player;
            this.stack = stack;
        }

        public Player getPlayer() { return player; }

        public net.minecraft.world.item.ItemStack getStack() { return stack; }
    }
}
