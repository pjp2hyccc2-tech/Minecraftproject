package com.eternalrealm.world.field;

import com.eternalrealm.monster.MonsterTable;
import com.eternalrealm.monster.MonsterTemplate;
import com.eternalrealm.monster.Monsters;
import com.eternalrealm.world.AreaDefinition;
import com.eternalrealm.world.Areas;
import com.eternalrealm.world.gen.LandmarkSite;
import com.eternalrealm.world.gen.LandmarkSites;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Development tool (not a test): places the monster habitats and the minor waystones of every field on the terrain and
 * writes them into the generated blocks of {@link Habitats} and {@link FieldLayout}. Run once when the field map
 * changes; the tables are then fixed, so the places never move between versions.
 *
 * <pre>java -cp .offline-build/main:.offline-build/test:gson.jar com.eternalrealm.world.field.FieldSitesGenerator .</pre>
 */
public final class FieldSitesGenerator {
    /** Minor waystones per field (44 in all: with the 11 shrines, five times as many waystones as before). */
    private static final int[] MINOR_PER_FIELD = {0, 5, 5, 4, 4, 4, 5, 4, 4, 5, 4};
    private static final int STEP = 12;

    record Box(String id, String name, String kind, int minX, int minZ, int maxX, int maxZ) {
        double distance(double x, double z) {
            double dx = Math.max(Math.max(minX - x, 0.0), x - maxX);
            double dz = Math.max(Math.max(minZ - z, 0.0), z - maxZ);
            return Math.hypot(dx, dz);
        }
    }

    record Candidate(double x, double z, double fromEntry) {
    }

    record Site(String id, int field, String monster, int x, int z, int radius, int population) {
    }

    private final List<Box> boxes = new ArrayList<>();
    private final List<Site> habitats = new ArrayList<>();
    private final List<FieldLayout.Waystone> minor = new ArrayList<>();

    private FieldSitesGenerator(Path root) throws IOException {
        Path manifest = root.resolve("src/main/resources/data/eternalrealm/fieldpieces/manifest.json");
        JsonObject json = JsonParser.parseString(Files.readString(manifest, StandardCharsets.UTF_8)).getAsJsonObject();
        for (JsonElement element : json.getAsJsonArray("pieces")) {
            JsonObject piece = element.getAsJsonObject();
            JsonArray box = piece.getAsJsonArray("box");
            boxes.add(new Box(piece.get("id").getAsString(), piece.get("name").getAsString(),
                    piece.get("kind").getAsString(), box.get(0).getAsInt(), box.get(2).getAsInt(), box.get(3).getAsInt(),
                    box.get(5).getAsInt()));
        }
    }

    public static void main(String[] args) throws IOException {
        Path root = Path.of(args.length > 0 ? args[0] : ".");
        FieldSitesGenerator generator = new FieldSitesGenerator(root);
        for (int field = 1; field <= FieldLayout.FIELD_COUNT; field++) {
            generator.placeHabitats(field);
        }
        for (int field = 1; field <= FieldLayout.FIELD_COUNT; field++) {
            generator.placeWaystones(field);
        }
        generator.write(root);
        generator.report();
        if (args.length > 1) {
            generator.image(Path.of(args[1]));
        }
    }

    // ------------------------------------------------------------------ the land

    static int slopeLimit(int field) {
        return switch (field) {
            case FieldLayout.SOARING_PEAKS -> 30;
            case FieldLayout.ANCIENT_RUINS, FieldLayout.SACRED_FALLS, FieldLayout.IRON_MINE -> 20;
            default -> 14;
        };
    }

    /** Dry land at (x, z): above the sea, no river, lake, pond or falls. */
    static boolean dry(double x, double z) {
        double h = FieldTerrain.heightAt(x, z);
        int top = (int) Math.floor(h);
        return top > FieldLayout.SEA_LEVEL && !FieldTerrain.isFalls(x, z)
                && FieldTerrain.waterTop(x, z, top) == FieldTerrain.NO_WATER;
    }

