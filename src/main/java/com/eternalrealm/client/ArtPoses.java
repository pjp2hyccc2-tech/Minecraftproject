package com.eternalrealm.client;

import com.eternalrealm.combat.HitFrame;
import com.eternalrealm.combat.WeaponProfile;
import com.eternalrealm.combat.WeaponProfiles;
import com.eternalrealm.item.ArtChoreography;
import com.eternalrealm.item.ArtChoreography.Stance;
import com.eternalrealm.item.ArtChoreography.Strike;
import com.eternalrealm.item.ArtTimeline;
import com.eternalrealm.item.WeaponArt;
import com.eternalrealm.item.WeaponArts;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The poses of the Z-key weapon skills ({@link ArtChoreography}): every stance and every strike has its own arm pose
 * (third person) and weapon motion (first person), played along the art's own hits ({@link ArtTimeline}): into the
 * wind-up stance, one fast eased strike per hit, chambering between them, the finishing stance, back to ready. Values
 * are for a right-handed wielder and mirrored for the left hand. Client thread only.
 */
final class ArtPoses {
    /**
     * Third person: the weapon arm ({@code m*}), the other arm ({@code o*}), the body's turn ({@code by}, positive =
     * weapon shoulder back) and lean ({@code bx}, positive = forward), radians; {@code aim} adds the head's turn to the
     * arms (bows, staves).
     */
    record Body(float mx, float my, float mz, float ox, float oy, float oz, float by, float bx, boolean aim) {
        Body lerp(Body to, float t) {
            return new Body(Mth.lerp(t, mx, to.mx), Mth.lerp(t, my, to.my), Mth.lerp(t, mz, to.mz),
                    Mth.lerp(t, ox, to.ox), Mth.lerp(t, oy, to.oy), Mth.lerp(t, oz, to.oz), Mth.lerp(t, by, to.by),
                    Mth.lerp(t, bx, to.bx), t < 0.5F ? aim : to.aim);
        }

        /** The same pose with the body's turn brought back within half a turn (after a spin). */
        Body wrapped() {
            return new Body(mx, my, mz, ox, oy, oz, (float) Math.toRadians(Mth.wrapDegrees(Math.toDegrees(by))), bx, aim);
        }
    }

    /** First person: translate, then turn Z, Y, X (degrees; with {@code yOuter} the Y turn comes first: spins). */
    record Hand(float tx, float ty, float tz, float rz, float ry, float rx, boolean yOuter) {
        Hand lerp(Hand to, float t) {
            return new Hand(Mth.lerp(t, tx, to.tx), Mth.lerp(t, ty, to.ty), Mth.lerp(t, tz, to.tz),
                    Mth.lerp(t, rz, to.rz), Mth.lerp(t, ry, to.ry), Mth.lerp(t, rx, to.rx), t < 0.5F ? yOuter : to.yOuter);
        }

        Hand wrapped() {
            return new Hand(tx, ty, tz, rz, Mth.wrapDegrees(ry), rx, yOuter);
        }

        void apply(PoseStack pose, int side) {
            pose.translate(side * tx, ty, tz);
            if (yOuter) {
                pose.mulPose(Axis.YP.rotationDegrees(side * ry));
                pose.mulPose(Axis.ZP.rotationDegrees(side * rz));
            } else {
                pose.mulPose(Axis.ZP.rotationDegrees(side * rz));
                pose.mulPose(Axis.YP.rotationDegrees(side * ry));
            }
            pose.mulPose(Axis.XP.rotationDegrees(rx));
        }
    }

    private record Move(Body from3, Body to3, Hand from1, Hand to1) {
    }

    private static final float TWO_PI = (float) (Math.PI * 2.0);
    private static final Body READY3 = b(-0.75F, 0.0F, 0.1F, -0.4F, 0.15F, -0.1F, 0.0F, 0.0F);
    private static final Hand READY1 = h(0, 0, 0, 0, 0, 0);
    /** The blade that waits while the other one strikes (twin blades). */
    private static final Hand WAIT1 = h(0.06F, -0.08F, 0.1F, 12, 0, 0);

    private static final Map<Stance, Body> STANCE3 = new EnumMap<>(Stance.class);
    private static final Map<Stance, Hand> STANCE1 = new EnumMap<>(Stance.class);
    private static final Map<Strike, Move> STRIKES = new EnumMap<>(Strike.class);

