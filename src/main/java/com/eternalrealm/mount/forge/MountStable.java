package com.eternalrealm.mount.forge;

import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.menu.MenuItems;
import com.eternalrealm.menu.ServerMenu;
import com.eternalrealm.mount.MountKind;
import com.eternalrealm.mount.MountMotion;
import com.eternalrealm.mount.MountOwnership;
import com.eternalrealm.mount.MountRules;
import com.eternalrealm.server.ServerContext;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The stablemaster's menu (buying a mount: the price, the level it needs, owned or not) and the mount chooser of
 * the choose key (the owned mounts; a click picks one and calls it). Buying needs a second click, because mounts
 * are dear.
 */
public final class MountStable {
    private static final int[] SLOTS = {11, 13, 15};
    /** A first click on a mount for sale, by player: the next click on the same mount buys it. */
    private static final Map<UUID, MountKind> CONFIRMING = new HashMap<>();

    private MountStable() {
    }

    public static Item icon(MountKind kind) {
        return switch (kind) {
            case HORSE -> Items.GOLDEN_HORSE_ARMOR;
            case GRIFFIN -> Items.ELYTRA;
            case BIKE -> Items.NETHER_STAR;
        };
    }

    // ------------------------------------------------------------------ the stablemaster

    public static void open(ServerPlayer player) {
        CONFIRMING.remove(player.getUUID());
        ServerMenu.open(player, Component.translatable(MountRules.STABLE_TITLE), 3, MountStable::fillStable);
    }

    private static void fillStable(ServerMenu menu) {
        ServerPlayer player = menu.viewer();
        for (int slot = 0; slot < menu.size(); slot++) {
            menu.set(slot, MenuItems.filler(), null);
        }
        MountOwnership owned = MountService.ownership(player);
        int level = ServerContext.current().flatMap(context -> context.playerData().snapshot(player.getUUID()))
                .map(CharacterSnapshot::level).orElse(0);
        MountKind selected = owned.selectedOrFirst().orElse(null);
        for (MountKind kind : MountKind.values()) {
            List<Component> lore = new ArrayList<>(description(kind));
            boolean confirming = CONFIRMING.get(player.getUUID()) == kind;
            if (owned.owns(kind)) {
                lore.add(Component.translatable(MountRules.STABLE_OWNED).withStyle(ChatFormatting.GREEN));
                lore.add(kind == selected ? Component.translatable(MountRules.STABLE_SELECTED, keyName())
                        .withStyle(ChatFormatting.AQUA) : Component.translatable(MountRules.STABLE_PICK)
                        .withStyle(ChatFormatting.YELLOW));
            } else {
                lore.add(Component.translatable(MountRules.STABLE_PRICE, kind.price()).withStyle(ChatFormatting.GOLD));
                if (level < kind.requiredLevel()) {
                    lore.add(Component.translatable(MountRules.STABLE_LOCKED, kind.requiredLevel())
                            .withStyle(ChatFormatting.RED));
                } else {
                    lore.add(Component.translatable(MountRules.STABLE_LEVEL, kind.requiredLevel())
                            .withStyle(ChatFormatting.GRAY));
                    lore.add(confirming ? Component.translatable(MountRules.STABLE_CONFIRM, kind.price())
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                            : Component.translatable(MountRules.STABLE_BUY).withStyle(ChatFormatting.YELLOW));
                }
            }
            Component name = Component.translatable(kind.translationKey()).withStyle(owned.owns(kind)
                    ? ChatFormatting.GREEN : level < kind.requiredLevel() ? ChatFormatting.DARK_GRAY : ChatFormatting.GOLD);
            menu.set(SLOTS[kind.ordinal()], MenuItems.icon(new ItemStack(icon(kind)), name, lore,
                    confirming || kind == selected), (p, button, shift) -> clickStable(menu, p, kind));
        }
        long gold = ServerContext.current().map(context -> context.playerData().gold(player.getUUID())).orElse(0L);
        menu.set(22, MenuItems.icon(Items.GOLD_INGOT, Component.translatable(MountRules.STABLE_GOLD, gold)
                .withStyle(ChatFormatting.GOLD), List.of(Component.translatable(MountRules.STABLE_CHOOSE))), null);
    }

