package com.clockworkdiver.item;

import com.clockworkdiver.combat.TickStrike;
import com.clockworkdiver.registry.ModBlocks;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The Tinker's Wrench (修理工のレンチ): weapon and tool. Hits that land on the clock's beat ("Just Tick")
 * deal bonus damage and build steam pressure; right click spends steam on a Steam Burst, sneak + right
 * click on a Piston Dash.
 */
public class WrenchItem extends Item {
    private final Multimap<Attribute, AttributeModifier> modifiers;

    public WrenchItem(Properties properties) {
        super(properties);
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Wrench damage", 5.0,
                AttributeModifier.Operation.ADDITION));
        builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Wrench speed", -2.2,
                AttributeModifier.Operation.ADDITION));
        this.modifiers = builder.build();
    }

    @Override
    @SuppressWarnings("deprecation")
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? modifiers : super.getDefaultAttributeModifiers(slot);
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return state.is(ModBlocks.RUST_CLUSTER.get()) ? 14.0f : 1.0f;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            TickStrike.useAbility(sp);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.clockworkdiver.wrench.0").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.clockworkdiver.wrench.1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.clockworkdiver.wrench.2").withStyle(ChatFormatting.GRAY));
    }
}