    static {
        stance(Stance.READY, READY3, READY1);
        stance(Stance.GUARD_LOW, b(-0.95F, -0.35F, 0.2F, -0.6F, 0.45F, -0.1F, 0.2F, 0.1F), h(-0.1F, -0.15F, 0.05F, 25, 20, 15));
        stance(Stance.HIGH_RAISE, b(-2.9F, -0.15F, 0.1F, -2.75F, 0.45F, -0.1F, 0.0F, -0.2F), h(0.0F, 0.36F, 0.12F, 10, 0, -75));
        stance(Stance.DRAW_BACK, b(-0.5F, 0.55F, 0.45F, -1.45F, 0.35F, 0.0F, 0.5F, 0.0F), h(0.05F, 0.0F, 0.25F, 0, 0, -80));
        stance(Stance.SIDE_LOAD, b(-1.2F, 1.1F, 0.5F, -0.8F, -0.1F, -0.2F, 0.6F, 0.05F), h(0.28F, 0.05F, 0.08F, 65, 45, -20));
        stance(Stance.BLADE_UP, b(-1.9F, -0.45F, -0.25F, -0.5F, 0.2F, -0.1F, 0.0F, 0.0F), h(-0.22F, 0.12F, -0.05F, -10, 0, 0));
        stance(Stance.LOW_FINISH, b(-0.35F, -0.2F, 0.05F, -0.3F, 0.3F, -0.1F, -0.2F, 0.3F), h(-0.05F, -0.28F, -0.25F, 10, 0, 70));
        stance(Stance.EXTENDED, b(-1.6F, -0.1F, 0.0F, -0.35F, -0.35F, -0.5F, -0.35F, 0.15F), h(-0.12F, 0.02F, -0.5F, 0, 0, -80));
        stance(Stance.CROUCH, b(-0.4F, 0.55F, 0.5F, -0.4F, -0.55F, -0.5F, 0.0F, 0.35F), h(0.12F, -0.22F, 0.12F, 35, 30, 25));
        stance(Stance.CROSSED, b(-1.6F, -0.6F, 0.1F, -1.6F, 0.6F, -0.1F, 0.0F, 0.05F), h(-0.28F, 0.08F, -0.05F, -45, 25, -15));
        stance(Stance.TWIN_HIGH, b(-2.8F, 0.25F, 0.25F, -2.8F, -0.25F, -0.25F, 0.0F, -0.15F), h(-0.05F, 0.38F, 0.1F, 15, 0, -70));
        stance(Stance.SPREAD_OUT, b(-1.3F, 0.9F, 0.8F, -1.3F, -0.9F, -0.8F, 0.0F, 0.0F), h(0.3F, 0.05F, -0.1F, 70, 40, -20));
        stance(Stance.COUCHED, b(-1.25F, -0.2F, 0.15F, -1.35F, 0.55F, 0.0F, 0.25F, 0.2F), h(-0.05F, -0.08F, 0.15F, 0, 0, -85));
        stance(Stance.OVERHAND, b(-2.95F, 0.35F, 0.5F, -1.3F, 0.35F, 0.0F, 0.55F, -0.1F), h(0.05F, 0.32F, 0.22F, 0, 0, -80));
        stance(Stance.STAFF_UP, b(-3.0F, -0.05F, 0.25F, -2.4F, 0.4F, -0.35F, 0.0F, -0.15F), h(-0.15F, 0.3F, -0.22F, 0, 0, -35));
        stance(Stance.STAFF_FORWARD, aim(b(-1.7F, -0.15F, 0.0F, -1.45F, 0.55F, 0.0F, 0.1F, 0.0F)), h(-0.12F, 0.12F, -0.25F, 0, 0, -65));
        stance(Stance.STAFF_LOW, b(-0.45F, 0.1F, 0.25F, -0.55F, 0.5F, 0.0F, 0.2F, 0.15F), h(-0.05F, -0.15F, 0.05F, 0, 0, 25));
        stance(Stance.STAFF_PLANTED, b(-0.9F, -0.1F, 0.0F, -0.9F, 0.5F, 0.0F, 0.0F, 0.3F), h(-0.1F, -0.22F, -0.2F, 0, 0, 40));
        stance(Stance.BOW_DRAW, aim(b(-1.57F, -0.1F, 0.0F, -1.57F, 0.5F, 0.0F, 0.0F, 0.0F)), h(-0.1F, 0.0F, 0.12F, -20, 0, 0));
        stance(Stance.BOW_STEADY, aim(b(-1.6F, -0.05F, 0.0F, -1.6F, 0.45F, 0.0F, 0.15F, 0.0F)), h(-0.12F, 0.02F, 0.16F, -14, 0, 0));
        stance(Stance.BOW_SKY, b(-2.6F, -0.1F, 0.0F, -2.6F, 0.5F, 0.0F, 0.0F, -0.3F), h(-0.1F, 0.22F, 0.1F, -20, 0, -35));
        stance(Stance.BOW_LOW, aim(b(-1.2F, -0.1F, 0.0F, -1.25F, 0.5F, 0.0F, 0.2F, 0.3F)), h(-0.12F, -0.12F, 0.1F, -32, 0, 12));

        Body offGuard = b(0, 0, 0, -0.5F, 0.2F, -0.1F, 0, 0);
        strike(Strike.SLASH_RIGHT, with(offGuard, -1.45F, -0.95F, 0.5F, -0.35F, 0.0F), with(offGuard, -1.45F, 0.85F, -0.3F, 0.4F, 0.0F),
                h(0.15F, 0.1F, 0.0F, 50, 35, -25), h(-0.45F, 0.0F, -0.15F, -80, -45, -25));
        strike(Strike.SLASH_LEFT, with(offGuard, -1.5F, 0.95F, 0.45F, 0.4F, 0.0F), with(offGuard, -1.4F, -0.95F, -0.35F, -0.4F, 0.0F),
                h(-0.45F, 0.05F, -0.05F, -75, -40, -25), h(0.25F, 0.0F, -0.15F, 60, 40, -25));
        strike(Strike.WIDE_SWEEP, b(-1.2F, 1.1F, 0.5F, -0.8F, -0.1F, -0.2F, 0.6F, 0.05F), b(-1.25F, -1.2F, -0.3F, -0.6F, -0.6F, -0.5F, -0.7F, 0.15F),
                h(0.32F, 0.0F, 0.05F, 70, 50, -15), h(-0.55F, -0.05F, -0.2F, -95, -60, -15));
        strike(Strike.RISING, with(offGuard, -0.25F, 0.35F, 0.35F, 0.15F, 0.25F), with(offGuard, -3.0F, -0.15F, -0.1F, -0.1F, -0.2F),
                h(0.05F, -0.3F, 0.05F, 20, 10, 55), h(-0.1F, 0.38F, -0.15F, -10, -10, -85));
        strike(Strike.FALLING, b(-2.95F, -0.1F, 0.1F, -2.8F, 0.45F, -0.1F, 0.0F, -0.2F), b(-0.45F, -0.15F, 0.0F, -0.55F, 0.45F, 0.0F, 0.0F, 0.35F),
                h(0.0F, 0.36F, 0.12F, 10, 0, -75), h(0.0F, -0.25F, -0.3F, 10, 0, 70));
        strike(Strike.DIAGONAL_RIGHT, with(offGuard, -2.6F, -0.75F, 0.15F, -0.3F, -0.1F), with(offGuard, -0.6F, 0.75F, -0.2F, 0.35F, 0.2F),
                h(-0.3F, 0.3F, 0.0F, -45, -20, -55), h(0.22F, -0.25F, -0.15F, 65, 30, 35));
        strike(Strike.DIAGONAL_LEFT, with(offGuard, -2.6F, 0.75F, 0.45F, 0.35F, -0.1F), with(offGuard, -0.6F, -0.8F, -0.3F, -0.35F, 0.2F),
                h(0.25F, 0.3F, 0.05F, 60, 30, -55), h(-0.4F, -0.22F, -0.15F, -70, -35, 35));
        strike(Strike.THRUST, b(-0.75F, 0.45F, 0.3F, -1.25F, 0.5F, 0.0F, 0.35F, 0.0F), b(-1.6F, -0.05F, 0.0F, -1.5F, 0.2F, 0.0F, -0.3F, 0.1F),
                h(-0.12F, 0.02F, 0.18F, 0, 0, -80), h(-0.12F, 0.02F, -0.55F, 0, 0, -80));
        strike(Strike.LUNGE, b(-0.5F, 0.55F, 0.45F, -1.45F, 0.35F, 0.0F, 0.5F, 0.0F), b(-1.62F, -0.05F, 0.0F, -0.35F, -0.4F, -0.5F, -0.45F, 0.3F),
                h(-0.05F, -0.02F, 0.28F, 0, 0, -82), h(-0.15F, 0.04F, -0.75F, 0, 0, -82));
        strike(Strike.JAB, b(-1.1F, 0.15F, 0.1F, -1.3F, 0.5F, 0.0F, 0.15F, 0.1F), b(-1.6F, -0.08F, 0.0F, -1.5F, 0.35F, 0.0F, -0.15F, 0.15F),
                h(-0.12F, 0.0F, 0.06F, 0, 0, -80), h(-0.13F, 0.02F, -0.42F, 0, 0, -80));
        strike(Strike.SPIN, b(-1.55F, 0.6F, 0.45F, -1.1F, -0.5F, -0.4F, 0.3F, 0.0F), b(-1.55F, 0.6F, 0.45F, -1.1F, -0.5F, -0.4F, 0.3F - TWO_PI, 0.0F),
                spin(0), spin(360));
        strike(Strike.SLAM, b(-3.0F, -0.05F, 0.05F, -2.95F, 0.4F, -0.05F, 0.0F, -0.3F), b(-0.2F, -0.1F, 0.0F, -0.3F, 0.4F, 0.0F, 0.0F, 0.55F),
                h(0.0F, 0.4F, 0.15F, 10, 0, -80), h(0.0F, -0.32F, -0.35F, 10, 0, 80));
        strike(Strike.STAB_DOWN, b(-2.9F, 0.0F, 0.1F, -2.9F, 0.4F, 0.0F, 0.0F, -0.2F), b(-0.65F, -0.05F, 0.0F, -0.7F, 0.45F, 0.0F, 0.0F, 0.45F),
                h(0.0F, 0.35F, 0.1F, 0, 0, -60), h(-0.05F, -0.3F, -0.35F, 0, 0, 110));
        strike(Strike.TWIN_RIGHT, b(-1.45F, -0.95F, 0.5F, -0.85F, 0.3F, 0.0F, -0.35F, 0.0F), b(-1.45F, 0.85F, -0.3F, -0.85F, 0.3F, 0.0F, 0.35F, 0.0F),
                h(0.15F, 0.1F, 0.0F, 50, 35, -25), h(-0.45F, 0.0F, -0.15F, -80, -45, -25));
        strike(Strike.TWIN_LEFT, b(-0.85F, -0.3F, 0.0F, -1.5F, -0.95F, -0.45F, 0.35F, 0.0F), b(-0.85F, -0.3F, 0.0F, -1.4F, 0.95F, 0.35F, -0.35F, 0.0F),
                h(0.15F, 0.1F, 0.0F, 50, 35, -25), h(-0.45F, 0.0F, -0.15F, -80, -45, -25));
        strike(Strike.TWIN_CROSS, b(-1.7F, -0.7F, 0.1F, -1.7F, 0.7F, -0.1F, 0.0F, 0.1F), b(-1.3F, 1.0F, 0.6F, -1.3F, -1.0F, -0.6F, 0.0F, 0.1F),
                h(-0.3F, 0.1F, -0.05F, -50, 30, -20), h(0.35F, 0.0F, -0.15F, 75, 45, -20));
        strike(Strike.TWIN_SPREAD, b(-1.4F, -0.6F, 0.0F, -1.4F, 0.6F, 0.0F, 0.0F, 0.0F), b(-1.6F, 0.7F, 0.2F, -1.6F, -0.7F, -0.2F, 0.0F, 0.05F),
                h(-0.25F, 0.05F, 0.0F, -40, 20, -30), h(0.3F, 0.05F, -0.25F, 50, 30, -60));
        strike(Strike.TWIN_STAB, b(-0.5F, 0.45F, 0.4F, -0.5F, -0.45F, -0.4F, 0.0F, 0.3F), b(-1.6F, -0.15F, 0.0F, -1.6F, 0.15F, 0.0F, 0.0F, 0.15F),
                h(0.1F, -0.2F, 0.15F, 30, 20, 25), h(-0.15F, 0.0F, -0.6F, 0, 0, -80));
        strike(Strike.TWIN_SPIN, b(-1.4F, 0.9F, 0.7F, -1.4F, -0.9F, -0.7F, 0.0F, 0.0F), b(-1.4F, 0.9F, 0.7F, -1.4F, -0.9F, -0.7F, -TWO_PI, 0.0F),
                spin(0), spin(360));
        strike(Strike.TWIN_SLAM, b(-2.8F, 0.25F, 0.25F, -2.8F, -0.25F, -0.25F, 0.0F, -0.15F), b(-0.35F, -0.2F, 0.0F, -0.35F, 0.2F, 0.0F, 0.0F, 0.5F),
                h(-0.05F, 0.38F, 0.1F, 15, 0, -70), h(0.0F, -0.3F, -0.3F, 10, 0, 75));
        strike(Strike.CHARGE, b(-1.25F, -0.2F, 0.15F, -1.35F, 0.55F, 0.0F, 0.25F, 0.2F), b(-1.55F, -0.1F, 0.0F, -1.5F, 0.45F, 0.0F, -0.2F, 0.45F),
                h(-0.05F, -0.08F, 0.15F, 0, 0, -85), h(-0.15F, 0.0F, -0.7F, 0, 0, -85));
        strike(Strike.VAULT, b(-2.8F, 0.1F, 0.1F, -2.4F, 0.45F, 0.0F, 0.0F, -0.25F), b(-0.7F, -0.1F, 0.0F, -0.9F, 0.5F, 0.0F, 0.0F, 0.5F),
                h(0.0F, 0.35F, 0.1F, 0, 0, -60), h(-0.05F, -0.28F, -0.3F, 0, 0, 100));
        strike(Strike.THROW, b(-2.95F, 0.35F, 0.5F, -1.3F, 0.35F, 0.0F, 0.55F, -0.1F), b(-1.3F, -0.35F, -0.1F, -0.4F, -0.5F, -0.4F, -0.5F, 0.25F),
                h(0.05F, 0.32F, 0.22F, 0, 0, -80), h(-0.2F, 0.05F, -0.8F, 0, 0, -80));
        strike(Strike.CAST_POINT, b(-3.0F, -0.05F, 0.25F, -2.4F, 0.4F, -0.35F, 0.0F, -0.15F), aim(b(-1.65F, -0.1F, 0.0F, -1.2F, 0.5F, 0.0F, 0.0F, 0.1F)),
                h(-0.15F, 0.3F, -0.22F, 0, 0, -35), h(-0.12F, 0.1F, -0.35F, 0, 0, -75));
        strike(Strike.CAST_CIRCLE, aim(b(-1.7F, -0.15F, 0.0F, -1.45F, 0.55F, 0.0F, 0.1F, 0.0F)), aim(b(-1.7F, -0.15F, 0.0F, -1.45F, 0.55F, 0.0F, 0.1F, 0.0F)),
                h(-0.12F, 0.12F, -0.25F, 0, 0, -65), h(-0.12F, 0.12F, -0.25F, 0, 0, -65));
        strike(Strike.CAST_THRUST, b(-0.45F, 0.1F, 0.25F, -0.55F, 0.5F, 0.0F, 0.2F, 0.15F), aim(b(-1.65F, -0.05F, 0.0F, -1.6F, 0.4F, 0.0F, -0.3F, 0.2F)),
                h(-0.05F, -0.15F, 0.05F, 0, 0, 25), h(-0.12F, 0.06F, -0.5F, 0, 0, -75));
        strike(Strike.CAST_SWEEP, b(-1.6F, 0.9F, 0.3F, -1.3F, 0.2F, 0.0F, 0.4F, 0.0F), b(-1.6F, -0.9F, -0.2F, -1.3F, 0.6F, 0.0F, -0.4F, 0.05F),
                h(0.25F, 0.15F, -0.15F, 40, 30, -45), h(-0.4F, 0.15F, -0.2F, -50, -30, -45));
        strike(Strike.CAST_SKYFALL, b(-3.1F, 0.0F, 0.2F, -3.0F, 0.3F, -0.2F, 0.0F, -0.3F), b(-0.9F, -0.1F, 0.0F, -0.95F, 0.45F, 0.0F, 0.0F, 0.35F),
                h(-0.15F, 0.38F, -0.15F, 0, 0, -25), h(-0.1F, -0.22F, -0.25F, 0, 0, 45));
        strike(Strike.CAST_BEAM, aim(b(-1.6F, -0.1F, 0.0F, -1.45F, 0.55F, 0.0F, 0.1F, 0.0F)), aim(b(-1.75F, -0.1F, 0.0F, -1.55F, 0.5F, 0.0F, 0.0F, -0.05F)),
                h(-0.12F, 0.12F, -0.25F, 0, 0, -65), h(-0.12F, 0.1F, -0.18F, 0, 0, -60));
        strike(Strike.LOOSE, aim(b(-1.57F, -0.1F, 0.0F, -1.57F, 0.5F, 0.0F, 0.0F, 0.0F)), aim(b(-1.45F, 0.15F, 0.1F, -1.6F, 0.45F, 0.0F, 0.0F, 0.0F)),
                h(-0.1F, 0.0F, 0.12F, -20, 0, 0), h(-0.1F, 0.0F, -0.05F, -20, 0, 0));
        strike(Strike.LOOSE_HEAVY, aim(b(-1.6F, -0.15F, 0.0F, -1.6F, 0.5F, 0.0F, 0.15F, 0.0F)), aim(b(-1.2F, 0.45F, 0.3F, -1.75F, 0.4F, 0.0F, 0.3F, -0.1F)),
                h(-0.12F, 0.02F, 0.18F, -14, 0, 0), h(-0.08F, 0.04F, -0.02F, -26, 0, -8));
        strike(Strike.LOOSE_SWEEP, aim(b(-1.57F, -0.1F, 0.0F, -1.57F, 0.5F, 0.0F, 0.45F, 0.0F)), aim(b(-1.57F, -0.1F, 0.0F, -1.57F, 0.5F, 0.0F, -0.45F, 0.0F)),
                h(0.15F, 0.0F, 0.1F, -20, 25, 0), h(-0.3F, 0.0F, 0.05F, -20, -25, 0));
        strike(Strike.LOOSE_SKY, b(-2.6F, -0.1F, 0.0F, -2.6F, 0.5F, 0.0F, 0.0F, -0.3F), b(-2.45F, 0.2F, 0.15F, -2.7F, 0.45F, 0.0F, 0.0F, -0.35F),
                h(-0.1F, 0.22F, 0.1F, -20, 0, -35), h(-0.1F, 0.25F, -0.05F, -20, 0, -45));
    }

