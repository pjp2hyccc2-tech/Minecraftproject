package com.huntcraft.gear.weapon;

import com.huntcraft.HuntCraft;
import com.huntcraft.core.HunterSkills;
import com.huntcraft.core.SkillType;
import com.huntcraft.core.WeaponClass;
import com.huntcraft.data.HunterData;
import com.huntcraft.entity.monster.HuntMonster;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

/**
 * 太刀 Long Sword: fast slashes fill the Spirit gauge. Right click = Spirit Blade combo (I, II, Roundslash) consuming the
 * gauge; a connecting Roundslash raises the spirit level white -> yellow -> red (+5/10/20% damage, decays over time).
 * Sneak + right click = Foresight Slash: brief i-frames; dodging a monster attack with it refills the gauge.
 */
public class LongSwordItem extends HunterWeaponItem {
    public static final float[] LEVEL_MULT = {1.0F, 1.05F, 1.10F, 1.20F};
    public static final int[] LEVEL_COLORS = {0x777777, 0xF4F4F4, 0xF2D93B, 0xE8402A};
    public static final int LEVEL_DURATION = 20 * 40;
    public static final float SPIRIT_COST = 15.0F;
    private static Field hitField;
    private static boolean hitFieldResolved;

    public LongSwordItem(WeaponLine line, Properties props) {
        super(WeaponClass.LONG_SWORD, line, props);
    }

    @Override
    public float getStateMultiplier(Player player, ItemStack stack) {
        int lv = WeaponState.get(player).spiritLevel;
        return LEVEL_MULT[Math.max(0, Math.min(3, lv))];
    }

