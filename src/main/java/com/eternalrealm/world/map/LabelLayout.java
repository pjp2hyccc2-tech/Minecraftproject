package com.eternalrealm.world.map;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Which name plates a map draws so that none covers another (owner, 2026-11-14: 「マップがテレポート先の名前で埋まってて
 * 見にくいからどうにかして」). Every plate asks for its box on the screen with a rank; plates that must show (the
 * player's party, the place under the mouse or chosen, the field names) are placed first whatever they cover and keep
 * their room, then the others by rank (a lower rank first, ties in the order asked) where they fit inside the map and
 * touch no plate placed before them, with a pixel of air between plates. Pure.
 */
public final class LabelLayout {
    /** Pixels of air kept between two plates. */
    public static final float GAP = 1.0F;

    /** A plate's box on the screen, its rank (lower comes first) and whether it shows whatever it covers. */
    public record Box(float x0, float y0, float x1, float y1, int rank, boolean always) {
        /** A plate of {@code width} × {@code height} centred on ({@code x}, {@code y}). */
        public static Box centred(float x, float y, float width, float height, int rank, boolean always) {
            return new Box(x - width / 2.0F, y - height / 2.0F, x + width / 2.0F, y + height / 2.0F, rank, always);
        }

        /** Whether the two boxes come closer than {@link #GAP}. */
        public boolean touches(Box other) {
            return x0 < other.x1 + GAP && other.x0 < x1 + GAP && y0 < other.y1 + GAP && other.y0 < y1 + GAP;
        }

        boolean inside(float minX, float minY, float maxX, float maxY) {
            return x0 >= minX && y0 >= minY && x1 <= maxX && y1 <= maxY;
        }
    }

    private LabelLayout() {
    }

    /**
     * The plates to draw, as indices into {@code boxes}: every {@code always} plate (in the order asked), then the
     * others that fit, by rank.
     */
    public static List<Integer> place(List<Box> boxes, float minX, float minY, float maxX, float maxY) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < boxes.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.<Integer>comparingInt(i -> boxes.get(i).always() ? 0 : 1)
                .thenComparingInt(i -> boxes.get(i).always() ? 0 : boxes.get(i).rank())
                .thenComparingInt(i -> i));
        List<Integer> drawn = new ArrayList<>();
        List<Box> taken = new ArrayList<>();
        for (int index : order) {
            Box box = boxes.get(index);
            if (!box.always()) {
                if (!box.inside(minX, minY, maxX, maxY)) {
                    continue;
                }
                boolean free = true;
                for (Box other : taken) {
                    if (box.touches(other)) {
                        free = false;
                        break;
                    }
                }
                if (!free) {
                    continue;
                }
            }
            drawn.add(index);
            taken.add(box);
        }
        return drawn;
    }
}
