package com.meikon.debug;

import com.meikon.Meikon;
import com.meikon.block.AndonBlock;
import com.meikon.block.CandleStandBlock;
import com.meikon.game.Closet;
import com.meikon.game.Mansion;
import com.meikon.game.Room;
import com.meikon.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Self-test helper: writes the generated mansion as text (room list, points of interest and a top-down
 * character map of feet and head level) to {@code meikon-layout.txt}, for planning camera paths.
 */
final class LayoutDump {
    private static final Path FILE = Path.of("meikon-layout.txt");
    private static final List<String> LINES = new ArrayList<>();
    private static final Map<String, Integer> UNKNOWN = new TreeMap<>();

    private LayoutDump() {}

    static void write(ServerLevel level, Mansion m) {
        try {
            LINES.clear();
            add("seed " + m.seed);
            for (Room r : m.rooms) add(String.format("room %-8s x %d..%d z %d..%d", r.type, r.x1, r.x2, r.z1, r.z2));
            if (m.weddingHall != null) add("wedding hall x " + m.weddingHall.x1 + ".." + m.weddingHall.x2 + " z " + m.weddingHall.z1 + ".." + m.weddingHall.z2);
            add("spawn " + s(m.spawn) + " yaw " + m.spawnYaw + "  safeDoor " + s(m.safeDoor) + "  safeOutside " + s(m.safeOutside));
            for (Closet c : m.closets) add("closet " + c.index() + " inside " + s(c.inside()) + " front " + s(c.front()) + " facing " + c.facing());
            add("andons " + list(m.andons));
            add("bellSpots " + list(m.bellSpots) + "  butsumaBell " + s(m.butsumaBell) + "  dollBell " + s(m.dollBell) + "  kuraBell " + s(m.kuraBell));
            add("candles " + list(Arrays.asList(m.candles)) + "  order " + Arrays.toString(m.candleOrder));
            add("headlessDoll " + s(m.headlessDoll) + "  dollSpawns " + list(m.dollSpawns) + "  decorDolls " + list(m.decorDolls));
            add("torii " + s(m.toriiCenter) + "  well " + s(m.well) + "  kuraDoor " + s(m.kuraDoor) + "  sakazuki " + s(m.sakazuki)
                    + "  brideStart " + s(m.brideStart));
            add("scrolls " + list(m.scrolls) + "  clocks " + list(m.clocks));
            add("doors " + list(m.doors));
            add("waypoints " + list(m.waypoints));
            add("darkSpots " + list(m.darkSpots));
            add("");
            add("map legend: # wall  = engawa  : tatami/room floor  , courtyard ground  . path  w water  P post  I torii pillar");
            add("  d fusuma door  j shoji door  S sealed door  o oshiire door  K kura door  (upper case letter of a door = open)");
            add("  A/a andon lit/unlit  C/c candle lit/unlit  B bell stand  T tansu  U butsudan  k kamado  L stone lantern  G torii veil");
            add("  H hina-dan  W well  X sakazuki  f futon  n letter  h headless doll  r carpet  s slab  | fence  t tree  b stone  ~ decal");
            add("  lower row = head level (y 66): # solid, - open, else the same letters");
            for (int y : new int[]{65, 66}) {
                add("== y " + y + "   x from -28 (left) to 28 (right), z from -28 (top) to 28 (bottom)");
                StringBuilder head = new StringBuilder("        ");
                for (int x = -28; x <= 28; x++) head.append(Math.floorMod(x, 10) == 0 ? (char) ('0' + Math.abs(x / 10)) : ' ');
                add(head.toString());
                for (int z = -28; z <= 28; z++) {
                    StringBuilder row = new StringBuilder(String.format("z %4d  ", z));
                    for (int x = -28; x <= 28; x++) row.append(cell(level, x, y, z));
                    add(row.toString());
                }
            }
            add("unclassified blocks: " + UNKNOWN);
            flush();
        } catch (Exception e) {
            Meikon.LOGGER.error("[meikon] layout dump failed", e);
        }
    }

