import com.eternalrealm.world.field.FieldLayout;
import com.eternalrealm.world.field.FieldTerrain;
import com.eternalrealm.world.gen.AstoriaBiome;
import com.eternalrealm.world.gen.AstoriaLayout;
import com.eternalrealm.world.gen.LandmarkSite;
import com.eternalrealm.world.gen.LandmarkSites;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Dumps the pure field-map terrain for the field set-piece builder ({@code tools/fields}); compiled against
 * {@code src/main/java} by {@code tools/fields/probe.py}, never shipped.
 *
 * <pre>
 *   java TerrainProbe layout OUT.json                 # FieldLayout geometry, landmark sites, constants
 *   java TerrainProbe grid REQUESTS.txt OUT.bin        # per request "x0 z0 x1 z1 step": columns x-major
 * </pre>
 *
 * Grid output per request: int32 x0, z0, nx, nz, step, then nx*nz records of
 * int16 top (floor(heightAt): the top solid block), int16 water (top water block or -32768),
 * uint8 surface (FieldTerrain.Surface ordinal), uint8 field (1..10), uint8 biome (AstoriaBiome ordinal),
 * uint8 flags (1 = falls curtain), float32 raw height.
 * The surface is computed with the same slope estimate as FieldChunkGenerator (central differences / 2).
 */
public final class TerrainProbe {
    private TerrainProbe() {
    }

    public static void main(String[] args) throws IOException {
        Locale.setDefault(Locale.ROOT);
        if (args.length >= 2 && args[0].equals("layout")) {
            layout(Path.of(args[1]));
        } else if (args.length >= 3 && args[0].equals("grid")) {
            grid(Path.of(args[1]), Path.of(args[2]));
        } else if (args.length >= 3 && args[0].equals("points")) {
            points(Path.of(args[1]), Path.of(args[2]));
        } else {
            System.err.println("usage: TerrainProbe layout OUT.json | grid REQUESTS.txt OUT.bin");
            System.exit(2);
        }
    }