    private ArtPoses() {
    }

    // ------------------------------------------------------------------ table helpers

    private static Body b(float mx, float my, float mz, float ox, float oy, float oz, float by, float bx) {
        return new Body(mx, my, mz, ox, oy, oz, by, bx, false);
    }

    private static Body aim(Body body) {
        return new Body(body.mx, body.my, body.mz, body.ox, body.oy, body.oz, body.by, body.bx, true);
    }

    /** {@code base}'s other arm with this weapon arm and body. */
    private static Body with(Body base, float mx, float my, float mz, float by, float bx) {
        return new Body(mx, my, mz, base.ox, base.oy, base.oz, by, bx, false);
    }

    private static Hand h(float tx, float ty, float tz, float rz, float ry, float rx) {
        return new Hand(tx, ty, tz, rz, ry, rx, false);
    }

    private static Hand spin(float degrees) {
        return new Hand(-0.2F, 0.05F, -0.1F, 75, degrees, 0, true);
    }

    private static void stance(Stance stance, Body third, Hand first) {
        STANCE3.put(stance, third);
        STANCE1.put(stance, first);
    }

    private static void strike(Strike strike, Body from3, Body to3, Hand from1, Hand to1) {
        STRIKES.put(strike, new Move(from3, to3, from1, to1));
    }

