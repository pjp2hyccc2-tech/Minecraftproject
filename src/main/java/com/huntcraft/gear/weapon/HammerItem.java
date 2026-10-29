package com.huntcraft.gear.weapon;

import com.huntcraft.core.DamageKind;
import com.huntcraft.core.WeaponClass;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * ハンマー Hammer: blunt damage that builds KO on heads. Hold right click to charge 3 levels (you can walk while
 * charging); release: Lv1 upswing, Lv2 side smash, Lv3 spinning big bang ending in a ground slam with heavy KO.
 */
public class HammerItem extends HunterWeaponItem {
    public HammerItem(WeaponLine line, Properties props) {
        super(WeaponClass.HAMMER, line, props);
    }

    @Override
    protected int[] chargeThresholds() {
        return new int[]{12, 26, 40};
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(stack);
        beginUse(player, hand, WeaponState.MODE_CHARGE);
        return InteractionResultHolder.consume(stack);
    }

    private static void blunt(WeaponHits.Target t, com.huntcraft.core.HunterDamage.Context ctx, float koHead, float koBody) {
        ctx.kind = DamageKind.BLUNT;
        ctx.extraKO = t.isKoZone() ? koHead : koBody;
    }

    @Override
    protected void onChargeRelease(Level level, Player p, ItemStack stack, int used, int lv) {
        if (lv <= 0) return;
        p.swing(InteractionHand.MAIN_HAND);
        if (level.isClientSide) return;
        WeaponState s = WeaponState.get(p);
        switch (lv) {
            case 1 -> {
                // upswing
                special(p, () -> WeaponHits.cone(p, 3.4, 80F, 0.60F, (t, ctx) -> {
                    blunt(t, ctx, 18.0F, 3.0F);
                    ctx.sharpnessCost = 1;
                }));
                playSound(p, WeaponSounds.SWING_HEAVY.get(), 1.0F, 0.9F);
                WeaponFx.send(p, ParticleTypes.CRIT, WeaponHits.front(p, 1.6, 1.6), 10, 0.3, 0.5, 0.3, 0.2);
            }
            case 2 -> {
                // side smash
                special(p, () -> WeaponHits.cone(p, 3.6, 130F, 0.85F, (t, ctx) -> {
                    blunt(t, ctx, 22.0F, 4.0F);
                    ctx.sharpnessCost = 2;
                }));
                playSound(p, WeaponSounds.SWING_HEAVY.get(), 1.0F, 0.75F);
                playSound(p, WeaponSounds.BASH.get(), 0.8F, 0.7F);
                WeaponFx.sweep(p, 1.8, 3);
            }
            default -> {
                // spinning big bang: 3 spin hits then a ground slam
                for (int i = 0; i < 3; i++) {
                    s.schedule(p, i * 4, sp -> {
                        if (sp.getMainHandItem().getItem() != this) return;
                        special(sp, () -> WeaponHits.area(sp, sp.position().add(0, 1.0, 0), 3.0, 0.22F, (t, ctx) -> blunt(t, ctx, 6.0F, 1.0F)));
                        WeaponFx.send(sp, ParticleTypes.SWEEP_ATTACK, sp.position().add(0, 1.0, 0), 4, 1.2, 0.1, 1.2, 0.0);
                        playSound(sp, WeaponSounds.SWING_HEAVY.get(), 0.8F, 1.1F);
                    });
                }
                s.schedule(p, 14, sp -> {
                    if (sp.getMainHandItem().getItem() != this) return;
                    special(sp, () -> WeaponHits.cone(sp, 3.8, 100F, 1.25F, (t, ctx) -> {
                        blunt(t, ctx, 40.0F, 8.0F);
                        ctx.sharpnessCost = 3;
                        ctx.elementMult = 1.5F;
                        ctx.statusMult = 1.5F;
                    }));
                    Vec3 at = WeaponHits.front(sp, 2.0, 0.1);
                    playSound(sp, WeaponSounds.SLAM.get(), 1.0F, 0.8F);
                    WeaponFx.slam(sp, at, 2.0F);
                    WeaponFx.element(sp, getElement(stack), at.add(0, 0.8, 0), 14);
                });
            }
        }
    }

    @Override
    protected void appendClassInfo(ItemStack stack, List<Component> tooltip) {
        super.appendClassInfo(stack, tooltip);
        tooltip.add(Component.translatable("tooltip.huntcraft.weapon.ko").withStyle(ChatFormatting.GRAY));
    }
}