    private static void grid(Path requests, Path out) throws IOException {
        List<String> lines = Files.readAllLines(requests, StandardCharsets.UTF_8);
        try (DataOutputStream data = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(out.toFile()),
                1 << 20))) {
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] p = line.split("\\s+");
                int x0 = Integer.parseInt(p[0]);
                int z0 = Integer.parseInt(p[1]);
                int x1 = Integer.parseInt(p[2]);
                int z1 = Integer.parseInt(p[3]);
                int step = p.length > 4 ? Integer.parseInt(p[4]) : 1;
                int nx = (x1 - x0) / step + 1;
                int nz = (z1 - z0) / step + 1;
                data.writeInt(x0);
                data.writeInt(z0);
                data.writeInt(nx);
                data.writeInt(nz);
                data.writeInt(step);
                for (int i = 0; i < nx; i++) {
                    for (int j = 0; j < nz; j++) {
                        int x = x0 + i * step;
                        int z = z0 + j * step;
                        double h = FieldTerrain.heightAt(x, z);
                        int top = (int) Math.floor(h);
                        double slope = Math.max(Math.abs(FieldTerrain.heightAt(x + 1, z) - FieldTerrain.heightAt(x - 1, z)),
                                Math.abs(FieldTerrain.heightAt(x, z + 1) - FieldTerrain.heightAt(x, z - 1))) / 2.0;
                        int water = FieldTerrain.waterTop(x, z, top);
                        FieldTerrain.Surface surface = FieldTerrain.surfaceAt(x, z, top, water, slope);
                        int field = FieldLayout.areaAt(x, z);
                        AstoriaBiome biome = AstoriaLayout.biomeAt(x, z);
                        data.writeShort(top);
                        data.writeShort(water == FieldTerrain.NO_WATER || water <= top ? -32768 : water);
                        data.writeByte(surface.ordinal());
                        data.writeByte(field);
                        data.writeByte(biome.ordinal());
                        data.writeByte(FieldTerrain.isFalls(x, z) ? 1 : 0);
                        data.writeFloat((float) h);
                    }
                }
            }
        }
    }

    /** One line "x z" per point (fractional allowed): writes field, top, water and border distance per point. */
    private static void points(Path requests, Path out) throws IOException {
        List<String> lines = Files.readAllLines(requests, StandardCharsets.UTF_8);
        try (DataOutputStream data = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(out.toFile()),
                1 << 20))) {
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                String[] p = line.split("\\s+");
                double x = Double.parseDouble(p[0]);
                double z = Double.parseDouble(p[1]);
                double h = FieldTerrain.heightAt(x, z);
                int top = (int) Math.floor(h);
                int water = FieldTerrain.waterTop(x, z, top);
                data.writeByte(FieldLayout.areaAt(x, z));
                data.writeShort(top);
                data.writeShort(water == FieldTerrain.NO_WATER || water <= top ? -32768 : water);
                data.writeFloat((float) FieldLayout.borderDistance(x, z));
            }
        }
    }

    private static void layout(Path out) throws IOException {
        StringBuilder s = new StringBuilder();
        s.append("{\n");
        s.append("\"bounds\":[").append(FieldLayout.MIN_X).append(',').append(FieldLayout.MIN_Z).append(',')
                .append(FieldLayout.MAX_X).append(',').append(FieldLayout.MAX_Z).append("],\n");
        s.append("\"seaLevel\":").append(FieldLayout.SEA_LEVEL).append(",\n");
        s.append("\"capital\":[").append(FieldLayout.CAPITAL_MIN_X).append(',').append(FieldLayout.CAPITAL_MIN_Z)
                .append(',').append(FieldLayout.CAPITAL_MAX_X).append(',').append(FieldLayout.CAPITAL_MAX_Z).append(',')
                .append(FieldLayout.CAPITAL_CORNER).append(',').append(FieldLayout.CAPITAL_GROUND_Y).append("],\n");
        s.append("\"lake\":[").append(FieldLayout.LAKE_X).append(',').append(FieldLayout.LAKE_Z).append(',')
                .append(FieldLayout.LAKE_RX).append(',').append(FieldLayout.LAKE_RZ).append("],\n");
        s.append("\"island\":[").append(FieldLayout.ISLAND_X).append(',').append(FieldLayout.ISLAND_Z).append(',')
                .append(FieldLayout.ISLAND_R).append("],\n");
        s.append("\"pond\":[").append(FieldLayout.POND_X).append(',').append(FieldLayout.POND_Z).append(',')
                .append(FieldLayout.POND_R).append("],\n");
        s.append("\"falls\":{\"lip\":[").append(FieldLayout.LIP_X).append(',').append(FieldLayout.LIP_Z)
                .append("],\"normal\":[").append(FieldLayout.CLIFF_NX).append(',').append(FieldLayout.CLIFF_NZ)
                .append("],\"plateauY\":").append(FieldLayout.PLATEAU_Y).append(",\"halfWidth\":")
                .append(FieldLayout.FALLS_HALF_WIDTH).append(",\"pool\":[").append(FieldLayout.POOL_X).append(',')
                .append(FieldLayout.POOL_Z).append(',').append(FieldLayout.POOL_R).append("]},\n");
        s.append("\"quarry\":[").append(FieldTerrain.QUARRY_X).append(',').append(FieldTerrain.QUARRY_Z).append(',')
                .append(FieldTerrain.QUARRY_R).append(',').append(FieldTerrain.QUARRY_FLOOR).append("],\n");
        s.append("\"camp\":[").append(FieldTerrain.CAMP_X).append(',').append(FieldTerrain.CAMP_Z).append(',')
                .append(FieldTerrain.CAMP_Y).append("],\n");
        s.append("\"ruins\":[").append(FieldTerrain.RUINS_X).append(',').append(FieldTerrain.RUINS_Z).append(',')
                .append(FieldTerrain.RUINS_TOP).append("],\n");
        s.append("\"tower\":[").append(FieldTerrain.TOWER_X).append(',').append(FieldTerrain.TOWER_Z).append(',')
                .append(FieldTerrain.TOWER_GROUND).append("],\n");
        s.append("\"harbourTown\":[").append(FieldTerrain.HARBOUR_TOWN_X).append(',')
                .append(FieldTerrain.HARBOUR_TOWN_Z).append("],\n");
        s.append("\"rivers\":[");
        boolean first = true;
        for (FieldLayout.River river : FieldLayout.rivers()) {
            s.append(first ? "\n" : ",\n");
            first = false;
            s.append("{\"id\":\"").append(river.id()).append("\",\"level\":").append(river.waterLevel())
                    .append(",\"halfWidth\":[").append(river.sourceHalfWidth()).append(',')
                    .append(river.mouthHalfWidth()).append("],\"points\":").append(points(river.points())).append('}');
        }
        s.append("],\n\"roads\":[");
        first = true;
        for (FieldLayout.Road road : FieldLayout.roads()) {
            s.append(first ? "\n" : ",\n");
            first = false;
            s.append("{\"id\":\"").append(road.id()).append("\",\"halfWidth\":").append(road.halfWidth())
                    .append(",\"points\":").append(points(road.points())).append('}');
        }
        s.append("],\n\"waystones\":[");
        first = true;
        for (FieldLayout.Waystone w : FieldLayout.waystones()) {
            s.append(first ? "\n" : ",\n");
            first = false;
            s.append("{\"id\":\"").append(w.id()).append("\",\"field\":").append(w.field()).append(",\"x\":")
                    .append(w.x()).append(",\"z\":").append(w.z()).append('}');
        }
        s.append("],\n\"labels\":[");
        for (int f = 1; f <= FieldLayout.FIELD_COUNT; f++) {
            double[] p = FieldLayout.labelPoint(f);
            s.append(f > 1 ? "," : "").append('[').append(p[0]).append(',').append(p[1]).append(']');
        }
        s.append("],\n\"landmarks\":[");
        first = true;
        for (LandmarkSite site : LandmarkSites.all()) {
            s.append(first ? "\n" : ",\n");
            first = false;
            s.append("{\"id\":\"").append(site.dungeonId()).append("\",\"area\":").append(site.area())
                    .append(",\"x\":").append(site.x()).append(",\"z\":").append(site.z()).append(",\"pad\":")
                    .append(site.padRadius()).append(",\"turns\":").append(site.quarterTurns())
                    .append(",\"front\":[").append(site.worldX(0, 1) - site.x()).append(',')
                    .append(site.worldZ(0, 1) - site.z()).append("],\"waypoint\":[")
                    .append(site.worldX(site.waypoint().dx(), site.waypoint().dz())).append(',')
                    .append(site.worldZ(site.waypoint().dx(), site.waypoint().dz())).append("]}");
        }
        s.append("],\n\"biomes\":[");
        AstoriaBiome[] biomes = AstoriaBiome.values();
        for (int i = 0; i < biomes.length; i++) {
            s.append(i > 0 ? "," : "").append('"').append(biomes[i].name().toLowerCase(Locale.ROOT)).append('"');
        }
        s.append("],\n\"surfaces\":[");
        FieldTerrain.Surface[] surfaces = FieldTerrain.Surface.values();
        for (int i = 0; i < surfaces.length; i++) {
            s.append(i > 0 ? "," : "").append('"').append(surfaces[i].name().toLowerCase(Locale.ROOT)).append('"');
        }
        s.append("],\n\"spireRadius\":").append(AstoriaLayout.SPIRE_RADIUS).append(",\n");
        s.append("\"layoutVersion\":").append(FieldLayout.VERSION).append("\n}\n");
        try (PrintStream print = new PrintStream(new FileOutputStream(out.toFile()), false, StandardCharsets.UTF_8)) {
            print.print(s);
        }
    }

    private static String points(double[][] points) {
        StringBuilder s = new StringBuilder("[");
        for (int i = 0; i < points.length; i++) {
            s.append(i > 0 ? "," : "").append('[').append(points[i][0]).append(',').append(points[i][1]).append(']');
        }
        return s.append(']').toString();
    }
}
