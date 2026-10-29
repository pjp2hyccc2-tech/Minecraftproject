package com.huntcraft.gear.weapon;

import com.huntcraft.core.DamageKind;
import com.huntcraft.core.WeaponClass;
import com.huntcraft.registry.HCItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/**
 * 片手剣 Sword & Shield: very fast, low motion values. Hold right click to guard (good guard), sneak + right click =
 * shield bash (blunt damage with KO buildup). Left click while guarding = guard slash.
 * Item use without sheathing: with a usable item in the off hand (potion, food, bomb, trap...), sneak + right click uses
 * it instead of the bash, and the sword & shield hunter uses it faster ({@link #QUICK_USE}) while still walking.
 */
public class SwordShieldItem extends HunterWeaponItem {
    /** Use-duration multiplier of off-hand items while a sword & shield is drawn. */
    public static final float QUICK_USE = 0.6F;

    public SwordShieldItem(WeaponLine line, Properties props) {
        super(WeaponClass.SWORD_AND_SHIELD, line, props);
    }

    /** Off-hand items the sword & shield can use without sheathing (consumables, tools, food...). */
    public static boolean isQuickUsable(ItemStack st) {
        if (st.isEmpty() || st.getItem() instanceof HunterWeaponItem || st.getItem() instanceof WhetstoneItem || st.getItem() instanceof CoatingItem) return false;
        if (st.getUseAnimation() != UseAnim.NONE || st.isEdible()) return true;
        for (RegistryObject<Item> ro : HCItems.CONSUMABLES) {
            if (ro.isPresent() && st.is(ro.get())) return true;
        }
        return false;
    }

    @Override
    public float guardPower() {
        return 0.65F;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack stack = p.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(stack);
        if (p.isShiftKeyDown()) {
            // pass to the off hand: Minecraft then uses the off-hand item (potion, bomb, trap...) without sheathing
            if (isQuickUsable(p.getOffhandItem())) return InteractionResultHolder.pass(stack);
            if (!level.isClientSide) shieldBash((ServerPlayer) p);
            p.getCooldowns().addCooldown(this, 18);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        beginUse(p, hand, WeaponState.MODE_GUARD);
        return InteractionResultHolder.consume(stack);
    }

    private void shieldBash(ServerPlayer p) {
        special(p, () -> WeaponHits.cone(p, 3.0, 70F, 0.24F, (t, ctx) -> {
            ctx.kind = DamageKind.BLUNT;
            ctx.extraKO = t.isKoZone() ? 14.0F : 4.0F;
            ctx.canBounce = false;
            ctx.useSharpness = false;
        }));
        playSound(p, WeaponSounds.BASH.get(), 1.0F, 1.0F);
        WeaponFx.send(p, ParticleTypes.CRIT, WeaponHits.front(p, 1.4, 1.2), 12, 0.4, 0.4, 0.4, 0.2);
    }

    @Override
    public void guardAttack(ServerPlayer p, ItemStack stack) {
        WeaponState s = WeaponState.get(p);
        if (s.counterCooldown > 0) return;
        s.counterCooldown = 8;
        float mv = nextComboMV(p);
        WeaponHits.cone(p, swingRange(), 90F, mv, null);
        swingSound(p, 0.8F);
    }

    @Override
    public void serverTick(ServerPlayer p, ItemStack stack, WeaponState s) {
        if (s.counterCooldown > 0) s.counterCooldown--;
    }

    @Override
    protected void appendClassInfo(ItemStack stack, List<Component> tooltip) {
        super.appendClassInfo(stack, tooltip);
        tooltip.add(Component.translatable("tooltip.huntcraft.weapon.bash").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.huntcraft.weapon.quick_item").withStyle(ChatFormatting.GRAY));
    }
}
