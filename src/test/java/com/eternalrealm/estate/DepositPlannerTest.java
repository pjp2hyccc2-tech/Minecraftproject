package com.eternalrealm.estate;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DepositPlannerTest {
    private static DepositPlanner.Box box(String name, StorageLabel label, int slots) {
        return new DepositPlanner.Box(name, label, false, 64,
                new ArrayList<>(Collections.nCopies(slots, DepositPlanner.Slot.EMPTY)));
    }

    private static DepositPlanner.Stack stack(int slot, String key, int count, StorageLabel kind) {
        return new DepositPlanner.Stack(slot, key, count, 64, kind, false);
    }

    @Test
    public void thingsGoToTheBoxesLabelledForThem() {
        List<DepositPlanner.Box> boxes = List.of(box("chest.1", StorageLabel.GEAR, 3),
                box("chest.2", StorageLabel.MATERIAL, 3), box("barrel.1", StorageLabel.NONE, 3));
        List<DepositPlanner.Move> moves = DepositPlanner.plan(boxes, List.of(
                stack(1, "sword", 1, StorageLabel.GEAR), stack(2, "iron", 40, StorageLabel.MATERIAL),
                stack(3, "stick", 10, StorageLabel.MISC)));
        assertEquals(List.of(new DepositPlanner.Move(1, 0, 0, 1), new DepositPlanner.Move(2, 1, 0, 40),
                new DepositPlanner.Move(3, 2, 0, 10)), moves);
    }

    @Test
    public void storedStacksAreToppedUpFirstEvenInAnotherBox() {
        List<DepositPlanner.Slot> slots = new ArrayList<>(Collections.nCopies(3, DepositPlanner.Slot.EMPTY));
        slots.set(1, new DepositPlanner.Slot("iron", 60));
        List<DepositPlanner.Box> boxes = List.of(box("chest.1", StorageLabel.MATERIAL, 2),
                new DepositPlanner.Box("barrel.1", StorageLabel.NONE, false, 64, slots));
        List<DepositPlanner.Move> moves = DepositPlanner.plan(boxes, List.of(stack(5, "iron", 10, StorageLabel.MATERIAL)));
        assertEquals(List.of(new DepositPlanner.Move(5, 1, 1, 4), new DepositPlanner.Move(5, 0, 0, 6)), moves);
    }

    @Test
    public void aBoxForAnotherKindTakesNothingNewAndTheRestStaysInTheBag() {
        List<DepositPlanner.Box> boxes = List.of(box("chest.1", StorageLabel.FOOD, 5), box("chest.2", StorageLabel.GEAR, 1));
        List<DepositPlanner.Move> moves = DepositPlanner.plan(boxes, List.of(stack(1, "bread", 10, StorageLabel.FOOD),
                stack(2, "helm", 1, StorageLabel.GEAR), stack(3, "boots", 1, StorageLabel.GEAR),
                stack(4, "rock", 64, StorageLabel.MISC)));
        int[] moved = DepositPlanner.movedPerSlot(moves, 5);
        assertEquals(10, moved[1]);
        assertEquals(1, moved[2]);
        assertEquals("the gear chest is full", 0, moved[3]);
        assertEquals("no box for odds and ends", 0, moved[4]);
        for (DepositPlanner.Move move : moves) {
            assertTrue(move.box() != 0 || move.bagSlot() == 1);
        }
    }

    @Test
    public void shelvesTakeOneBookASlotAndMiscTakesWhatIsLeft() {
        DepositPlanner.Box shelf = new DepositPlanner.Box("shelf.1", StorageLabel.BOOK, true, 1,
                new ArrayList<>(Collections.nCopies(6, DepositPlanner.Slot.EMPTY)));
        List<DepositPlanner.Box> boxes = List.of(shelf, box("chest.9", StorageLabel.MISC, 2));
        List<DepositPlanner.Move> moves = DepositPlanner.plan(boxes, List.of(
                new DepositPlanner.Stack(1, "book", 8, 64, StorageLabel.BOOK, true),
                stack(2, "feather", 3, StorageLabel.MATERIAL)));
        int[] moved = DepositPlanner.movedPerSlot(moves, 3);
        assertEquals(8, moved[1]);
        assertEquals(3, moved[2]);
        long intoShelf = moves.stream().filter(m -> m.box() == 0).count();
        assertEquals("six books on the shelf, one per slot", 6, intoShelf);
        assertTrue(moves.stream().filter(m -> m.box() == 0).allMatch(m -> m.count() == 1));
    }

    @Test
    public void booksGoOnTheShelvesBeforeAnUnlabelledBarrel() {
        DepositPlanner.Box shelf = new DepositPlanner.Box("shelf.1", StorageLabel.NONE, true, 1,
                new ArrayList<>(Collections.nCopies(6, DepositPlanner.Slot.EMPTY)));
        List<DepositPlanner.Box> boxes = List.of(box("barrel.1", StorageLabel.NONE, 3), shelf);
        List<DepositPlanner.Move> moves = DepositPlanner.plan(boxes, List.of(
                new DepositPlanner.Stack(4, "book", 2, 64, StorageLabel.BOOK, true),
                new DepositPlanner.Stack(5, "map", 1, 64, StorageLabel.BOOK, false)));
        assertTrue("the books are on the shelf", moves.stream().filter(m -> m.bagSlot() == 4)
                .allMatch(m -> m.box() == 1 && m.count() == 1));
        assertEquals(2, DepositPlanner.movedPerSlot(moves, 6)[4]);
        assertTrue("a map is no book for the shelf", moves.stream().filter(m -> m.bagSlot() == 5)
                .allMatch(m -> m.box() == 0));
    }

    @Test
    public void thePlanNeverOverfillsASlotOrMovesMoreThanTheBagHolds() {
        java.util.SplittableRandom random = new java.util.SplittableRandom(7);
        String[] keys = {"a", "b", "c", "d"};
        StorageLabel[] kinds = StorageLabel.values();
        for (int round = 0; round < 200; round++) {
            List<DepositPlanner.Box> boxes = new ArrayList<>();
            for (int b = 0; b < 4; b++) {
                List<DepositPlanner.Slot> slots = new ArrayList<>();
                for (int s = 0; s < 4; s++) {
                    slots.add(random.nextInt(3) == 0 ? new DepositPlanner.Slot(keys[random.nextInt(4)],
                            1 + random.nextInt(64)) : DepositPlanner.Slot.EMPTY);
                }
                boxes.add(new DepositPlanner.Box("box." + b, kinds[random.nextInt(kinds.length)], false, 64, slots));
            }
            List<DepositPlanner.Stack> stacks = new ArrayList<>();
            for (int i = 1; i <= 6; i++) {
                stacks.add(stack(i, keys[random.nextInt(4)], 1 + random.nextInt(64), kinds[random.nextInt(kinds.length)]));
            }
            List<DepositPlanner.Move> moves = DepositPlanner.plan(boxes, stacks);
            int[] moved = DepositPlanner.movedPerSlot(moves, 7);
            for (DepositPlanner.Stack each : stacks) {
                assertTrue(moved[each.bagSlot()] <= each.count());
            }
            int[][] fill = new int[4][4];
            String[][] what = new String[4][4];
            for (int b = 0; b < 4; b++) {
                for (int s = 0; s < 4; s++) {
                    fill[b][s] = boxes.get(b).slots().get(s).count();
                    what[b][s] = boxes.get(b).slots().get(s).itemKey();
                }
            }
            for (DepositPlanner.Move move : moves) {
                String key = stacks.stream().filter(st -> st.bagSlot() == move.bagSlot()).findFirst()
                        .map(DepositPlanner.Stack::itemKey).orElseThrow();
                Optional<String> before = Optional.of(what[move.box()][move.slot()]).filter(w -> !w.isEmpty());
                assertTrue("only the same thing in a slot", before.isEmpty() || before.get().equals(key));
                what[move.box()][move.slot()] = key;
                fill[move.box()][move.slot()] += move.count();
                assertTrue(fill[move.box()][move.slot()] <= 64);
                assertTrue(move.count() > 0);
            }
        }
    }
}
