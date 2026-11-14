package com.eternalrealm.item.forge;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.dungeon.forge.DungeonLevels;
import com.eternalrealm.item.KeepZones;
import com.eternalrealm.world.forge.Realm;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * No vanilla items lie about in アストリア and the dungeons (owner, 2026-11-14: 「バニラのアイテムが落ちててそれを勝手に
 * 拾ってバッグがパンパンになるからバニラのアイテムは落ちないようにして」). What was found in a played world: some 1,300
 * stacks on the ground, nearly all plants knocked loose while fighting (pink petals, seeds from grass, flowers, crops
 * at the farms, a few sticks from leaves). A vanilla ({@code minecraft:}) item that would appear on the ground there
 * with nobody's name on it does not appear, and those already lying there vanish when their chunk loads.
 *
 * <p>Kept, because they are someone's: what a player throws or lets fall ({@link ItemTossEvent}, or a thrower on the
 * item), loot that did not fit the bag (it carries its owner), what a dying player drops, and what spills from a block
 * holding things that a player breaks (a chest, a barrel, a furnace). The game's own items are never touched, and
 * player rooms keep vanilla rules.
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VanillaDrops {
    /** Persistent tag of an item that is someone's though it carries no name (survives saving). */
    public static final String KEEP_TAG = "er_keep";

    private static final KeepZones SPILLS = new KeepZones();

    private VanillaDrops() {
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ItemEntity item)
                || !(event.getLevel() instanceof ServerLevel level) || !inTheGame(level)) {
            return;
        }
        if (stray(item) && !SPILLS.covers(level.dimension().location().toString(), item.getX(), item.getY(),
                item.getZ(), level.getGameTime())) {
            event.setCanceled(true);
        }
    }

    /** Thrown with Q, dragged out of a window, or pushed out of a full inventory: the player's. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onToss(ItemTossEvent event) {
        event.getEntity().getPersistentData().putBoolean(KEEP_TAG, true);
    }

    /** What a dying player drops is theirs, though it carries no thrower. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof Player) {
            for (ItemEntity drop : event.getDrops()) {
                drop.getPersistentData().putBoolean(KEEP_TAG, true);
            }
        }
    }

    /** A player breaking a block that holds things: what spills out is theirs. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level) || !inTheGame(level)) {
            return;
        }
        BlockPos pos = event.getPos();
        if (level.getBlockEntity(pos) != null) {
            SPILLS.open(level.dimension().location().toString(), pos.getX() + 0.5, pos.getY() + 0.5,
                    pos.getZ() + 0.5, level.getGameTime());
        }
    }

    static boolean inTheGame(Level level) {
        return level.dimension() == Realm.ASTORIA || level.dimension() == DungeonLevels.DUNGEONS;
    }

    /** A vanilla item with nobody's name on it (from a block, a plant, a vanilla mob). */
    public static boolean stray(ItemEntity item) {
        if (item.getItem().isEmpty()) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item.getItem().getItem());
        if (id == null || !"minecraft".equals(id.getNamespace())) {
            return false;
        }
        if (item.getPersistentData().getBoolean(KEEP_TAG)) {
            return false;
        }
        CompoundTag saved = item.saveWithoutId(new CompoundTag());
        return !saved.hasUUID("Thrower") && !saved.hasUUID("Owner");
    }
}