    // ------------------------------------------------------------------ the timeline

    /**
     * The art an animation plays: its choreography, its hit ticks (ascending) and how far each hit reaches (blocks;
     * launches and effects count as 3).
     */
    record Play(ArtChoreography.Choreo choreo, int[] hits, float[] reaches, int duration) {
        float reach(int hit) {
            return reaches[Math.max(0, Math.min(hit, reaches.length - 1))];
        }
    }

    private static final java.util.Map<String, List<HitFrame>> FRAMES = new java.util.concurrent.ConcurrentHashMap<>();

    static Optional<Play> play(CombatAnimations.Anim anim) {
        if (anim.motion() != CombatAnimations.Motion.ART || anim.actionId() == null) {
            return Optional.empty();
        }
        Optional<ArtChoreography.Choreo> choreo = ArtChoreography.forAction(anim.actionId());
        if (choreo.isEmpty()) {
            return Optional.empty();
        }
        List<HitFrame> frames = frames(anim.actionId());
        if (frames.isEmpty()) {
            return Optional.of(new Play(choreo.get(), new int[]{Math.max(2, anim.duration() / 2)}, new float[]{3.0F},
                    anim.duration()));
        }
        int[] hits = new int[frames.size()];
        float[] reaches = new float[frames.size()];
        for (int i = 0; i < frames.size(); i++) {
            HitFrame frame = frames.get(i);
            hits[i] = frame.offsetTicks();
            reaches[i] = frame.isProjectile() || !frame.damaging() ? 3.0F : (float) frame.shape().reach();
        }
        return Optional.of(new Play(choreo.get(), hits, reaches, anim.duration()));
    }

