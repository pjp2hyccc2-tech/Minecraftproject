package com.eternalrealm.combat.forge;

import com.eternalrealm.combat.ActionDefinition;
import com.eternalrealm.combat.WeaponProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * An ETERNAL REALM weapon. Attacks are driven by the combat system (left click is intercepted on the client and turned
 * into a combat request); holding right click guards. The vanilla click attack never happens with these items, on
 * either side, so a modified client cannot use the vanilla attack path to bypass the combat rules.
 */
public final class WeaponItem extends Item {
    /** Effectively "until released", like the shield. */
    private static final int USE_DURATION = 72_000;

    private final WeaponProfile profile;

    public WeaponItem(WeaponProfile profile, Properties properties) {
        super(properties);
        this.profile = Objects.requireNonNull(profile, "profile");
    }

    public WeaponProfile profile() {
        return profile;
    }

    /**
     * @return the weapon profile of the stack, or {@code null} if it is not an ETERNAL REALM weapon. A named weapon
     *         (armory design) has its own profile: its reach, and for a boss weapon its weapon art; the same on the
     *         server and the client, which both read the design tag of the stack.
     */
    @Nullable
    public static WeaponProfile profileOf(ItemStack stack) {
        if (!(stack.getItem() instanceof WeaponItem weapon)) {
            return null;
        }
        return com.eternalrealm.item.forge.EquipmentStacks.designOf(stack)
                .filter(com.eternalrealm.item.WeaponDesign.class::isInstance)
                .map(design -> com.eternalrealm.item.DesignProfiles.of((com.eternalrealm.item.WeaponDesign) design))
                .orElse(weapon.profile);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BLOCK;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return USE_DURATION;
    }

    /**
     * Right click: guard. Only the server decides (D-035); the client sends the vanilla use packet and shows the guard
     * once vanilla syncs the "using item" state back. Sneaking + right click skips the guard so the off-hand item can
     * be used.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSecondaryUseActive()) {
            return InteractionResultHolder.pass(stack);
        }
        if (hand != InteractionHand.MAIN_HAND || !GuardGate.tryStartGuard(player, profileOf(stack))) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    /** Cancels the vanilla attack (Forge hook, both sides). Damage only comes from the combat system. */
    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        return true;
    }

    /** Weapons are not mining tools. */
    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }

    /** Equipment weapons carry their rarity-coloured name ("+3 鉄の片手剣"). */
    @Override
    public Component getName(ItemStack stack) {
        return com.eternalrealm.item.forge.EquipmentStacks.displayName(stack).orElseGet(() -> super.getName(stack));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return com.eternalrealm.item.forge.EquipmentStacks.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        WeaponProfile profile = profileOf(stack);
        boolean equipment = com.eternalrealm.item.forge.EquipmentStacks.isEquipment(stack);
        if (equipment) {
            com.eternalrealm.item.forge.EquipmentStacks.appendTooltip(stack, tooltip);
        } else {
            tooltip.add(Component.translatable(profile.type().translationKey()).withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("tooltip.eternalrealm.weapon.attack", format(profile.baseAttack()))
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.eternalrealm.weapon.combo", profile.combo().size())
                .withStyle(ChatFormatting.GRAY));
        ActionDefinition skill = profile.skill();
        tooltip.add(Component.translatable("tooltip.eternalrealm.weapon.skill",
                Component.translatable(skill.translationKey()), format(skill.cooldownTicks() / 20.0))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.eternalrealm.weapon.guard",
                Math.round(profile.guard().reduction() * 100)).withStyle(ChatFormatting.GRAY));
        if (profile.critBonus() > 0) {
            tooltip.add(Component.translatable("tooltip.eternalrealm.weapon.crit_bonus",
                    Math.round(profile.critBonus() * 100)).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.eternalrealm.weapon.controls").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static String format(double value) {
        return value == Math.rint(value)
                ? Long.toString((long) value)
                : String.format(Locale.ROOT, "%.1f", value);
    }

    /**
     * Client-only rendering hooks (only called on the client; the anonymous class is never loaded on a server): weapon
     * arm poses for every player and the first-person attack motions.
     */
    // CLIENT-ONLY-BEGIN (Forge calls initializeClient only on the physical client)
    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
            @Override
            public net.minecraft.client.model.HumanoidModel.ArmPose getArmPose(net.minecraft.world.entity.LivingEntity entity,
                                                                              InteractionHand hand, ItemStack stack) {
                return com.eternalrealm.client.WeaponAnimations.armPose(entity, hand, stack);
            }

            @Override
            public boolean applyForgeHandTransform(com.mojang.blaze3d.vertex.PoseStack poseStack,
                                                   net.minecraft.client.player.LocalPlayer player,
                                                   net.minecraft.world.entity.HumanoidArm arm, ItemStack itemInHand,
                                                   float partialTick, float equipProcess, float swingProcess) {
                return com.eternalrealm.client.WeaponAnimations.firstPerson(poseStack, player, arm, itemInHand,
                        partialTick, equipProcess, swingProcess);
            }
        });
    }
    // CLIENT-ONLY-END
}
