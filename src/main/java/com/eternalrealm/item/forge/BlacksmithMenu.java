package com.eternalrealm.item.forge;

import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.combat.CombatStats;
import com.eternalrealm.combat.Combatant;
import com.eternalrealm.item.Cost;
import com.eternalrealm.item.Crafting;
import com.eternalrealm.item.Enhancement;
import com.eternalrealm.item.EquipmentData;
import com.eternalrealm.item.EquipmentStats;
import com.eternalrealm.item.ItemTier;
import com.eternalrealm.item.Recipe;
import com.eternalrealm.item.Recipes;
import com.eternalrealm.item.Reroll;
import com.eternalrealm.menu.MenuItems;
import com.eternalrealm.menu.ServerMenu;
import com.eternalrealm.server.ServerContext;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.UUID;

/**
 * The blacksmith: enhancement (+1 per attempt with enhancement stones and gold, may fail), reroll of the affixes
 * (reroll crystals and gold) and crafting of tiered equipment from monster materials. The piece to work on is chosen
 * by clicking it in the player's own inventory; every action is checked against the inventory and gold again on the
 * server before anything is taken.
 */
public final class BlacksmithMenu {
    private static final SplittableRandom RANDOM = new SplittableRandom();

    private enum Page { MAIN, ENHANCE, REROLL, CRAFT }

    /** What the open menu shows. */
    private static final class State {
        Page page = Page.MAIN;
        int selectedSlot = -1;
        UUID selectedId;
        ItemTier craftTier = ItemTier.IRON;
    }

    private BlacksmithMenu() {
    }

    public static void open(ServerPlayer player) {
        State state = new State();
        ServerMenu.open(player, Component.translatable("screen.eternalrealm.smith.title"), 6, menu -> fill(menu, state));
    }

    private static void fill(ServerMenu menu, State state) {
        for (int slot = 0; slot < menu.size(); slot++) {
            menu.set(slot, MenuItems.filler(), null);
        }
        tab(menu, state, 1, Page.ENHANCE, Items.ANVIL, "screen.eternalrealm.smith.enhance");
        tab(menu, state, 4, Page.REROLL, Items.AMETHYST_SHARD, "screen.eternalrealm.smith.reroll");
        tab(menu, state, 7, Page.CRAFT, Items.SMITHING_TABLE, "screen.eternalrealm.smith.craft");
        switch (state.page) {
            case ENHANCE, REROLL -> fillWork(menu, state);
            case CRAFT -> fillCraft(menu, state);
            default -> menu.set(22, MenuItems.icon(Items.BOOK, Component.translatable("screen.eternalrealm.smith.title")
                    .withStyle(ChatFormatting.GOLD), List.of(Component.translatable("screen.eternalrealm.smith.hint"))),
                    null);
        }
        menu.onInventoryClick((player, inventorySlot, stack, shift) -> {
            Optional<EquipmentData> data = EquipmentStacks.read(stack);
            if (data.isEmpty() || state.page == Page.CRAFT || state.page == Page.MAIN) {
                return;
            }
            state.selectedSlot = inventorySlot;
            state.selectedId = data.get().uniqueId();
            menu.refresh();
        });
    }

    private static void tab(ServerMenu menu, State state, int slot, Page page, Item icon, String key) {
        menu.set(slot, MenuItems.icon(new ItemStack(icon), Component.translatable(key).withStyle(state.page == page
                ? ChatFormatting.GOLD : ChatFormatting.WHITE), List.of(), state.page == page), (player, button, shift) -> {
            state.page = page;
            menu.refresh();
        });
    }

    // ------------------------------------------------------------------ enhance / reroll

