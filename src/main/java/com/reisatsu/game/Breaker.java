package com.reisatsu.game;

import com.reisatsu.network.S2C;
import com.reisatsu.registry.ModSounds;
import com.reisatsu.world.Circuit;
import com.reisatsu.world.Fixtures;
import com.reisatsu.world.Layout;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** The breaker panel 分電盤 (docs/DESIGN.md §3.9): a small GUI of big switches. */
public final class Breaker {
    private Breaker() {}

    public static int mask(ServerLevel level) {
        int m = 0;
        for (Circuit c : Circuit.values()) if (Fixtures.circuitOn(level, c)) m |= 1 << c.ordinal();
        return m;
    }

    public static void open(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        if (!GameManager.isGameLevel(level)) return;
        GameManager.send(p, new S2C.Breaker(mask(level)));
    }

    /** A switch thrown at the panel (C2S). */
    public static void toggle(ServerPlayer p, int circuit, boolean on) {
        ServerLevel level = p.serverLevel();
        if (!GameManager.isGameLevel(level) || circuit < 0 || circuit >= Circuit.values().length) return;
        Vec3 at = Layout.marker("breaker").center();
        if (p.position().distanceToSqr(at) > 7.0 * 7.0) return;
        Circuit c = Circuit.values()[circuit];
        switchCircuit(level, c, on, p);
        GameManager.send(p, new S2C.Breaker(mask(level)));
    }

    /** Switches a circuit with the big clunk (the player, the manager, a trip). */
    public static void switchCircuit(ServerLevel level, Circuit c, boolean on, @javax.annotation.Nullable ServerPlayer by) {
        if (Fixtures.circuitOn(level, c) == on) return;
        Fixtures.setCircuit(level, c, on);
        Vec3 at = Layout.marker("breaker").center().add(0, 1, 0);
        level.playSound(null, at.x, at.y, at.z, on ? ModSounds.FIXTURE_BREAKER_ON.get() : ModSounds.FIXTURE_BREAKER_OFF.get(),
                SoundSource.BLOCKS, 1.0f, 1.0f);
        NoiseSystem.emit(level, at, 10.0f, "breaker", by);
        // the world may not report switches it did not make itself: tell the game's own listener
        GameFixtureListener.circuitSwitched(level, c, on);
    }
}