    /** The hit (or launch) frames of a Z-key action, by their ticks. */
    private static List<HitFrame> frames(String actionId) {
        return FRAMES.computeIfAbsent(actionId, id -> {
            Optional<WeaponArt> art = WeaponArts.byId(id);
            List<HitFrame> frames = art.map(a -> a.action().frames()).orElse(null);
            if (frames == null) {
                for (WeaponProfile profile : WeaponProfiles.all()) {
                    if (profile.skill().id().equals(id)) {
                        frames = profile.skill().frames();
                    }
                }
            }
            if (frames == null) {
                return List.of();
            }
            return frames.stream().sorted(java.util.Comparator.comparingInt(HitFrame::offsetTicks)).toList();
        });
    }

    private static Move move(Strike strike) {
        return STRIKES.get(strike);
    }

    /** The third-person pose at {@code elapsed} ticks into the art. */
    static Body body(Play play, float elapsed) {
        ArtTimeline.Moment at = ArtTimeline.at(play.hits(), play.duration(), elapsed);
        ArtChoreography.Choreo choreo = play.choreo();
        Body windup = STANCE3.get(choreo.windup());
        Body finish = STANCE3.get(choreo.finish());
        return switch (at.phase()) {
            case WINDUP -> windup(READY3, windup, move(choreo.strike(0)).from3(), at.progress(), choreo.heavy(),
                    elapsed);
            case CHAMBER -> move(choreo.strike(at.hit() - 1)).to3().wrapped()
                    .lerp(move(choreo.strike(at.hit())).from3(), smooth(at.progress()));
            case STRIKE -> strike3(choreo.strike(at.hit()), at.progress());
            case FINISH -> move(choreo.strike(at.hit())).to3().wrapped().lerp(finish, smooth(at.progress()));
            case RECOVER -> finish.lerp(READY3, smooth(at.progress()));
        };
    }