    static void entities(ServerLevel level) {
        try {
            add("");
            add("entities:");
            for (Entity e : level.getEntities((Entity) null, new AABB(-60, 40, -60, 60, 120, 60), x -> true)) {
                add(String.format("  %s at %.2f %.2f %.2f yaw %.0f", BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()), e.getX(), e.getY(), e.getZ(), e.getYRot()));
            }
            flush();
        } catch (Exception e) {
            Meikon.LOGGER.error("[meikon] entity dump failed", e);
        }
    }

    private static char cell(ServerLevel level, int x, int y, int z) {
        BlockPos p = new BlockPos(x, y, z);
        BlockState st = level.getBlockState(p);
        Block b = st.getBlock();
        if (st.isAir()) {
            if (y == 66) return '-';
            Block floor = level.getBlockState(p.below()).getBlock();
            if (floor == ModBlocks.ENGAWA.get() || floor == ModBlocks.ROTTEN_PLANKS.get()) return '=';
            if (floor == Blocks.GRAVEL || floor == Blocks.COBBLESTONE) return '.';
            if (floor == Blocks.COARSE_DIRT || floor == Blocks.PODZOL || floor == Blocks.ROOTED_DIRT || floor == Blocks.MUD) return ',';
            if (floor == Blocks.WATER) return 'w';
            return ':';
        }
        if (b instanceof DoorBlock) {
            char c = b == ModBlocks.FUSUMA_DOOR.get() ? 'd' : b == ModBlocks.SHOJI_DOOR.get() ? 'j' : b == ModBlocks.SEALED_DOOR.get() ? 's'
                    : b == ModBlocks.OSHIIRE_DOOR.get() ? 'o' : b == ModBlocks.KURA_DOOR.get() ? 'k' : 'd';
            if (c == 's') return 'S';
            if (c == 'k') return 'K';
            return st.getValue(DoorBlock.OPEN) ? Character.toUpperCase(c) : c;
        }
        if (b == ModBlocks.ANDON.get()) return st.getValue(AndonBlock.LIT) ? 'A' : 'a';
        if (b == ModBlocks.CANDLE_STAND.get()) return st.getValue(CandleStandBlock.LIT) ? 'C' : 'c';
        if (b == ModBlocks.SUZU_DAI.get()) return 'B';
        if (b == ModBlocks.TANSU.get()) return 'T';
        if (b == ModBlocks.BUTSUDAN.get()) return 'U';
        if (b == ModBlocks.KAMADO.get()) return 'k';
        if (b == ModBlocks.STONE_LANTERN.get()) return 'L';
        if (b == ModBlocks.TORII_GATE.get()) return 'G';
        if (b == ModBlocks.HINA_DAN.get()) return 'H';
        if (b == ModBlocks.WELL.get()) return 'W';
        if (b == ModBlocks.SAKAZUKI_TABLE.get()) return 'X';
        if (b == ModBlocks.FUTON.get()) return 'f';
        if (b == ModBlocks.LETTER.get()) return 'n';
        if (b == ModBlocks.HEADLESS_DOLL.get()) return 'h';
        if (b == ModBlocks.DARK_BEAM.get()) return 'P';
        if (b == ModBlocks.VERMILION.get()) return 'I';
        if (b == ModBlocks.BLOOD_STAIN.get() || b == ModBlocks.HAIR_STRANDS.get() || b == ModBlocks.FOOTPRINTS.get()) return '~';
        if (b == Blocks.WATER) return 'w';
        String id = BuiltInRegistries.BLOCK.getKey(b).getPath();
        if (id.contains("carpet")) return 'r';
        if (id.contains("slab")) return 's';
        if (id.contains("fence") || id.equals("chain")) return '|';
        if (id.contains("log")) return 't';
        if (id.contains("stone_brick") || id.contains("blackstone")) return 'b';
        if (id.contains("bush") || id.contains("mushroom") || id.contains("roots")) return ',';
        if (st.isCollisionShapeFullBlock(level, p)) return '#';
        UNKNOWN.merge(id, 1, Integer::sum);
        return '?';
    }

    private static String s(BlockPos p) {
        return p == null ? "-" : p.getX() + "," + p.getY() + "," + p.getZ();
    }

    private static String list(List<BlockPos> ps) {
        StringBuilder sb = new StringBuilder("[");
        for (BlockPos p : ps) {
            if (sb.length() > 1) sb.append(' ');
            sb.append(s(p));
        }
        return sb.append(']').toString();
    }

    private static void add(String line) {
        LINES.add(line);
    }

    private static void flush() throws IOException {
        Files.writeString(FILE, String.join("\n", LINES) + "\n", StandardCharsets.UTF_8);
    }
}
