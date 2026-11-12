package com.eternalrealm.item.forge;

import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.combat.CombatStats;
import com.eternalrealm.combat.Combatant;
import com.eternalrealm.item.Cost;
import com.eternalrealm.item.Crafting;
import com.eternalrealm.item.Enhancement;
import com.eternalrealm.item.EquipmentData;
import com.eternalrealm.item.Recipe;
import com.eternalrealm.item.Recipes;
import com.eternalrealm.item.Reroll;
import com.eternalrealm.network.forge.ERNetwork;
import com.eternalrealm.network.forge.UiDataPacket;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.server.UiActions;
import com.eternalrealm.server.UiArgs;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.SplittableRandom;
import java.util.UUID;

/**
 * The blacksmith on the server. The client's smithy screen ({@code ui.screen.SmithScreen}) shows everything from the
 * player's own inventory and the pure rules ({@link Enhancement}, {@link Reroll}, {@link Crafting}); it only sends
 * intents — enhance or reroll the piece in inventory slot N with id U, craft recipe R — and the server checks them
 * all again (the piece is still there, the stones/materials and gold are there) before it takes anything. The outcome
 * goes back on {@link #RESULT_CHANNEL} so the screen can play its success or failure animation. Worn trinkets are
 * worked on where they are ({@link #FIRST_TRINKET_SLOT}).
 */
public final class SmithService {
    /** S2C: open the smithy screen ({@code {"npc": id}}). */
    public static final String CHANNEL = "smith";
    /** S2C: the outcome of an action ({@code {"kind", "outcome", "plus", "name"}}). */
    public static final String RESULT_CHANNEL = "smith_result";
    public static final String ENHANCE = "smith.enhance";
    public static final String REROLL = "smith.reroll";
    public static final String CRAFT = "smith.craft";
    /** Smithy slots 41..46 are the worn trinkets ({@link TrinketSlots.Slot} in storage order). */
    public static final int FIRST_TRINKET_SLOT = 41;
    public static final int LAST_SLOT = FIRST_TRINKET_SLOT + 5;
    private static final SplittableRandom RANDOM = new SplittableRandom();

    private SmithService() {
    }

    public static void register() {
        UiActions.register(ENHANCE, (player, context, args) -> enhance(player, context, slot(args), UiArgs.uuid(args, "id")));
        UiActions.register(REROLL, (player, context, args) -> reroll(player, context, slot(args), UiArgs.uuid(args, "id")));
        UiActions.register(CRAFT, (player, context, args) -> craft(player, context, UiArgs.id(args, "recipe")));
    }

    /** Opens the smithy screen on the player's client. */
    public static void open(ServerPlayer player, String npcId) {
        JsonObject data = new JsonObject();
        data.addProperty("npc", npcId);
        data.addProperty("t", System.nanoTime());
        ERNetwork.sendToPlayer(player, new UiDataPacket(CHANNEL, data.toString()));
    }

    private static int slot(JsonObject args) {
        return (int) UiArgs.number(args, "slot", 0, LAST_SLOT);
    }

    /** The worn trinket slot of a smithy slot (41..46), if it is one. */
    public static Optional<TrinketSlots.Slot> trinketSlot(int slot) {
        int index = slot - FIRST_TRINKET_SLOT;
        TrinketSlots.Slot[] slots = TrinketSlots.Slot.values();
        return index >= 0 && index < slots.length ? Optional.of(slots[index]) : Optional.empty();
    }

    /**
     * The stack in {@code slot} of the smithy's numbering: 0..35 the main inventory, 36..39 the worn armour (feet,
     * legs, chest, head), 40 the off hand (the worn trinkets, 41..46, live elsewhere: {@link #stackAt(ServerPlayer,
     * int)}).
     */
    public static ItemStack stackAt(net.minecraft.world.entity.player.Inventory inventory, int slot) {
        if (slot >= 0 && slot < inventory.items.size()) {
            return inventory.items.get(slot);
        }
        if (slot >= 36 && slot < 36 + inventory.armor.size()) {
            return inventory.armor.get(slot - 36);
        }
        return slot == 40 ? inventory.offhand.get(0) : ItemStack.EMPTY;
    }

    /** The stack in a smithy slot, worn trinkets included (a copy for those: write it back with {@link #store}). */
    static ItemStack stackAt(ServerPlayer player, int slot) {
        Optional<TrinketSlots.Slot> trinket = trinketSlot(slot);
        return trinket.isPresent() ? TrinketSlots.get(player, trinket.get()) : stackAt(player.getInventory(), slot);
    }

    /** After a change: a worn trinket goes back into its slot (stats re-derived, client told). */
    private static void store(ServerPlayer player, int slot, ItemStack stack) {
        Optional<TrinketSlots.Slot> trinket = trinketSlot(slot);
        if (trinket.isPresent()) {
            TrinketSlots.set(player, trinket.get(), stack);
        } else {
            player.getInventory().setChanged();
        }
    }

