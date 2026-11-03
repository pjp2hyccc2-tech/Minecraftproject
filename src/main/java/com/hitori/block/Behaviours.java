package com.hitori.block;

import com.hitori.registry.ModSounds;
import com.hitori.world.FixtureEvents;
import com.hitori.world.Fixtures;
import com.hitori.world.Layout;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

/**
 * The behaviour hooks of the apartment's props (right-click and particles). Every use tells the game through
 * {@link FixtureEvents.Listener#interact} with the keys of its javadoc; the game may intercept any click earlier with
 * Forge's {@code PlayerInteractEvent.RightClickBlock}. Switch-like things also switch (lights, taps, the TV...).
 */
public final class Behaviours {
    private Behaviours() {}

    static InteractionResult done(Level level) {
        return InteractionResult.sidedSuccess(level.isClientSide);
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

    /** A plain "use" that only tells the game, with an optional sound and noise (sneaking: 40 %). */
    public static PropBlock.Use tell(String key, Supplier<SoundEvent> sound, float noise) {
        return (b, s, level, pos, player) -> {
            if (!level.isClientSide) {
                if (sound != null) sound(level, pos, sound, 0.8f);
                if (noise > 0) noise(level, pos, player.isShiftKeyDown() ? noise * 0.4f : noise, key, player);
                interact(player, key, pos);
            }
            return done(level);
        };
    }

    public static InteractionResult lightSwitch(PropBlock b, BlockState s, Level level, BlockPos pos, Player player) {
        if (level instanceof ServerLevel sl) {
            String room = Fixtures.switchRoom(pos);
            if (room != null) {
                Fixtures.playerSwitch(sl, room, !Fixtures.switchOn(sl, room), pos);
                interact(player, "switch_" + room, pos);
            } else {
                level.setBlock(pos, s.cycle(Props.ON), 3);
                sound(level, pos, ModSounds.FIXTURE_SWITCH, 0.7f);
            }
            noise(level, pos, 2.0f, "switch", player);
        }
        return done(level);
    }

    /** The hanging lamp's pull-cord (and the lamp itself): the tatami room's light. */
    public static InteractionResult pullSwitch(PropBlock b, BlockState s, Level level, BlockPos pos, Player player) {
        if (level instanceof ServerLevel sl) {
            Fixtures.playerSwitch(sl, "tatami", !Fixtures.switchOn(sl, "tatami"), pos);
            interact(player, "switch_tatami", pos);
            noise(level, pos, 2.0f, "pull_cord", player);
        }
        return done(level);
    }

    public static InteractionResult breaker(PropBlock b, BlockState s, Level level, BlockPos pos, Player player) {
        if (level instanceof ServerLevel sl) {
            if (!s.getValue(Props.OPEN)) level.setBlock(pos, s.setValue(Props.OPEN, true), 3);
            Fixtures.setBreaker(sl, !Fixtures.breakerOn(sl));
            interact(player, "breaker", pos);
            noise(level, pos, 6.0f, "breaker", player);
        }
        return done(level);
    }

    public static InteractionResult tv(PropBlock b, BlockState s, Level level, BlockPos pos, Player player) {
        if (level instanceof ServerLevel sl) {
            Fixtures.playerTv(sl, pos);
            interact(player, "tv", pos);
            noise(level, pos, 4.0f, "tv", player);
        }
        return done(level);
    }

    /** The stand (and the TV's lower body, which hangs into the stand's block) uses the TV above. */
    public static InteractionResult tvStand(PropBlock b, BlockState s, Level level, BlockPos pos, Player player) {
        BlockState up = level.getBlockState(pos.above());
        if (up.getBlock() instanceof PropBlock tvBlock && tvBlock.id.equals("crt_tv")) return tv(tvBlock, up, level, pos.above(), player);
        return InteractionResult.PASS;
    }

    public static InteractionResult curtain(PropBlock b, BlockState s, Level level, BlockPos pos, Player player) {
        if (level instanceof ServerLevel sl) {
            Fixtures.setCurtainColumn(sl, pos, !s.getValue(Props.CLOSED));
            noise(level, pos, 1.5f, "curtain", player);
            interact(player, "curtain", pos);
        }
        return done(level);
    }

    public static InteractionResult bathTap(PropBlock b, BlockState s, Level level, BlockPos pos, Player player) {
        if (level instanceof ServerLevel sl) {
            Fixtures.setBathTap(sl, !s.getValue(Props.RUNNING));
            noise(level, pos, player.isShiftKeyDown() ? 2 : 5, "tap", player);
            interact(player, "bath_tap", pos);
        }
        return done(level);
    }

    public static InteractionResult kitchenSink(PropBlock b, BlockState s, Level level, BlockPos pos, Player player) {
        if (level instanceof ServerLevel sl) {
            Fixtures.setTap(sl, "kitchen", !s.getValue(Props.RUNNING));
            noise(level, pos, player.isShiftKeyDown() ? 2 : 5, "tap", player);
            interact(player, "kitchen_tap", pos);
        }
        return done(level);
    }

    public static InteractionResult washbasin(PropBlock b, BlockState s, Level level, BlockPos pos, Player player) {
        if (level instanceof ServerLevel sl) {
            Fixtures.setTap(sl, "washbasin", !s.getValue(Props.RUNNING));
            noise(level, pos, player.isShiftKeyDown() ? 2 : 5, "tap", player);
            interact(player, "washbasin", pos);
        }
        return done(level);
    }

    public static InteractionResult washingMachine(PropBlock b, BlockState s, Level level, BlockPos pos, Player player) {
        if (level instanceof ServerLevel sl) {
            Fixtures.setDevice(sl, "washing_machine", !s.getValue(Props.RUNNING));
            noise(level, pos, 6.0f, "washing_machine", player);
            interact(player, "washing_machine", pos);
        }
        return done(level);
    }

    public static InteractionResult fridge(PropBlock b, BlockState s, Level level, BlockPos pos, Player player) {
        if (level instanceof ServerLevel sl) {
            Fixtures.setDevice(sl, "fridge", !s.getValue(Props.OPEN));
            noise(level, pos, player.isShiftKeyDown() ? 2 : 4, "fridge", player);
            interact(player, "fridge", pos);
        }
        return done(level);
    }

    public static InteractionResult gasStove(PropBlock b, BlockState s, Level level, BlockPos pos, Player player) {
        if (level instanceof ServerLevel sl) {
            Fixtures.setDevice(sl, "gas_stove", !s.getValue(Props.LIT));
            noise(level, pos, 4.0f, "gas_stove", player);
            interact(player, "gas_stove", pos);
        }
        return done(level);
    }

    /** Drawers open and shut; the one by the stove holds the lighter ("lighter_drawer"). */
    public static InteractionResult drawer(PropBlock b, BlockState s, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide) {
            boolean open = !s.getValue(Props.OPEN);
            level.setBlock(pos, s.setValue(Props.OPEN, open), 3);
            sound(level, pos, ModSounds.FIXTURE_CREAK, open ? 0.35f : 0.3f);
            noise(level, pos, player.isShiftKeyDown() ? 2 : 4, "drawer", player);
            boolean lighter = Layout.marker("lighter").pos().equals(pos);
            interact(player, lighter ? "lighter_drawer" : "drawer", pos);
        }
        return done(level);
    }

