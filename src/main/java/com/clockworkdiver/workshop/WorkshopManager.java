package com.clockworkdiver.workshop;

import com.clockworkdiver.clock.ClockData;
import com.clockworkdiver.clock.CommissionGenerator;
import com.clockworkdiver.clock.Defect;
import com.clockworkdiver.customer.CustomerEntity;
import com.clockworkdiver.data.PlayerProgress;
import com.clockworkdiver.data.WorldProgress;
import com.clockworkdiver.interior.DiveManager;
import com.clockworkdiver.network.ModNetwork;
import com.clockworkdiver.network.S2C;
import com.clockworkdiver.registry.ModDimensions;
import com.clockworkdiver.registry.ModEntities;
import com.clockworkdiver.registry.ModItems;
import com.clockworkdiver.registry.ModSounds;
import com.clockworkdiver.util.DelayedTasks;
import com.clockworkdiver.util.Teleport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The workshop side of the game loop: entering the shop, customers arriving with commissions, accepting
 * a clock, the 修理台 (repair bench) with the spectacles, and delivering the repaired clock.
 */
public final class WorkshopManager {
    public static final int SLOT_SPACING = 512;
    /** Relative positions inside a workshop. */
    public static final BlockPos BENCH = new BlockPos(0, 0, -6);
    public static final BlockPos COUNTER_SPOT = new BlockPos(0, 0, 5);
    public static final BlockPos STREET_SPAWN = new BlockPos(0, 0, 22);

    private WorkshopManager() {}

    public static BlockPos origin(int slot) {
        return new BlockPos(slot * SLOT_SPACING, 64, 0);
    }

    @Nullable
    public static ServerLevel workshopLevel(MinecraftServer server) {
        return server.getLevel(ModDimensions.WORKSHOP);
    }

    public static void ensureBuilt(ServerLevel level, PlayerProgress progress) {
        if (!progress.workshopBuilt || progress.workshopVersion < WorkshopBuilder.VERSION) {
            WorkshopBuilder.build(level, origin(progress.slot));
            progress.workshopBuilt = true;
            progress.workshopVersion = WorkshopBuilder.VERSION;
        }
    }

    /** Remembers where to send the player back to when they leave the workshop. */
    public static void rememberReturn(ServerPlayer player, PlayerProgress progress) {
        if (ModDimensions.isModDimension(player.level())) return;
        progress.returnDim = player.level().dimension().location().toString();
        progress.returnX = player.getX();
        progress.returnY = player.getY();
        progress.returnZ = player.getZ();
        progress.returnYaw = player.getYRot();
    }

    // ------------------------------------------------------------------ travelling

    public static void enterWorkshop(ServerPlayer player) {
        ServerLevel ws = workshopLevel(player.server);
        if (ws == null) {
            player.displayClientMessage(Component.translatable("message.clockworkdiver.no_dimension"), false);
            return;
        }
        if (DiveManager.session(player) != null) return;
        PlayerProgress progress = WorldProgress.of(player);
        rememberReturn(player, progress);
        ensureBuilt(ws, progress);
        BlockPos o = origin(progress.slot);
        Teleport.to(player, ws, o.getX() + 0.5, o.getY(), o.getZ() + 6.5, 180.0f, 0.0f);
        boolean first = !progress.started;
        if (first) {
            progress.started = true;
            progress.nextCustomerTime = ws.getGameTime() + 100;
        }
        DelayedTasks.schedule(player.server, 12, () -> {
            ModNetwork.send(player, new S2C.Transition(S2C.TRANSITION_WORKSHOP, "title.clockworkdiver.workshop", "title.clockworkdiver.workshop.sub"));
            Objectives.sync(player);
            if (progress.markSeen(PlayerProgress.HINT_WORKSHOP)) {
                ModNetwork.send(player, new S2C.Hint(List.of("hint.clockworkdiver.workshop.0", "hint.clockworkdiver.workshop.1")));
            }
        });
    }

