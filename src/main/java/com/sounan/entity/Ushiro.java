package com.sounan.entity;

import com.sounan.game.GameManager;
import com.sounan.registry.ModSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * 後ろ: it walks right behind you, bent over your shoulder. Footsteps that fall a moment after yours, breath at your
 * neck, 「ふりかえって」. Look back and it is face to face with you. Keep walking until it loses interest; stand still
 * too long and it comes closer. The bear bell, a flare or a resting place make it let go.
 */
public class Ushiro extends SounanMonster {
    public static final int LEAN = 0, GRAB = 1;
    private Vec3 moveDir;
    private double walked;
    private int still;
    private int duration = 20 * 26;
    private int grab = -1;
    private int nextBreath = 60;
    private int nextWhisper = 140;

    public Ushiro(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return baseAttributes(0.25, 32);
    }

    public void attach(ServerPlayer p) {
        setTarget(p);
        moveDir = Vec3.directionFromRotation(0.0f, p.getYRot()).normalize();
        duration = 20 * (22 + random.nextInt(14));
        placeBehind(p);
    }

    @Override
    public boolean hostileNow() {
        return grab >= 0;
    }

    /** Lets go (bell, flare, a resting place, or simply bored). */
    public void letGo(boolean bored) {
        ServerPlayer p = target();
        play(ModSounds.USHIRO_LEAVE.get(), 0.9f, 1.0f);
        if (p != null && bored) GameManager.thought(p, "thought.sounan.ushiro_gone");
        vanish();
    }

    private void placeBehind(ServerPlayer p) {
        Vec3 pos = p.position().subtract(moveDir.scale(1.35));
        moveTo(pos.x, p.getY(), pos.z, p.getYRot(), 20.0f);
        setYHeadRot(p.getYRot());
        yBodyRot = p.getYRot();
        setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    protected void serverTick(@Nullable ServerPlayer p) {
        if (p == null) return;
        if (moveDir == null) attach(p);
        if (grab >= 0) {
            // face to face
            grab++;
            setAnimPose(GRAB);
            Vec3 front = p.getEyePosition().add(p.getViewVector(1.0f).multiply(1, 0, 1).normalize().scale(0.9));
            moveTo(front.x, p.getY() - 0.15, front.z, p.getYRot() + 180.0f, 0.0f);
            setYHeadRot(p.getYRot() + 180.0f);
            yBodyRot = p.getYRot() + 180.0f;
            GameManager.reportThreat(p, 1.0f);
            if (grab == 2) play(ModSounds.USHIRO_ATTACK.get(), 1.6f, 1.0f);
            if (grab >= 9) {
                GameManager.beginCapture(p, GameManager.KIND_USHIRO, this);
                vanish();
            }
            return;
        }
        setAnimPose(LEAN);
        Vec3 vel = p.getDeltaMovement().multiply(1, 0, 1);
        double speed = vel.length();
        if (speed > 0.03) {
            moveDir = moveDir.lerp(vel.normalize(), 0.12).normalize();
            walked += speed;
            still = 0;
        } else {
            still++;
        }
        placeBehind(p);
        GameManager.reportThreat(p, 0.5f + Math.min(0.3f, still / 400.0f));
        // its steps fall a moment after yours
        if (walked > 1.7) {
            walked = 0;
            play(ModSounds.USHIRO_STEP.get(), 0.55f, 0.92f + random.nextFloat() * 0.12f);
        }
        if (--nextBreath <= 0) {
            play(ModSounds.USHIRO_BREATH.get(), 0.45f + Math.min(0.5f, still / 200.0f), 1.0f);
            nextBreath = (still > 80 ? 40 : 80) + random.nextInt(60);
        }
        if (--nextWhisper <= 0) {
            play(ModSounds.USHIRO_WHISPER.get(), 0.5f, 1.0f);
            nextWhisper = 180 + random.nextInt(140);
        }
        // did you look back?
        Vec3 to = getEyePosition().subtract(p.getEyePosition());
        double dist = to.length();
        double dot = dist > 0.01 ? to.scale(1.0 / dist).dot(p.getViewVector(1.0f)) : 0.0;
        if (dot > 0.35 && dist < 4.0) {
            grab = 0;
            GameManager.event(p, GameManager.EV_STINGER, 10, "");
            return;
        }
        if (still > 20 * 14) {
            grab = 0;
            return;
        }
        if (stateTicks > duration) letGo(true);
        if (Mth.abs((float) (p.getY() - getY())) > 4.0f) placeBehind(p);
    }
}