    private static void fillWork(ServerMenu menu, State state) {
        ServerPlayer player = menu.viewer();
        ItemStack stack = selected(player, state);
        Optional<EquipmentData> data = EquipmentStacks.read(stack);
        if (data.isEmpty()) {
            menu.set(22, MenuItems.icon(new ItemStack(Items.ITEM_FRAME), Component.translatable("screen.eternalrealm.smith.select"),
                    List.of(Component.translatable("screen.eternalrealm.smith.select_hint")), false), null);
            return;
        }
        menu.set(20, stack.copy(), null);
        boolean enhance = state.page == Page.ENHANCE;
        List<Component> lore = new ArrayList<>();
        Map<String, Integer> counts = Inventories.counts(player);
        long gold = gold(player);
        if (enhance) {
            if (Enhancement.isMaxed(data.get())) {
                lore.add(Component.translatable("message.eternalrealm.enhancement.max_level"));
            } else {
                Cost cost = Enhancement.cost(data.get());
                describe(lore, cost);
                lore.add(Component.translatable("screen.eternalrealm.smith.chance", String.format(Locale.ROOT, "%.0f",
                        Enhancement.successChance(data.get().enhancement() + 1) * 100.0)).withStyle(ChatFormatting.AQUA));
                lore.add(Component.translatable("screen.eternalrealm.smith.bonus", String.format(Locale.ROOT, "%.0f",
                        (EquipmentStats.enhancementFactor(data.get().enhancement() + 1) - 1.0) * 100.0))
                        .withStyle(ChatFormatting.GRAY));
                lore.add(Component.translatable(Enhancement.check(data.get(), counts, gold).translationKey())
                        .withStyle(ChatFormatting.YELLOW));
            }
        } else if (!Reroll.canReroll(data.get())) {
            lore.add(Component.translatable("message.eternalrealm.reroll.no_affixes"));
        } else {
            describe(lore, Reroll.cost(data.get()));
            lore.add(Component.translatable(Reroll.check(data.get(), counts, gold).translationKey())
                    .withStyle(ChatFormatting.YELLOW));
        }
        menu.set(24, MenuItems.icon(new ItemStack(enhance ? Items.ANVIL : Items.AMETHYST_CLUSTER),
                Component.translatable(enhance ? "screen.eternalrealm.smith.do_enhance" : "screen.eternalrealm.smith.do_reroll")
                        .withStyle(ChatFormatting.GREEN), lore, true), (p, button, shift) -> {
            if (enhance) {
                enhance(p, state);
            } else {
                reroll(p, state);
            }
            menu.refresh();
        });
    }

    private static void describe(List<Component> lore, Cost cost) {
        lore.add(Component.translatable("screen.eternalrealm.smith.cost_gold", cost.gold()).withStyle(ChatFormatting.GOLD));
        for (Map.Entry<String, Integer> item : cost.items().entrySet()) {
            Item type = Inventories.item(item.getKey());
            lore.add(Component.translatable("screen.eternalrealm.smith.cost_item",
                    type == null ? Component.literal(item.getKey()) : type.getDescription(), item.getValue()));
        }
    }

    /** The selected piece, if it is still in its slot. */
    private static ItemStack selected(ServerPlayer player, State state) {
        if (state.selectedSlot < 0 || state.selectedSlot >= player.getInventory().items.size()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = player.getInventory().items.get(state.selectedSlot);
        Optional<EquipmentData> data = EquipmentStacks.read(stack);
        if (data.isEmpty() || (state.selectedId != null && !state.selectedId.equals(data.get().uniqueId()))) {
            return ItemStack.EMPTY;
        }
        return stack;
    }

    private static void enhance(ServerPlayer player, State state) {
        ItemStack stack = selected(player, state);
        Optional<EquipmentData> data = EquipmentStacks.read(stack);
        ServerContext context = ServerContext.current().orElse(null);
        if (data.isEmpty() || context == null) {
            return;
        }
        Enhancement.Check check = Enhancement.check(data.get(), Inventories.counts(player), gold(player));
        if (check != Enhancement.Check.OK) {
            player.displayClientMessage(Component.translatable(check.translationKey()).withStyle(ChatFormatting.RED), true);
            return;
        }
        Cost cost = Enhancement.cost(data.get());
        if (!context.playerData().spendGold(player.getUUID(), cost.gold()) || !Inventories.take(player, cost.items())) {
            return;
        }
        Enhancement.Result result = Enhancement.attempt(data.get(), RANDOM);
        EquipmentStacks.write(stack, result.item());
        boolean success = result.outcome() == Enhancement.Outcome.SUCCESS;
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                success ? com.eternalrealm.sound.ERSounds.ENHANCE_SUCCESS.get() : com.eternalrealm.sound.ERSounds.ENHANCE_FAIL.get(),
                SoundSource.PLAYERS, 1.0F, 1.0F);
        player.displayClientMessage(Component.translatable(result.outcome().translationKey(),
                result.item().enhancement()).withStyle(success ? ChatFormatting.GREEN : ChatFormatting.RED), true);
        context.combat().markStatsDirty(player.getUUID());
    }

