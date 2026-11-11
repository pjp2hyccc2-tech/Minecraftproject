package com.eternalrealm.guild.house.forge;

import com.eternalrealm.guild.Guild;
import com.eternalrealm.guild.GuildRank;
import com.eternalrealm.guild.house.GuildHouseRules;
import com.eternalrealm.guild.house.StorageDiff;
import com.eternalrealm.guild.house.StorageLogEntry;
import com.eternalrealm.world.forge.AstoriaTravel;
import com.eternalrealm.world.forge.Capital;
import com.eternalrealm.world.forge.HubLayout;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Forge events of the guild houses: the gates' plaques open the catalogue, the rally bell in a house calls the
 * guild's members, vault chests in a house only open for its guild's master and officers, and every deposit and
 * withdrawal of a house's storage is logged when the chest is closed (per kind of item; changes others made
 * meanwhile are kept out of a player's share).
 */
public final class GuildHouseEvents {
    /** Open storage of a house: who looks into which piece, and what it held when they opened it (or last synced). */
    private record Session(UUID guild, String piece, Container container, Map<String, Integer> snapshot,
                           Map<String, String> ids) {
    }

    private static final Map<UUID, Session> OPEN = new HashMap<>();

    private GuildHouseEvents() {
    }

    // ------------------------------------------------------------------ plaques and vaults

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (level.getBlockState(pos).getBlock() instanceof SignBlock) {
            Optional<String> gate = gateNear(player, pos);
            if (gate.isPresent()) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                if (event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND) {
                    GuildHouseUi.openCatalogue(player, gate.get());
                }
            }
            return;
        }
        if (level.getBlockState(pos).getBlock() instanceof BellBlock) {
            if (event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND) {
                GuildHouses.estates().houseAt(level, pos).ifPresent(guildId ->
                        GuildHouses.get().ifPresent(houses -> houses.rallyBell(player, guildId)));
            }
            return;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        if (!(entity instanceof Container)) {
            return;
        }
        GuildEstates estates = GuildHouses.estates();
        Optional<UUID> house = estates.houseAt(level, pos);
        if (house.isEmpty()) {
            return;
        }
        String piece = estates.pieceName(level, pos).orElse("");
        if (!GuildHouseRules.isVault(piece)) {
            return;
        }
        Optional<Guild> guild = GuildHouses.guildOf(player);
        GuildRank rank = guild.filter(g -> g.id().equals(house.get())).flatMap(g -> g.rankOf(player.getUUID()))
                .orElse(null);
        if (rank == null || !GuildHouseRules.mayOpenVault(rank)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            player.displayClientMessage(Component.translatable("message.eternalrealm.guildhouse.vault_locked")
                    .withStyle(ChatFormatting.RED), true);
        }
    }

    /** The design of the gate whose plaque is at {@code pos} (a sign within a few blocks of a gate's spot). */
    static Optional<String> gateNear(ServerPlayer player, BlockPos pos) {
        Optional<ServerLevel> astoria = AstoriaTravel.astoria(player.server);
        if (astoria.isEmpty() || player.serverLevel() != astoria.get()) {
            return Optional.empty();
        }
        HubLayout layout = Capital.layout(astoria.get());
        String best = null;
        double bestDistance = 7.5 * 7.5;
        for (Map.Entry<String, BlockPos> gate : layout.namedWithPrefix("guildgate.").entrySet()) {
            double d = gate.getValue().distSqr(pos);
            if (d < bestDistance) {
                bestDistance = d;
                best = gate.getKey().substring("guildgate.".length());
            }
        }
        return Optional.ofNullable(best).filter(GuildGates::known);
    }

    // ------------------------------------------------------------------ the storage log

    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getContainer() instanceof ChestMenu menu)) {
            return;
        }
        Container container = menu.getContainer();
        if (!(container instanceof BlockEntity entity) || entity.getLevel() == null) {
            return;
        }
        GuildEstates estates = GuildHouses.estates();
        Optional<UUID> house = estates.houseAt(entity.getLevel(), entity.getBlockPos());
        if (house.isEmpty()) {
            return;
        }
        String piece = estates.pieceName(entity.getLevel(), entity.getBlockPos()).orElse("storage");
        Map<String, String> ids = new HashMap<>();
        OPEN.put(player.getUUID(), new Session(house.get(), piece, container, snapshot(container, ids), ids));
    }

    public static void onContainerClose(PlayerContainerEvent.Close event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Session session = OPEN.remove(player.getUUID());
        if (session == null) {
            return;
        }
        Map<String, String> ids = new HashMap<>(session.ids());
        Map<String, Integer> now = snapshot(session.container(), ids);
        List<StorageDiff.Change<String>> changes = StorageDiff.between(session.snapshot(), now);
        if (changes.isEmpty()) {
            return;
        }
        Instant at = Instant.now();
        GuildHouses.get().ifPresent(houses -> {
            for (StorageDiff.Change<String> change : changes) {
                houses.service().log(session.guild(), new StorageLogEntry(at, player.getUUID(),
                        player.getGameProfile().getName(), ids.getOrDefault(change.key(), "minecraft:air"),
                        change.count(), change.deposit(), session.piece()));
            }
        });
        // others looking into the same piece: their share must not include this player's changes
        List<UUID> viewers = new ArrayList<>();
        OPEN.forEach((viewer, other) -> {
            if (other.container() == session.container()) {
                viewers.add(viewer);
            }
        });
        for (UUID viewer : viewers) {
            Session other = OPEN.get(viewer);
            OPEN.put(viewer, new Session(other.guild(), other.piece(), other.container(),
                    StorageDiff.apply(other.snapshot(), changes), other.ids()));
        }
    }

    /** Counts per kind (item and data) of a container; {@code ids} learns each kind's item id. */
    private static Map<String, Integer> snapshot(Container container, Map<String, String> ids) {
        List<Map.Entry<String, Integer>> stacks = new ArrayList<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            String key = stack.hasTag() ? id + "#" + Integer.toHexString(stack.getTag().hashCode()) : id;
            ids.putIfAbsent(key, id);
            stacks.add(Map.entry(key, stack.getCount()));
        }
        return StorageDiff.count(stacks);
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        OPEN.remove(event.getEntity().getUUID());
        GuildGates.forget(event.getEntity().getUUID());
    }

    static boolean viewing(AbstractContainerMenu menu) {
        return OPEN.values().stream().anyMatch(session -> menu instanceof ChestMenu chest
                && chest.getContainer() == session.container());
    }
}
