package com.sounan.entity;

import com.sounan.game.GameManager;
import com.sounan.registry.ModSounds;
import com.sounan.world.TerrainData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * 呼び声: far away between the trees a headlamp, an orange rain jacket, an arm waving — 「おーい」. It looks like rescue.
 * Walk toward it and the lamp goes out; a moment of silence; then it is beside you. Keep away and, after a while, it
 * calls one last time in a voice that is no longer a man's, and is gone.
 */
public class Yobigoe extends SounanMonster {
    public static final int CALL = 0, IDLE = 1, TURN = 2, RUSH = 3;
    private static final int S_CALL = 0, S_DARK = 1, S_RUSH = 2, S_LEAVE = 3;
    /** Ticks it stands there twisted round at you before the lamp goes out (the model's turn takes 18). */
    private static final int TURN_TICKS = 26;
    private int state = S_CALL;
    private int nextCall = 20;
    private double startDist = -1;
    private int calls;

    public Yobigoe(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return baseAttributes(0.25, 64);
    }

    @Override
    protected double cullMargin() {
        return 4.0; // the headlamp's beam reaches far in front of it
    }

    @Override
    public boolean hostileNow() {
        return state == S_RUSH;
    }

    public void repel() {
        if (state == S_RUSH) play(ModSounds.YOBIGOE_LAUGH.get(), 1.0f, 0.8f);
        vanish();
    }

    @Override
    protected void serverTick(@Nullable ServerPlayer p) {
        if (p == null) return;
        double d = distanceTo(p);
        if (startDist < 0) startDist = d;
        switch (state) {
            case S_CALL -> {
                faceTowards(p.position());
                getNavigation().stop();
                setAnimPose(calls % 3 == 2 ? IDLE : CALL);
                if (--nextCall <= 0) {
                    calls++;
                    play(ModSounds.YOBIGOE_CALL.get(), 1.0f, 0.96f + random.nextFloat() * 0.08f);
                    nextCall = 110 + random.nextInt(70);
                    if (calls == 1) GameManager.thought(p, "thought.sounan.light");
                }
                GameManager.reportThreat(p, d < 24 ? 0.35f : 0.15f);
                // walking toward the light is the mistake
                if (d < 16.0 || (startDist - d > 12.0 && d < 26.0)) {
                    // it stops waving; the body turns away and the head stays on you
                    state = S_DARK;
                    stateTicks = 0;
                    setAnimPose(TURN);
                } else if (calls >= 5 && nextCall < 40) {
                    state = S_LEAVE;
                    stateTicks = 0;
                    play(ModSounds.YOBIGOE_CALL_WRONG.get(), 1.0f, 0.85f);
                }
            }
            case S_DARK -> {
                GameManager.reportThreat(p, 0.6f);
                if (stateTicks < TURN_TICKS) {
                    faceTowards(p.position());
                } else if (stateTicks == TURN_TICKS) {
                    // then the lamp clicks off, and everything goes quiet
                    play(ModSounds.ITEM_FLASHLIGHT_OFF.get(), 1.2f, 0.7f);
                    setInvisible(true);
                    GameManager.event(p, GameManager.EV_SILENCE, 0, "");
                }
                if (stateTicks == TURN_TICKS + 30) {
                    // it is beside you
                    Vec3 look = p.getViewVector(1.0f);
                    float side = random.nextBoolean() ? 1.0f : -1.0f;
                    double ang = Math.atan2(look.z, look.x) + side * (Math.PI * 0.6);
                    double x = p.getX() + Math.cos(ang) * 3.0, z = p.getZ() + Math.sin(ang) * 3.0;
                    int y = TerrainData.get().height(Mth.floor(x), Mth.floor(z)) + 1;
                    if (Math.abs(y - p.getY()) > 3) y = (int) p.getY();
                    teleportTo(x, y, z);
                    setInvisible(false);
                    setAnimPose(RUSH);
                    play(ModSounds.YOBIGOE_RUSH.get(), 2.0f, 1.0f);
                    GameManager.event(p, GameManager.EV_STINGER, 8, "");
                    GameManager.addFear(p, 28.0f);
                    state = S_RUSH;
                    stateTicks = 0;
                }
            }
            case S_RUSH -> {
                GameManager.reportThreat(p, 1.0f);
                if (stateTicks > 8) chase(p, 0.34);
                else faceTowards(p.position());
                if (touching(p, 1.4)) {
                    if (GameManager.beginCapture(p, GameManager.KIND_YOBIGOE, this)) vanish();
                    else vanish();
                } else if (stateTicks > 20 * 6 || d > 30) {
                    play(ModSounds.YOBIGOE_LAUGH.get(), 1.4f, 0.9f);
                    vanish();
                }
            }
            case S_LEAVE -> {
                setAnimPose(TURN);
                if (stateTicks > 60) vanish();
            }
            default -> {
            }
        }
    }
}
