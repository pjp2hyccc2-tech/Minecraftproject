package com.sounan.entity;

import com.sounan.game.GameManager;
import com.sounan.registry.ModSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * 覗き: peeks from behind a cedar trunk, head tilted out past the edge. A glance is allowed; staring at it, keeping the
 * flashlight on it, walking up to it or a flash photo make it shriek and come for you on all fours. Look away and keep
 * your distance and it slips back behind the tree — and sometimes it is waiting one tree closer.
 */
public class Nozoki extends SounanMonster {
    public static final int PEEK_LEFT = 0, PEEK_RIGHT = 1, STAND = 2, CRAWL = 3, RETREAT = 4;
    private static final int S_PEEK = 0, S_RETREAT = 1, S_WINDUP = 2, S_ATTACK = 3, S_DASH = 4;
    private int state = S_PEEK;
    private int stare;
    private int lit;
    private int unseen;
    private boolean noticed;
    public int relocations;
    private int lostSight;
    private net.minecraft.world.phys.Vec3 dashTo;

    public Nozoki(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return baseAttributes(0.25, 48);
    }

    public void peek(boolean left) {
        setAnimPose(left ? PEEK_LEFT : PEEK_RIGHT);
        state = S_PEEK;
        stateTicks = 0;
    }

    @Override
    public boolean hostileNow() {
        return state == S_WINDUP || state == S_ATTACK;
    }

    /** Something running past between the trees, on all fours, gone before you can light it. */
    public void dash(net.minecraft.world.phys.Vec3 to) {
        state = S_DASH;
        stateTicks = 0;
        dashTo = to;
        setAnimPose(CRAWL);
    }

    /** Something made it angry (flash photo). */
    public void provoke() {
        if (state == S_PEEK || state == S_RETREAT) {
            state = S_WINDUP;
            stateTicks = 0;
            setAnimPose(STAND);
            play(ModSounds.NOZOKI_SHRIEK.get(), 1.5f, 0.9f + random.nextFloat() * 0.15f);
            ServerPlayer p = target();
            if (p != null) {
                GameManager.event(p, GameManager.EV_STINGER, 6, "");
                GameManager.addFear(p, 22.0f);
            }
        }
    }

    /** The bear bell: it hates the sound. */
    public void repel() {
        if (state == S_ATTACK || state == S_WINDUP) play(ModSounds.NOZOKI_CREAK.get(), 1.0f, 0.7f);
        state = S_RETREAT;
        stateTicks = 0;
        setAnimPose(RETREAT);
        getNavigation().stop();
    }

    @Override
    protected void serverTick(@Nullable ServerPlayer p) {
        if (p == null) return;
        double d = distanceTo(p);
        switch (state) {
            case S_PEEK -> {
                faceTowards(p.position());
                getNavigation().stop();
                boolean looking = GameManager.looksAt(p, this, 0.955, 40.0);
                boolean beam = GameManager.inBeam(p, this, 26.0);
                if (looking) {
                    stare++;
                    unseen = 0;
                    if (!noticed) {
                        noticed = true;
                        play(ModSounds.NOZOKI_CREAK.get(), 0.6f, 1.0f);
                        GameManager.addFear(p, 10.0f);
                        GameManager.thought(p, "thought.sounan.watched");
                    }
                } else {
                    stare = Math.max(0, stare - 2);
                    unseen++;
                }
                if (beam) lit++;
                else lit = Math.max(0, lit - 1);
                if (random.nextInt(140) == 0) play(ModSounds.NOZOKI_BREATH.get(), 0.5f, 1.0f);
                if (noticed && random.nextInt(260) == 0) play(ModSounds.NOZOKI_GIGGLE.get(), 0.7f, 1.0f);
                GameManager.reportThreat(p, d < 22 ? 0.3f + (noticed ? 0.1f : 0.0f) : 0.12f);
                if (stare > 45 || lit > 24 || d < 7.0) {
                    provoke();
                } else if (noticed && unseen > 60) {
                    state = S_RETREAT;
                    stateTicks = 0;
                    setAnimPose(RETREAT);
                } else if (stateTicks > 20 * 50) {
                    state = S_RETREAT;
                    stateTicks = 0;
                    setAnimPose(RETREAT);
                }
            }
            case S_DASH -> {
                if (dashTo == null || stateTicks > 50 || distanceToSqr(dashTo) < 2.0) {
                    vanish();
                    return;
                }
                getMoveControl().setWantedPosition(dashTo.x, dashTo.y, dashTo.z, mod(0.42));
                if (tickCount % 5 == 0) play(ModSounds.NOZOKI_CRAWL.get(), 0.9f, 1.1f);
                GameManager.reportThreat(p, 0.4f);
            }
            case S_RETREAT -> {
                faceTowards(p.position());
                if (stateTicks > 30) vanish();
            }
            case S_WINDUP -> {
                faceTowards(p.position());
                GameManager.reportThreat(p, 0.85f);
                if (stateTicks > 16) {
                    state = S_ATTACK;
                    stateTicks = 0;
                    setAnimPose(CRAWL);
                }
            }
            case S_ATTACK -> {
                GameManager.reportThreat(p, 1.0f);
                chase(p, 0.32);
                if (tickCount % 6 == 0) play(ModSounds.NOZOKI_CRAWL.get(), 1.0f, 0.9f + random.nextFloat() * 0.2f);
                if (!hasLineOfSight(p)) lostSight++;
                else lostSight = 0;
                if (touching(p, 1.3)) {
                    if (GameManager.beginCapture(p, GameManager.KIND_NOZOKI, this)) vanish();
                    else repel();
                } else if (stateTicks > 20 * 8 || lostSight > 60 || d > 40) {
                    repel();
                }
            }
            default -> {
            }
        }
    }
}