    public static void leaveWorkshop(ServerPlayer player) {
        if (DiveManager.session(player) != null) return;
        PlayerProgress progress = WorldProgress.of(player);
        ServerLevel target = null;
        if (progress.returnDim != null) {
            ResourceLocation id = ResourceLocation.tryParse(progress.returnDim);
            if (id != null) target = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        }
        double x = progress.returnX, y = progress.returnY, z = progress.returnZ;
        float yaw = progress.returnYaw;
        if (target == null || ModDimensions.isModDimension(target)) {
            target = player.server.overworld();
            BlockPos spawn = target.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, target.getSharedSpawnPos());
            x = spawn.getX() + 0.5;
            y = spawn.getY();
            z = spawn.getZ() + 0.5;
            yaw = 0.0f;
        }
        Teleport.to(player, target, x, y, z, yaw, 0.0f);
        DelayedTasks.schedule(player.server, 10, () -> Objectives.sync(player));
    }

    /** The big 修理台 button: go to the bench, put the spectacles on and open the inspection view. */
    public static void goToBench(ServerPlayer player) {
        if (DiveManager.session(player) != null) return;
        ServerLevel ws = workshopLevel(player.server);
        if (ws == null) return;
        PlayerProgress progress = WorldProgress.of(player);
        rememberReturn(player, progress);
        ensureBuilt(ws, progress);
        boolean crossDimension = player.level() != ws;
        teleportToBench(player, ws, progress);
        DelayedTasks.schedule(player.server, crossDimension ? 25 : 3, () -> openBench(player, true));
    }

    /** After a dive: back at the bench, back to full size. */
    public static void returnToBench(ServerPlayer player, boolean reopen) {
        ServerLevel ws = workshopLevel(player.server);
        if (ws == null) return;
        PlayerProgress progress = WorldProgress.of(player);
        ensureBuilt(ws, progress);
        teleportToBench(player, ws, progress);
        player.level().playSound(null, player.blockPosition(), ModSounds.GROW.get(), SoundSource.PLAYERS, 0.8f, 1.0f);
        DelayedTasks.schedule(player.server, 30, () -> {
            Objectives.sync(player);
            if (reopen && DiveManager.session(player) == null && player.isAlive()) openBench(player, false);
        });
    }

    private static void teleportToBench(ServerPlayer player, ServerLevel ws, PlayerProgress progress) {
        BlockPos o = origin(progress.slot);
        Teleport.to(player, ws, o.getX() + BENCH.getX() + 0.5, o.getY(), o.getZ() + BENCH.getZ() + 1.7, 180.0f, 38.0f);
    }

    // ------------------------------------------------------------------ the bench

    public static void useBench(ServerPlayer player) {
        openBench(player, true);
    }

    public static void openBench(ServerPlayer player, boolean glassesAnimation) {
        if (!player.isAlive() || DiveManager.session(player) != null) return;
        equipSpectacles(player);
        PlayerProgress progress = WorldProgress.of(player);
        ClockData clock = progress.accepted ? progress.activeClock : null;
        CompoundTag tag = clock != null ? clock.save() : new CompoundTag();
        ModNetwork.send(player, new S2C.OpenBench(tag, Upgrades.coins(player), progress.wrenchLevel, progress.steamLevel, progress.lensLevel,
                glassesAnimation, !progress.tutorialDone, progress.reputation));
        if (glassesAnimation) {
            player.level().playSound(null, player.blockPosition(), ModSounds.GLASSES.get(), SoundSource.PLAYERS, 0.8f, 1.0f);
        }
        Objectives.sync(player);
    }

    /** Puts the Chrono Spectacles on the player's head (taking them from the inventory or making a pair). */
    public static void equipSpectacles(ServerPlayer player) {
        if (player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.CHRONO_SPECTACLES.get())) return;
        Inventory inv = player.getInventory();
        ItemStack glasses = ItemStack.EMPTY;
        for (int i = 0; i < inv.items.size(); i++) {
            if (inv.items.get(i).is(ModItems.CHRONO_SPECTACLES.get())) {
                glasses = inv.removeItemNoUpdate(i);
                break;
            }
        }
        boolean fresh = glasses.isEmpty();
        if (fresh) glasses = new ItemStack(ModItems.CHRONO_SPECTACLES.get());
        ItemStack old = player.getItemBySlot(EquipmentSlot.HEAD);
        player.setItemSlot(EquipmentSlot.HEAD, glasses);
        if (!old.isEmpty() && !inv.add(old)) player.drop(old, false);
        player.displayClientMessage(Component.translatable(fresh ? "message.clockworkdiver.got_glasses" : "message.clockworkdiver.wear_glasses"), true);
    }

    public static void discoverDefect(ServerPlayer player, int defectId) {
        PlayerProgress progress = WorldProgress.of(player);
        ClockData clock = progress.activeClock;
        if (clock == null || !progress.accepted) return;
        Defect d = clock.defect(defectId);
        if (d == null || d.found) return;
        d.found = true;
        syncClockItem(player, clock);
        player.level().playSound(null, player.blockPosition(), ModSounds.DISCOVER.get(), SoundSource.PLAYERS, 0.9f, 1.0f);
        Objectives.sync(player);
    }

    /** Using the clock item reopens the 修理台 prompt. */
    public static void promptRepair(ServerPlayer player) {
        PlayerProgress progress = WorldProgress.of(player);
        ClockData clock = progress.activeClock;
        if (clock == null || !progress.accepted) {
            player.displayClientMessage(Component.translatable("message.clockworkdiver.no_commission"), true);
            return;
        }
        if (DiveManager.session(player) != null) return;
        ModNetwork.send(player, new S2C.RepairPrompt(clock.save()));
    }

    // ------------------------------------------------------------------ customers

    public static void tick(MinecraftServer server) {
        ServerLevel ws = workshopLevel(server);
        if (ws == null) return;
        for (ServerPlayer player : ws.players()) {
            PlayerProgress progress = WorldProgress.of(player);
            BlockPos o = origin(progress.slot);
            if (player.getY() < o.getY() - 20 && !player.isSpectator()) {
                // Fell off the street into the void: back to the shop door.
                Teleport.to(player, ws, o.getX() + 0.5, o.getY(), o.getZ() + 6.5, 180.0f, 0.0f);
                continue;
            }
            if (!progress.started || DiveManager.session(player) != null) continue;
            if (player.distanceToSqr(o.getX(), o.getY(), o.getZ()) > 80.0 * 80.0) continue;
            CustomerEntity customer = findCustomer(ws, progress);
            if (progress.activeClock == null) {
                if (customer != null && customer.state() != CustomerEntity.LEAVING) customer.leave();
                if ((customer == null || customer.state() == CustomerEntity.LEAVING) && ws.getGameTime() >= progress.nextCustomerTime) {
                    newCommission(player, progress, ws, null);
                }
            } else if (customer == null) {
                if (++progress.missingCustomerChecks > 3) {
                    progress.missingCustomerChecks = 0;
                    spawnCustomer(player, progress, ws, false);
                }
            } else {
                progress.missingCustomerChecks = 0;
            }
        }
    }

    @Nullable
    public static CustomerEntity findCustomer(ServerLevel ws, PlayerProgress progress) {
        if (progress.customerId == null) return null;
        Entity e = ws.getEntity(progress.customerId);
        return e instanceof CustomerEntity c && c.isAlive() ? c : null;
    }

    /** Creates the next commission (the tutorial watch first) and sends its customer walking in. */
    public static void newCommission(ServerPlayer player, PlayerProgress progress, ServerLevel ws, @Nullable ClockData forced) {
        progress.commissionCounter++;
        ClockData clock = forced != null ? forced
                : progress.tutorialDone ? CommissionGenerator.random(ws.random, progress.reputation, progress.commissionCounter)
                : CommissionGenerator.tutorial(progress.commissionCounter);
        clock.commissionId = progress.commissionCounter;
        progress.activeClock = clock;
        progress.accepted = false;
        CustomerEntity old = findCustomer(ws, progress);
        if (old != null) old.discard();
        spawnCustomer(player, progress, ws, true);
        Objectives.sync(player);
    }

    private static void spawnCustomer(ServerPlayer player, PlayerProgress progress, ServerLevel ws, boolean walkIn) {
        ClockData clock = progress.activeClock;
        if (clock == null) return;
        CustomerEntity customer = ModEntities.CUSTOMER.get().create(ws);
        if (customer == null) return;
        BlockPos o = origin(progress.slot);
        BlockPos counter = o.offset(COUNTER_SPOT);
        BlockPos exit = o.offset(STREET_SPAWN);
        customer.setup(player.getUUID(), clock.profile, counter, exit);
        if (walkIn) {
            customer.moveTo(exit.getX() + 0.5, exit.getY(), exit.getZ() + 0.5, 180.0f, 0.0f);
            customer.setState(CustomerEntity.ARRIVING);
            player.displayClientMessage(Component.translatable("message.clockworkdiver.customer_coming",
                    Component.translatable(clock.profile.nameKey())), true);
        } else {
            customer.moveTo(counter.getX() + 0.5, counter.getY(), counter.getZ() + 0.5, 180.0f, 0.0f);
            customer.setState(progress.accepted ? CustomerEntity.WAITING_REPAIR : CustomerEntity.WAITING);
        }
        ws.addFreshEntity(customer);
        progress.customerId = customer.getUUID();
    }

    /** Debug and CI helper: puts the current customer at the counter at once. */
    public static boolean customerToCounter(ServerPlayer player) {
        ServerLevel ws = workshopLevel(player.server);
        if (ws == null) return false;
        PlayerProgress progress = WorldProgress.of(player);
        CustomerEntity c = findCustomer(ws, progress);
        if (c == null) return false;
        BlockPos counter = c.counterPos();
        if (counter == null) return false;
        c.moveTo(counter.getX() + 0.5, counter.getY(), counter.getZ() + 0.5, 180.0f, 0.0f);
        c.getNavigation().stop();
        if (c.state() == CustomerEntity.ARRIVING) {
            c.setState(CustomerEntity.WAITING);
            onCustomerArrived(c);
        }
        return true;
    }

    public static void onCustomerArrived(CustomerEntity customer) {
        customer.level().playSound(null, customer.blockPosition(), ModSounds.SHOP_BELL.get(), SoundSource.NEUTRAL, 1.0f, 1.0f);
        if (customer.owner() == null || customer.getServer() == null) return;
        ServerPlayer owner = customer.getServer().getPlayerList().getPlayer(customer.owner());
        if (owner != null) {
            owner.displayClientMessage(Component.translatable("message.clockworkdiver.customer_arrived",
                    Component.translatable(customer.profile().nameKey())), true);
            Objectives.sync(owner);
        }
    }

    public static void onCustomerInteract(ServerPlayer player, CustomerEntity customer) {
        if (customer.owner() == null || !player.getUUID().equals(customer.owner())) {
            player.displayClientMessage(Component.translatable("message.clockworkdiver.not_your_customer"), true);
            return;
        }
        PlayerProgress progress = WorldProgress.of(player);
        ClockData clock = progress.activeClock;
        if (clock == null || customer.state() == CustomerEntity.LEAVING) {
            player.displayClientMessage(Component.translatable("message.clockworkdiver.customer_leaving"), true);
            return;
        }
        int mode = !progress.accepted ? S2C.DIALOGUE_OFFER : clock.allRepaired() ? S2C.DIALOGUE_DELIVER : S2C.DIALOGUE_WAITING;
        ModNetwork.send(player, new S2C.OpenDialogue(customer.getId(), mode, clock.profile.id, clock.save(), clock.reward));
    }

    public static void acceptCommission(ServerPlayer player, int entityId) {
        PlayerProgress progress = WorldProgress.of(player);
        ClockData clock = progress.activeClock;
        if (clock == null || progress.accepted) return;
        progress.accepted = true;
        if (player.level().getEntity(entityId) instanceof CustomerEntity c) c.setState(CustomerEntity.WAITING_REPAIR);
        syncClockItem(player, clock);
        player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6f, 0.8f);
        if (progress.markSeen(PlayerProgress.HINT_BENCH)) {
            ModNetwork.send(player, new S2C.Hint(List.of("hint.clockworkdiver.bench.0", "hint.clockworkdiver.bench.1")));
        }
        Objectives.sync(player);
    }

    public static void deliverClock(ServerPlayer player, int entityId) {
        PlayerProgress progress = WorldProgress.of(player);
        ClockData clock = progress.activeClock;
        if (clock == null || !progress.accepted || !clock.allRepaired()) return;
        removeClockItems(player, clock.commissionId);
        ItemStack coins = new ItemStack(ModItems.BRASS_COIN.get(), clock.reward);
        if (!player.getInventory().add(coins)) player.drop(coins, false);
        progress.reputation += clock.tutorial ? 1 : 1 + clock.difficulty() / 2;
        progress.repairedClocks++;
        progress.coinsEarned += clock.reward;
        progress.activeClock = null;
        progress.accepted = false;
        Level level = player.level();
        if (level.getEntity(entityId) instanceof CustomerEntity c) {
            c.leave();
            if (level instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, c.getX(), c.getY() + 1.8, c.getZ(), 12, 0.4, 0.3, 0.4, 0.0);
            }
        }
        level.playSound(null, player.blockPosition(), ModSounds.FANFARE.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.2f);
        ServerLevel ws = workshopLevel(player.server);
        long now = ws != null ? ws.getGameTime() : level.getGameTime();
        progress.nextCustomerTime = now + (clock.tutorial ? 200 : 360);
        ModNetwork.send(player, new S2C.OpenDialogue(entityId, S2C.DIALOGUE_THANKS, clock.profile.id, clock.save(), clock.reward));
        if (clock.tutorial && !progress.tutorialDone) {
            progress.tutorialDone = true;
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("title.clockworkdiver.tutorial_done.sub")));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("title.clockworkdiver.tutorial_done")));
            ModNetwork.send(player, new S2C.Hint(List.of("hint.clockworkdiver.after.0", "hint.clockworkdiver.after.1",
                    "hint.clockworkdiver.after.2")));
        }
        Objectives.sync(player);
    }

    // ------------------------------------------------------------------ the clock item

    @Nullable
    private static ItemStack findClockItem(ServerPlayer player, long commissionId) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (!st.is(ModItems.BROKEN_CLOCK.get())) continue;
            ClockData d = ClockData.fromStack(st);
            if (d != null && d.commissionId == commissionId) return st;
        }
        return null;
    }

    /** Writes the commission into the player's clock item, handing them a new one if it went missing. */
    public static void syncClockItem(ServerPlayer player, ClockData clock) {
        ItemStack st = findClockItem(player, clock.commissionId);
        if (st != null) {
            clock.writeTo(st);
            return;
        }
        ItemStack stack = new ItemStack(ModItems.BROKEN_CLOCK.get());
        clock.writeTo(stack);
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    private static void removeClockItems(ServerPlayer player, long commissionId) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (!st.is(ModItems.BROKEN_CLOCK.get())) continue;
            ClockData d = ClockData.fromStack(st);
            if (d == null || d.commissionId == commissionId) inv.setItem(i, ItemStack.EMPTY);
        }
    }
}
