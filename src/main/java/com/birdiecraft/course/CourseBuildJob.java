package com.birdiecraft.course;

import com.birdiecraft.block.FlagBlock;
import com.birdiecraft.block.GreenBlock;
import com.birdiecraft.block.SlopeDir;
import com.birdiecraft.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Random;
import java.util.UUID;

/** Builds a {@link CoursePlan} into the world over many ticks. */
public final class CourseBuildJob {
    private enum Phase { TERRAIN, DECOR, DONE }

    private static final int CLEAR_HEIGHT = 28;
    private static final int FILL_DEPTH = 4;

    private final ServerLevel level;
    private final BlockWriter w;
    private final CoursePlan plan;
    private final String courseId;
    private final String displayName;
    private final String presetId;
    private final UUID requester;
    private final long total;

    private Phase phase = Phase.TERRAIN;
    private int hole;
    private int u;
    private int v;
    private long done;
    private int lastPercent = -1;
    private int decorHole;

    public CourseBuildJob(ServerLevel level, CoursePlan plan, String courseId, String displayName, String presetId, UUID requester) {
        this.level = level;
        this.w = new BlockWriter(level);
        this.plan = plan;
        this.courseId = courseId;
        this.displayName = displayName;
        this.presetId = presetId == null ? "" : presetId;
        this.requester = requester;
        this.total = Math.max(1, plan.columnCount());
        CoursePlan.HolePlan first = plan.holes.get(0);
        this.u = first.uMin();
        this.v = -CoursePlan.LANE_HALF;
    }

    public String courseId() {
        return courseId;
    }

    /** @return true when finished */
    public boolean tick(int columnBudget) {
        switch (phase) {
            case TERRAIN -> {
                for (int i = 0; i < columnBudget && phase == Phase.TERRAIN; i++) {
                    CoursePlan.HolePlan hp = plan.holes.get(hole);
                    buildColumn(hp, u, v);
                    done++;
                    v++;
                    if (v >= CoursePlan.LANE_HALF) {
                        v = -CoursePlan.LANE_HALF;
                        u++;
                        if (u > hp.uMax()) {
                            hole++;
                            if (hole >= plan.holes.size()) {
                                phase = Phase.DECOR;
                            } else {
                                u = plan.holes.get(hole).uMin();
                            }
                        }
                    }
                }
                report();
            }
            case DECOR -> {
                decorate(plan.holes.get(decorHole));
                decorHole++;
                if (decorHole >= plan.holes.size()) {
                    finish();
                    phase = Phase.DONE;
                }
            }
            case DONE -> {
                return true;
            }
        }
        return phase == Phase.DONE;
    }

    private void report() {
        int pct = (int) (done * 100 / total);
        if (pct / 5 != lastPercent / 5) {
            lastPercent = pct;
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(requester);
            if (p != null) {
                p.displayClientMessage(Component.translatable("msg.birdiecraft.build_progress", pct).withStyle(ChatFormatting.GREEN), true);
            }
        }
    }

    // ------------------------------------------------------------------ terrain

    private void buildColumn(CoursePlan.HolePlan hp, int lu, int lv) {
        int x = hp.worldX(lu, lv);
        int z = hp.worldZ(lu, lv);
        int base = plan.baseY;
        CoursePlan.Cell cell = hp.classify(lu, lv);
        CourseTheme theme = plan.theme;
        int top = base + cell.h();

        // clear everything above the surface
        int clearFrom = cell.surface() == CoursePlan.Surface.CHASM ? top + 1 : Math.min(top, base) + 1;
        for (int y = clearFrom; y <= base + CLEAR_HEIGHT; y++) {
            if (!w.get(x, y, z).isAir()) w.set(x, y, z, Blocks.AIR.defaultBlockState());
        }

        Block sub = theme.subsurface();
        switch (cell.surface()) {
            case WATER -> {
                BlockState fluid = switch (theme.fluid) {
                    case LAVA -> Blocks.LAVA.defaultBlockState();
                    case ICE -> Blocks.WATER.defaultBlockState();
                    default -> Blocks.WATER.defaultBlockState();
                };
                BlockState surfaceFluid = theme.fluid == CourseTheme.Fluid.ICE ? Blocks.ICE.defaultBlockState() : fluid;
                w.set(x, base, z, surfaceFluid);
                w.set(x, base - 1, z, fluid);
                Block bed = theme.fluid == CourseTheme.Fluid.LAVA ? Blocks.BLACKSTONE : Blocks.SAND;
                w.set(x, base - 2, z, bed);
                w.set(x, base - 3, z, sub);
            }
            case CHASM -> {
                for (int y = top + 1; y <= base; y++) w.set(x, y, z, Blocks.AIR.defaultBlockState());
                w.set(x, top, z, theme.obGround[0]);
            }
            default -> {
                for (int y = base - FILL_DEPTH; y < top; y++) {
                    w.set(x, y, z, cell.surface() == CoursePlan.Surface.BUNKER ? Blocks.SAND : sub);
                }
                w.set(x, top, z, surfaceState(hp, cell, lu, lv, x, z));
                if (cell.surface() == CoursePlan.Surface.CUP) {
                    w.set(x, top + 1, z, ModBlocks.GOLF_FLAG.get().defaultBlockState().setValue(FlagBlock.COLOR, theme.flag));
                }
            }
        }
    }

