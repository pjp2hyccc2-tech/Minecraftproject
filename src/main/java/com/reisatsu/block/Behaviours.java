package com.reisatsu.block;

import com.reisatsu.registry.ModSounds;
import com.reisatsu.world.FixtureEvents;
import com.reisatsu.world.Fixtures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** The behaviour hooks of the restaurant's props (right-click, stepping, bumping, particles). */
public final class Behaviours {
    private Behaviours() {}

    private static final Map<Long, Long> COOLDOWN = new HashMap<>();

    static InteractionResult done(Level level) {
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static boolean cooled(Level level, BlockPos pos, int ticks) {
        long now = level.getGameTime();
        Long last = COOLDOWN.get(pos.asLong());
        if (last != null && now - last < ticks && now >= last) return false;
        if (COOLDOWN.size() > 4096) COOLDOWN.clear();
        COOLDOWN.put(pos.asLong(), now);
        return true;
    }

    static void interact(Player player, String key, BlockPos pos) {
        if (player instanceof ServerPlayer sp) FixtureEvents.LISTENER.interact(sp, key, pos);
    }

    static void noise(Level level, BlockPos pos, float radius, String cause, Entity source) {
        if (level instanceof ServerLevel sl && radius > 0) FixtureEvents.LISTENER.noise(sl, Vec3.atCenterOf(pos), radius, cause, source);
    }

    static void sound(Level level, BlockPos pos, Supplier<SoundEvent> ev, float volume) {
        level.playSound(null, pos, ev.get(), SoundSource.BLOCKS, volume, 0.95f + level.getRandom().nextFloat() * 0.1f);
    }

    // ------------------------------------------------------------------------------------------------ right-click

    /** A plain "use" that only tells the game (time recorder, menu board...), with an optional sound and noise. */
    public static PropBlock.Use tell(String key, Supplier<SoundEvent> sound, float noise) {
        return (b, s, level, pos, player) -> {
            if (!level.isClientSide) {
                if (sound != null) sound(level, pos, sound, 1.0f);
                if (noise > 0) noise(level, pos, player.isShiftKeyDown() && noise < 16 ? noise * 0.4f : noise, key, player);
                interact(player, key, pos);
            }
            return done(level);
        };
    }

    public static InteractionResult callButton(PropBlock b, BlockState s, Level level, BlockPos pos, Player player) {
        if (level instanceof ServerLevel sl) Fixtures.pressCallButton(sl, pos, player);
        return done(level);
    }

    public static InteractionResult register(PropBlock b, BlockState s, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide) {
            level.setBlock(pos, s.cycle(Props.OPEN), 3);
            sound(level, pos, ModSounds.FIXTURE_REGISTER_DRAWER, 0.9f);
            noise(level, pos, player.isShiftKeyDown() ? 3 : 6, "register_drawer", player);
            interact(player, "register_drawer", pos);
        }
        return done(level);
    }

