import com.eternalrealm.dungeon.BlockBox;
import com.eternalrealm.dungeon.BlockFill;
import com.eternalrealm.dungeon.DungeonLayout;
import com.eternalrealm.dungeon.DungeonLayoutGenerator;
import com.eternalrealm.dungeon.Dungeons;
import com.eternalrealm.dungeon.RoomLayout;
import com.eternalrealm.dungeon.decor.DecorBlock;
import com.eternalrealm.dungeon.decor.DecorPlan;
import com.eternalrealm.dungeon.decor.DungeonDecor;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Writes the voxels of a decorated dungeon layout for tools/dungeon-preview/preview.py: the build plan replayed
 * (palette roles as {@code #role}) with the decoration over it, plus the spaces to point cameras at.
 *
 * <pre>java -cp .offline-build/main:tools/dungeon-preview/out DumpDecor d1 7 /tmp/d1.txt</pre>
 */
public final class DumpDecor {
    private static final int MIN_Y = -3;

    public static void main(String[] args) throws IOException {
        String id = args[0];
        long seed = Long.parseLong(args[1]);
        DungeonLayout layout = id.equals(Dungeons.FINAL_RAID.id())
                ? DungeonLayoutGenerator.raidArena(Dungeons.FINAL_RAID)
                : DungeonLayoutGenerator.generate(Dungeons.byId(id).orElseThrow(), seed);
        DecorPlan plan = DungeonDecor.plan(layout);
        int size = DungeonLayout.FOOTPRINT;
        int height = DungeonLayout.MAX_HEIGHT - MIN_Y;
        String[] grid = new String[size * size * height];
        for (BlockFill fill : layout.buildPlan()) {
            BlockBox b = fill.box();
            String state = fill.role().name().equals("AIR") ? null : "#" + fill.role().name().toLowerCase(Locale.ROOT);
            for (int x = b.minX(); x <= b.maxX(); x++) {
                for (int y = b.minY(); y <= b.maxY(); y++) {
                    for (int z = b.minZ(); z <= b.maxZ(); z++) {
                        grid[index(x, y, z)] = state;
                    }
                }
            }
        }
        for (DecorBlock block : plan.blocks()) {
            grid[index(block.x(), block.y(), block.z())] = block.state().equals("minecraft:air") ? null : block.state();
        }
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(Path.of(args[2])))) {
            out.println("T " + layout.theme().id());
            out.println("H " + box(plan.hall()));
            out.println("C " + box(layout.entranceHall()));
            for (RoomLayout room : layout.rooms()) {
                out.println("R " + box(room.bounds()));
            }
            for (BlockBox corridor : layout.corridors()) {
                out.println("K " + box(corridor));
            }
            out.println("A " + box(layout.bossArena()));
            if (plan.antechamber() != null) {
                out.println("N " + box(plan.antechamber()));
            }
            for (BlockBox piece : plan.setPieces()) {
                out.println("S " + box(piece));
            }
            out.println("V " + plan.revivalPoint().x() + " " + plan.revivalPoint().y() + " " + plan.revivalPoint().z()
                    + " " + plan.revivalYaw());
            out.println("E " + layout.entry().x() + " " + layout.entry().y() + " " + layout.entry().z());
            Map<String, Integer> palette = new HashMap<>();
            for (int i = 0; i < grid.length; i++) {
                String state = grid[i];
                if (state == null) {
                    continue;
                }
                Integer k = palette.get(state);
                if (k == null) {
                    k = palette.size();
                    palette.put(state, k);
                    out.println("P " + k + " " + state);
                }
                int x = i % size;
                int z = (i / size) % size;
                int y = i / (size * size) + MIN_Y;
                out.println(x + " " + y + " " + z + " " + k);
            }
        }
    }

    private static int index(int x, int y, int z) {
        return ((y - MIN_Y) * DungeonLayout.FOOTPRINT + z) * DungeonLayout.FOOTPRINT + x;
    }

    private static String box(BlockBox b) {
        return b.minX() + " " + b.minY() + " " + b.minZ() + " " + b.maxX() + " " + b.maxY() + " " + b.maxZ();
    }

    private DumpDecor() {
    }
}