    private BlockState surfaceState(CoursePlan.HolePlan hp, CoursePlan.Cell cell, int lu, int lv, int x, int z) {
        CourseTheme theme = plan.theme;
        return switch (cell.surface()) {
            case TEE_BOX -> ModBlocks.TEE_BOX.get().defaultBlockState();
            case TEE_MARKER -> ModBlocks.TEE_MARKER.get().defaultBlockState();
            case FAIRWAY -> ModBlocks.FAIRWAY.get().defaultBlockState();
            case FAIRWAY_DARK -> ModBlocks.FAIRWAY_DARK.get().defaultBlockState();
            case FRINGE -> ModBlocks.FRINGE.get().defaultBlockState();
            case CUP -> ModBlocks.GOLF_CUP.get().defaultBlockState();
            case GREEN -> {
                int[] steep = new int[1];
                SlopeDir dir = hp.slopeAt(lu, lv, steep);
                yield ModBlocks.GREEN.get().defaultBlockState().setValue(GreenBlock.SLOPE, dir).setValue(GreenBlock.STEEP, steep[0]);
            }
            case ROUGH -> theme.rough().defaultBlockState();
            case DEEP -> theme.deepRough().defaultBlockState();
            case BUNKER -> theme.bunker().defaultBlockState();
            default -> {
                int hash = (x * 73856093) ^ (z * 19349663);
                Block[] g = theme.obGround;
                yield g[Math.floorMod(hash, g.length)].defaultBlockState();
            }
        };
    }

    // ------------------------------------------------------------------ decoration

    private void decorate(CoursePlan.HolePlan hp) {
        CourseTheme theme = plan.theme;
        Random r = new Random(hp.decorSeed);
        int base = plan.baseY;

        // trees
        int spacing = Math.max(4, (int) Math.round(10 - 6 * theme.treeDensity));
        for (int lu = hp.uMin(); lu <= hp.uMax(); lu += spacing) {
            for (int lv = -CoursePlan.LANE_HALF + 2; lv < CoursePlan.LANE_HALF - 2; lv += spacing) {
                int tu = lu + r.nextInt(3) - 1;
                int tv = lv + r.nextInt(3) - 1;
                if (r.nextDouble() > theme.treeDensity + 0.15) continue;
                CoursePlan.Cell c = hp.classify(tu, tv);
                boolean ok = c.surface() == CoursePlan.Surface.OB || c.surface() == CoursePlan.Surface.DEEP;
                if (!ok && c.surface() == CoursePlan.Surface.ROUGH && theme.treeDensity > 0.8) {
                    ok = Math.abs(tv - hp.center(tu)) > hp.fwHalf(tu) + 6;
                }
                if (!ok || !clearAround(hp, tu, tv)) continue;
                CourseTheme.TreeStyle style = theme.trees[r.nextInt(theme.trees.length)];
                CourseDecorator.tree(w, style, hp.worldX(tu, tv), base + c.h() + 1, hp.worldZ(tu, tv), r);
            }
        }

        // ground decorations in the out-of-bounds zone
        for (int lu = hp.uMin(); lu <= hp.uMax(); lu += 3) {
            for (int lv = -CoursePlan.LANE_HALF; lv < CoursePlan.LANE_HALF; lv += 3) {
                if (r.nextDouble() > 0.22) continue;
                int du = lu + r.nextInt(3);
                int dv = lv + r.nextInt(3);
                CoursePlan.Cell c = hp.classify(du, dv);
                if (c.surface() != CoursePlan.Surface.OB) continue;
                CourseTheme.Decor d = theme.decor[r.nextInt(theme.decor.length)];
                if (d == CourseTheme.Decor.HEDGES || d == CourseTheme.Decor.MESAS || d == CourseTheme.Decor.ICE_SPIKES) {
                    if (r.nextInt(3) != 0) continue;
                }
                CourseDecorator.decor(w, d, hp.worldX(du, dv), base + c.h() + 1, hp.worldZ(du, dv), r);
            }
        }

        // lily pads on ponds
        if (theme.fluid == CourseTheme.Fluid.WATER && (theme == CourseTheme.SAKURA || theme == CourseTheme.MYSTIC || theme == CourseTheme.MEADOW)) {
            for (int lu = hp.uMin(); lu <= hp.uMax(); lu++) {
                for (int lv = -CoursePlan.LANE_HALF; lv < CoursePlan.LANE_HALF; lv++) {
                    if (r.nextInt(16) != 0) continue;
                    if (hp.classify(lu, lv).surface() == CoursePlan.Surface.WATER) {
                        w.setIfFree(hp.worldX(lu, lv), base + 1, hp.worldZ(lu, lv), Blocks.LILY_PAD.defaultBlockState());
                    }
                }
            }
        }

        // hedges along the boundary for the royal course, OB stakes for everyone
        for (int lu = hp.uMin(); lu <= hp.uMax(); lu++) {
            boolean stakeRow = Math.floorMod(lu, 5) == 0;
            boolean hedgeRow = theme == CourseTheme.ROYAL && Math.floorMod(lu, 9) < 6;
            if (!stakeRow && !hedgeRow) continue;
            for (int side = -1; side <= 1; side += 2) {
                CoursePlan.Surface prev = hp.classify(lu, 0).surface();
                for (int k = 1; k < CoursePlan.LANE_HALF; k++) {
                    int lv = side * k;
                    CoursePlan.Cell c = hp.classify(lu, lv);
                    if (c.surface() == CoursePlan.Surface.OB && prev == CoursePlan.Surface.DEEP) {
                        int wx = hp.worldX(lu, lv);
                        int wz = hp.worldZ(lu, lv);
                        if (stakeRow) {
                            w.setIfFree(wx, base + c.h() + 1, wz, ModBlocks.OB_STAKE.get().defaultBlockState());
                        } else {
                            int hx = hp.worldX(lu, lv + side * 2);
                            int hz = hp.worldZ(lu, lv + side * 2);
                            CourseDecorator.decor(w, CourseTheme.Decor.HEDGES, hx, base + c.h() + 1, hz, r);
                        }
                        break;
                    }
                    prev = c.surface();
                }
            }
        }

        placeSign(hp);
    }

