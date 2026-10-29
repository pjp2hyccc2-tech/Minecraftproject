package com.huntcraft.gear.weapon;

import com.huntcraft.core.HunterSkills;
import com.huntcraft.core.SkillType;
import com.huntcraft.core.WeaponClass;
import com.huntcraft.data.HunterData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * 双剣 Dual Blades: right click toggles Demon Mode (faster and stronger, drains stamina, fills the demon gauge with
 * hits). With a full gauge the hunter enters Archdemon Mode after leaving Demon Mode. Sneak + right click in Demon
 * Mode = Blade Dance (multi-hit spin); in Archdemon Mode = Demon Flurry (spends gauge).
 */
public class DualBladesItem extends HunterWeaponItem {
    public static final UUID DEMON_SPEED_UUID = UUID.fromString("0f6f2c8a-9d41-4c57-8a3e-6b1d2e7c4f10");
    public static final float DEMON_MULT = 1.15F;
    public static final float ARCHDEMON_MULT = 1.08F;
    public static final float DEMON_DRAIN = 0.35F;

    public DualBladesItem(WeaponLine line, Properties props) {
        super(WeaponClass.DUAL_BLADES, line, props);
    }

    @Override
    public float getStateMultiplier(Player player, ItemStack stack) {
        WeaponState s = WeaponState.get(player);
        return s.demon ? DEMON_MULT : s.archdemon ? ARCHDEMON_MULT : 1.0F;
    }