    private static void reroll(ServerPlayer player, State state) {
        ItemStack stack = selected(player, state);
        Optional<EquipmentData> data = EquipmentStacks.read(stack);
        ServerContext context = ServerContext.current().orElse(null);
        if (data.isEmpty() || context == null) {
            return;
        }
        Reroll.Check check = Reroll.check(data.get(), Inventories.counts(player), gold(player));
        if (check != Reroll.Check.OK) {
            player.displayClientMessage(Component.translatable(check.translationKey()).withStyle(ChatFormatting.RED), true);
            return;
        }
        Cost cost = Reroll.cost(data.get());
        if (!context.playerData().spendGold(player.getUUID(), cost.gold()) || !Inventories.take(player, cost.items())) {
            return;
        }
        EquipmentStacks.write(stack, Reroll.reroll(data.get(), RANDOM));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 1.0F, 1.0F);
        player.displayClientMessage(Component.translatable(Reroll.DONE_KEY).withStyle(ChatFormatting.LIGHT_PURPLE), true);
        context.combat().markStatsDirty(player.getUUID());
    }

    // ------------------------------------------------------------------ craft

    private static void fillCraft(ServerMenu menu, State state) {
        ServerPlayer player = menu.viewer();
        int slot = 9;
        for (ItemTier tier : ItemTier.values()) {
            ItemTier shown = tier;
            menu.set(slot++, MenuItems.icon(new ItemStack(Items.PAPER), Component.translatable(tier.translationKey())
                    .withStyle(state.craftTier == tier ? ChatFormatting.GOLD : ChatFormatting.WHITE),
                    List.of(Component.translatable("tooltip.eternalrealm.equipment.required_level", tier.requiredLevel())),
                    state.craftTier == tier), (p, button, shift) -> {
                state.craftTier = shown;
                menu.refresh();
            });
        }
        Map<String, Integer> counts = Inventories.counts(player);
        long gold = gold(player);
        int level = level(player);
        slot = 27;
        for (Recipe recipe : Recipes.forTier(state.craftTier)) {
            ItemStack preview = new ItemStack(ERGearItems.equipment(recipe.output()));
            EquipmentStacks.write(preview, EquipmentStacks.plain(recipe.output()));
            List<Component> lore = new ArrayList<>();
            describe(lore, recipe.cost());
            Crafting.Check check = Crafting.check(recipe, counts, gold, level);
            lore.add(Component.translatable(check.result().translationKey()).withStyle(check.isOk()
                    ? ChatFormatting.GREEN : ChatFormatting.RED));
            menu.set(slot++, MenuItems.icon(preview, preview.getHoverName(), lore, check.isOk()), (p, button, shift) -> {
                craft(p, recipe);
                menu.refresh();
            });
            if (slot >= 54) {
                break;
            }
        }
    }

    private static void craft(ServerPlayer player, Recipe recipe) {
        ServerContext context = ServerContext.current().orElse(null);
        if (context == null) {
            return;
        }
        Crafting.Check check = Crafting.check(recipe, Inventories.counts(player), gold(player), level(player));
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
        ItemStack result = EquipmentStacks.create(Crafting.craft(recipe, luck, RANDOM));
        player.displayClientMessage(Component.translatable(Crafting.DONE_KEY, result.getHoverName())
                .withStyle(ChatFormatting.GREEN), true);
        Inventories.give(player, result);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SMITHING_TABLE_USE,
                SoundSource.PLAYERS, 1.0F, 1.0F);
        context.game().rankings().onItemCrafted(player, 1);
    }

    private static long gold(ServerPlayer player) {
        return ServerContext.current().flatMap(context -> context.playerData().snapshot(player.getUUID()))
                .map(CharacterSnapshot::gold).orElse(0L);
    }

    private static int level(ServerPlayer player) {
        return ServerContext.current().flatMap(context -> context.playerData().snapshot(player.getUUID()))
                .map(CharacterSnapshot::level).orElse(1);
    }
}
