package com.hitori.game;

import com.hitori.Hitori;
import com.hitori.command.HitoriCommand;
import com.hitori.debug.SelfTest;
import com.hitori.item.GameItem;
import com.hitori.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Forge-bus events on the logical server. */
@Mod.EventBusSubscriber(modid = Hitori.MODID)
public final class GameEvents {
    private GameEvents() {}

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        GameManager.serverTick(event.getServer());
        SelfTest.tick(event.getServer());
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) GameManager.onLogin(p);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) GameManager.onLogout(p);
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        GameData data = GameData.get(p.server);
        if (data.started && p.getPersistentData().contains("hitori_return")) {
            var level = GameManager.level(p.server);
            if (level != null) {
                var w = com.hitori.world.Layout.marker("spawn");
                GameManager.teleport(p, level, w.center(), w.yaw(), 0.0f);
            }
        }
    }

    /** Nothing in the apartment hurts you the ordinary way. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void attack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (!GameManager.inGame(p)) return;
        if (event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD) || event.getSource().is(DamageTypes.GENERIC_KILL)) return;
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void death(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (!GameManager.inGame(p) || event.getSource().is(DamageTypes.GENERIC_KILL)) return;
        event.setCanceled(true);
        p.setHealth(p.getMaxHealth());
    }

    /** The ritual's things never leave your hands (a dropped item is a noise, and gone). */
    @SubscribeEvent
    public static void toss(ItemTossEvent event) {
        ItemStack stack = event.getEntity().getItem();
        if (!(event.getPlayer() instanceof ServerPlayer p) || !GameManager.inGame(p)) return;
        if (GameItem.kindOf(stack) != null) {
            event.setCanceled(true);
            p.getInventory().add(stack.copy());
            NoiseSystem.emit(p.serverLevel(), p.position(), 6.0f, "drop", p);
        }
    }

    /** Item-on-thing actions of the ritual, before the block itself reacts. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void rightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (Ritual.rightClickBlock(p, event.getHand(), event.getPos())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    /**
     * Placing a fixed camera on a block face. The night is played in adventure mode, where Minecraft never calls
     * {@code Item#useOn} for a player who may not build (Forge answers PASS on both sides), so the camera's own useOn only
     * works in survival / creative. For everyone else the same steps run here, on both sides: the block reacts first (a
     * door, a switch) unless you sneak, then the client swings and the server places the camera (or says why not).
     */
    @SubscribeEvent
    public static void placeCamera(PlayerInteractEvent.RightClickBlock event) {
        Player p = event.getEntity();
        if (!event.getItemStack().is(ModItems.FIXED_CAMERA.get()) || p.getAbilities().mayBuild) return;
        if (!p.isSecondaryUseActive()) {
            BlockState state = event.getLevel().getBlockState(event.getPos());
            InteractionResult used = state.use(event.getLevel(), p, event.getHand(), event.getHitVec());
            if (used.consumesAction()) {
                event.setCanceled(true);
                event.setCancellationResult(used);
                return;
            }
        }
        event.setCanceled(true);
        if (p instanceof ServerPlayer sp) {
            boolean placed = GameManager.inGame(sp) && Cameras.place(sp, new UseOnContext(sp, event.getHand(), event.getHitVec()));
            event.setCancellationResult(placed ? InteractionResult.SUCCESS : InteractionResult.FAIL);
        } else {
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent
    public static void breakBlock(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer p && GameManager.inGame(p) && !p.isCreative()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        HitoriCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        SelfTest.onStarted(event.getServer());
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        GameManager.onServerStopping(event.getServer());
    }
}
