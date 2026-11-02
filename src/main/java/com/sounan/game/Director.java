package com.sounan.game;

import com.sounan.block.AndonBlock;
import com.sounan.block.KakejikuBlock;
import com.sounan.entity.Hanayome;
import com.sounan.entity.Ichimatsu;
import com.sounan.entity.Kageboshi;
import com.sounan.entity.Maboroshi;
import com.sounan.registry.ModBlocks;
import com.sounan.registry.ModEntities;
import com.sounan.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The AI director: decides when something frightening happens. Small unexplained sounds come often, visible
 * events less often, hallucinations rarely — and everything backs off for a while after a chase or a scare,
 * so the dread can build again. When the player has been calm too long, she is nudged closer.
 */
public final class Director {
    private static final RandomSource RAND = RandomSource.create();
    private static final List<Task> TASKS = new ArrayList<>();

    private record Task(long due, Runnable action) {}

    private Director() {}

    public static void later(ServerLevel level, int ticks, Runnable action) {
        TASKS.add(new Task(level.getGameTime() + ticks, action));
    }

    public static void runTasks(ServerLevel level) {
        if (TASKS.isEmpty()) return;
        long now = level.getGameTime();
        List<Task> due = new ArrayList<>();
        Iterator<Task> it = TASKS.iterator();
        while (it.hasNext()) {
            Task t = it.next();
            if (t.due() <= now) {
                due.add(t);
                it.remove();
            }
        }
        for (Task t : due) {
            try {
                t.action().run();
            } catch (Exception e) {
                com.sounan.Sounan.LOGGER.warn("[sounan] director task failed", e);
            }
        }
    }

    public static void clearTasks() {
        TASKS.clear();
    }

    /** Calm for a long time: time to bring her closer. */
    public static boolean wantsMenace(PlayerState ps, long now) {
        return ps.everLeftSafeRoom && !ps.inSafeRoom && ps.fear < 35.0f && ps.calmTicks > 20 * 35;
    }