    @Override
    protected void onWeaponHit(Player p, ItemStack stack, WeaponState s, LivingEntity target, float damage, boolean weak, boolean special) {
        if (special) return;
        float gain = 7.0F * (1.0F + 0.1F * HunterSkills.level(p, SkillType.FOCUS));
        s.spirit = Math.min(100.0F, s.spirit + gain);
        s.markDirty();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack stack = p.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(stack);
        if (p.isShiftKeyDown()) {
            foresight(level, p, stack);
            p.getCooldowns().addCooldown(this, 30);
        } else {
            WeaponState s = WeaponState.get(p);
            int step = level.getGameTime() - s.lastSpiritTime > 30 ? 0 : s.spiritStep;
            if (!level.isClientSide) spiritBlade((ServerPlayer) p, stack, s, step);
            p.getCooldowns().addCooldown(this, step == 2 ? 18 : 11);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private void spiritBlade(ServerPlayer p, ItemStack stack, WeaponState s, int step) {
        boolean powered = s.spirit >= SPIRIT_COST || p.getAbilities().instabuild;
        if (powered) s.spirit = Math.max(0, s.spirit - SPIRIT_COST);
        float mv = switch (step) {
            case 0 -> 0.30F;
            case 1 -> 0.34F;
            default -> 0.42F;
        };
        if (!powered) mv *= 0.6F;
        float fmv = mv;
        int[] hits = new int[1];
        special(p, () -> {
            if (step == 2) {
                hits[0] = WeaponHits.area(p, p.position().add(0, 1.0, 0), 3.4, fmv, null);
            } else {
                hits[0] = WeaponHits.cone(p, 3.6 + 0.2 * step, step == 0 ? 90F : 130F, fmv, null);
            }
        });
        playSound(p, WeaponSounds.SPIRIT_SLASH.get(), 1.0F, 0.9F + 0.15F * step);
        if (step == 2) {
            WeaponFx.send(p, ParticleTypes.SWEEP_ATTACK, p.position().add(0, 1.0, 0), 8, 1.4, 0.1, 1.4, 0.0);
        } else {
            WeaponFx.sweep(p, 1.8, 3);
        }
        if (powered) {
            WeaponFx.send(p, WeaponFx.dust(0xFF3A2A, 1.0F), WeaponHits.front(p, 1.6, 1.1), 14, 0.8, 0.4, 0.8, 0.0);
        }
        if (step == 2 && powered && hits[0] > 0) {
            raiseLevel(p, s);
        }
        s.spiritStep = (step + 1) % 3;
        s.lastSpiritTime = p.level().getGameTime();
        s.markDirty();
    }

    private void raiseLevel(ServerPlayer p, WeaponState s) {
        s.spiritLevel = Math.min(3, s.spiritLevel + 1);
        s.spiritLevelTicks = LEVEL_DURATION;
        playSound(p, WeaponSounds.SPIRIT_UP.get(), 1.0F, 0.8F + 0.2F * s.spiritLevel);
        WeaponFx.send(p, WeaponFx.dust(LEVEL_COLORS[s.spiritLevel], 1.6F), p.position().add(0, 1.0, 0), 30, 0.6, 0.8, 0.6, 0.0);
        p.displayClientMessage(Component.translatable("message.huntcraft.weapon.spirit_level." + s.spiritLevel)
                .withStyle(s.spiritLevel == 3 ? ChatFormatting.RED : s.spiritLevel == 2 ? ChatFormatting.YELLOW : ChatFormatting.WHITE), true);
    }

    private void foresight(Level level, Player p, ItemStack stack) {
        Vec3 f = WeaponHits.flatLook(p);
        if (level.isClientSide) {
            // small hop backwards (client drives player movement)
            p.setDeltaMovement(-f.x * 0.75, 0.18, -f.z * 0.75);
            return;
        }
        ServerPlayer sp = (ServerPlayer) p;
        WeaponState s = WeaponState.get(p);
        HunterData d = HunterData.get(p);
        d.iframes = Math.max(d.iframes, 12 + 2 * HunterSkills.level(p, SkillType.EVADE_WINDOW));
        s.spirit = 0;
        s.foresightTicks = 14;
        s.foresightDodged = false;
        s.foresightPre.clear();
        for (HuntMonster m : nearbyMonsters(p)) {
            if (monsterHitContains(m, p.getId())) s.foresightPre.add(m.getId());
        }
        s.markDirty();
        playSound(p, WeaponSounds.FORESIGHT.get(), 1.0F, 1.0F);
        WeaponFx.send(p, ParticleTypes.CLOUD, p.position().add(0, 0.2, 0), 8, 0.4, 0.05, 0.4, 0.02);
        s.schedule(p, 6, sp2 -> {
            if (sp2.getMainHandItem().getItem() != this) return;
            special(sp2, () -> WeaponHits.cone(sp2, 3.8, 110F, 0.32F, null));
            playSound(sp2, WeaponSounds.SPIRIT_SLASH.get(), 1.0F, 1.3F);
            WeaponFx.sweep(sp2, 1.8, 4);
        });
        // also check right away (an attack landing this very tick)
        checkDodge(sp, s);
    }

    @Override
    public void serverTick(ServerPlayer p, ItemStack stack, WeaponState s) {
        long now = p.level().getGameTime();
        if (s.foresightTicks > 0) {
            checkDodge(p, s);
            if (--s.foresightTicks == 0) {
                if (s.foresightDodged) {
                    s.spirit = 100.0F;
                    playSound(p, WeaponSounds.SPIRIT_UP.get(), 1.0F, 1.6F);
                    WeaponFx.send(p, WeaponFx.dust(0xFF3A2A, 1.5F), p.position().add(0, 1.0, 0), 30, 0.6, 0.8, 0.6, 0.0);
                    p.displayClientMessage(Component.translatable("message.huntcraft.weapon.foresight_success").withStyle(ChatFormatting.RED), true);
                }
                s.foresightPre.clear();
                s.markDirty();
            }
        }
        if (s.spiritLevel > 0 && --s.spiritLevelTicks <= 0) {
            s.spiritLevel--;
            s.spiritLevelTicks = s.spiritLevel > 0 ? LEVEL_DURATION : 0;
            s.markDirty();
        }
        if (s.spirit > 0 && now - s.lastHitTime > 100 && s.foresightTicks == 0) {
            s.spirit = Math.max(0, s.spirit - 0.25F);
            if (p.tickCount % 10 == 0) s.markDirty();
        }
        if (s.spirit >= 100.0F && p.tickCount % 6 == 0) {
            WeaponFx.send(p, WeaponFx.dust(0xFF3A2A, 0.7F), WeaponFx.hand(p), 2, 0.2, 0.3, 0.2, 0.0);
        }
        if (s.spiritLevel > 0 && p.tickCount % 8 == 0) {
            WeaponFx.send(p, WeaponFx.dust(LEVEL_COLORS[s.spiritLevel], 0.8F), WeaponFx.hand(p), 1, 0.15, 0.3, 0.15, 0.0);
        }
    }

    /** Called from the attack event when a hit was cancelled by i-frames during the foresight window. */
    public static void markDodged(Player p) {
        WeaponState s = WeaponState.get(p);
        if (s.foresightTicks > 0) s.foresightDodged = true;
    }

    private static List<HuntMonster> nearbyMonsters(Player p) {
        return p.level().getEntitiesOfClass(HuntMonster.class, new AABB(p.blockPosition()).inflate(24.0), m -> m.isAlive() && !m.isCarcass());
    }

    private static void checkDodge(ServerPlayer p, WeaponState s) {
        if (s.foresightDodged) return;
        for (HuntMonster m : nearbyMonsters(p)) {
            if (!m.isActing() || s.foresightPre.contains(m.getId())) continue;
            if (monsterHitContains(m, p.getId())) {
                s.foresightDodged = true;
                return;
            }
        }
    }

    /**
     * True if the monster's current action already tried to hit the entity (monsters record every entity they swing
     * at, even when the hit is negated by i-frames). Read reflectively because the core keeps the set protected.
     */
    private static boolean monsterHitContains(HuntMonster m, int entityId) {
        if (!hitFieldResolved) {
            hitFieldResolved = true;
            try {
                hitField = HuntMonster.class.getDeclaredField("hitThisAction");
                hitField.setAccessible(true);
            } catch (Exception e) {
                HuntCraft.LOGGER.warn("Long sword foresight: monster hit set unavailable ({})", e.toString());
                hitField = null;
            }
        }
        if (hitField == null) return false;
        try {
            Object o = hitField.get(m);
            return o instanceof Set<?> set && set.contains(entityId);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected void appendClassInfo(ItemStack stack, List<Component> tooltip) {
        super.appendClassInfo(stack, tooltip);
        tooltip.add(Component.translatable("tooltip.huntcraft.weapon.spirit").withStyle(ChatFormatting.GRAY));
    }
}
