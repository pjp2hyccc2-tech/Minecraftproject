package com.birdiecraft.course;

import com.birdiecraft.config.GolfConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** Runs course build jobs, a few hundred columns per server tick. */
public final class CourseBuilder {
    private static final List<CourseBuildJob> JOBS = new ArrayList<>();

    private CourseBuilder() {
    }

    public static boolean isBusy() {
        return !JOBS.isEmpty();
    }

    public static void tick() {
        if (JOBS.isEmpty()) return;
        int budget = GolfConfig.BUILD_COLUMNS_PER_TICK.get();
        Iterator<CourseBuildJob> it = JOBS.iterator();
        if (it.hasNext()) {
            CourseBuildJob job = it.next();
            if (job.tick(budget)) it.remove();
        }
    }

    public static void clear() {
        JOBS.clear();
    }

    /** Footprint in blocks (width across lanes, length along the course) for UI warnings. */
    public static int[] footprint(int holes) {
        return new int[]{holes * CoursePlan.LANE_WIDTH, 320};
    }

    public static void buildPreset(ServerPlayer player, CoursePreset preset) {
        ServerLevel level = player.serverLevel();
        BlockPos origin = player.blockPosition().below();
        Direction forward = player.getDirection();
        CoursePlan plan = CoursePlan.create(preset.theme, preset.seed, preset.pars, preset.lengthScale, origin, forward);
        String name = net.minecraft.network.chat.Component.translatable(preset.translationKey()).getString();
        JOBS.add(new CourseBuildJob(level, plan, preset.id, name, preset.id, player.getUUID()));
    }

    public static void buildRandom(ServerPlayer player, String id, String displayName, int holes, CourseTheme theme) {
        ServerLevel level = player.serverLevel();
        BlockPos origin = player.blockPosition().below();
        Direction forward = player.getDirection();
        long seed = new Random().nextLong() ^ id.hashCode();
        int[] pattern = {4, 3, 5, 4, 4, 3, 4, 5, 4};
        int[] pars = new int[Math.max(1, Math.min(9, holes))];
        for (int i = 0; i < pars.length; i++) pars[i] = pattern[i];
        CoursePlan plan = CoursePlan.create(theme, seed, pars, 1.0, origin, forward);
        JOBS.add(new CourseBuildJob(level, plan, id, displayName, "", player.getUUID()));
    }
}
