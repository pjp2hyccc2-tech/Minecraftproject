package com.eternalrealm.estate.forge;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.estate.EstateKey;
import com.eternalrealm.item.forge.Bags;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CartographyTableBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EnchantmentTableBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.LoomBlock;
import net.minecraft.world.level.block.SmithingTableBlock;
import net.minecraft.world.level.block.StonecutterBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * The estates dimension is a place to live, not to build: nobody breaks or places blocks (operators in creative mode
 * aside) or pours buckets, and players never hurt each other. Inside an estate, those who may be there use its
 * storage (any container), doors and gates, buttons and levers, work stations, item frames (a hit takes the item into
 * the bag; frames never break) and armour stands (they never break either); everything else does nothing, and so do
 * items used on blocks. Ender pearls and chorus fruit do not carry anyone out of an estate. Beds are for show: there
 * is no night here.
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EstateProtection {
    /** Blocks of other features that may be used inside estates (besides every {@link UsableInEstates} block). */
    private static final Set<Block> USABLE = new CopyOnWriteArraySet<>();

    /** Marks a block players may use inside estates (the room door, the ledger desk...). */
    public interface UsableInEstates {
    }

    private EstateProtection() {
    }

    /** Lets players use {@code block} inside estates (call during common setup, once blocks exist). */
    public static void allowUse(Block block) {
        USABLE.add(block);
    }

    static boolean inEstates(LevelAccessor level) {
        return level instanceof Level real && EstateLevels.isEstateLevel(real);
    }

    static boolean mayBuild(Player player) {
        return player != null && player.isCreative() && player.hasPermissions(2);
    }

    private static boolean mayUseHere(Player player, BlockPos pos) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return true; // the client follows what the server decides
        }
        Optional<EstateKey> estate = EstateService.at(serverPlayer.level(), pos);
        return estate.isPresent() && EstateService.mayAccess(serverPlayer, estate.get());
    }

    // ------------------------------------------------------------------ blocks

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (inEstates(event.getLevel()) && !mayBuild(event.getPlayer())) {
            event.setCanceled(true);
            event.getPlayer().displayClientMessage(Component.translatable("message.eternalrealm.estate.protected")
                    .withStyle(ChatFormatting.GRAY), true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (inEstates(event.getLevel()) && !(event.getEntity() instanceof Player player && mayBuild(player))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBucket(FillBucketEvent event) {
        if (inEstates(event.getLevel()) && !mayBuild(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (inEstates(event.getLevel()) && !mayBuild(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!inEstates(event.getLevel()) || mayBuild(player)) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockState state = event.getLevel().getBlockState(pos);
        if (!mayUseHere(player, pos)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        if (state.getBlock() instanceof BedBlock) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            player.displayClientMessage(Component.translatable("message.eternalrealm.estate.bed")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        event.setUseItem(Event.Result.DENY);
        if (!usable(event.getLevel(), pos, state)) {
            event.setUseBlock(Event.Result.DENY);
        }
    }

    /** Blocks a visitor may use: storage, doors and gates, buttons and levers, work stations, books on lecterns. */
    private static boolean usable(Level level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (block instanceof UsableInEstates || USABLE.contains(block)) {
            return true;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof Container) {
            return true;
        }
        if (block instanceof LecternBlock) {
            return state.getValue(LecternBlock.HAS_BOOK);
        }
        return block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock
                || block instanceof ButtonBlock || block instanceof LeverBlock || block instanceof CraftingTableBlock
                || block instanceof AnvilBlock || block instanceof SmithingTableBlock || block instanceof GrindstoneBlock
                || block instanceof StonecutterBlock || block instanceof LoomBlock
                || block instanceof CartographyTableBlock || block instanceof EnchantmentTableBlock;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (inEstates(event.getLevel()) && !mayBuild(event.getEntity())
                && event.getItemStack().getItem() instanceof net.minecraft.world.item.SpawnEggItem) {
            event.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------ entities

    /** Item frames and armour stands: used only by those who may be there; frames hand their item over. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Entity target = event.getTarget();
        if (inEstates(event.getLevel()) && !mayBuild(event.getEntity()) && (target instanceof HangingEntity
                || target instanceof ArmorStand) && !mayUseHere(event.getEntity(), target.blockPosition())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        Entity target = event.getTarget();
        if (inEstates(event.getLevel()) && !mayBuild(event.getEntity()) && (target instanceof HangingEntity
                || target instanceof ArmorStand) && !mayUseHere(event.getEntity(), target.blockPosition())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    /**
     * Hitting things in an estate: armour stands, paintings and frames never break; a frame's item goes into the bag
     * of whoever may be there.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        Entity target = event.getTarget();
        if (!inEstates(player.level()) || mayBuild(player)) {
            return;
        }
        if (target instanceof Player) {
            event.setCanceled(true);
            return;
        }
        if (!(target instanceof HangingEntity) && !(target instanceof ArmorStand)) {
            return;
        }
        event.setCanceled(true);
        if (target instanceof ItemFrame frame && player instanceof ServerPlayer serverPlayer
                && mayUseHere(player, frame.blockPosition()) && !frame.getItem().isEmpty()) {
            ItemStack item = frame.getItem().copy();
            frame.setItem(ItemStack.EMPTY);
            Bags.give(serverPlayer, item);
            frame.playSound(SoundEvents.ITEM_FRAME_REMOVE_ITEM, 1.0F, 1.0F);
        }
    }

    /** No fighting between players in the estates, whatever the source. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof Player && inEstates(event.getEntity().level())
                && event.getSource().getEntity() instanceof Player attacker && attacker != event.getEntity()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPearl(EntityTeleportEvent.EnderPearl event) {
        if (inEstates(event.getEntity().level()) && !(event.getEntity() instanceof Player player && mayBuild(player))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onChorus(EntityTeleportEvent.ChorusFruit event) {
        if (inEstates(event.getEntity().level())) {
            event.setCanceled(true);
        }
    }

    /** Sound feedback for a refused use (client side does nothing). */
    static void refuse(ServerPlayer player) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHEST_LOCKED,
                SoundSource.PLAYERS, 0.6F, 1.0F);
    }
}
