package com.huntcraft.entity.monster.ai;

import com.huntcraft.entity.monster.HuntMonster;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;

/**
 * Move control with a limited turn rate: big monsters turn in place before running, which gives
 * them their heavy MH feel. Small monsters use a high turn speed and behave almost like vanilla.
 */
public class HeavyMoveControl extends MoveControl {
    private final HuntMonster monster;

    public HeavyMoveControl(HuntMonster monster) {
        super(monster);
        this.monster = monster;
    }

    @Override
    public void tick() {
        if (monster.isLocked() || (monster.isActing() && monster.getCurrentAttack() != null)) {
            // attacks drive movement themselves
            if (this.operation == Operation.MOVE_TO) this.operation = Operation.WAIT;
            monster.setZza(0);
            return;
        }
        if (this.operation == Operation.MOVE_TO) {
            this.operation = Operation.WAIT;
            double dx = this.wantedX - monster.getX();
            double dz = this.wantedZ - monster.getZ();
            double dy = this.wantedY - monster.getY();
            double distSq = dx * dx + dz * dz + dy * dy;
            if (distSq < 2.5E-7) {
                monster.setZza(0);
                return;
            }
            float targetYaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
            float turn = monster.getTurnSpeed() * (monster.isEnraged() ? 1.3F : 1.0F);
            float newYaw = Mth.approachDegrees(monster.getYRot(), targetYaw, turn);
            monster.setYRot(newYaw);
            monster.yBodyRot = newYaw;
            float diff = Math.abs(Mth.wrapDegrees(targetYaw - newYaw));
            float speed = (float) (this.speedModifier * monster.getAttributeValue(Attributes.MOVEMENT_SPEED));
            if (monster.isEnraged()) speed *= 1.2F;
            if (monster.isTired()) speed *= 0.7F;
            if (monster.isLimping()) speed *= 0.6F;
            // slow down while turning sharply (turn in place)
            if (diff > 70) speed *= 0.15F;
            else if (diff > 35) speed *= 0.5F;
            monster.setSpeed(speed);
            if (monster.horizontalCollision && monster.onGround() && !monster.isFlying()) {
                monster.getJumpControl().jump();
            }
        } else if (this.operation == Operation.JUMPING) {
            monster.setSpeed((float) (this.speedModifier * monster.getAttributeValue(Attributes.MOVEMENT_SPEED)));
            if (monster.onGround()) this.operation = Operation.WAIT;
        } else {
            monster.setZza(0);
        }
    }
}