    private static void clickStable(ServerMenu menu, ServerPlayer player, MountKind kind) {
        MountOwnership owned = MountService.ownership(player);
        if (owned.owns(kind)) {
            CONFIRMING.remove(player.getUUID());
            MountService.select(player, kind);
        } else if (CONFIRMING.get(player.getUUID()) == kind) {
            CONFIRMING.remove(player.getUUID());
            MountService.buy(player, kind);
        } else {
            int level = ServerContext.current().flatMap(context -> context.playerData().snapshot(player.getUUID()))
                    .map(CharacterSnapshot::level).orElse(0);
            long gold = ServerContext.current().map(context -> context.playerData().gold(player.getUUID())).orElse(0L);
            MountRules.Purchase check = MountRules.purchase(kind, level, gold, owned);
            if (check == MountRules.Purchase.OK) {
                CONFIRMING.put(player.getUUID(), kind);
            } else {
                CONFIRMING.remove(player.getUUID());
                MountService.buy(player, kind); // refused: tells the player why
            }
        }
        menu.refresh();
    }

    // ------------------------------------------------------------------ the chooser

    /** The choose key: the mounts the player owns. */
    public static void openChooser(ServerPlayer player) {
        ServerMenu.open(player, Component.translatable(MountRules.CHOOSE_TITLE), 3, MountStable::fillChooser);
    }

    private static void fillChooser(ServerMenu menu) {
        ServerPlayer player = menu.viewer();
        for (int slot = 0; slot < menu.size(); slot++) {
            menu.set(slot, MenuItems.filler(), null);
        }
        MountOwnership owned = MountService.ownership(player);
        if (owned.owned().isEmpty()) {
            menu.set(13, MenuItems.icon(Items.SADDLE, Component.translatable(MountRules.STABLE_NONE)
                    .withStyle(ChatFormatting.GRAY), List.of(Component.translatable(MountRules.NONE_OWNED))), null);
            return;
        }
        MountKind selected = owned.selectedOrFirst().orElse(null);
        for (MountKind kind : MountKind.values()) {
            if (!owned.owns(kind)) {
                continue;
            }
            List<Component> lore = new ArrayList<>(description(kind));
            if (kind == selected) {
                lore.add(Component.translatable(MountRules.STABLE_SELECTED, keyName()).withStyle(ChatFormatting.AQUA));
            }
            lore.add(Component.translatable(MountRules.STABLE_CALL).withStyle(ChatFormatting.YELLOW));
            menu.set(SLOTS[kind.ordinal()], MenuItems.icon(new ItemStack(icon(kind)), Component.translatable(
                    kind.translationKey()).withStyle(ChatFormatting.GOLD), lore, kind == selected),
                    (p, button, shift) -> choose(p, kind));
        }
    }

    /** Picks {@code kind} and calls it (getting off the current mount first if it is another one). */
    static void choose(ServerPlayer player, MountKind kind) {
        player.closeContainer();
        MountService.select(player, kind);
        if (player.getVehicle() instanceof MountEntity mount) {
            if (mount.kind() == kind) {
                return;
            }
            MountService.dismiss(player, false);
        }
        if (!MountService.isCalling(player)) {
            MountService.startCall(player);
        }
    }

    // ------------------------------------------------------------------ text

    private static List<Component> description(MountKind kind) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(kind.descriptionKey()).withStyle(ChatFormatting.GRAY));
        double times = kind.stats().topSpeed() / MountMotion.SPRINT_SPEED;
        lines.add(Component.translatable(MountRules.STABLE_SPEED, String.format(Locale.ROOT, "%.1f", times))
                .withStyle(ChatFormatting.WHITE));
        if (kind.flies()) {
            lines.add(Component.translatable(MountRules.STABLE_FLIES).withStyle(ChatFormatting.AQUA));
        }
        return lines;
    }

    private static Component keyName() {
        return Component.keybind("key." + com.eternalrealm.core.ModInfo.MOD_ID + ".mount");
    }

    /** Forgets a player's half-made purchase (logout). */
    static void forget(UUID playerId) {
        CONFIRMING.remove(playerId);
    }
}
