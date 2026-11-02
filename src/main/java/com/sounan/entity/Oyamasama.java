package com.sounan.entity;

import com.sounan.game.GameManager;
import com.sounan.registry.ModSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * 御山様: the mountain itself. In the final act it stands far off between the trees, watching; then it hunts, crawling
 * on all fours. The flashlight beam slows it; fire (the flare) drives it away. In the old tunnel it comes from behind.
 */
public class Oyamasama extends SounanMonster {
    public static final int STAND = 0, WALK = 1, CRAWL = 2, REACH = 3, LOOM = 4;
    public static final int MODE_STALK = 0, MODE_HUNT = 1, MODE_TUNNEL = 2;
    private int mode = MODE_STALK;
    private int watched;
    private int fleeing = -1;
    private int stepIn;
    private int groanIn = 60;
    private int maxTicks = 20 * 18;

    public Oyamasama(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setMaxUpStep(1.6f);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return baseAttributes(0.25, 96);
    }

    public void setMode(int mode, int maxTicks) {
        this.mode = mode;
        this.maxTicks = maxTicks;
        stateTicks = 0;
        setAnimPose(mode == MODE_STALK ? STAND : CRAWL);
        if (mode != MODE_STALK) play(ModSounds.OYAMA_ROAR.get(), 2.5f, 0.9f);
    }

    public int mode() {
        return mode;
    }

    @Override
    public boolean hostileNow() {
        return mode != MODE_STALK && fleeing < 0;
    }

    /** Fire: it recoils and leaves. */
    public void repel() {
        if (fleeing >= 0) return;
        fleeing = 0;
        setAnimPose(WALK);
        play(ModSounds.OYAMA_RECOIL.get(), 2.0f, 1.0f);
    }

    @Override
    protected void serverTick(@Nullable ServerPlayer p) {
        if (p == null) return;
        double d = distanceTo(p);
        if (--groanIn <= 0) {
            play(ModSounds.OYAMA_GROAN.get(), 1.6f, 0.85f + random.nextFloat() * 0.2f);
            groanIn = 160 + random.nextInt(200);
        }
        if (fleeing >= 0) {
            fleeing++;
            Vec3 away = position().subtract(p.position()).multiply(1, 0, 1).normalize().scale(10);
            getNavigation().moveTo(getX() + away.x, getY(), getZ() + away.z, mod(0.3));
            if (fleeing > 60) vanish();
            return;
        }
        if (GameManager.flareLit(p) && d < 14.0) {
            repel();
            return;
        }
        switch (mode) {
            case MODE_STALK -> {
                faceTowards(p.position());
                getNavigation().stop();
                setAnimPose(STAND);
                GameManager.reportThreat(p, d < 40 ? 0.35f : 0.2f);
                if (GameManager.looksAt(p, this, 0.97, 80.0)) watched++;
                if (watched == 1) {
                    GameManager.addFear(p, 14.0f);
                    GameManager.event(p, GameManager.EV_STINGER, 4, "");
                }
                if (watched > 60 || d < 24.0 || stateTicks > maxTicks) {
                    setAnimPose(WALK);
                    vanish();
                }
            }
            case MODE_HUNT, MODE_TUNNEL -> {
                GameManager.reportThreat(p, 1.0f);
                boolean lit = GameManager.inBeam(p, this, 30.0);
                double speed = mode == MODE_TUNNEL ? (lit ? 0.27 : 0.34) : (lit ? 0.18 : 0.29);
                setAnimPose(d < 3.5 ? REACH : CRAWL);
                chase(p, speed);
                if (--stepIn <= 0) {
                    play(ModSounds.OYAMA_STEP.get(), 1.8f, 0.8f + random.nextFloat() * 0.2f);
                    if (random.nextInt(3) == 0) play(ModSounds.OYAMA_BRANCH.get(), 1.2f, 1.0f);
                    stepIn = lit ? 9 : 6;
                }
                if (touching(p, 2.0)) {
                    setAnimPose(LOOM);
                    GameManager.beginCapture(p, GameManager.KIND_OYAMASAMA, this);
                    vanish();
                } else if (stateTicks > maxTicks || (mode == MODE_HUNT && d > 70)) {
                    play(ModSounds.OYAMA_ROAR.get(), 2.0f, 0.75f);
                    vanish();
                }
            }
            default -> {
            }
        }
    }
}
