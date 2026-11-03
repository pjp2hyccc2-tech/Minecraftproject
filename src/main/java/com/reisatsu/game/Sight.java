package com.reisatsu.game;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Line of sight in the restaurant (docs/DESIGN.md §3.1 rule 3, §3.3): the eye's view, as {@code ClipContext.Block.VISUAL}
 * — glass, the shop windows, the mirror, the pass shelf, water glasses and the cake case are see-through (and so is
 * everything with an empty visual shape, and flat decals); booth partitions (frosted), counters, shelves, closed blinds
 * and doors block. Used both by the spirits' eyes and by the camera.
 */
public final class Sight {
    private enum Kind { NORMAL, THROUGH, BLOCKS, BLIND }

    private static final Map<Block, Kind> KINDS = new ConcurrentHashMap<>();

    private Sight() {}

    private static Kind kind(BlockState s) {
        return KINDS.computeIfAbsent(s.getBlock(), b -> {
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(b);
            if (id == null) return Kind.NORMAL;
            String p = id.getPath();
            if (id.getNamespace().equals("reisatsu")) {
                return switch (p) {
                    case "shop_window", "mirror", "pass_shelf", "water_glass", "cake_case", "menu_stand", "call_button", "menu_scattered",
                         "broken_plate", "note_paper", "puddle", "entrance_mat", "frost", "kids_mat", "exit_sign", "pass_bell",
                         "service_bell", "cutting_board" -> Kind.THROUGH;
                    case "booth_partition" -> Kind.BLOCKS;
                    case "window_blind" -> Kind.BLIND;
                    default -> Kind.NORMAL;
                };
            }
            if (p.contains("glass") || p.endsWith("_pane") || p.equals("iron_bars") || p.contains("carpet")) return Kind.THROUGH;
            return Kind.NORMAL;
        });
    }

    /** Can the eye see through this block at all (ignoring its exact shape)? */
    public static boolean seeThrough(BlockState s) {
        if (s.isAir()) return true;
        Kind k = kind(s);
        if (k == Kind.THROUGH) return true;
        if (k == Kind.BLIND) return !closed(s);
        return false;
    }

    private static boolean closed(BlockState s) {
        for (Property<?> prop : s.getProperties()) {
            if (prop.getName().equals("closed")) {
                Object v = s.getValue(prop);
                return v instanceof Boolean b && b;
            }
        }
        return true;
    }

    /** Is the straight line between two points free for the eye? */
    public static boolean clear(BlockGetter level, Vec3 from, Vec3 to) {
        Boolean r = BlockGetter.traverseBlocks(from, to, level, (BlockGetter lv, BlockPos pos) -> {
            BlockState s = lv.getBlockState(pos);
            if (s.isAir()) return null;
            Kind k = kind(s);
            if (k == Kind.THROUGH) return null;
            if (k == Kind.BLIND && !closed(s)) return null;
            VoxelShape shape = k == Kind.BLOCKS ? s.getShape(lv, pos) : s.getVisualShape(lv, pos, CollisionContext.empty());
            if (shape.isEmpty()) return null;
            if (k != Kind.BLOCKS && shape.max(Direction.Axis.Y) <= 0.2) return null; // floor decals
            BlockHitResult hit = shape.clip(from, to, pos);
            return hit != null ? Boolean.FALSE : null;
        }, lv -> Boolean.TRUE);
        return r == null || r;
    }
}