    @Override
    protected void onWeaponHit(Player p, ItemStack stack, WeaponState s, LivingEntity target, float damage, boolean weak, boolean special) {
        if (s.demon) {
            float gain = (special ? 1.5F : 3.0F) * (1.0F + 0.1F * HunterSkills.level(p, SkillType.FOCUS));
            s.demonGauge = Math.min(100.0F, s.demonGauge + gain);
            if (s.demonGauge >= 100.0F && !s.archdemon) {
                s.archdemon = true;
                playSound(p, WeaponSounds.SPIRIT_UP.get(), 0.8F, 0.7F);
            }
            s.markDirty();
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack stack = p.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(stack);
        WeaponState s = WeaponState.get(p);
        if (p.isShiftKeyDown() && (s.demon || s.archdemon)) {
            if (!level.isClientSide) {
                if (s.demon) bladeDance((ServerPlayer) p, s);
                else demonFlurry((ServerPlayer) p, s);
            }
            p.getCooldowns().addCooldown(this, s.demon ? 32 : 24);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (!level.isClientSide) {
            if (s.demon) {
                setDemon((ServerPlayer) p, s, false);
            } else if (HunterData.get(p).stamina >= 10.0F || p.getAbilities().instabuild) {
                setDemon((ServerPlayer) p, s, true);
            } else {
                p.displayClientMessage(Component.translatable("message.huntcraft.weapon.no_stamina").withStyle(ChatFormatting.GRAY), true);
            }
        }
        p.getCooldowns().addCooldown(this, 8);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private void setDemon(ServerPlayer p, WeaponState s, boolean on) {
        s.demon = on;
        s.markDirty();
        if (on) {
            playSound(p, WeaponSounds.DEMON_ON.get(), 0.9F, 1.2F);
            WeaponFx.send(p, WeaponFx.dust(0xE0201A, 1.6F), p.position().add(0, 1.0, 0), 30, 0.5, 0.8, 0.5, 0.0);
            p.displayClientMessage(Component.translatable("message.huntcraft.weapon.demon_on").withStyle(ChatFormatting.RED), true);
        } else {
            playSound(p, WeaponSounds.DEMON_OFF.get(), 0.6F, 1.2F);
            if (s.archdemon) {
                p.displayClientMessage(Component.translatable("message.huntcraft.weapon.archdemon").withStyle(ChatFormatting.GOLD), true);
            }
        }
    }

    private void bladeDance(ServerPlayer p, WeaponState s) {
        if (!useStamina(p, 25.0F)) {
            p.displayClientMessage(Component.translatable("message.huntcraft.weapon.no_stamina").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        playSound(p, WeaponSounds.BLADE_DANCE.get(), 1.0F, 1.0F);
        for (int i = 0; i < 7; i++) {
            final int k = i;
            s.schedule(p, i * 3, sp -> {
                if (sp.getMainHandItem().getItem() != this) return;
                float mv = k == 6 ? 0.28F : 0.15F;
                Vec3 c = WeaponHits.front(sp, 1.0, 1.0);
                special(sp, () -> WeaponHits.area(sp, c, 2.8, mv, null));
                WeaponFx.send(sp, ParticleTypes.SWEEP_ATTACK, c, 2, 0.9, 0.2, 0.9, 0.0);
                WeaponFx.send(sp, WeaponFx.dust(0xE0201A, 1.0F), c, 6, 1.0, 0.4, 1.0, 0.0);
                if (k % 2 == 0) playSound(sp, WeaponSounds.SWING_LIGHT.get(), 0.7F, 1.2F + 0.05F * k);
            });
        }
    }

    private void demonFlurry(ServerPlayer p, WeaponState s) {
        if (s.demonGauge < 25.0F && !p.getAbilities().instabuild) return;
        s.demonGauge = Math.max(0, s.demonGauge - 25.0F);
        if (s.demonGauge <= 0) s.archdemon = false;
        s.markDirty();
        for (int i = 0; i < 3; i++) {
            s.schedule(p, i * 4, sp -> {
                if (sp.getMainHandItem().getItem() != this) return;
                special(sp, () -> WeaponHits.cone(sp, 3.4, 110F, 0.18F, null));
                WeaponFx.sweep(sp, 1.6, 2);
                playSound(sp, WeaponSounds.SWING_LIGHT.get(), 0.8F, 1.3F);
            });
        }
    }

    @Override
    public void serverTick(ServerPlayer p, ItemStack stack, WeaponState s) {
        HunterData d = HunterData.get(p);
        if (s.demon) {
            if (!p.getAbilities().instabuild) {
                float drain = DEMON_DRAIN * (1.0F - 0.1F * HunterSkills.level(p, SkillType.CONSTITUTION));
                d.stamina = Math.max(0, d.stamina - drain);
                d.staminaDelay = Math.max(d.staminaDelay, 10);
                if (d.stamina <= 0) {
                    d.exhausted = true;
                    d.exhaustTicks = 50;
                    setDemon(p, s, false);
                }
            }
            if (p.tickCount % 4 == 0) {
                WeaponFx.send(p, WeaponFx.dust(0xE0201A, 0.9F), p.position().add(0, 1.0, 0), 3, 0.35, 0.6, 0.35, 0.0);
            }
        } else if (s.archdemon) {
            s.demonGauge = Math.max(0, s.demonGauge - 0.12F);
            if (p.tickCount % 10 == 0) s.markDirty();
            if (s.demonGauge <= 0) {
                s.archdemon = false;
                s.markDirty();
            }
            if (p.tickCount % 8 == 0) {
                WeaponFx.send(p, WeaponFx.dust(0xFF8A2A, 0.8F), WeaponFx.hand(p), 2, 0.25, 0.4, 0.25, 0.0);
            }
        }
    }

    @Override
    public void onUnequip(ServerPlayer p, WeaponState s) {
        s.demon = false;
        s.markDirty();
    }

    /** Applies / removes the demon attack speed bonus (server, every tick). */
    public static void updateSpeed(Player p, WeaponState s, boolean holding) {
        AttributeInstance inst = p.getAttribute(Attributes.ATTACK_SPEED);
        if (inst == null) return;
        double want = !holding ? 0 : s.demon ? 0.35 : s.archdemon ? 0.15 : 0;
        AttributeModifier cur = inst.getModifier(DEMON_SPEED_UUID);
        if (want <= 0) {
            if (cur != null) inst.removeModifier(DEMON_SPEED_UUID);
            return;
        }
        if (cur == null || Math.abs(cur.getAmount() - want) > 1.0E-4) {
            if (cur != null) inst.removeModifier(DEMON_SPEED_UUID);
            inst.addTransientModifier(new AttributeModifier(DEMON_SPEED_UUID, "huntcraft demon mode", want, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    @Override
    protected void appendClassInfo(ItemStack stack, List<Component> tooltip) {
        super.appendClassInfo(stack, tooltip);
        tooltip.add(Component.translatable("tooltip.huntcraft.weapon.demon").withStyle(ChatFormatting.GRAY));
    }
}
