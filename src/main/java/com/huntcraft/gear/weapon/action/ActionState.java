package com.huntcraft.gear.weapon.action;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Server side per-player action bookkeeping (lives in WeaponState). */
public final class ActionState {
    @Nullable
    public ActionDef current;
    public long start;
    /** Bit i set once hit i of the current action has been applied. */
    public int hitsDone;
    public boolean queued;
    public boolean queuedSneak;
    /** The action that ended last and when (combo continuation after the recovery). */
    @Nullable
    public String lastName;
    public long lastEnd = -1000;
    /** Free use for weapons (e.g. long sword: spirit blade was powered). */
    public boolean flag;
    public int value;

    // ---- broadcast caches (stance / sheathed weapon)
    public int sentMode = -1;
    public int sentFlags = -1;
    public ItemStack sentSheathed = ItemStack.EMPTY;
    public boolean sheathSent;
    /** Last hunter weapon held in the main hand (shown on the back while something else is held). */
    public ItemStack lastWeapon = ItemStack.EMPTY;

    public boolean active() {
        return current != null;
    }

    public int elapsed(long now) {
        return current == null ? 0 : (int) (now - start);
    }

    public void clear() {
        current = null;
        hitsDone = 0;
        queued = false;
        queuedSneak = false;
        flag = false;
        value = 0;
    }
}