    public static InteractionResult phone(PropBlock b, BlockState s, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide) {
            if (s.getValue(Props.RINGING)) level.setBlock(pos, s.setValue(Props.RINGING, false), 3);
            interact(player, "phone", pos);
        }
        return done(level);
    }

    public static InteractionResult note(PropBlock b, BlockState s, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide) {
            String id = Fixtures.noteAt(pos);
            sound(level, pos, ModSounds.ITEM_PAPER, 0.7f);
            if (id != null && player instanceof ServerPlayer sp) FixtureEvents.LISTENER.note(sp, id);
            else interact(player, b.id, pos);
        }
        return done(level);
    }

    public static InteractionResult drinkBar(PropBlock b, BlockState s, Level level, BlockPos pos, Player player) {
        if (level instanceof ServerLevel sl) {
            Fixtures.pour(sl, pos, 60);
            noise(level, pos, player.isShiftKeyDown() ? 3 : 6, "drink_bar", player);
            interact(player, "drink_bar", pos);
        }
        return done(level);
    }

    public static InteractionResult tv(PropBlock b, BlockState s, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide) {
            level.setBlock(pos, s.cycle(Props.ON), 3);
            noise(level, pos, 6, "tv", player);
            interact(player, "tv", pos);
        }
        return done(level);
    }

    public static InteractionResult breaker(PropBlock b, BlockState s, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide) {
            if (!s.getValue(Props.OPEN)) {
                level.setBlock(pos, s.setValue(Props.OPEN, true), 3);
                sound(level, pos, ModSounds.FIXTURE_LOCKER, 0.5f);
            }
            if (level instanceof ServerLevel sl) Fixtures.closeLater(sl, pos, Props.OPEN, 200);
            interact(player, "breaker", pos);
        }
        return done(level);
    }

    public static InteractionResult locker(PropBlock b, BlockState s, Level level, BlockPos pos, Player player) {
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) return done(level);
        BlockPos lower = s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        boolean inside = player.blockPosition().equals(lower);
        if (inside) {
            FixtureEvents.LISTENER.unhide(sp, lower);
        } else if (!FixtureEvents.LISTENER.hide(sp, lower)) {
            return done(level);
        }
        sound(level, lower, ModSounds.FIXTURE_LOCKER, player.isShiftKeyDown() ? 0.5f : 0.9f);
        noise(level, lower, player.isShiftKeyDown() ? 3 : 6, "locker", player);
        Fixtures.flapLocker((ServerLevel) level, lower);
        return done(level);
    }

    public static InteractionResult doorSwitch(PropBlock b, BlockState s, Level level, BlockPos pos, Player player) {
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) return done(level);
        if (!FixtureEvents.LISTENER.canUnlock(sp, "door_switch")) {
            sound(level, pos, ModSounds.DOOR_LOCKED, 0.6f);
            return done(level);
        }
        Fixtures.setAutoDoorsEnabled((ServerLevel) level, !s.getValue(Props.ON));
        interact(player, "door_switch", pos);
        return done(level);
    }

    public static InteractionResult tap(PropBlock b, BlockState s, Level level, BlockPos pos, Player player) {
        if (level instanceof ServerLevel sl) {
            Fixtures.setTap(sl, pos, !s.getValue(Props.RUNNING));
            noise(level, pos, player.isShiftKeyDown() ? 2 : 5, "tap", player);
            interact(player, "tap", pos);
        }
        return done(level);
    }

    public static InteractionResult handDryer(PropBlock b, BlockState s, Level level, BlockPos pos, Player player) {
        if (level instanceof ServerLevel sl) {
            Fixtures.runHandDryer(sl, pos);
            noise(level, pos, 12, "hand_dryer", player);
            interact(player, "hand_dryer", pos);
        }
        return done(level);
    }

    public static InteractionResult gasRange(PropBlock b, BlockState s, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide) {
            boolean lit = !s.getValue(Props.LIT);
            level.setBlock(pos, s.setValue(Props.LIT, lit), 3);
            if (lit) sound(level, pos, ModSounds.FIXTURE_GAS_IGNITE, 0.9f);
            noise(level, pos, 5, "gas_range", player);
            interact(player, "gas_range", pos);
        }
        return done(level);
    }

    public static InteractionResult toilet(PropBlock b, BlockState s, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide && cooled(level, pos, 60)) {
            sound(level, pos, ModSounds.FIXTURE_TOILET_FLUSH, 1.0f);
            noise(level, pos, 12, "toilet", player);
            interact(player, "toilet", pos);
        }
        return done(level);
    }

    public static InteractionResult bear(PropBlock b, BlockState s, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide && cooled(level, pos, 10)) {
            sound(level, pos, ModSounds.FIXTURE_TOY_SQUEAK, 0.8f);
            noise(level, pos, 4, "toy", player);
            interact(player, "stuffed_bear", pos);
        }
        return done(level);
    }

    // ------------------------------------------------------------------------------------------------ inside

    /** Walking into a chair pushes it a block away with a scrape (noise 10). */
    public static void chairBump(PropBlock b, BlockState s, Level level, BlockPos pos, Entity e) {
        if (level.isClientSide || !(e instanceof LivingEntity) || e.isSpectator()) return;
        Vec3 v = e.getDeltaMovement();
        double speed = Math.sqrt(v.x * v.x + v.z * v.z);
        if (e instanceof Player p && (p.isShiftKeyDown() || speed < 0.01)) return;
        if (!cooled(level, pos, 12)) return;
        double dx = pos.getX() + 0.5 - e.getX(), dz = pos.getZ() + 0.5 - e.getZ();
        Direction push = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
        if (level instanceof ServerLevel sl) Fixtures.slideChair(sl, pos, push, e);
    }

    /** Stepping on glass shards: crunch (noise 10, sneaking 3). */
    public static void shards(PropBlock b, BlockState s, Level level, BlockPos pos, Entity e) {
        stepNoise(level, pos, e, 10, 3, "glass_shards");
    }

    /** Stepping on scattered menus: slap and slide (noise 8, sneaking silent). */
    public static void menus(PropBlock b, BlockState s, Level level, BlockPos pos, Entity e) {
        stepNoise(level, pos, e, 8, 0, "menus");
    }

    private static void stepNoise(Level level, BlockPos pos, Entity e, float walk, float sneak, String cause) {
        if (level.isClientSide || !(e instanceof LivingEntity) || e.isSpectator()) return;
        Vec3 v = e.getDeltaMovement();
        if (v.x * v.x + v.z * v.z < 0.0004) return;
        if (!cooled(level, pos, 10)) return;
        float r = e.isShiftKeyDown() ? sneak : walk;
        if (r > 0) noise(level, pos, r, cause, e);
    }

    // ------------------------------------------------------------------------------------------------ particles

    public static void tapWater(BlockState s, Level level, BlockPos pos, RandomSource r) {
        if (!s.getValue(Props.RUNNING)) return;
        Direction f = s.getValue(FacingBlock.FACING);
        // the spout hangs over the basin a little behind the front edge
        double x = pos.getX() + 0.5 - f.getStepX() * 0.12, z = pos.getZ() + 0.5 - f.getStepZ() * 0.12;
        double y = pos.getY() + (s.getBlock() instanceof PropBlock pb && pb.id.equals("kitchen_sink") ? 1.05 : 0.92);
        for (int i = 0; i < 3; i++) {
            level.addParticle(ParticleTypes.FALLING_WATER, x + (r.nextDouble() - 0.5) * 0.06, y, z + (r.nextDouble() - 0.5) * 0.06, 0, 0, 0);
        }
        if (r.nextInt(3) == 0) level.addParticle(ParticleTypes.SPLASH, x, pos.getY() + 0.6, z, 0, 0.02, 0);
    }

    public static void pour(BlockState s, Level level, BlockPos pos, RandomSource r) {
        if (!s.getValue(Props.POURING)) return;
        Direction f = s.getValue(FacingBlock.FACING);
        double x = pos.getX() + 0.5 + f.getStepX() * 0.2, z = pos.getZ() + 0.5 + f.getStepZ() * 0.2;
        level.addParticle(ParticleTypes.FALLING_WATER, x + (r.nextDouble() - 0.5) * 0.3, pos.getY() + 0.45, z, 0, 0, 0);
    }

    public static void gasFlames(BlockState s, Level level, BlockPos pos, RandomSource r) {
        if (!s.getValue(Props.LIT)) return;
        for (int i = 0; i < 2; i++) {
            double x = pos.getX() + 0.25 + (r.nextInt(2)) * 0.5 + (r.nextDouble() - 0.5) * 0.12;
            double z = pos.getZ() + 0.25 + (r.nextInt(2)) * 0.5 + (r.nextDouble() - 0.5) * 0.12;
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x, pos.getY() + 1.02, z, 0, 0.004, 0);
        }
    }

    public static void fryer(BlockState s, Level level, BlockPos pos, RandomSource r) {
        if (!s.getValue(Props.ON)) return;
        level.addParticle(ParticleTypes.BUBBLE_POP, pos.getX() + 0.2 + r.nextDouble() * 0.6, pos.getY() + 0.9, pos.getZ() + 0.2 + r.nextDouble() * 0.6, 0, 0.01, 0);
        if (r.nextInt(4) == 0) level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 0, 0.02, 0);
    }

    public static void steam(BlockState s, Level level, BlockPos pos, RandomSource r) {
        if (!s.getValue(Props.RUNNING) || r.nextInt(3) != 0) return;
        level.addParticle(ParticleTypes.CLOUD, pos.getX() + 0.5 + (r.nextDouble() - 0.5) * 0.5, pos.getY() + 1.0, pos.getZ() + 0.5 + (r.nextDouble() - 0.5) * 0.5, 0, 0.02, 0);
    }
}