    /**
     * The first-person transform of the weapon hand ({@code offhand}: the second of the twin blades) at
     * {@code elapsed} ticks into the art.
     */
    static Hand hand(Play play, float elapsed, boolean offhand) {
        ArtTimeline.Moment at = ArtTimeline.at(play.hits(), play.duration(), elapsed);
        ArtChoreography.Choreo choreo = play.choreo();
        Hand windup = STANCE1.get(choreo.windup());
        Hand finish = STANCE1.get(choreo.finish());
        return switch (at.phase()) {
            case WINDUP -> {
                Hand from = start1(choreo.strike(0), offhand);
                float p = at.progress();
                yield p < 0.55F ? READY1.lerp(windup, easeOut(p / 0.55F))
                        : p < 0.8F ? tremble(windup, choreo.heavy(), elapsed)
                        : windup.lerp(from, smooth((p - 0.8F) / 0.2F));
            }
            case CHAMBER -> end1(choreo.strike(at.hit() - 1), offhand).wrapped()
                    .lerp(start1(choreo.strike(at.hit()), offhand), smooth(at.progress()));
            case STRIKE -> strike1(choreo.strike(at.hit()), at.progress(), offhand);
            case FINISH -> end1(choreo.strike(at.hit()), offhand).wrapped().lerp(finish, smooth(at.progress()));
            case RECOVER -> finish.lerp(READY1, smooth(at.progress()));
        };
    }

