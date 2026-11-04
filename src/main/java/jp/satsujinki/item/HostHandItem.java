package jp.satsujinki.item;

import jp.satsujinki.entity.ContestantEntity;
import jp.satsujinki.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 主催者の手: right-click a contestant to lift them, right-click again to hurl them (sneak = drop gently).
 */
public class HostHandItem extends Item {
    /** player UUID → held contestant UUID (server only). */
    private static final Map<UUID, UUID> HELD = new HashMap<>();
    public static final float THROW_STRENGTH = 1.4F;

    public HostHandItem(Properties props) {
        super(props);
    }

    public static boolean isHoldingItem(Player p) {
        return p.getMainHandItem().getItem() instanceof HostHandItem || p.getOffhandItem().getItem() instanceof HostHandItem;
    }

    public static boolean isHolding(Player p, ContestantEntity c) {
        return c.getUUID().equals(HELD.get(p.getUUID())) && isHoldingItem(p);
    }

    public static void forgetPlayer(Player p) {
        HELD.remove(p.getUUID());
    }

    public static void forgetEntity(ContestantEntity c) {
        HELD.values().removeIf(id -> id.equals(c.getUUID()));
    }

    public static void releaseAll(Player p) {
        UUID id = HELD.remove(p.getUUID());
        if (id != null && p.level() instanceof ServerLevel sl && sl.getEntity(id) instanceof ContestantEntity c) c.release(null, 0.0F);
    }

    public static void clear() {
        HELD.clear();
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof ContestantEntity c)) return InteractionResult.PASS;
        if (!(player.level() instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        if (HELD.containsKey(player.getUUID())) {
            throwHeld(sl, player, player.isShiftKeyDown() ? 0.0F : THROW_STRENGTH);
            return InteractionResult.SUCCESS;
        }
        if (c.isHeld()) return InteractionResult.FAIL;
        hold(player, c);
        return InteractionResult.SUCCESS;
    }

    /** Server: the player lifts the contestant (also used by the CI autotest). */
    public static void hold(Player player, ContestantEntity c) {
        if (c.isLocked()) c.unlock();
        HELD.put(player.getUUID(), c.getUUID());
        c.grab(player);
        player.level().playSound(null, c.getX(), c.getY(), c.getZ(), ModSounds.HOST_GRAB.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** Server: drop / throw whatever the player holds. */
    public static void letGo(Player player, float strength) {
        if (player.level() instanceof ServerLevel sl) throwHeld(sl, player, strength);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel sl && HELD.containsKey(player.getUUID())) {
            throwHeld(sl, player, player.isShiftKeyDown() ? 0.0F : THROW_STRENGTH);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static void throwHeld(ServerLevel level, Player player, float strength) {
        UUID id = HELD.remove(player.getUUID());
        if (id == null) return;
        Entity e = level.getEntity(id);
        if (e instanceof ContestantEntity c) {
            c.release(player, strength);
            level.playSound(null, c.getX(), c.getY(), c.getZ(), strength > 0.0F ? ModSounds.HOST_THROW.get() : ModSounds.HOST_GRAB.get(),
                    SoundSource.PLAYERS, 1.0F, strength > 0.0F ? 1.0F : 0.8F);
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.satsujinki.host_hand.desc1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.satsujinki.host_hand.desc2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.satsujinki.host_hand.desc3").withStyle(ChatFormatting.DARK_RED));
    }
}
