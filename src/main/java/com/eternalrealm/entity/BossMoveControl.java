package com.eternalrealm.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;

/**
 * Movement of a giant: the body turns at most {@code maxTurn} degrees per tick towards where it walks (vanilla turns
 * up to 90), and slows down while it turns hard, so a ten-block colossus swings round with its weight instead of
 * sliding sideways.
 */
final class BossMoveControl extends MoveControl {
    private final float maxTurn;

    BossMoveControl(Mob mob, float maxTurn) {
        super(mob);
        this.maxTurn = maxTurn;
    }

    @Override
    public void tick() {
        float before = this.mob.getYRot();
        super.tick();
        float turn = Mth.wrapDegrees(this.mob.getYRot() - before);
        if (Math.abs(turn) <= this.maxTurn) {
            return;
        }
        float yaw = before + Math.copySign(this.maxTurn, turn);
        this.mob.setYRot(yaw);
        this.mob.setYBodyRot(yaw);
        // walking on while still facing away: slow steps until the body points the right way
        float slow = Mth.clamp(1.0F - (Math.abs(turn) - this.maxTurn) / 90.0F, 0.25F, 1.0F);
        this.mob.setSpeed(this.mob.getSpeed() * slow);
    }
}