    private boolean clearAround(CoursePlan.HolePlan hp, int tu, int tv) {
        if (Math.abs(tu) < 9 && Math.abs(tv) < 9) return false;
        if (Math.hypot(tu - hp.cupU, tv - hp.cupV) < 13) return false;
        for (int du = -2; du <= 2; du++) {
            for (int dv = -2; dv <= 2; dv++) {
                CoursePlan.Surface s = hp.classify(tu + du, tv + dv).surface();
                if (s == CoursePlan.Surface.WATER || s == CoursePlan.Surface.CHASM || s == CoursePlan.Surface.BUNKER
                        || s == CoursePlan.Surface.FAIRWAY || s == CoursePlan.Surface.FAIRWAY_DARK || s == CoursePlan.Surface.GREEN
                        || s == CoursePlan.Surface.FRINGE || s == CoursePlan.Surface.TEE_BOX) {
                    return false;
                }
            }
        }
        return true;
    }

    private void placeSign(CoursePlan.HolePlan hp) {
        int su = -2;
        int sv = -6;
        CoursePlan.Cell c = hp.classify(su, sv);
        int x = hp.worldX(su, sv);
        int z = hp.worldZ(su, sv);
        int y = plan.baseY + c.h() + 1;
        // face the tee box centre
        double dx = (hp.worldX(0, 0) + 0.5) - (x + 0.5);
        double dz = (hp.worldZ(0, 0) + 0.5) - (z + 0.5);
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        int rot = Math.floorMod((int) Math.round(yaw / 22.5), 16);
        w.set(x, y, z, Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, rot));
        BlockEntity be = level.getBlockEntity(new net.minecraft.core.BlockPos(x, y, z));
        if (be instanceof SignBlockEntity sign) {
            int yards = com.birdiecraft.golf.engine.GolfUnits.yardsRounded(
                    com.birdiecraft.golf.engine.GolfUnits.horizontalDistance(hp.worldX(0, 0), hp.worldZ(0, 0),
                            hp.worldX(hp.cupU, hp.cupV), hp.worldZ(hp.cupU, hp.cupV)));
            SignText text = new SignText()
                    .setMessage(0, Component.literal("HOLE " + (hp.index + 1)).withStyle(ChatFormatting.BOLD))
                    .setMessage(1, Component.literal("PAR " + hp.par))
                    .setMessage(2, Component.literal(yards + " Y"))
                    .setMessage(3, Component.literal(displayName));
            sign.setText(text, true);
        }
    }

    // ------------------------------------------------------------------ registration

    private void finish() {
        Course course = new Course(courseId, displayName, level.dimension());
        course.presetId = presetId;
        course.flagColor = plan.theme.flag;
        course.windFactor = plan.theme.windFactor;
        for (CoursePlan.HolePlan hp : plan.holes) {
            course.holes.add(new Hole(hp.teePos(), hp.cupPos(), hp.par));
        }
        GolfCourseData.get(level.getServer()).put(course);
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(requester);
        if (p != null) {
            p.sendSystemMessage(Component.translatable("msg.birdiecraft.build_done", course.title(), course.holes.size(), course.totalPar())
                    .withStyle(ChatFormatting.GOLD));
        }
    }
}