    /** A circle of dry, walkable land of the field: rings of samples, all dry and within the field's slope limit. */
    static boolean landCircle(int field, double x, double z, double radius, int slopeLimit) {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        double[] rings = {0.0, 0.5, 1.0};
        for (double ring : rings) {
            int samples = ring == 0.0 ? 1 : 12;
            for (int i = 0; i < samples; i++) {
                double angle = Math.PI * 2.0 * i / samples;
                double sx = x + Math.cos(angle) * radius * ring;
                double sz = z + Math.sin(angle) * radius * ring;
                if (FieldLayout.areaAt(sx, sz) != field || !dry(sx, sz)) {
                    return false;
                }
                double h = FieldTerrain.heightAt(sx, sz);
                min = Math.min(min, h);
                max = Math.max(max, h);
                if (max - min > slopeLimit) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Clear of the capital, the edge of the world and the field, the lords, the dungeons and the set pieces. */
    boolean clear(int field, double x, double z, double radius, double pieceGap) {
        if (FieldLayout.capitalDistance(x, z) < radius + 40.0 || FieldLayout.borderDistance(x, z) < radius + 6.0) {
            return false;
        }
        if (x - radius - 24 < FieldLayout.MIN_X || x + radius + 24 > FieldLayout.MAX_X
                || z - radius - 24 < FieldLayout.MIN_Z || z + radius + 24 > FieldLayout.MAX_Z) {
            return false;
        }
        for (FieldThreats.Lord lord : FieldThreats.lords()) {
            if (Math.hypot(x - lord.x(), z - lord.z()) < lord.territory() + radius + 16.0) {
                return false;
            }
        }
        for (LandmarkSite site : LandmarkSites.all()) {
            if (Math.hypot(x - site.x(), z - site.z()) < site.padRadius() + radius + 24.0) {
                return false;
            }
        }
        for (FieldThreats.Calamity calamity : FieldThreats.calamities()) {
            if (Math.hypot(x - calamity.x(), z - calamity.z()) < FieldThreats.LAIR_RADIUS + radius + 30.0) {
                return false;
            }
        }
        for (FieldLayout.Waystone waystone : FieldLayout.shrines()) {
            if (Math.hypot(x - waystone.x(), z - waystone.z()) < radius + 28.0) {
                return false;
            }
        }
        for (Box box : boxes) {
            if (!box.kind().equals("road") && !box.kind().equals("flowers") && box.distance(x, z) < radius + pieceGap) {
                return false;
            }
        }
        return true;
    }

    /** The field's shrine (the meadow's is the capital's): the weakest monsters live nearest to it. */
    static FieldLayout.Waystone shrine(int field) {
        for (FieldLayout.Waystone waystone : FieldLayout.shrines()) {
            if (waystone.field() == field && (field == FieldLayout.MEADOW) == waystone.id().equals("capital")) {
                return waystone;
            }
        }
        throw new IllegalStateException("no shrine in field " + field);
    }

    private static double[] entry(int field) {
        FieldLayout.Waystone shrine = shrine(field);
        return new double[]{shrine.x(), shrine.z()};
    }

    private List<Candidate> candidates(int field, double radius, int slopeLimit, double pieceGap) {
        int[] bounds = FieldLayout.regionBounds(field);
        double[] entry = entry(field);
        List<Candidate> out = new ArrayList<>();
        for (int x = bounds[0]; x <= bounds[2]; x += STEP) {
            for (int z = bounds[1]; z <= bounds[3]; z += STEP) {
                if (FieldLayout.areaAt(x, z) != field || !clear(field, x, z, radius, pieceGap)
                        || !landCircle(field, x, z, radius, slopeLimit)) {
                    continue;
                }
                out.add(new Candidate(x, z, Math.hypot(x - entry[0], z - entry[1])));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ habitats

    static int radiusOf(MonsterTemplate template) {
        return switch (template.baseEntityType()) {
            case "minecraft:ravager" -> 32;
            case "minecraft:blaze", "minecraft:phantom" -> 30;
            default -> 28;
        };
    }

    static int populationOf(MonsterTemplate template) {
        return switch (template.baseEntityType()) {
            case "minecraft:ravager" -> 3;
            case "minecraft:blaze", "minecraft:phantom" -> 4;
            default -> 6;
        };
    }

    private void placeHabitats(int field) {
        AreaDefinition area = Areas.require(field);
        MonsterTable table = Monsters.tableFor(area);
        List<MonsterTable.Entry> species = new ArrayList<>(table.entries());
        species.sort(Comparator.comparingInt((MonsterTable.Entry e) -> e.template().levels().min())
                .thenComparingInt(e -> e.template().levels().max()).thenComparing(e -> e.template().id()));
        List<Candidate> all = candidates(field, 32, slopeLimit(field), 10.0);
        if (all.isEmpty()) {
            throw new IllegalStateException("no habitat ground in field " + field);
        }
        List<Double> distances = new ArrayList<>();
        all.forEach(c -> distances.add(c.fromEntry()));
        distances.sort(null);
        double near = distances.get(distances.size() / 20);
        double far = distances.get(distances.size() * 19 / 20);
        int n = species.size();
        List<Site> placed = new ArrayList<>();
        // first homes in level order, then the second homes of the common kinds
        for (int pass = 0; pass < 2; pass++) {
            for (int rank = 0; rank < n; rank++) {
                MonsterTable.Entry entry = species.get(rank);
                if (pass == 1 && entry.weight() < 25) {
                    continue;
                }
                MonsterTemplate template = entry.template();
                int radius = radiusOf(template);
                double target = near + (far - near) * (rank + 0.5) / n;
                Site first = pass == 1 ? placed.stream().filter(s -> s.monster().equals(template.id())).findFirst()
                        .orElse(null) : null;
                // the best-scoring candidate that also holds this monster's own circle (radius may differ from 32)
                List<Candidate> ranked = new ArrayList<>();
                for (Candidate c : all) {
                    if (!fits(c, radius, placed)) {
                        continue;
                    }
                    if (first != null && Math.hypot(c.x() - first.x(), c.z() - first.z()) < 220.0) {
                        continue;
                    }
                    ranked.add(c);
                }
                ranked.sort(Comparator.comparingDouble(c -> Math.abs(c.fromEntry() - target)
                        - 0.35 * Math.min(160.0, nearest(c, placed))));
                Candidate best = null;
                for (Candidate c : ranked) {
                    if (radius == 32 || landCircle(field, c.x(), c.z(), radius, slopeLimit(field))) {
                        best = c;
                        break;
                    }
                }
                if (best == null) {
                    if (pass == 0) {
                        throw new IllegalStateException("no room for " + template.id() + " in field " + field);
                    }
                    continue;
                }
                String id = template.id() + (pass == 1 ? "_2" : "");
                if (habitats.stream().anyMatch(h -> h.id().equals(id)) || placed.stream().anyMatch(h -> h.id().equals(id))) {
                    throw new IllegalStateException("habitat id " + id + " twice");
                }
                placed.add(new Site(id, field, template.id(), (int) best.x(), (int) best.z(), radius,
                        populationOf(template)));
            }
        }
        habitats.addAll(placed);
    }

    private static boolean fits(Candidate c, int radius, List<Site> placed) {
        for (Site site : placed) {
            if (Math.hypot(c.x() - site.x(), c.z() - site.z()) < radius + site.radius() + 24.0) {
                return false;
            }
        }
        return true;
    }

    private static double nearest(Candidate c, List<Site> placed) {
        double best = Double.MAX_VALUE;
        for (Site site : placed) {
            best = Math.min(best, Math.hypot(c.x() - site.x(), c.z() - site.z()));
        }
        return best;
    }

    // ------------------------------------------------------------------ minor waystones

    /**
     * Places the field's minor waystones where they serve the most: every pick lowers the summed distance from the
     * field's habitats, lords and dungeon entrances to their nearest waystone the most (greedy k-median), at least
     * {@value #MIN_STONE_GAP} blocks from any other waystone and off the habitats.
     */
    private void placeWaystones(int field) {
        List<Candidate> all = candidates(field, 6, 3, 14.0);
        List<double[]> stones = new ArrayList<>();
        for (FieldLayout.Waystone waystone : FieldLayout.shrines()) {
            stones.add(new double[]{waystone.x(), waystone.z()});
        }
        for (FieldLayout.Waystone waystone : minor) {
            stones.add(new double[]{waystone.x(), waystone.z()});
        }
        List<double[]> places = new ArrayList<>();
        for (Site habitat : habitats) {
            if (habitat.field() == field) {
                places.add(new double[]{habitat.x(), habitat.z()});
            }
        }
        for (FieldThreats.Lord lord : FieldThreats.lordsOf(field)) {
            places.add(new double[]{lord.x(), lord.z()});
        }
        for (LandmarkSite site : LandmarkSites.all()) {
            if (FieldLayout.areaAt(site.x(), site.z()) == field) {
                places.add(new double[]{site.x(), site.z()});
            }
        }
        double[] nearestStone = new double[places.size()];
        for (int p = 0; p < places.size(); p++) {
            nearestStone[p] = Double.MAX_VALUE;
            for (double[] stone : stones) {
                nearestStone[p] = Math.min(nearestStone[p], Math.hypot(places.get(p)[0] - stone[0], places.get(p)[1] - stone[1]));
            }
        }
        String base = shrine(field).id().equals("capital") ? "meadow" : shrine(field).id();
        for (int i = 0; i < MINOR_PER_FIELD[field]; i++) {
            Candidate best = null;
            double bestSum = Double.MAX_VALUE;
            for (Candidate c : all) {
                if (!offHabitats(c) || nearestPoint(c, stones) < MIN_STONE_GAP) {
                    continue;
                }
                double sum = 0.0;
                for (int p = 0; p < places.size(); p++) {
                    sum += Math.min(nearestStone[p], Math.hypot(places.get(p)[0] - c.x(), places.get(p)[1] - c.z()));
                }
                sum += 0.2 * Math.min(FieldLayout.nearestRoad(c.x(), c.z())[0], 200.0); // a little nearer the roads
                if (sum < bestSum) {
                    bestSum = sum;
                    best = c;
                }
            }
            if (best == null) {
                throw new IllegalStateException("no room for waystone " + (i + 2) + " of field " + field);
            }
            FieldLayout.Waystone stone = new FieldLayout.Waystone(base + "_" + (i + 2), field, (int) best.x(),
                    (int) best.z());
            minor.add(stone);
            stones.add(new double[]{stone.x(), stone.z()});
            for (int p = 0; p < places.size(); p++) {
                nearestStone[p] = Math.min(nearestStone[p], Math.hypot(places.get(p)[0] - stone.x(), places.get(p)[1] - stone.z()));
            }
        }
    }

    private static final double MIN_STONE_GAP = 140.0;

    private boolean offHabitats(Candidate c) {
        for (Site habitat : habitats) {
            if (Math.hypot(c.x() - habitat.x(), c.z() - habitat.z()) < habitat.radius() + 14.0) {
                return false;
            }
        }
        return true;
    }

    private static double nearestPoint(Candidate c, List<double[]> points) {
        double best = Double.MAX_VALUE;
        for (double[] point : points) {
            best = Math.min(best, Math.hypot(c.x() - point[0], c.z() - point[1]));
        }
        return best;
    }

    // ------------------------------------------------------------------ output

    private void write(Path root) throws IOException {
        StringBuilder h = new StringBuilder();
        h.append("    private static final List<Habitat> ALL = List.of(\n");
        for (int i = 0; i < habitats.size(); i++) {
            Site s = habitats.get(i);
            h.append(String.format(Locale.ROOT, "            new Habitat(\"%s\", %d, \"%s\", %d, %d, %d, %d)%s%n", s.id(),
                    s.field(), s.monster(), s.x(), s.z(), s.radius(), s.population(),
                    i + 1 < habitats.size() ? "," : ");"));
        }
        replace(root.resolve("src/main/java/com/eternalrealm/world/field/Habitats.java"),
                "    // GENERATED HABITATS BEGIN (FieldSitesGenerator)\n", "    // GENERATED HABITATS END\n", h.toString());

        StringBuilder w = new StringBuilder();
        w.append("    private static final List<Waystone> MINOR_WAYSTONES = List.of(\n");
        for (int i = 0; i < minor.size(); i++) {
            FieldLayout.Waystone s = minor.get(i);
            w.append(String.format(Locale.ROOT, "            new Waystone(\"%s\", %d, %d, %d)%s%n", s.id(), s.field(), s.x(),
                    s.z(), i + 1 < minor.size() ? "," : ");"));
        }
        replace(root.resolve("src/main/java/com/eternalrealm/world/field/FieldLayout.java"),
                "    // GENERATED MINOR WAYSTONES BEGIN (FieldSitesGenerator)\n", "    // GENERATED MINOR WAYSTONES END\n",
                w.toString());
    }

    private static void replace(Path file, String begin, String end, String body) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        int a = text.indexOf(begin);
        int b = text.indexOf(end);
        if (a < 0 || b < a) {
            throw new IllegalStateException("markers missing in " + file);
        }
        Files.writeString(file, text.substring(0, a + begin.length()) + body + text.substring(b), StandardCharsets.UTF_8);
    }

    /** What is near each minor waystone (for naming them). */
    private void report() {
        for (FieldLayout.Waystone stone : minor) {
            StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "%-16s f%d (%5d,%5d) h=%3.0f", stone.id(),
                    stone.field(), stone.x(), stone.z(), FieldTerrain.heightAt(stone.x(), stone.z())));
            double[] label = FieldLayout.labelPoint(stone.field());
            line.append(" dir=").append(direction(stone.x() - label[0], stone.z() - label[1]));
            Box piece = boxes.stream().filter(b -> !b.kind().equals("road") && !b.kind().equals("flowers"))
                    .min(Comparator.comparingDouble(b -> b.distance(stone.x(), stone.z()))).orElse(null);
            if (piece != null) {
                line.append(String.format(Locale.ROOT, " piece=%s(%.0f)", piece.name(), piece.distance(stone.x(), stone.z())));
            }
            line.append(String.format(Locale.ROOT, " road=%.0f river=%.0f lake=%.0f coast=%.0f",
                    FieldLayout.nearestRoad(stone.x(), stone.z())[0], FieldLayout.nearestRiver(stone.x(), stone.z())[0],
                    FieldLayout.lakeRadius(stone.x(), stone.z()), FieldLayout.coastDistance(stone.x(), stone.z())));
            habitats.stream().filter(s -> s.field() == stone.field())
                    .sorted(Comparator.comparingDouble(s -> Math.hypot(s.x() - stone.x(), s.z() - stone.z()))).limit(2)
                    .forEach(s -> line.append(" ").append(s.monster()).append(String.format(Locale.ROOT, "(%.0f)",
                            Math.hypot(s.x() - stone.x(), s.z() - stone.z()))));
            FieldThreats.lordsOf(stone.field()).stream()
                    .min(Comparator.comparingDouble(l -> Math.hypot(l.x() - stone.x(), l.z() - stone.z())))
                    .ifPresent(l -> line.append(" lord=").append(l.id()).append(String.format(Locale.ROOT, "(%.0f)",
                            Math.hypot(l.x() - stone.x(), l.z() - stone.z()))));
            System.out.println(line);
        }
        System.out.println(habitats.size() + " habitats, " + minor.size() + " minor waystones");
    }

    private static final int[] FIELD_COLORS = {0, 0x9CC86A, 0x3E7A4A, 0xB08A5A, 0xC8DCE8, 0xB8604A, 0xE8E8F0, 0x5A4870,
            0xD8C070, 0x7AB8A0, 0x8AA8C8};

    /** A preview of the field map (1 pixel = 4 blocks): fields, water, habitats, lords, dungeons and waystones. */
    private void image(Path file) throws IOException {
        int scale = 4;
        int width = (FieldLayout.MAX_X - FieldLayout.MIN_X) / scale;
        int height = (FieldLayout.MAX_Z - FieldLayout.MIN_Z) / scale;
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(width, height,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int py = 0; py < height; py++) {
            for (int px = 0; px < width; px++) {
                double x = FieldLayout.MIN_X + px * scale;
                double z = FieldLayout.MIN_Z + py * scale;
                int color = dry(x, z) ? FIELD_COLORS[FieldLayout.areaAt(x, z)] : 0x3A6AB0;
                if (FieldLayout.inCapital(x, z)) {
                    color = 0xE8C66A;
                }
                img.setRGB(px, py, color);
            }
        }
        java.awt.Graphics2D g = img.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        java.util.function.DoubleUnaryOperator sx = x -> (x - FieldLayout.MIN_X) / scale;
        java.util.function.DoubleUnaryOperator sz = z -> (z - FieldLayout.MIN_Z) / scale;
        for (FieldThreats.Lord lord : FieldThreats.lords()) {
            double r = lord.territory() / (double) scale;
            g.setColor(new java.awt.Color(150, 60, 200, 140));
            g.fill(new java.awt.geom.Ellipse2D.Double(sx.applyAsDouble(lord.x()) - r, sz.applyAsDouble(lord.z()) - r, 2 * r, 2 * r));
        }
        for (Site habitat : habitats) {
            double r = habitat.radius() / (double) scale;
            g.setColor(new java.awt.Color(220, 40, 40, 170));
            g.fill(new java.awt.geom.Ellipse2D.Double(sx.applyAsDouble(habitat.x()) - r, sz.applyAsDouble(habitat.z()) - r, 2 * r, 2 * r));
        }
        g.setColor(java.awt.Color.BLACK);
        for (LandmarkSite site : LandmarkSites.all()) {
            g.fillRect((int) sx.applyAsDouble(site.x()) - 4, (int) sz.applyAsDouble(site.z()) - 4, 8, 8);
        }
        for (FieldLayout.Waystone stone : FieldLayout.shrines()) {
            g.setColor(java.awt.Color.WHITE);
            g.fillOval((int) sx.applyAsDouble(stone.x()) - 6, (int) sz.applyAsDouble(stone.z()) - 6, 12, 12);
            g.setColor(new java.awt.Color(40, 200, 230));
            g.fillOval((int) sx.applyAsDouble(stone.x()) - 4, (int) sz.applyAsDouble(stone.z()) - 4, 8, 8);
        }
        for (FieldLayout.Waystone stone : minor) {
            g.setColor(java.awt.Color.WHITE);
            g.fillOval((int) sx.applyAsDouble(stone.x()) - 4, (int) sz.applyAsDouble(stone.z()) - 4, 8, 8);
            g.setColor(new java.awt.Color(40, 200, 230));
            g.fillOval((int) sx.applyAsDouble(stone.x()) - 2, (int) sz.applyAsDouble(stone.z()) - 2, 5, 5);
        }
        g.dispose();
        Files.createDirectories(file.toAbsolutePath().getParent());
        javax.imageio.ImageIO.write(img, "png", file.toFile());
    }

    private static String direction(double dx, double dz) {
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return names[(int) Math.floorMod(Math.round(angle / 45.0), 8L)];
    }
}
