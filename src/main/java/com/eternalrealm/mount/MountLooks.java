package com.eternalrealm.mount;

import com.eternalrealm.core.ModInfo;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;

/**
 * How individual mounts can look (docs/modules/mounts.md): a <b>coat</b> (the colours of the body, mane, feathers and
 * trappings; each coat is a texture of the breed's model, its id the model variant) and a <b>shape</b> (the horses'
 * manes and tails, the griffins' wings; applied to the model's parts, shared by every breed of a family). Both are
 * rolled by weight; a few coats are rare. The first coat and shape of each breed are its classic look (for the first
 * horse and griffin: the mounts sold before individuality). The arcbike is a machine: one look. Ids are stored with
 * the player and in deeds: never rename them.
 */
public final class MountLooks {
    /** A coat: {@code weight} out of the kind's total; {@code rare} ones are marked at the stable. */
    public record Coat(String id, int weight, boolean rare) {
    }

    /** A shape of the horses' manes or the griffins' wings. */
    public record Shape(String id, int weight) {
    }

    private static final List<Shape> HORSE_SHAPES = List.of(
            new Shape("flowing", 50), new Shape("cropped", 25), new Shape("long", 25));
    private static final List<Shape> GRIFFIN_SHAPES = List.of(
            new Shape("broad", 40), new Shape("swept", 30), new Shape("fan", 20), new Shape("royal", 10));
    private static final List<Shape> BIKE_SHAPES = List.of(new Shape("standard", 1));
    private static final Map<MountKind, List<Coat>> COATS = new EnumMap<>(MountKind.class);

    static {
        COATS.put(MountKind.HORSE, List.of(
                new Coat("night", 14, false), new Coat("chestnut", 16, false), new Coat("bay", 16, false),
                new Coat("grey", 14, false), new Coat("palomino", 10, false), new Coat("dapple", 10, false),
                new Coat("pinto", 8, false), new Coat("ember", 6, false), new Coat("frost", 4, true),
                new Coat("star", 2, true)));
        COATS.put(MountKind.GRIFFIN, List.of(
                new Coat("azure", 16, false), new Coat("golden", 17, false), new Coat("snow", 15, false),
                new Coat("raven", 15, false), new Coat("crimson", 14, false), new Coat("verdant", 13, false),
                new Coat("dawn", 6, true), new Coat("aurora", 4, true)));
        COATS.put(MountKind.BIKE, List.of(new Coat("bike", 1, false)));
        COATS.put(MountKind.HORSE_BLOOM, breed("fawn", "moss", "autumn", "birch", "spirit"));
        COATS.put(MountKind.HORSE_TIDE, breed("lagoon", "deep", "coral", "pearl", "abyss"));
        COATS.put(MountKind.HORSE_GALE, breed("silver", "dawn", "slate", "sunset", "moonlit"));
        COATS.put(MountKind.HORSE_BLAZE, breed("ember", "ashen", "brimstone", "soulfire", "solar"));
        COATS.put(MountKind.HORSE_HALO, breed("pearl", "rose", "azure", "twilight", "prism"));
        COATS.put(MountKind.GRIFFIN_GOLD, breed("tawny", "sable", "ivory", "russet", "sunfire"));
        COATS.put(MountKind.GRIFFIN_OWL, breed("barn", "tawny", "snowy", "ashen", "moonlit"));
        COATS.put(MountKind.GRIFFIN_STORM, breed("thunder", "squall", "tempest", "gale", "fulgur"));
        COATS.put(MountKind.GRIFFIN_FROST, breed("glacier", "polar", "deepice", "dawnfrost", "starfrost"));
        COATS.put(MountKind.GRIFFIN_VOID, breed("nebula", "eclipse", "abyssal", "rift", "starborn"));
        for (MountKind kind : MountKind.values()) {
            if (!COATS.containsKey(kind)) {
                throw new IllegalStateException("no coats for " + kind);
            }
        }
    }

    private MountLooks() {
    }

    /** A breed's five coats: four common ones (the first the classic) and a rare one. */
    private static List<Coat> breed(String classic, String second, String third, String fourth, String rare) {
        return List.of(new Coat(classic, 20, false), new Coat(second, 18, false), new Coat(third, 16, false),
                new Coat(fourth, 14, false), new Coat(rare, 4, true));
    }

    public static List<Coat> coats(MountKind kind) {
        return COATS.get(kind);
    }

    public static List<Shape> shapes(MountKind kind) {
        return switch (kind.family()) {
            case HORSE -> HORSE_SHAPES;
            case GRIFFIN -> GRIFFIN_SHAPES;
            case BIKE -> BIKE_SHAPES;
        };
    }

    /** Whether the kind's looks differ between individuals (every horse and griffin breed). */
    public static boolean varies(MountKind kind) {
        return coats(kind).size() > 1 || shapes(kind).size() > 1;
    }

    /** The breed's first look (the look of the mounts sold before individuality, and of the arcbike). */
    public static MountLook classic(MountKind kind) {
        return new MountLook(coats(kind).get(0).id(), shapes(kind).get(0).id());
    }

    public static Optional<Coat> coat(MountKind kind, String id) {
        for (Coat coat : coats(kind)) {
            if (coat.id().equals(id)) {
                return Optional.of(coat);
            }
        }
        return Optional.empty();
    }

    public static Optional<Shape> shape(MountKind kind, String id) {
        for (Shape shape : shapes(kind)) {
            if (shape.id().equals(id)) {
                return Optional.of(shape);
            }
        }
        return Optional.empty();
    }

    public static boolean valid(MountKind kind, MountLook look) {
        return coat(kind, look.coat()).isPresent() && shape(kind, look.shape()).isPresent();
    }

    /** {@code look} if it belongs to the kind, else the classic look (lenient reading of stored data). */
    public static MountLook orClassic(MountKind kind, MountLook look) {
        return look != null && valid(kind, look) ? look : classic(kind);
    }

    public static boolean rare(MountKind kind, MountLook look) {
        return coat(kind, look.coat()).map(Coat::rare).orElse(false);
    }

    /** A coat and a shape, each by weight. */
    public static MountLook roll(MountKind kind, SplittableRandom random) {
        List<Coat> coats = coats(kind);
        int coatTotal = 0;
        for (Coat coat : coats) {
            coatTotal += coat.weight();
        }
        int pick = random.nextInt(coatTotal);
        String coat = coats.get(coats.size() - 1).id();
        for (Coat each : coats) {
            pick -= each.weight();
            if (pick < 0) {
                coat = each.id();
                break;
            }
        }
        List<Shape> shapes = shapes(kind);
        int shapeTotal = 0;
        for (Shape shape : shapes) {
            shapeTotal += shape.weight();
        }
        pick = random.nextInt(shapeTotal);
        String shape = shapes.get(shapes.size() - 1).id();
        for (Shape each : shapes) {
            pick -= each.weight();
            if (pick < 0) {
                shape = each.id();
                break;
            }
        }
        return new MountLook(coat, shape);
    }

    public static String coatKey(MountKind kind, String coat) {
        return "mount." + ModInfo.MOD_ID + ".coat." + kind.id() + "." + coat;
    }

    /** The shape's text: one per family (a breed's mane or wings are its family's). */
    public static String shapeKey(MountKind kind, String shape) {
        return "mount." + ModInfo.MOD_ID + ".shape." + kind.family().id() + "." + shape;
    }
}
