package com.reisatsu.game;

import com.reisatsu.entity.Spirit;
import com.reisatsu.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Hiding in the staff lockers (docs/DESIGN.md §3.6): you step in and look out through the slits. A spirit that did not
 * see you go in cannot find you there. Moving the camera too fast rattles the door (noise 4). Sneak to step out.
 * The player rides an invisible marker inside the locker (so nothing pushes them out of the block); the same seat
 * is used for the 相席 ending at table 7.
 */
public final class Hiding {
    public static final String SEAT_TAG = "reisatsu_seat";

    private Hiding() {}

    public static boolean enter(ServerPlayer p, BlockPos locker) {
        PlayerState ps = GameManager.state(p);
        if (ps.hiding() || ps.caught() || ps.locked || ps.introTicks >= 0 || !GameManager.inGame(p)) return false;
        ServerLevel level = p.serverLevel();
        BlockPos lower = locker;
        if (level.getBlockState(locker.below()).getBlock() == level.getBlockState(locker).getBlock()) lower = locker.below();
        Direction facing = facingOf(level.getBlockState(lower), p);
        // whoever sees you now saw you climb in
        for (Spirit s : Spirits.all(level)) s.markSawHide(p, Stealth.sees(s, p) || (s.hunting() && s.huntTarget() == p && Stealth.lineOfSight(s, p)));
        GameManager.lowerCamera(p);
        ps.hiddenAt = lower;
        ps.hiddenFacing = facing;
        Vec3 c = Vec3.atBottomCenterOf(lower);
        Entity seat = seat(level, c, facing.toYRot());
        GameManager.teleport(p, level, c, facing.toYRot(), 0.0f);
        if (seat != null) p.startRiding(seat, true);
        p.setYRot(facing.toYRot());
        p.setYHeadRot(facing.toYRot());
        ps.lastYaw = facing.toYRot();
        ps.lastPitch = 0;
        // the locker's door sound, noise and flap are the world's (Behaviours.locker)
        GameManager.event(p, GameManager.EV_LOCKER, 1 + facing.get2DDataValue(), "");
        return true;
    }

    /** Steps out in front of the locker; sound = the game's own exits (sneaking out, being taken), not the world's. */
    public static void exit(ServerPlayer p, boolean sound) {
        PlayerState ps = GameManager.state(p);
        if (ps.hiddenAt == null) return;
        BlockPos at = ps.hiddenAt;
        Direction f = ps.hiddenFacing;
        ps.hiddenAt = null;
        Entity v = p.getVehicle();
        p.stopRiding();
        if (v != null && v.getTags().contains(SEAT_TAG)) v.discard();
        Vec3 out = Vec3.atBottomCenterOf(at.relative(f));
        GameManager.teleport(p, p.serverLevel(), out, p.getYRot(), p.getXRot());
        if (sound && p.level() instanceof ServerLevel level) {
            level.playSound(null, out.x, out.y + 1, out.z, ModSounds.FIXTURE_LOCKER.get(), SoundSource.BLOCKS, p.isShiftKeyDown() ? 0.5f : 0.9f, 0.95f);
            NoiseSystem.emit(level, out.add(0, 1, 0), p.isShiftKeyDown() ? 3.0f : 6.0f, "locker", p);
            if (level.getBlockState(at).hasProperty(com.reisatsu.block.Props.OPEN)) com.reisatsu.world.Fixtures.flapLocker(level, at);
        }
        if (p.level() instanceof ServerLevel level) for (Spirit s : Spirits.all(level)) s.markSawHide(p, false);
        GameManager.event(p, GameManager.EV_LOCKER, 0, "");
    }

    static void tick(ServerLevel level, ServerPlayer p, PlayerState ps) {
        if (ps.hiddenAt == null) return;
        if (!p.isPassenger()) {
            // sneaked out (dismounted)
            exit(p, true);
            return;
        }
        if (ps.rattleCooldown > 0) ps.rattleCooldown--;
        float dy = Math.abs(Mth.wrapDegrees(p.getYRot() - ps.lastYaw)), dp = Math.abs(p.getXRot() - ps.lastPitch);
        ps.lastYaw = p.getYRot();
        ps.lastPitch = p.getXRot();
        if (dy + dp > 24.0f && ps.rattleCooldown == 0) {
            ps.rattleCooldown = 12;
            Vec3 c = Vec3.atCenterOf(ps.hiddenAt);
            level.playSound(null, c.x, c.y + 0.5, c.z, ModSounds.FIXTURE_LOCKER.get(), SoundSource.BLOCKS, 0.35f, 1.35f);
            NoiseSystem.emit(level, c, 4.0f, "rattle", p);
        }
    }

    private static Direction facingOf(BlockState s, ServerPlayer p) {
        if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return s.getValue(BlockStateProperties.HORIZONTAL_FACING);
        return p.getDirection().getOpposite();
    }

    /** An invisible marker to sit on (lockers, the 相席 ending). */
    @Nullable
    public static Entity seat(ServerLevel level, Vec3 feet, float yaw) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:armor_stand");
        tag.putBoolean("Marker", true);
        tag.putBoolean("Invisible", true);
        tag.putBoolean("NoGravity", true);
        tag.putBoolean("Invulnerable", true);
        tag.putBoolean("Silent", true);
        ListTag tags = new ListTag();
        tags.add(StringTag.valueOf(SEAT_TAG));
        tag.put("Tags", tags);
        // a rider sits 0.35 below the marker; lift it so the eyes stay where a standing player's would be
        Entity e = EntityType.loadEntityRecursive(tag, level, ent -> {
            ent.moveTo(feet.x, feet.y + 0.35, feet.z, yaw, 0.0f);
            return ent;
        });
        if (e == null) return null;
        level.addFreshEntity(e);
        return e;
    }

    /** Removes seats left over from an earlier session. */
    public static void clearSeats(ServerLevel level) {
        for (Entity e : level.getEntities((Entity) null, Spirits.AREA, en -> en.getTags().contains(SEAT_TAG))) {
            if (!e.isVehicle()) e.discard();
        }
    }
}
