package com.huntcraft.gear.weapon;

import com.huntcraft.core.WeaponClass;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 弓 Bow: hold right click to draw (charge levels 1-3, no arrows needed, stamina per shot). Shot type per bow:
 * rapid (1-3 arrows in a line), spread (fan of arrows, strong up close) or pierce (one arrow hitting several parts).
 * Sneak + right click cycles coatings (power / poison / paralysis), consumed from the inventory per shot.
 */
public class HunterBowItem extends HunterWeaponItem {
    public static final String COAT_TAG = "HCCoat";
    public static final int COAT_NONE = 0, COAT_POWER = 1, COAT_POISON = 2, COAT_PARA = 3;
    public static final float SHOT_STAMINA = 8.0F;
    public static final float[] CHARGE_MULT = {0.7F, 0.8F, 1.0F, 1.2F};

    public HunterBowItem(WeaponLine line, Properties props) {
        super(WeaponClass.BOW, line, props);
    }

    @Override
    public boolean usesSharpness() {
        return false;
    }

    @Override
    protected int[] chargeThresholds() {
        return new int[]{6, 16, 28};
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    public static int getCoating(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().getInt(COAT_TAG) : COAT_NONE;
    }

    public static void setCoating(ItemStack stack, int coat) {
        stack.getOrCreateTag().putInt(COAT_TAG, coat);
    }

    /** Coating item for a coating index (null for none). */
    public static Item coatingItem(int coat) {
        return switch (coat) {
            case COAT_POWER -> WeaponsModule.COATING_POWER.get();
            case COAT_POISON -> WeaponsModule.COATING_POISON.get();
            case COAT_PARA -> WeaponsModule.COATING_PARA.get();
            default -> null;
        };
    }

    public static int countCoating(Player p, int coat) {
        Item it = coatingItem(coat);
        if (it == null) return 0;
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(it)) n += s.getCount();
        }
        return n;
    }

    private static boolean consumeCoating(Player p, int coat) {
        if (p.getAbilities().instabuild) return true;
        Item it = coatingItem(coat);
        if (it == null) return false;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(it)) {
                s.shrink(1);
                return true;
            }
        }
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack stack = p.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(stack);
        if (p.isShiftKeyDown()) {
            if (!level.isClientSide) cycleCoating((ServerPlayer) p, stack);
            p.getCooldowns().addCooldown(this, 6);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        beginUse(p, hand, WeaponState.MODE_CHARGE);
        if (!level.isClientSide) playSound(p, WeaponSounds.BOW_DRAW.get(), 0.6F, 1.0F);
        return InteractionResultHolder.consume(stack);
    }

    private void cycleCoating(ServerPlayer p, ItemStack stack) {
        int cur = getCoating(stack);
        int next = COAT_NONE;
        for (int i = 1; i <= 3; i++) {
            int c = (cur + i) % 4;
            if (c == COAT_NONE || countCoating(p, c) > 0 || p.getAbilities().instabuild) {
                next = c;
                break;
            }
        }
        setCoating(stack, next);
        playSound(p, WeaponSounds.COAT_SWITCH.get(), 0.8F, 1.0F + 0.1F * next);
        Component name = next == COAT_NONE ? Component.translatable("tooltip.huntcraft.weapon.coat_none") : Component.translatable(coatingItem(next).getDescriptionId());
        p.displayClientMessage(Component.translatable("message.huntcraft.weapon.coating", name).withStyle(ChatFormatting.AQUA), true);
    }

    @Override
    protected void onChargeLevel(Player p, ItemStack stack, int level) {
        playSound(p, WeaponSounds.BOW_DRAW.get(), 0.7F, 1.0F + 0.2F * level);
        WeaponFx.chargeBurst(p, level);
    }

    @Override
    protected void onChargeRelease(Level level, Player p, ItemStack stack, int used, int lv) {
        if (lv <= 0 && used < 4) return;
        int charge = Math.max(1, lv);
        if (level.isClientSide) return;
        if (!useStamina(p, SHOT_STAMINA)) {
            p.displayClientMessage(Component.translatable("message.huntcraft.weapon.no_stamina").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        int coat = getCoating(stack);
        if (coat != COAT_NONE && !consumeCoating(p, coat)) {
            coat = COAT_NONE;
            setCoating(stack, COAT_NONE);
            p.displayClientMessage(Component.translatable("message.huntcraft.weapon.coating_empty").withStyle(ChatFormatting.GRAY), true);
        }
        float cm = CHARGE_MULT[charge];
        ItemStack snapshot = stack.copy();
        final int fcoat = coat;
        switch (line.shot) {
            case WeaponLine.SHOT_SPREAD -> {
                int n = charge == 3 ? 5 : 3;
                float step = charge == 1 ? 10F : charge == 2 ? 7F : 6F;
                float mv = (charge == 3 ? 0.13F : 0.15F) * cm;
                for (int i = 0; i < n; i++) {
                    float yaw = (i - (n - 1) / 2.0F) * step;
                    fire(p, snapshot, mv, 0.7F, fcoat, 6.0F, 1, yaw, charge);
                }
            }
            case WeaponLine.SHOT_PIERCE -> fire(p, snapshot, (0.12F + 0.02F * charge) * cm, 0.8F, fcoat, 16.0F, 2 + charge, 0F, charge);
            default -> {
                float mv = (charge == 1 ? 0.22F : charge == 2 ? 0.18F : 0.16F) * cm;
                fire(p, snapshot, mv, 0.85F, fcoat, 12.0F, 1, 0F, charge);
                WeaponState s = WeaponState.get(p);
                for (int i = 1; i < charge; i++) {
                    s.schedule(p, i * 2, sp -> {
                        if (sp.getMainHandItem().getItem() == this) fire(sp, snapshot, mv, 0.85F, fcoat, 12.0F, 1, 0F, charge);
                    });
                }
            }
        }
        playSound(p, WeaponSounds.BOW_SHOT.get(), 1.0F, 0.9F + 0.1F * charge);
    }

    private void fire(Player p, ItemStack bow, float mv, float elementMult, int coat, float critRange, int hits, float yawOffset, int charge) {
        HunterArrow arrow = new HunterArrow(p.level(), p);
        arrow.setup(bow, mv, elementMult, coat, critRange, hits);
        arrow.shootFromRotation(p, p.getXRot(), p.getYRot() + yawOffset, 0.0F, 2.6F + 0.3F * charge, 0.6F);
        arrow.setCritArrow(charge >= 3);
        p.level().addFreshEntity(arrow);
    }

    @Override
    protected void appendClassInfo(ItemStack stack, List<Component> tooltip) {
        tooltip.add(Component.translatable("tooltip.huntcraft.weapon.shot", Component.translatable("shot.huntcraft." + shotKey(line.shot)))
                .withStyle(ChatFormatting.GRAY));
        int coat = getCoating(stack);
        Component name = coat == COAT_NONE ? Component.translatable("tooltip.huntcraft.weapon.coat_none") : Component.translatable(coatingItem(coat).getDescriptionId());
        tooltip.add(Component.translatable("tooltip.huntcraft.weapon.coating", name).withStyle(ChatFormatting.GRAY));
    }

    public static String shotKey(int shot) {
        return switch (shot) {
            case WeaponLine.SHOT_SPREAD -> "spread";
            case WeaponLine.SHOT_PIERCE -> "pierce";
            default -> "rapid";
        };
    }
}