    public static InteractionResult sewingBox(PropBlock b, BlockState s, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide) {
            if (!s.getValue(Props.OPEN)) level.setBlock(pos, s.setValue(Props.OPEN, true), 3);
            sound(level, pos, ModSounds.ITEM_PAPER, 0.5f);
            interact(player, "sewing_box", pos);
        }
        return done(level);
    }

    public static InteractionResult printout(PropBlock b, BlockState s, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide) {
            sound(level, pos, ModSounds.ITEM_PAPER, 0.7f);
            if (player instanceof ServerPlayer sp) FixtureEvents.LISTENER.note(sp, "howto");
            interact(player, "printout", pos);
        }
        return done(level);
    }

    public static InteractionResult plush(PropBlock b, BlockState s, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide && s.getValue(Props.PRESENT)) interact(player, "plush_shelf", pos);
        return done(level);
    }

    public static InteractionResult deskLamp(PropBlock b, BlockState s, Level level, BlockPos pos, Player player) {
        if (level instanceof ServerLevel sl) {
            Fixtures.setDevice(sl, "desk_lamp", !s.getValue(Props.LIT));
            interact(player, "desk_lamp", pos);
            noise(level, pos, 2.0f, "switch", player);
        }
        return done(level);
    }

    public static InteractionResult intercom(PropBlock b, BlockState s, Level level, BlockPos pos, Player player) {
        if (level instanceof ServerLevel sl) {
            if (s.getValue(Props.RINGING)) Fixtures.setDevice(sl, "intercom", false);
            sound(level, pos, ModSounds.FIXTURE_SWITCH, 0.5f);
            interact(player, "intercom", pos);
        }
        return done(level);
    }

    /** The washroom's mirror cabinet gives the nail clipper; other mirrors are mirrors. */
    public static InteractionResult mirror(PropBlock b, BlockState s, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide) {
            boolean clipper = Layout.marker("nail_clipper").pos().equals(pos);
            if (clipper) sound(level, pos, ModSounds.FIXTURE_CREAK, 0.25f);
            interact(player, clipper ? "nail_clipper" : "mirror", pos);
        }
        return done(level);
    }

    // ------------------------------------------------------------------------------------------------ particles

    public static void tapWater(BlockState s, Level level, BlockPos pos, RandomSource r) {
        if (!s.getValue(Props.RUNNING)) return;
        Direction f = s.getValue(FacingBlock.FACING);
        String id = s.getBlock() instanceof PropBlock pb ? pb.id : "";
        double x, y, z;
        switch (id) {
            case "bath_tap" -> {
                // the spout sticks out of the wall over the tub (the tap's back is on the wall)
                x = pos.getX() + 0.5 + f.getStepX() * 0.05;
                z = pos.getZ() + 0.5 + f.getStepZ() * 0.05;
                y = pos.getY() + 0.3;
            }
            case "kitchen_sink" -> {
                x = pos.getX() + 0.5 - f.getStepX() * 0.2;
                z = pos.getZ() + 0.5 - f.getStepZ() * 0.2;
                y = pos.getY() + 1.15;
            }
            default -> {
                x = pos.getX() + 0.5 - f.getStepX() * 0.12;
                z = pos.getZ() + 0.5 - f.getStepZ() * 0.12;
                y = pos.getY() + 0.95;
            }
        }
        for (int i = 0; i < 3; i++) {
            level.addParticle(ParticleTypes.FALLING_WATER, x + (r.nextDouble() - 0.5) * 0.06, y, z + (r.nextDouble() - 0.5) * 0.06, 0, 0, 0);
        }
        if (r.nextInt(3) == 0) level.addParticle(ParticleTypes.SPLASH, x, y - 0.4, z, 0, 0.02, 0);
    }

    public static void gasFlames(BlockState s, Level level, BlockPos pos, RandomSource r) {
        if (!s.getValue(Props.LIT)) return;
        Direction f = s.getValue(FacingBlock.FACING);
        // two burners side by side across the stove
        Direction side = f.getClockWise();
        for (int i = 0; i < 2; i++) {
            double k = (r.nextInt(2) == 0 ? -0.25 : 0.25);
            double x = pos.getX() + 0.5 + side.getStepX() * k + (r.nextDouble() - 0.5) * 0.1;
            double z = pos.getZ() + 0.5 + side.getStepZ() * k + (r.nextDouble() - 0.5) * 0.1;
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x, pos.getY() + 1.02, z, 0, 0.004, 0);
        }
    }

    public static void washing(BlockState s, Level level, BlockPos pos, RandomSource r) {
        if (!s.getValue(Props.RUNNING) || r.nextInt(4) != 0) return;
        level.addParticle(ParticleTypes.BUBBLE_POP, pos.getX() + 0.3 + r.nextDouble() * 0.4, pos.getY() + 1.0, pos.getZ() + 0.3 + r.nextDouble() * 0.4, 0, 0.01, 0);
    }

    /** Static motes drifting off the CRT's screen when it is on. */
    public static void tvStatic(BlockState s, Level level, BlockPos pos, RandomSource r) {
        if (!s.getValue(Props.ON) || r.nextInt(3) != 0) return;
        Direction f = s.getValue(FacingBlock.FACING);
        double x = pos.getX() + 0.5 + f.getStepX() * 0.45 + (r.nextDouble() - 0.5) * 0.5 * Math.abs(f.getStepZ());
        double z = pos.getZ() + 0.5 + f.getStepZ() * 0.45 + (r.nextDouble() - 0.5) * 0.5 * Math.abs(f.getStepX());
        level.addParticle(ParticleTypes.WHITE_ASH, x, pos.getY() - 0.2 + r.nextDouble() * 0.5, z, f.getStepX() * 0.01, 0, f.getStepZ() * 0.01);
    }
}
