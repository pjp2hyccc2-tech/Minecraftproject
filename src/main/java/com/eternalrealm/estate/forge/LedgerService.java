package com.eternalrealm.estate.forge;

import com.eternalrealm.estate.DepositPlanner;
import com.eternalrealm.estate.EstateKey;
import com.eternalrealm.estate.Ledger;
import com.eternalrealm.estate.StorageLabel;
import com.eternalrealm.item.bag.Bag;
import com.eternalrealm.item.forge.ConsumableItem;
import com.eternalrealm.server.UiActions;
import com.eternalrealm.server.UiArgs;
import com.eternalrealm.server.UiData;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The sorting desk's ledger (docs/modules/estates.md): everything in the room's chests, barrels and shelves as one
 * list (one line per kind of thing, with where it lies), to take out by the stack or all at once, and "put away" that
 * empties the bag into the room by the chests' labels ({@link DepositPlanner}). Everything is decided here; the
 * screen only shows the document ({@value #CHANNEL}) and sends requests. The room is read afresh for every request,
 * so it is always what lies in the chests right now (someone may have opened one meanwhile). Server thread.
 */
public final class LedgerService {
    public static final String CHANNEL = "estate.ledger";
    public static final String VIEW = "estate.ledger.view";
    public static final String TAKE = "estate.ledger.take";
    public static final String DEPOSIT = "estate.ledger.deposit";
    public static final String NOT_HERE = "message.eternalrealm.ledger.not_here";
    public static final String AWAY = "message.eternalrealm.ledger.away";
    /** How far from the desk the ledger keeps working (blocks). */
    static final double REACH = 6.0;
    /** At most this many lines (and about this many characters) per document; the rest comes page by page. */
    static final int MAX_ENTRIES = 300;
    static final int MAX_CHARS = 90_000;
    /** Item data longer than this is not sent (the line shows the name only). */
    static final int MAX_TAG_CHARS = 900;
    /** "all" or a label id: what the ledger shows. */
    static final String ALL = "all";

    private record Desk(ResourceKey<Level> dimension, BlockPos pos) {
    }

    private static final Map<UUID, Desk> DESKS = new HashMap<>();

    /** What a put-away did. */
    public record Deposit(int items, int stacks, int left, int boxes) {
    }

    private LedgerService() {
    }

    static void register() {
        UiActions.register(VIEW, (player, context, args) -> {
            String kind = kind(args);
            int offset = (int) UiArgs.number(args, "offset", 0, 100_000);
            desk(player).ifPresent(key -> send(player, key, kind, offset, false, null));
        });
        UiActions.register(TAKE, (player, context, args) -> {
            String itemKey = UiArgs.string(args, "key", 160);
            boolean all = UiArgs.flag(args, "all");
            String kind = kind(args);
            int offset = (int) UiArgs.number(args, "offset", 0, 100_000);
            desk(player).ifPresent(key -> {
                int taken = take(player, key, itemKey, all);
                JsonObject result = new JsonObject();
                result.addProperty("took", taken);
                result.addProperty("full", taken == 0);
                send(player, key, kind, offset, false, result);
            });
        });
        UiActions.register(DEPOSIT, (player, context, args) -> {
            String what = args.has("what") ? checkedKind(UiArgs.string(args, "what", 16)) : ALL;
            String kind = kind(args);
            int offset = (int) UiArgs.number(args, "offset", 0, 100_000);
            desk(player).ifPresent(key -> {
                Optional<StorageLabel> only = ALL.equals(what) ? Optional.empty() : StorageLabel.byId(what);
                Deposit done = deposit(player, key, only);
                JsonObject result = new JsonObject();
                result.addProperty("deposited", done.items());
                result.addProperty("stacks", done.stacks());
                result.addProperty("left", done.left());
                result.addProperty("boxes", done.boxes());
                send(player, key, kind, offset, false, result);
            });
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) ->
                DESKS.remove(event.getEntity().getUUID()));
    }

    private static String kind(JsonObject args) {
        return args.has("kind") ? checkedKind(UiArgs.string(args, "kind", 16)) : ALL;
    }

    private static String checkedKind(String kind) {
        if (!ALL.equals(kind) && StorageLabel.byId(kind).filter(label -> label != StorageLabel.NONE).isEmpty()) {
            throw new UiArgs.InvalidArgumentException("unknown kind " + kind);
        }
        return kind;
    }

    // ------------------------------------------------------------------ opening

    /** A player used the sorting desk at {@code pos}: the ledger of the room it stands in. */
    public static void open(ServerPlayer player, BlockPos pos) {
        Optional<EstateKey> key = EstateService.at(player.level(), pos);
        if (key.isEmpty() || !EstateService.mayAccess(player, key.get())) {
            player.displayClientMessage(Component.translatable(NOT_HERE).withStyle(ChatFormatting.GRAY), true);
            return;
        }
        DESKS.put(player.getUUID(), new Desk(player.level().dimension(), pos.immutable()));
        player.level().playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.9F, 1.0F);
        send(player, key.get(), ALL, 0, true, null);
    }

    /** The room of the desk the player last opened, while they stay near it. */
    private static Optional<EstateKey> desk(ServerPlayer player) {
        Desk desk = DESKS.get(player.getUUID());
        if (desk == null || player.level().dimension() != desk.dimension()
                || player.distanceToSqr(Vec3.atCenterOf(desk.pos())) > REACH * REACH
                || !(player.level().getBlockState(desk.pos()).getBlock() instanceof SortingDeskBlock)) {
            player.displayClientMessage(Component.translatable(AWAY).withStyle(ChatFormatting.GRAY), true);
            return Optional.empty();
        }
        return EstateService.at(player.level(), desk.pos()).filter(key -> EstateService.mayAccess(player, key));
    }

    static void send(ServerPlayer player, EstateKey key, String kind, int offset, boolean open, JsonObject result) {
        JsonObject data = document(key, kind, offset);
        data.addProperty("open", open);
        if (result != null) {
            data.add("result", result);
        }
        data.addProperty("nonce", System.nanoTime());
        UiData.send(player, CHANNEL, data);
    }

    // ------------------------------------------------------------------ the room's storage

    /** The containers of a room in the order the ledger names them: chests, barrels, shelves, then others. */
    static List<EstateContents.ContainerHolder> boxes(EstateContents.Found found) {
        List<EstateContents.ContainerHolder> boxes = new ArrayList<>();
        for (EstateContents.Holder holder : found.holders()) {
            if (holder instanceof EstateContents.ContainerHolder container) {
                boxes.add(container);
            }
        }
        boxes.sort(Comparator.comparingInt((EstateContents.ContainerHolder box) -> kindOrder(box.name()))
                .thenComparingInt(box -> number(box.name())));
        return boxes;
    }

    private static int kindOrder(String name) {
        if (name == null) {
            return 9;
        }
        String kind = name.substring(0, Math.max(0, name.indexOf('.')));
        return switch (kind) {
            case "chest" -> 0;
            case "barrel" -> 1;
            case "shelf" -> 2;
            default -> 3;
        };
    }

    private static int number(String name) {
        if (name == null || name.indexOf('.') < 0) {
            return 0;
        }
        try {
            return Integer.parseInt(name.substring(name.indexOf('.') + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static StorageLabel label(EstateContents.ContainerHolder box) {
        Level level = box.blockEntity.getLevel();
        return level == null ? StorageLabel.NONE : StorageChestBlock.labelAt(level, box.pos()).label();
    }

    static boolean isShelf(EstateContents.ContainerHolder box) {
        return box.blockEntity instanceof ChiseledBookShelfBlockEntity;
    }

    /** The ledger document of {@code key}, showing {@code kind} from line {@code offset} on. */
    public static JsonObject document(EstateKey key, String kind, int offset) {
        JsonObject data = new JsonObject();
        data.addProperty("room", key.design());
        data.addProperty("kind", kind);
        Optional<EstateContents.Found> found = EstateService.contents(key);
        if (found.isEmpty()) {
            data.addProperty("unavailable", true);
            return data;
        }
        List<EstateContents.ContainerHolder> boxes = boxes(found.get());
        JsonArray boxJson = new JsonArray();
        List<Ledger.Stored> stored = new ArrayList<>();
        Map<String, ItemStack> samples = new HashMap<>();
        int used = 0;
        int slots = 0;
        for (int b = 0; b < boxes.size(); b++) {
            EstateContents.ContainerHolder box = boxes.get(b);
            int boxUsed = 0;
            for (int i = 0; i < box.size(); i++) {
                ItemStack stack = box.get(i);
                if (stack.isEmpty()) {
                    continue;
                }
                boxUsed++;
                String itemKey = EstateItems.key(stack);
                samples.putIfAbsent(itemKey, stack);
                stored.add(new Ledger.Stored(String.format(Locale.ROOT, "%03d", b), itemKey, stack.getCount(),
                        EstateItems.kind(stack)));
            }
            JsonObject json = new JsonObject();
            json.addProperty("name", box.name() == null ? "" : box.name());
            json.addProperty("label", label(box).id());
            json.addProperty("used", boxUsed);
            json.addProperty("size", box.size());
            boxJson.add(json);
            used += boxUsed;
            slots += box.size();
        }
        data.add("boxes", boxJson);
        data.addProperty("used", used);
        data.addProperty("slots", slots);
        List<Ledger.Entry> entries = Ledger.aggregate(stored);
        Map<StorageLabel, Integer> perKind = new EnumMap<>(StorageLabel.class);
        for (Ledger.Entry entry : entries) {
            perKind.merge(entry.kind(), 1, Integer::sum);
        }
        JsonObject kinds = new JsonObject();
        kinds.addProperty(ALL, entries.size());
        for (StorageLabel label : StorageLabel.values()) {
            if (label != StorageLabel.NONE) {
                kinds.addProperty(label.id(), perKind.getOrDefault(label, 0));
            }
        }
        data.add("kinds", kinds);
        List<Ledger.Entry> shown = ALL.equals(kind) ? entries
                : entries.stream().filter(entry -> entry.kind().id().equals(kind)).toList();
        int start = Math.max(0, Math.min(offset, Math.max(0, shown.size() - 1)));
        JsonArray lines = new JsonArray();
        int chars = 0;
        int index = start;
        for (; index < shown.size() && lines.size() < MAX_ENTRIES && chars < MAX_CHARS; index++) {
            Ledger.Entry entry = shown.get(index);
            JsonObject line = line(entry, samples.get(entry.itemKey()));
            chars += line.toString().length();
            lines.add(line);
        }
        data.addProperty("total", shown.size());
        data.addProperty("offset", start);
        data.addProperty("next", index < shown.size() ? index : -1);
        data.add("entries", lines);
        return data;
    }

    private static JsonObject line(Ledger.Entry entry, ItemStack sample) {
        JsonObject line = new JsonObject();
        line.addProperty("key", entry.itemKey());
        line.addProperty("item", String.valueOf(ForgeRegistries.ITEMS.getKey(sample.getItem())));
        if (sample.hasTag()) {
            String tag = String.valueOf(sample.getTag());
            if (tag.length() <= MAX_TAG_CHARS) {
                line.addProperty("tag", tag);
            } else {
                line.addProperty("name", Component.Serializer.toJson(sample.getHoverName()));
            }
        }
        line.addProperty("count", entry.count());
        line.addProperty("stacks", entry.stacks());
        line.addProperty("kind", entry.kind().id());
        JsonArray places = new JsonArray();
        for (String place : entry.places()) {
            places.add(Integer.parseInt(place));
        }
        line.add("places", places);
        return line;
    }

    // ------------------------------------------------------------------ taking out

    /**
     * Takes things with {@code itemKey} out of the room's containers into the bag: one stack's worth, or as much as the
     * bag holds. Exactly what reaches the bag leaves the chests. @return the number of items taken
     */
    public static int take(ServerPlayer player, EstateKey key, String itemKey, boolean all) {
        Optional<EstateContents.Found> found = EstateService.contents(key);
        if (found.isEmpty()) {
            return 0;
        }
        Inventory inventory = player.getInventory();
        int taken = 0;
        int budget = -1;
        Set<BlockPos> touched = new LinkedHashSet<>();
        for (EstateContents.ContainerHolder box : boxes(found.get())) {
            for (int i = 0; i < box.size(); i++) {
                ItemStack stack = box.get(i);
                if (stack.isEmpty() || !EstateItems.key(stack).equals(itemKey)) {
                    continue;
                }
                if (budget < 0) {
                    budget = all ? Integer.MAX_VALUE : stack.getMaxStackSize();
                }
                int want = Math.min(budget - taken, stack.getCount());
                int room = roomFor(inventory, stack) + com.eternalrealm.item.forge.BagBack.roomFor(player, stack);
                int moving = Math.min(want, room);
                if (moving <= 0) {
                    return finishTake(player, taken, touched);
                }
                ItemStack part = box.container().removeItem(i, moving);
                int put = insert(inventory, part);
                if (!part.isEmpty()) {
                    put += com.eternalrealm.item.forge.BagBack.insert(player, part); // the front was full: the back
                }
                if (!part.isEmpty()) {
                    // cannot happen (room was counted); never lose it
                    box.insert(part);
                }
                box.changed();
                taken += put;
                touched.add(box.pos());
                if (taken >= budget) {
                    return finishTake(player, taken, touched);
                }
            }
        }
        return finishTake(player, taken, touched);
    }

    private static int finishTake(ServerPlayer player, int taken, Set<BlockPos> touched) {
        if (taken > 0) {
            inventoryChanged(player);
            sparkle(player.serverLevel(), touched);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUNDLE_REMOVE_ONE,
                    SoundSource.PLAYERS, 0.8F, 1.0F);
        } else {
            player.displayClientMessage(Component.translatable("message.eternalrealm.ledger.bag_full")
                    .withStyle(ChatFormatting.RED), true);
        }
        return taken;
    }

    /** How many of {@code stack} the bag (slots 1..35) can still hold. */
    static int roomFor(Inventory inventory, ItemStack stack) {
        int room = 0;
        int max = Math.min(stack.getMaxStackSize(), inventory.getMaxStackSize());
        for (int slot = Bag.FIRST_SLOT; slot <= Bag.LAST_SLOT; slot++) {
            ItemStack held = inventory.items.get(slot);
            if (held.isEmpty()) {
                room += max;
            } else if (ItemStack.isSameItemSameTags(held, stack)) {
                room += Math.max(0, max - held.getCount());
            }
        }
        return room;
    }

    /** Puts {@code stack} into the bag (merging first); it shrinks by what went in. @return how many went in */
    static int insert(Inventory inventory, ItemStack stack) {
        int before = stack.getCount();
        int max = Math.min(stack.getMaxStackSize(), inventory.getMaxStackSize());
        for (int slot = Bag.FIRST_SLOT; slot <= Bag.LAST_SLOT && !stack.isEmpty(); slot++) {
            ItemStack held = inventory.items.get(slot);
            if (!held.isEmpty() && ItemStack.isSameItemSameTags(held, stack) && held.getCount() < max) {
                int moved = Math.min(max - held.getCount(), stack.getCount());
                held.grow(moved);
                stack.shrink(moved);
            }
        }
        for (int slot = Bag.FIRST_SLOT; slot <= Bag.LAST_SLOT && !stack.isEmpty(); slot++) {
            if (inventory.items.get(slot).isEmpty()) {
                inventory.items.set(slot, stack.split(Math.min(max, stack.getCount())));
            }
        }
        return before - stack.getCount();
    }

    // ------------------------------------------------------------------ putting away

    /**
     * Empties the bag into the room by the chests' labels: everything ({@code only} empty; the field kit of potions,
     * tonics and scrolls stays in the bag) or one kind of thing (all of it). Slot 0, the weapon in hand, is never
     * touched. What waits in the bag's back comes forward into the room's way as the front empties (a few rounds).
     * @return what happened
     */
    public static Deposit deposit(ServerPlayer player, EstateKey key, Optional<StorageLabel> only) {
        Deposit total = depositFront(player, key, only);
        for (int round = 0; round < 4 && total.items() > 0 && com.eternalrealm.item.forge.BagBack.refill(player);
             round++) {
            Deposit more = depositFront(player, key, only);
            total = new Deposit(total.items() + more.items(), total.stacks() + more.stacks(), more.left(),
                    Math.max(total.boxes(), more.boxes()));
            if (more.items() == 0) {
                break;
            }
        }
        return total;
    }

    private static Deposit depositFront(ServerPlayer player, EstateKey key, Optional<StorageLabel> only) {
        Optional<EstateContents.Found> found = EstateService.contents(key);
        if (found.isEmpty()) {
            return new Deposit(0, 0, 0, 0);
        }
        List<EstateContents.ContainerHolder> boxes = boxes(found.get());
        List<DepositPlanner.Box> plannerBoxes = new ArrayList<>();
        for (EstateContents.ContainerHolder box : boxes) {
            boolean shelf = isShelf(box);
            List<DepositPlanner.Slot> slots = new ArrayList<>();
            for (int i = 0; i < box.size(); i++) {
                ItemStack stack = box.get(i);
                slots.add(stack.isEmpty() ? DepositPlanner.Slot.EMPTY
                        : new DepositPlanner.Slot(EstateItems.key(stack), stack.getCount()));
            }
            plannerBoxes.add(new DepositPlanner.Box(box.name() == null ? "" : box.name(), label(box), shelf,
                    shelf ? 1 : Math.min(64, box.container().getMaxStackSize()), slots));
        }
        Inventory inventory = player.getInventory();
        List<DepositPlanner.Stack> stacks = new ArrayList<>();
        for (int slot = Bag.FIRST_SLOT; slot <= Bag.LAST_SLOT; slot++) {
            ItemStack stack = inventory.items.get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            StorageLabel kind = EstateItems.kind(stack);
            if (only.isPresent() ? kind != only.get() : stack.getItem() instanceof ConsumableItem) {
                continue;
            }
            stacks.add(new DepositPlanner.Stack(slot, EstateItems.key(stack), stack.getCount(),
                    stack.getMaxStackSize(), kind, EstateItems.isShelfBook(stack)));
        }
        List<DepositPlanner.Move> moves = DepositPlanner.plan(plannerBoxes, stacks);
        int items = 0;
        Set<BlockPos> touched = new LinkedHashSet<>();
        Set<Integer> emptied = new LinkedHashSet<>();
        for (DepositPlanner.Move move : moves) {
            ItemStack from = inventory.items.get(move.bagSlot());
            EstateContents.ContainerHolder box = boxes.get(move.box());
            ItemStack there = box.get(move.slot());
            int count = Math.min(move.count(), from.getCount());
            if (count <= 0) {
                continue;
            }
            if (there.isEmpty()) {
                ItemStack part = from.copyWithCount(count);
                if (!box.container().canPlaceItem(move.slot(), part)) {
                    continue;
                }
                box.container().setItem(move.slot(), part);
            } else if (ItemStack.isSameItemSameTags(there, from)
                    && there.getCount() + count <= Math.min(there.getMaxStackSize(), box.container().getMaxStackSize())) {
                there.grow(count);
            } else {
                continue;
            }
            from.shrink(count);
            if (from.isEmpty()) {
                inventory.items.set(move.bagSlot(), ItemStack.EMPTY);
                emptied.add(move.bagSlot());
            }
            box.changed();
            items += count;
            touched.add(box.pos());
        }
        int left = 0;
        for (DepositPlanner.Stack stack : stacks) {
            if (!inventory.items.get(stack.bagSlot()).isEmpty()) {
                left++;
            }
        }
        if (items > 0) {
            inventoryChanged(player);
            sparkle(player.serverLevel(), touched);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUNDLE_INSERT,
                    SoundSource.PLAYERS, 0.8F, 1.0F);
        }
        return new Deposit(items, emptied.size(), left, touched.size());
    }

    private static void inventoryChanged(ServerPlayer player) {
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
    }

    /** A little glint on every chest that was used, so the player sees where things went or came from. */
    private static void sparkle(ServerLevel level, Set<BlockPos> boxes) {
        for (BlockPos pos : boxes) {
            level.sendParticles(ParticleTypes.WAX_ON, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 6, 0.3, 0.15,
                    0.3, 0.0);
        }
    }
}
