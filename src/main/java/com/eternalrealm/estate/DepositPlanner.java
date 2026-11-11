package com.eternalrealm.estate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Where the sorting desk's "put away" sends each stack of the bag (docs/modules/estates.md), decided before anything
 * moves. For every stack, in bag order:
 * <ol>
 *     <li>top up stacks of the same thing already stored (in boxes labelled for its kind first, then anywhere);</li>
 *     <li>empty slots of boxes labelled for its kind;</li>
 *     <li>empty slots of unlabelled boxes (plain chests, barrels);</li>
 *     <li>empty slots of boxes labelled {@link StorageLabel#MISC}.</li>
 * </ol>
 * A box labelled for another kind never takes it (only the same thing to top up), shelves take books only, one per
 * slot. What finds no place stays in the bag. The plan never moves more than a stack holds or a slot takes, so
 * carrying it out moves exactly the planned amounts. Pure.
 */
public final class DepositPlanner {
    /**
     * A storage box as the desk sees it.
     *
     * @param booksOnly a shelf: one book per slot, nothing else
     * @param limit     the most one slot holds (64 for chests and barrels)
     */
    public record Box(String name, StorageLabel label, boolean booksOnly, int limit, List<Slot> slots) {
        public Box {
            Objects.requireNonNull(label, "label");
            slots = List.copyOf(slots);
        }
    }

    /** A slot of a box: the thing in it ("" when empty) and how many. */
    public record Slot(String itemKey, int count) {
        public static final Slot EMPTY = new Slot("", 0);

        public boolean empty() {
            return itemKey.isEmpty() || count <= 0;
        }
    }

    /**
     * A stack of the bag to put away.
     *
     * @param itemKey  what it is (item and data: stacks with the same key merge)
     * @param maxStack how many of it make a full stack
     * @param kind     its kind ({@link StorageLabel#NONE} is treated as {@link StorageLabel#MISC})
     */
    public record Stack(int bagSlot, String itemKey, int count, int maxStack, StorageLabel kind, boolean book) {
    }

    /** Move {@code count} from bag slot {@code bagSlot} into slot {@code slot} of box {@code box}. */
    public record Move(int bagSlot, int box, int slot, int count) {
    }

    private DepositPlanner() {
    }

    public static List<Move> plan(List<Box> boxes, List<Stack> stacks) {
        int[][] counts = new int[boxes.size()][];
        String[][] keys = new String[boxes.size()][];
        for (int b = 0; b < boxes.size(); b++) {
            List<Slot> slots = boxes.get(b).slots();
            counts[b] = new int[slots.size()];
            keys[b] = new String[slots.size()];
            for (int s = 0; s < slots.size(); s++) {
                Slot slot = slots.get(s);
                counts[b][s] = slot.empty() ? 0 : slot.count();
                keys[b][s] = slot.empty() ? "" : slot.itemKey();
            }
        }
        List<Move> moves = new ArrayList<>();
        for (Stack stack : stacks) {
            if (stack.count() <= 0 || stack.itemKey().isEmpty()) {
                continue;
            }
            StorageLabel kind = stack.kind() == StorageLabel.NONE ? StorageLabel.MISC : stack.kind();
            int left = stack.count();
            // 1. top up the same thing: labelled boxes first, then the rest
            for (int pass = 0; pass < 2 && left > 0; pass++) {
                for (int b = 0; b < boxes.size() && left > 0; b++) {
                    Box box = boxes.get(b);
                    if ((box.label() == kind) != (pass == 0) || !accepts(box, stack)) {
                        continue;
                    }
                    int limit = Math.min(box.limit(), box.booksOnly() ? 1 : stack.maxStack());
                    for (int s = 0; s < counts[b].length && left > 0; s++) {
                        if (keys[b][s].equals(stack.itemKey()) && counts[b][s] < limit) {
                            int moved = Math.min(left, limit - counts[b][s]);
                            counts[b][s] += moved;
                            left -= moved;
                            moves.add(new Move(stack.bagSlot(), b, s, moved));
                        }
                    }
                }
            }
            // 2. a book goes on a shelf first (shelves take nothing else)
            if (stack.book()) {
                for (int b = 0; b < boxes.size() && left > 0; b++) {
                    if (!boxes.get(b).booksOnly()) {
                        continue;
                    }
                    for (int s = 0; s < counts[b].length && left > 0; s++) {
                        if (counts[b][s] == 0) {
                            counts[b][s] = 1;
                            keys[b][s] = stack.itemKey();
                            left--;
                            moves.add(new Move(stack.bagSlot(), b, s, 1));
                        }
                    }
                }
            }
            // 3-5. empty slots: its own kind, then unlabelled boxes, then the "everything else" boxes
            StorageLabel[] order = kind == StorageLabel.MISC
                    ? new StorageLabel[]{StorageLabel.MISC, StorageLabel.NONE}
                    : new StorageLabel[]{kind, StorageLabel.NONE, StorageLabel.MISC};
            for (StorageLabel wanted : order) {
                for (int b = 0; b < boxes.size() && left > 0; b++) {
                    Box box = boxes.get(b);
                    if (box.label() != wanted || !accepts(box, stack)) {
                        continue;
                    }
                    int limit = Math.min(box.limit(), box.booksOnly() ? 1 : stack.maxStack());
                    for (int s = 0; s < counts[b].length && left > 0; s++) {
                        if (counts[b][s] == 0) {
                            int moved = Math.min(left, limit);
                            counts[b][s] = moved;
                            keys[b][s] = stack.itemKey();
                            left -= moved;
                            moves.add(new Move(stack.bagSlot(), b, s, moved));
                        }
                    }
                }
            }
        }
        return moves;
    }

    private static boolean accepts(Box box, Stack stack) {
        return !box.booksOnly() || stack.book();
    }

    /** How many of each bag slot the plan puts away (index = bag slot; slots not planned stay 0). */
    public static int[] movedPerSlot(List<Move> moves, int bagSize) {
        int[] moved = new int[bagSize];
        for (Move move : moves) {
            moved[move.bagSlot()] += move.count();
        }
        return moved;
    }
}
