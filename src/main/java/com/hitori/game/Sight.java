package com.hitori.game;

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
 * Line of sight in the apartment: the eye's view ({@code VISUAL} shapes). Window glass, the rain windows and the
 * balcony's sliding glass doors are see-through; the bathroom's frosted folding door, the fusuma and the closed
 * curtains block; floor decals (puddles, wet marks) do not. Used by the doll's eyes, by "who sees the doll" (your
 * eyes, the cameras) and by the cameras' feeds.
 */
public final class Sight {
    private enum Kind { NORMAL, THROUGH, BLOCKS, CURTAIN }

    private static final Map<Block, Kind> KINDS = new ConcurrentHashMap<>();

    private Sight() {}

    private static Kind kind(BlockState s) {
        return KINDS.computeIfAbsent(s.getBlock(), b -> {
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(b);
            if (id == null) return Kind.NORMAL;
            String p = id.getPath();
            if (id.getNamespace().equals("hitori")) {
                return switch (p) {
                    case "rain_window", "balcony_door", "puddle", "cup_glass", "mirror_small", "calendar", "poster", "wall_clock",
                         "light_switch", "pull_switch", "nameplate", "printout" -> Kind.THROUGH;
                    case "bath_door", "fusuma", "oshiire_fusuma" -> Kind.BLOCKS;
                    case "curtain" -> Kind.CURTAIN;
                    default -> Kind.NORMAL;
                };
            }
            if (p.contains("glass") || p.endsWith("_pane") || p.equals("iron_bars") || p.contains("carpet") || p.equals("light")
                    || p.equals("barrier")) return Kind.THROUGH;
            return Kind.NORMAL;
        });
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
            if (k == Kind.CURTAIN && !closed(s)) return null;
            VoxelShape shape = k == Kind.BLOCKS ? s.getShape(lv, pos) : s.getVisualShape(lv, pos, CollisionContext.empty());
            if (shape.isEmpty()) return null;
            if (k != Kind.BLOCKS && shape.max(Direction.Axis.Y) <= 0.2) return null; // floor decals
            BlockHitResult hit = shape.clip(from, to, pos);
            return hit != null ? Boolean.FALSE : null;
        }, lv -> Boolean.TRUE);
        return r == null || r;
    }
}
