package com.huntcraft.gear.weapon;

import com.huntcraft.core.CoreEvents;
import com.huntcraft.core.WeaponClass;
import com.huntcraft.network.HCNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Network actions of the weapon module (string keyed, see {@link HCNetwork}). */
public final class WeaponNet {
    /** client -> server: start sharpening (sharpen key). */
    public static final String SHARPEN = "weapons/sharpen";
    /** client -> server: left click on air / block with a melee weapon. */
    public static final String SWING = "weapons/swing";
    /** client -> server: left click while holding right click (lance counter, guard slash). */
    public static final String GUARD_ATTACK = "weapons/guard_attack";
    /** server -> client: weapon state mirror (gauges, notes, buffs, sharpening). */
    public static final String STATE = "weapons/state";

    private WeaponNet() {
    }

    public static void registerServerActions() {
        HCNetwork.registerServerAction(SHARPEN, (p, tag) -> Sharpening.start(p));
        HCNetwork.registerServerAction(SWING, (p, tag) -> handleSwing(p));
        HCNetwork.registerServerAction(GUARD_ATTACK, (p, tag) -> {
            ItemStack st = p.getMainHandItem();
            if (st.getItem() instanceof HunterWeaponItem w && w.isGuarding(p) && !CoreEvents.isLocked(p)) w.guardAttack(p, st);
        });
    }

    private static void handleSwing(ServerPlayer p) {
        ItemStack st = p.getMainHandItem();
        if (!(st.getItem() instanceof HunterWeaponItem w) || w.weaponClass() == WeaponClass.BOW) return;
        if (p.isUsingItem() || Sharpening.isSharpening(p) || CoreEvents.isLocked(p) || p.isSpectator()) return;
        float strength = p.getAttackStrengthScale(0.5F);
        p.resetAttackStrengthTicker();
        w.airSwing(p, st, strength);
    }

    public static void sync(ServerPlayer p, WeaponState s) {
        HCNetwork.sendToPlayer(p, STATE, s.toSyncTag());
    }
}