    // ================================================================== per level (every second)
    public static void tickLevel(ServerLevel level, GameData data) {
        // keep the number of shadows in check
        List<Kageboshi> shadows = level.getEntitiesOfClass(Kageboshi.class, new net.minecraft.world.phys.AABB(-60, 40, -60, 60, 110, 60));
        int max = Math.max(0, GameManager.phase() - 1) + 1;
        for (int i = max; i < shadows.size(); i++) shadows.get(i).discard();
        if (GameManager.anyBride(level) == null && GameManager.mansion() != null) {
            Hanayome bride = ModEntities.HANAYOME.get().create(level);
            if (bride != null) {
                BlockPos p = GameManager.farWaypoint(level, GameManager.mansion().brideStart);
                bride.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0, 0);
                level.addFreshEntity(bride);
            }
        }
    }

    // ================================================================== per player (every second)
    public static void tickPlayer(ServerLevel level, ServerPlayer p, PlayerState ps, GameData data) {
        if (!ps.everLeftSafeRoom || ps.captured || ps.ending) return;
        ps.calmTicks = ps.fear < 30.0f ? ps.calmTicks + 20 : 0;
        if (ps.inSafeRoom) {
            // only the faintest things happen in the room of lamplight
            if (RAND.nextInt(90) == 0) sound(p, ModSounds.AMBIENT_FOOTSTEPS_ABOVE.get(), p.position().add(RAND.nextGaussian() * 2, 5, RAND.nextGaussian() * 2), 0.6f);
            return;
        }
        long now = level.getGameTime();
        boolean recentChase = (int) (now % Integer.MAX_VALUE) - ps.lastChase < 20 * 30 && ps.lastChase != 0;
        float pace = ps.fear > 75.0f || recentChase ? 0.4f : ps.calmTicks > 20 * 40 ? 1.7f : 1.0f;
        int step = Math.round(20 * pace);
        ps.nextMinor -= step;
        ps.nextMajor -= step;
        ps.nextPhantom -= step;
        ps.nextShadow -= step;
        int phase = GameManager.phase();
        if (ps.nextMinor <= 0) {
            minorEvent(level, p, ps, phase);
            ps.nextMinor = 20 * (10 + RAND.nextInt(22));
        }
        if (ps.nextMajor <= 0) {
            majorEvent(level, p, ps, phase);
            ps.nextMajor = 20 * (40 + RAND.nextInt(55));
        }
        if (data.bells >= 1 && ps.nextPhantom <= 0) {
            boolean peek = phase >= 2 && ps.peeksLeft > 0 && ps.fear < 70.0f && RAND.nextFloat() < 0.4f;
            if (peek ? spawnPeek(level, p) : spawnFar(level, p)) {
                if (peek) ps.peeksLeft--;
                ps.nextPhantom = 20 * (110 + RAND.nextInt(130));
            } else {
                ps.nextPhantom = 20 * 15;
            }
        }
        if (phase >= 2 && ps.nextShadow <= 0) {
            spawnShadow(level, p);
            ps.nextShadow = 20 * Math.max(18, 45 - phase * 6 + RAND.nextInt(30));
        }
        // the bride has been far away for too long
        if (wantsMenace(ps, now) && RAND.nextInt(12) == 0) {
            Hanayome bride = GameManager.anyBride(level);
            if (bride != null && bride.distanceTo(p) > 30 && (bride.state() == Hanayome.State.WANDER || bride.state() == Hanayome.State.IDLE)) {
                bride.force(Hanayome.State.INVESTIGATE, p.position().add(RAND.nextGaussian() * 6, 0, RAND.nextGaussian() * 6));
                ps.calmTicks = 0;
            }
        }
    }

    // ================================================================== small things
    private static void minorEvent(ServerLevel level, ServerPlayer p, PlayerState ps, int phase) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getViewVector(1.0f);
        Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
        int roll = RAND.nextInt(phase >= 2 ? 12 : 9);
        switch (roll) {
            case 0, 1 -> sound(p, ModSounds.AMBIENT_CREAK.get(), around(eye, 4, 10, 0), 0.9f);
            case 2 -> sound(p, ModSounds.AMBIENT_KNOCK.get(), around(eye, 3, 6, 0), 1.0f);
            case 3 -> sound(p, ModSounds.AMBIENT_FOOTSTEPS_ABOVE.get(), p.position().add(RAND.nextGaussian() * 3, 5, RAND.nextGaussian() * 3), 1.0f);
            case 4 -> sound(p, ModSounds.AMBIENT_WHISPER.get(), eye.add(right.scale(RAND.nextBoolean() ? 1.1 : -1.1)), 0.9f);
            case 5 -> sound(p, ModSounds.AMBIENT_DOOR_SLAM.get(), around(eye, 18, 30, 0), 1.4f);
            case 6 -> sound(p, ModSounds.AMBIENT_DRIP.get(), around(eye, 5, 10, 1), 0.8f);
            case 7 -> sound(p, ModSounds.AMBIENT_SCRATCH.get(), eye.subtract(look.scale(3)), 0.9f);
            case 8 -> sound(p, ModSounds.AMBIENT_THUD.get(), p.position().add(RAND.nextGaussian() * 4, 5, RAND.nextGaussian() * 4), 1.0f);
            case 9 -> sound(p, ModSounds.AMBIENT_CRYING.get(), around(eye, 14, 24, 0), 1.2f);
            case 10 -> sound(p, ModSounds.AMBIENT_MUSIC_BOX.get(), around(eye, 12, 20, 0), 1.0f);
            default -> {
                // footsteps behind you that are not there
                Vec3 behind = p.position().subtract(look.x * 5, 0, look.z * 5);
                for (int i = 0; i < 4; i++) {
                    Vec3 at = behind.add(look.x * i * 0.7, 0.1, look.z * i * 0.7);
                    later(level, i * 9, () -> sound(p, ModSounds.BRIDE_STEP.get(), at, 0.7f));
                }
            }
        }
    }

    private static Vec3 around(Vec3 c, double min, double max, double dy) {
        double a = RAND.nextDouble() * Math.PI * 2;
        double r = min + RAND.nextDouble() * (max - min);
        return c.add(Math.cos(a) * r, dy, Math.sin(a) * r);
    }

    private static void sound(ServerPlayer p, SoundEvent s, Vec3 at, float vol) {
        GameManager.sound(p, s, at, vol, 0.88f + RAND.nextFloat() * 0.2f);
    }

    // ================================================================== visible things
    private static void majorEvent(ServerLevel level, ServerPlayer p, PlayerState ps, int phase) {
        Mansion m = GameManager.mansion();
        if (m == null) return;
        for (int attempt = 0; attempt < 4; attempt++) {
            int roll = RAND.nextInt(phase >= 2 ? 7 : 5);
            boolean ok = switch (roll) {
                case 0 -> lampFlicker(level, p, m, phase);
                case 1 -> footprints(level, p);
                case 2 -> handprint(level, p, m);
                case 3 -> scrollFace(level, p, m);
                case 4 -> doorOpens(level, p, m);
                case 5 -> lanternFlicker(level, p, ps);
                default -> dollAppears(level, p);
            };
            if (ok) return;
        }
    }

    private static boolean lampFlicker(ServerLevel level, ServerPlayer p, Mansion m, int phase) {
        for (BlockPos a : shuffled(m.andons)) {
            if (a.distSqr(p.blockPosition()) > 14 * 14) continue;
            BlockState s = level.getBlockState(a);
            if (!s.is(ModBlocks.ANDON.get()) || !s.getValue(AndonBlock.LIT)) continue;
            level.setBlock(a, s.setValue(AndonBlock.LIT, false), 3);
            GameManager.sound(p, ModSounds.GAME_LANTERN_OFF.get(), Vec3.atCenterOf(a), 0.8f, 0.7f);
            if (phase < 3 || RAND.nextBoolean()) {
                later(level, 60 + RAND.nextInt(120), () -> {
                    BlockState now = level.getBlockState(a);
                    if (now.is(ModBlocks.ANDON.get())) level.setBlock(a, now.setValue(AndonBlock.LIT, true), 3);
                });
            }
            return true;
        }
        return false;
    }

    private static boolean footprints(ServerLevel level, ServerPlayer p) {
        Vec3 look = p.getViewVector(1.0f);
        Vec3 dir = new Vec3(-look.x, 0, -look.z).normalize();
        BlockPos start = p.blockPosition();
        List<BlockPos> placed = new ArrayList<>();
        Direction facing = Direction.getNearest(-dir.x, 0, -dir.z);
        for (int i = 2; i <= 9; i++) {
            BlockPos at = BlockPos.containing(start.getX() + 0.5 + dir.x * i, start.getY(), start.getZ() + 0.5 + dir.z * i);
            if (!level.getBlockState(at).isAir() || !level.getBlockState(at.below()).isSolidRender(level, at.below())) break;
            placed.add(at);
        }
        if (placed.size() < 3) return false;
        for (BlockPos at : placed) {
            level.setBlock(at, ModBlocks.FOOTPRINTS.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing), 3);
        }
        later(level, 20 * 45, () -> {
            for (BlockPos at : placed) if (level.getBlockState(at).is(ModBlocks.FOOTPRINTS.get())) level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
        });
        return true;
    }

    private static boolean handprint(ServerLevel level, ServerPlayer p, Mansion m) {
        for (BlockPos s : shuffled(m.shoji)) {
            if (s.distSqr(p.blockPosition()) > 10 * 10 || s.distSqr(p.blockPosition()) < 3 * 3) continue;
            if (!level.getBlockState(s).is(ModBlocks.SHOJI.get())) continue;
            if (visible(p, Vec3.atCenterOf(s), 0.3)) continue;
            level.setBlock(s, ModBlocks.SHOJI_BLOODY.get().defaultBlockState(), 3);
            GameManager.sound(p, ModSounds.AMBIENT_SCRATCH.get(), Vec3.atCenterOf(s), 0.9f, 1.0f);
            return true;
        }
        return false;
    }

    private static boolean scrollFace(ServerLevel level, ServerPlayer p, Mansion m) {
        for (BlockPos s : shuffled(m.scrolls)) {
            if (s.distSqr(p.blockPosition()) > 12 * 12) continue;
            BlockState st = level.getBlockState(s);
            if (!st.is(ModBlocks.KAKEJIKU.get()) || st.getValue(KakejikuBlock.FACE)) continue;
            if (visible(p, Vec3.atCenterOf(s), 0.3)) continue;
            level.setBlock(s, st.setValue(KakejikuBlock.FACE, true), 3);
            revertScroll(level, s, p, 0);
            return true;
        }
        return false;
    }

    private static void revertScroll(ServerLevel level, BlockPos s, ServerPlayer p, int tries) {
        later(level, 20 * 8, () -> {
            BlockState st = level.getBlockState(s);
            if (!st.is(ModBlocks.KAKEJIKU.get())) return;
            if (visible(p, Vec3.atCenterOf(s), 0.2) && tries < 20) {
                revertScroll(level, s, p, tries + 1);
            } else {
                level.setBlock(s, st.setValue(KakejikuBlock.FACE, false), 3);
            }
        });
    }

    private static boolean doorOpens(ServerLevel level, ServerPlayer p, Mansion m) {
        List<BlockPos> doors = new ArrayList<>(m.doors);
        for (Closet c : m.closets) doors.add(c.front());
        for (BlockPos d : shuffled(doors)) {
            if (d.distSqr(p.blockPosition()) > 9 * 9 || d.distSqr(p.blockPosition()) < 2 * 2) continue;
            if (d.equals(m.safeDoor)) continue;
            BlockState s = level.getBlockState(d);
            if (!(s.getBlock() instanceof DoorBlock door) || s.getValue(DoorBlock.OPEN)) continue;
            door.setOpen(null, level, s, d, true);
            return true;
        }
        return false;
    }

    private static boolean lanternFlicker(ServerLevel level, ServerPlayer p, PlayerState ps) {
        if (!ps.lanternLit) return false;
        GameManager.event(p, GameManager.EVENT_FLICKER, 0, "");
        for (int i = 0; i < 3; i++) {
            later(level, i * 7, () -> LanternLight.suppress(p, 4));
        }
        return true;
    }

    private static boolean dollAppears(ServerLevel level, ServerPlayer p) {
        Vec3 look = p.getViewVector(1.0f);
        Vec3 behind = p.position().subtract(look.x * 3.2, 0, look.z * 3.2);
        BlockPos at = BlockPos.containing(behind);
        if (!level.getBlockState(at).isAir() || !level.getBlockState(at.above()).isAir()) return false;
        if (!level.getBlockState(at.below()).isSolidRender(level, at.below())) return false;
        Ichimatsu doll = ModEntities.ICHIMATSU.get().create(level);
        if (doll == null) return false;
        float yaw = (float) (Mth.atan2(look.z, look.x) * (180.0 / Math.PI)) - 90.0f + 180.0f;
        doll.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0);
        doll.setYHeadRot(yaw);
        doll.setHome(at, true);
        level.addFreshEntity(doll);
        later(level, 20 * 50, () -> {
            if (doll.isAlive() && !GameManager.anyoneSees(level, doll)) doll.discard();
            else if (doll.isAlive()) later(level, 20 * 30, doll::discard);
        });
        return true;
    }

    // ================================================================== hallucinations & shadows
    public static boolean spawnFar(ServerLevel level, ServerPlayer p) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getViewVector(1.0f);
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1.0e-3) return false;
        flat = flat.normalize();
        float side = (RAND.nextFloat() - 0.5f) * 0.5f;
        Vec3 dir = new Vec3(flat.x - flat.z * side, 0, flat.z + flat.x * side).normalize();
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(dir.scale(26)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 end = hit.getType() == HitResult.Type.MISS ? eye.add(dir.scale(26)) : hit.getLocation().subtract(dir.scale(0.8));
        double dist = end.distanceTo(eye);
        if (dist < 10) return false;
        BlockPos at = BlockPos.containing(end.x, p.getY(), end.z);
        for (int dy = 0; dy < 3 && !level.getBlockState(at.below()).isSolidRender(level, at.below()); dy++) at = at.below();
        if (!level.getBlockState(at).isAir() || !level.getBlockState(at.above()).isAir()) return false;
        Maboroshi m = ModEntities.MABOROSHI.get().create(level);
        if (m == null) return false;
        m.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        m.setup(p, Maboroshi.MODE_FAR);
        level.addFreshEntity(m);
        return true;
    }

    public static boolean spawnPeek(ServerLevel level, ServerPlayer p) {
        Vec3 look = p.getViewVector(1.0f);
        Vec3 behind = p.position().subtract(look.x * 1.7, 0, look.z * 1.7);
        BlockPos at = BlockPos.containing(behind);
        if (!level.getBlockState(at).isAir() || !level.getBlockState(at.above()).isAir()) return false;
        Maboroshi m = ModEntities.MABOROSHI.get().create(level);
        if (m == null) return false;
        m.moveTo(behind.x, p.getY(), behind.z, 0, 0);
        m.setup(p, Maboroshi.MODE_PEEK);
        level.addFreshEntity(m);
        GameManager.sound(p, ModSounds.BRIDE_BREATH.get(), behind.add(0, 1.7, 0), 0.7f, 0.8f);
        return true;
    }

    public static boolean spawnShadow(ServerLevel level, ServerPlayer p) {
        Mansion m = GameManager.mansion();
        if (m == null) return false;
        for (BlockPos s : shuffled(m.darkSpots)) {
            double d = s.distSqr(p.blockPosition());
            if (d < 10 * 10 || d > 26 * 26) continue;
            if (level.getMaxLocalRawBrightness(s) > 3) continue;
            if (visible(p, Vec3.atCenterOf(s), 0.4)) continue;
            Kageboshi k = ModEntities.KAGEBOSHI.get().create(level);
            if (k == null) return false;
            k.moveTo(s.getX() + 0.5, s.getY(), s.getZ() + 0.5, RAND.nextFloat() * 360, 0);
            level.addFreshEntity(k);
            GameManager.sound(p, ModSounds.SHADOW_WHOOSH.get(), Vec3.atCenterOf(s), 0.6f, 0.7f);
            return true;
        }
        return false;
    }

    // ================================================================== helpers
    private static boolean visible(ServerPlayer p, Vec3 target, double minDot) {
        Vec3 to = target.subtract(p.getEyePosition());
        double len = to.length();
        if (len < 1.0e-3) return true;
        if (p.getViewVector(1.0f).dot(to.scale(1.0 / len)) < minDot) return false;
        BlockHitResult hit = p.level().clip(new ClipContext(p.getEyePosition(), target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(target) < 1.0;
    }

    private static <T> List<T> shuffled(List<T> in) {
        List<T> out = new ArrayList<>(in);
        for (int i = out.size() - 1; i > 0; i--) {
            int j = RAND.nextInt(i + 1);
            T t = out.get(i);
            out.set(i, out.get(j));
            out.set(j, t);
        }
        return out;
    }
}
