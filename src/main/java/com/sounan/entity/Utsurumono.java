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
 * 写るもの: the lost. Only a camera sees them — standing between the trees, hood up, each one pointing the same way:
 * the way out. Come close and they are not there. Photograph one with the flash and it looks at you.
 */
public class Utsurumono extends SounanMonster {
    public static final int POINT = 0, STAND = 1, LOOK = 2;
    public float facingYaw;

    public Utsurumono(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return baseAttributes(0.25, 16);
    }

    public void pointAt(float yaw) {
        facingYaw = yaw;
        setYRot(yaw);
        setYHeadRot(yaw);
        yBodyRot = yaw;
        setAnimPose(POINT);
    }

    /** A flash photo: it turns its head toward the camera. */
    public void photographed(ServerPlayer p) {
        if (pose() == LOOK) return;
        setAnimPose(LOOK);
        stateTicks = 0;
        play(ModSounds.UTSURU_TURN.get(), 0.8f, 1.0f);
        GameManager.addFear(p, 12.0f);
    }

    @Override
    protected void serverTick(@Nullable ServerPlayer p) {
        getNavigation().stop();
        if (p == null) return;
        if (pose() == LOOK) {
            getLookControl().setLookAt(p, 30.0f, 30.0f);
            if (stateTicks > 20 * 12) vanish();
        } else {
            setYRot(facingYaw);
            yBodyRot = facingYaw;
            setYHeadRot(facingYaw);
        }
        if (distanceToSqr(p) < 6.0 * 6.0 || life > 20 * 120) vanish();
    }
}