    private static Body windup(Body ready, Body stance, Body first, float p, boolean heavy, float elapsed) {
        if (p < 0.55F) {
            return ready.lerp(stance, easeOut(p / 0.55F));
        }
        if (p < 0.8F) {
            if (!heavy) {
                return stance;
            }
            float shake = Mth.sin(elapsed * 2.6F) * 0.03F;
            return new Body(stance.mx + shake, stance.my, stance.mz, stance.ox + shake, stance.oy, stance.oz, stance.by,
                    stance.bx, stance.aim);
        }
        return stance.lerp(first, smooth((p - 0.8F) / 0.2F));
    }

    private static Hand tremble(Hand hand, boolean heavy, float elapsed) {
        if (!heavy) {
            return hand;
        }
        float shake = Mth.sin(elapsed * 2.6F) * 1.2F;
        return new Hand(hand.tx, hand.ty, hand.tz, hand.rz + shake, hand.ry, hand.rx + shake, hand.yOuter);
    }

    private static Body strike3(Strike strike, float p) {
        Move move = move(strike);
        if (strike == Strike.CAST_CIRCLE) {
            double angle = Math.PI * 2.0 * easeOut(p);
            Body base = move.from3();
            return new Body(base.mx + (float) Math.sin(angle) * 0.35F, base.my + (float) Math.cos(angle) * 0.4F - 0.4F,
                    base.mz, base.ox, base.oy, base.oz, base.by, base.bx, base.aim);
        }
        return move.from3().lerp(move.to3(), easeOut(p));
    }