    /** The equipment in {@code slot}, if it is still the piece the screen showed. */
    private static ItemStack piece(ServerPlayer player, int slot, UUID id) {
        ItemStack stack = stackAt(player, slot);
        Optional<EquipmentData> data = EquipmentStacks.read(stack);
        return data.isPresent() && data.get().uniqueId().equals(id) ? stack : ItemStack.EMPTY;
    }

    private static void result(ServerPlayer player, String kind, String outcome, int plus, Component name) {
        JsonObject data = new JsonObject();
        data.addProperty("kind", kind);
        data.addProperty("outcome", outcome);
        data.addProperty("plus", plus);
        data.addProperty("name", Component.Serializer.toJson(name));
        data.addProperty("t", System.nanoTime());
        ERNetwork.sendToPlayer(player, new UiDataPacket(RESULT_CHANNEL, data.toString()));
    }

    static void enhance(ServerPlayer player, ServerContext context, int slot, UUID id) {
        ItemStack stack = piece(player, slot, id);
        Optional<EquipmentData> data = EquipmentStacks.read(stack);
        if (data.isEmpty()) {
            return;
        }
        Enhancement.Check check = Enhancement.check(data.get(), Inventories.counts(player), gold(context, player));
        if (check != Enhancement.Check.OK) {
            player.displayClientMessage(Component.translatable(check.translationKey()).withStyle(ChatFormatting.RED), true);
            return;
        }
        Cost cost = Enhancement.cost(data.get());
        if (!context.playerData().spendGold(player.getUUID(), cost.gold()) || !Inventories.take(player, cost.items())) {
            return;
        }
        Enhancement.Result outcome = Enhancement.attempt(data.get(), RANDOM);
        EquipmentStacks.write(stack, outcome.item());
        store(player, slot, stack);
        boolean success = outcome.outcome() == Enhancement.Outcome.SUCCESS;
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                success ? com.eternalrealm.sound.ERSounds.ENHANCE_SUCCESS.get()
                        : com.eternalrealm.sound.ERSounds.ENHANCE_FAIL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_USE,
                SoundSource.PLAYERS, 0.6F, success ? 1.2F : 0.8F);
        result(player, "enhance", success ? "success" : "failure", outcome.item().enhancement(),
                stack.getHoverName());
        context.combat().markStatsDirty(player.getUUID());
    }

    static void reroll(ServerPlayer player, ServerContext context, int slot, UUID id) {
        ItemStack stack = piece(player, slot, id);
        Optional<EquipmentData> data = EquipmentStacks.read(stack);
        if (data.isEmpty()) {
            return;
        }
        Reroll.Check check = Reroll.check(data.get(), Inventories.counts(player), gold(context, player));
        if (check != Reroll.Check.OK) {
            player.displayClientMessage(Component.translatable(check.translationKey()).withStyle(ChatFormatting.RED), true);
            return;
        }
        Cost cost = Reroll.cost(data.get());
        if (!context.playerData().spendGold(player.getUUID(), cost.gold()) || !Inventories.take(player, cost.items())) {
            return;
        }
        EquipmentStacks.write(stack, Reroll.reroll(data.get(), RANDOM));
        store(player, slot, stack);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 1.0F, 1.0F);
        result(player, "reroll", "success", data.get().enhancement(), stack.getHoverName());
        context.combat().markStatsDirty(player.getUUID());
    }

    static void craft(ServerPlayer player, ServerContext context, String recipeId) {
        Optional<Recipe> recipe = Recipes.byId(recipeId);
        if (recipe.isEmpty()) {
            throw new UiArgs.InvalidArgumentException("unknown recipe " + recipeId);
        }
        Crafting.Check check = Crafting.check(recipe.get(), Inventories.counts(player), gold(context, player),
                level(context, player));
        if (!check.isOk()) {
            player.displayClientMessage(Component.translatable(check.result().translationKey())
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!context.playerData().spendGold(player.getUUID(), check.goldToRemove())
                || !Inventories.take(player, check.ingredientsToRemove())) {
            return;
        }
        double luck = context.combat().service().get(player.getUUID()).map(Combatant::stats)
                .map(CombatStats::lootBonus).orElse(0.0);
        ItemStack made = EquipmentStacks.create(Crafting.craft(recipe.get(), luck, RANDOM));
        Component name = made.getHoverName();
        Inventories.give(player, made);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SMITHING_TABLE_USE,
                SoundSource.PLAYERS, 1.0F, 1.0F);
        result(player, "craft", "success", 0, name);
        context.game().rankings().onItemCrafted(player, 1);
    }

    private static long gold(ServerContext context, ServerPlayer player) {
        return context.playerData().snapshot(player.getUUID()).map(CharacterSnapshot::gold).orElse(0L);
    }

    private static int level(ServerContext context, ServerPlayer player) {
        return context.playerData().snapshot(player.getUUID()).map(CharacterSnapshot::level).orElse(1);
    }
}
