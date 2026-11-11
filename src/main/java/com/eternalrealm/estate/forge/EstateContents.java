package com.eternalrealm.estate.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything players keep in an estate, and moving it: containers (any block entity that is a {@link Container}:
 * chests, barrels, wardrobes, shelves...), armour stands (their six slots), item frames (the item and its turn) and
 * things lying on the floor. A storage piece of a design carries its name in its persistent data
 * ({@value #SLOT}, e.g. {@code chest.3}, written by the blueprint), so a redesign puts every piece's contents into the
 * piece of the same name, slot by slot, whatever the old blueprint looked like; what has no such place goes into any
 * free space of the new build, and only what does not fit at all into the owner's keepsafe. Every move takes the stack
 * out of its old place and puts it into its new one in the same tick, so nothing is ever in two places or none.
 */
public final class EstateContents {
    /** Persistent-data key naming a storage piece of a design ({@code chest.1}, {@code stand.2}, {@code frame.5}). */
    public static final String SLOT = "er_slot";
    private static final EquipmentSlot[] STAND_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};

    private EstateContents() {
    }

    /** What a box holds. */
    public static final class Found {
        /** Named storage pieces, by name. */
        final Map<String, Holder> named = new LinkedHashMap<>();
        /** Storage without a name (containers and holders the design did not name). */
        final List<Holder> unnamed = new ArrayList<>();
        /** Things lying on the floor. */
        final List<ItemEntity> loose = new ArrayList<>();

        /** Every holder, the named ones first (in name order), then the others. */
        public List<Holder> holders() {
            List<Holder> all = new ArrayList<>(named.values());
            all.addAll(unnamed);
            return all;
        }

        /** Stacks stored in total (named, unnamed and loose). */
        public int stacks() {
            int count = loose.size();
            for (Holder holder : holders()) {
                count += holder.stacks();
            }
            return count;
        }

        public boolean isEmpty() {
            return stacks() == 0;
        }
    }

    /** A storage piece. */
    public abstract static sealed class Holder permits ContainerHolder, StandHolder, FrameHolder {
        final String name;

        Holder(String name) {
            this.name = name;
        }

        /** The design's name for the piece, or null. */
        public String name() {
            return name;
        }

        /** Number of positions (container slots, stand slots, 1 for a frame). */
        public abstract int size();

        public abstract ItemStack get(int position);

        /** Takes the stack at {@code position} out (it is empty afterwards). */
        abstract ItemStack take(int position);

        /** Puts {@code stack} at the empty {@code position}; false if it cannot go there. */
        abstract boolean put(int position, ItemStack stack);

        /** Adds as much of {@code stack} as fits anywhere (stack shrinks). Frames and stands take nothing here. */
        abstract void insert(ItemStack stack);

        abstract void changed();

        public int stacks() {
            int count = 0;
            for (int i = 0; i < size(); i++) {
                if (!get(i).isEmpty()) {
                    count++;
                }
            }
            return count;
        }

        /** Whether this holds the same kind of positions as {@code other} (slot-by-slot moves). */
        boolean sameKind(Holder other) {
            return getClass() == other.getClass();
        }
    }

    /** A block entity container. */
    public static final class ContainerHolder extends Holder {
        final Container container;
        final BlockEntity blockEntity;

        ContainerHolder(String name, BlockEntity blockEntity) {
            super(name);
            this.blockEntity = blockEntity;
            this.container = (Container) blockEntity;
        }

        public BlockPos pos() {
            return blockEntity.getBlockPos();
        }

        public Container container() {
            return container;
        }

        @Override
        public int size() {
            return container.getContainerSize();
        }

        @Override
        public ItemStack get(int position) {
            return container.getItem(position);
        }

        @Override
        ItemStack take(int position) {
            return container.removeItemNoUpdate(position);
        }

        @Override
        boolean put(int position, ItemStack stack) {
            if (position >= size() || !container.getItem(position).isEmpty() || !container.canPlaceItem(position, stack)
                    || stack.getCount() > Math.min(stack.getMaxStackSize(), container.getMaxStackSize())) {
                return false;
            }
            container.setItem(position, stack);
            return true;
        }

        @Override
        void insert(ItemStack stack) {
            int limit = container.getMaxStackSize();
            for (int i = 0; i < size() && !stack.isEmpty(); i++) {
                ItemStack held = container.getItem(i);
                if (!held.isEmpty() && ItemStack.isSameItemSameTags(held, stack) && container.canPlaceItem(i, stack)) {
                    int room = Math.min(held.getMaxStackSize(), limit) - held.getCount();
                    if (room > 0) {
                        int moved = Math.min(room, stack.getCount());
                        held.grow(moved);
                        stack.shrink(moved);
                    }
                }
            }
            for (int i = 0; i < size() && !stack.isEmpty(); i++) {
                if (container.getItem(i).isEmpty() && container.canPlaceItem(i, stack)) {
                    container.setItem(i, stack.split(Math.min(stack.getCount(), Math.min(stack.getMaxStackSize(), limit))));
                }
            }
        }

        @Override
        void changed() {
            container.setChanged();
        }
    }

    /** An armour stand: head, chest, legs, feet, main hand, off hand. */
    public static final class StandHolder extends Holder {
        final ArmorStand stand;

        StandHolder(String name, ArmorStand stand) {
            super(name);
            this.stand = stand;
        }

        @Override
        public int size() {
            return STAND_SLOTS.length;
        }

        @Override
        public ItemStack get(int position) {
            return stand.getItemBySlot(STAND_SLOTS[position]);
        }

        @Override
        ItemStack take(int position) {
            ItemStack held = stand.getItemBySlot(STAND_SLOTS[position]);
            stand.setItemSlot(STAND_SLOTS[position], ItemStack.EMPTY);
            return held;
        }

        @Override
        boolean put(int position, ItemStack stack) {
            if (!stand.getItemBySlot(STAND_SLOTS[position]).isEmpty()) {
                return false;
            }
            stand.setItemSlot(STAND_SLOTS[position], stack);
            return true;
        }

        @Override
        void insert(ItemStack stack) {
            // stands only take what was on a stand (slot by slot)
        }

        @Override
        void changed() {
        }
    }

    /** An item frame: its item (and how it is turned). */
    public static final class FrameHolder extends Holder {
        final ItemFrame frame;
        int rotation;

        FrameHolder(String name, ItemFrame frame) {
            super(name);
            this.frame = frame;
            this.rotation = frame.getRotation();
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public ItemStack get(int position) {
            return frame.getItem();
        }

        @Override
        ItemStack take(int position) {
            ItemStack held = frame.getItem().copy();
            frame.setItem(ItemStack.EMPTY, false);
            return held;
        }

        @Override
        boolean put(int position, ItemStack stack) {
            if (!frame.getItem().isEmpty()) {
                return false;
            }
            frame.setItem(stack, false);
            return true;
        }

        @Override
        void insert(ItemStack stack) {
            // frames only take what was in a frame
        }

        @Override
        void changed() {
        }
    }

    // ------------------------------------------------------------------ finding

    /** Everything stored in {@code box} (loaded chunks are loaded). */
    public static Found scan(ServerLevel level, AABB box) {
        Found found = new Found();
        Map<String, Holder> named = new java.util.TreeMap<>();
        int cx0 = (int) Math.floor(box.minX) >> 4;
        int cx1 = (int) Math.floor(box.maxX - 1.0E-4) >> 4;
        int cz0 = (int) Math.floor(box.minZ) >> 4;
        int cz1 = (int) Math.floor(box.maxZ - 1.0E-4) >> 4;
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                LevelChunk chunk = level.getChunk(cx, cz);
                for (BlockEntity blockEntity : List.copyOf(chunk.getBlockEntities().values())) {
                    BlockPos pos = blockEntity.getBlockPos();
                    if (!(blockEntity instanceof Container) || !box.contains(pos.getX() + 0.5, pos.getY() + 0.5,
                            pos.getZ() + 0.5)) {
                        continue;
                    }
                    add(found, named, new ContainerHolder(slotOf(blockEntity.getPersistentData()), blockEntity));
                }
            }
        }
        for (Entity entity : level.getEntities((Entity) null, box, e -> !e.isRemoved())) {
            if (entity instanceof ArmorStand stand) {
                add(found, named, new StandHolder(slotOf(stand.getPersistentData()), stand));
            } else if (entity instanceof ItemFrame frame) {
                add(found, named, new FrameHolder(slotOf(frame.getPersistentData()), frame));
            } else if (entity instanceof ItemEntity item && !item.getItem().isEmpty()) {
                found.loose.add(item);
            }
        }
        found.named.putAll(named);
        found.loose.sort(Comparator.comparingInt(Entity::getId));
        return found;
    }

    private static void add(Found found, Map<String, Holder> named, Holder holder) {
        if (holder.name() == null || named.putIfAbsent(holder.name(), holder) != null) {
            found.unnamed.add(holder);
        }
    }

    private static String slotOf(CompoundTag data) {
        String name = data.getString(SLOT);
        return name.isEmpty() ? null : name;
    }

    // ------------------------------------------------------------------ moving

    /**
     * Moves everything in {@code from} into {@code to}: each named piece into the piece of the same name and kind
     * position by position, then the rest (and what did not fit there) into any free space of {@code to}'s
     * containers. Frames keep their turn. @return what found no place (the caller keeps it safe)
     */
    public static List<ItemStack> move(Found from, Found to) {
        List<ItemStack> pool = new ArrayList<>();
        for (Holder source : from.holders()) {
            Holder target = source.name() == null ? null : to.named.get(source.name());
            boolean slotBySlot = target != null && target.sameKind(source);
            for (int i = 0; i < source.size(); i++) {
                if (source.get(i).isEmpty()) {
                    continue;
                }
                ItemStack stack = source.take(i);
                if (slotBySlot && i < target.size() && target.put(i, stack)) {
                    continue;
                }
                pool.add(stack);
            }
            if (slotBySlot && source instanceof FrameHolder frame && target instanceof FrameHolder destination
                    && !destination.frame.getItem().isEmpty()) {
                destination.frame.setRotation(frame.rotation);
            }
            source.changed();
            if (target != null) {
                target.changed();
            }
        }
        for (ItemEntity item : from.loose) {
            pool.add(item.getItem().copy());
            item.discard();
        }
        return insertAnywhere(to, pool);
    }

    /** Puts {@code stacks} into any free container space of {@code into}. @return what did not fit */
    public static List<ItemStack> insertAnywhere(Found into, List<ItemStack> stacks) {
        List<ItemStack> left = new ArrayList<>();
        for (ItemStack stack : stacks) {
            for (Holder holder : into.holders()) {
                if (stack.isEmpty()) {
                    break;
                }
                if (holder instanceof ContainerHolder) {
                    holder.insert(stack);
                    holder.changed();
                }
            }
            if (!stack.isEmpty()) {
                left.add(stack);
            }
        }
        return left;
    }

    /** Takes everything out of {@code found} (for a wipe). @return the stacks */
    public static List<ItemStack> takeAll(Found found) {
        List<ItemStack> taken = new ArrayList<>();
        for (Holder holder : found.holders()) {
            for (int i = 0; i < holder.size(); i++) {
                if (!holder.get(i).isEmpty()) {
                    taken.add(holder.take(i));
                }
            }
            holder.changed();
        }
        for (ItemEntity item : found.loose) {
            taken.add(item.getItem().copy());
            item.discard();
        }
        return taken;
    }
}