    private static Hand strike1(Strike strike, float p, boolean offhand) {
        if (isWaiting(strike, offhand)) {
            return WAIT1;
        }
        Move move = move(own(strike, offhand));
        if (strike == Strike.CAST_CIRCLE) {
            double angle = Math.PI * 2.0 * easeOut(p);
            Hand base = move.from1();
            return new Hand(base.tx + (float) Math.cos(angle) * 0.12F - 0.12F, base.ty + (float) Math.sin(angle) * 0.12F,
                    base.tz, base.rz, base.ry, base.rx, false);
        }
        return move.from1().lerp(move.to1(), easeOut(p));
    }

    private static Hand start1(Strike strike, boolean offhand) {
        return isWaiting(strike, offhand) ? WAIT1 : move(own(strike, offhand)).from1();
    }

    private static Hand end1(Strike strike, boolean offhand) {
        return isWaiting(strike, offhand) ? WAIT1 : move(own(strike, offhand)).to1();
    }

    /** Twin blades: the main blade waits while the off blade cuts (and the other way round). */
    private static boolean isWaiting(Strike strike, boolean offhand) {
        return offhand ? strike == Strike.TWIN_RIGHT : strike == Strike.TWIN_LEFT;
    }

    /** The off blade's cut is drawn like the main blade's on its own side. */
    private static Strike own(Strike strike, boolean offhand) {
        return offhand && strike == Strike.TWIN_LEFT ? Strike.TWIN_RIGHT : strike;
    }

    // ------------------------------------------------------------------ applying

    /**
     * Poses the model's arms (and turns the body, carrying the arms with it) for a right ({@code side} 1) or left
     * ({@code side} -1) handed wielder. The lean ({@code bx}) is not applied to the body: the legs would come apart
     * from it; the arms carry the motion.
     */
    static void pose(HumanoidModel<?> model, ModelPart main, ModelPart other, float side, Body body) {
        float aimX = body.aim ? model.head.xRot : 0.0F;
        float aimY = body.aim ? model.head.yRot : 0.0F;
        float turn = side * body.by;
        model.body.yRot = turn;
        main.xRot = body.mx + aimX;
        main.yRot = side * body.my + aimY + turn;
        main.zRot = side * body.mz;
        other.xRot = body.ox + aimX;
        other.yRot = side * body.oy + aimY + turn;
        other.zRot = side * body.oz;
        // the shoulders turn with the body (as in vanilla's attack swing)
        model.rightArm.z = Mth.sin(turn) * 5.0F;
        model.rightArm.x = -Mth.cos(turn) * 5.0F;
        model.leftArm.z = -Mth.sin(turn) * 5.0F;
        model.leftArm.x = Mth.cos(turn) * 5.0F;
    }

    // ------------------------------------------------------------------ curves

    static float easeOut(float t) {
        float u = 1.0F - Mth.clamp(t, 0.0F, 1.0F);
        return 1.0F - u * u * u;
    }

    static float smooth(float t) {
        float u = Mth.clamp(t, 0.0F, 1.0F);
        return u * u * (3.0F - 2.0F * u);
    }
}
