package com.huntcraft.gear.weapon;

import com.huntcraft.gear.weapon.action.CombatActions;
import com.huntcraft.network.HCNetwork;
import net.minecraft.server.level.ServerPlayer;

/** Network actions of the weapon module (string keyed, see {@link HCNetwork}). */
public final class WeaponNet {
    /** client -> server: start sharpening (sharpen key). */
    public static final String SHARPEN = "weapons/sharpen";
    /** client -> server: attack key with a hunter weapon ("sneak" flag) - starts / chains a combat action. */
    public static final String SWING = "weapons/swing";
    /** client -> server: attack key while holding right click (lance counter, guard slash). */
    public static final String GUARD_ATTACK = "weapons/guard_attack";
    /** server -> client: weapon state mirror (gauges, notes, buffs, sharpening). */
    public static final String STATE = "weapons/state";

    private WeaponNet() {
    }

    public static void registerServerActions() {
        HCNetwork.registerServerAction(SHARPEN, (p, tag) -> Sharpening.start(p));
        HCNetwork.registerServerAction(SWING, (p, tag) -> CombatActions.attackInput(p, tag.getBoolean("sneak")));
        HCNetwork.registerServerAction(GUARD_ATTACK, (p, tag) -> CombatActions.attackInput(p, false));
        CombatActions.registerServerActions();
    }

    public static void sync(ServerPlayer p, WeaponState s) {
        HCNetwork.sendToPlayer(p, STATE, s.toSyncTag());
    }
}
