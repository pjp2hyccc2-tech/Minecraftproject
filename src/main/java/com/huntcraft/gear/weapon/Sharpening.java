package com.huntcraft.gear.weapon;

import com.huntcraft.core.HunterSkills;
import com.huntcraft.core.SkillType;
import com.huntcraft.data.HunterData;
import com.huntcraft.registry.HCSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Whetstone sharpening process (server). Started by the whetstone item or the sharpen key ("weapons/sharpen"). */
public final class Sharpening {
    public static final int NONE = -99;
    public static final int OFFHAND = -1;

    private Sharpening() {
    }

    public static boolean hasWhetstone(Player p) {
        if (p.getAbilities().instabuild) return true;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (p.getInventory().getItem(i).getItem() instanceof WhetstoneItem) return true;
        }
        return false;
    }

    public static int duration(Player p) {
        int lv = HunterSkills.level(p, SkillType.SPEED_SHARPENING);
        return lv >= 2 ? 16 : lv == 1 ? 28 : 40;
    }

    private static boolean sharpenable(ItemStack st) {
        return st.getItem() instanceof HunterWeaponItem w && w.usesSharpness();
    }

    private static boolean needs(Player p, ItemStack st) {
        return st.getItem() instanceof HunterWeaponItem w && w.needsSharpening(st, p);
    }

    /** Weapon to sharpen: main hand, off hand, then hotbar (preferring dull weapons). */
    public static int findTarget(Player p) {
        int sel = p.getInventory().selected;
        if (sharpenable(p.getMainHandItem())) return sel;
        if (sharpenable(p.getOffhandItem())) return OFFHAND;
        for (int i = 0; i < 9; i++) if (needs(p, p.getInventory().getItem(i))) return i;
        for (int i = 0; i < 9; i++) if (sharpenable(p.getInventory().getItem(i))) return i;
        return NONE;
    }

    public static ItemStack stackAt(Player p, int slot) {
        if (slot == OFFHAND) return p.getOffhandItem();
        if (slot < 0 || slot >= p.getInventory().getContainerSize()) return ItemStack.EMPTY;
        return p.getInventory().getItem(slot);
    }

    public static boolean isSharpening(Player p) {
        return WeaponState.get(p).sharpenLeft > 0;
    }

    public static void start(ServerPlayer p) {
        WeaponState s = WeaponState.get(p);
        if (s.sharpenLeft > 0) return;
        if (!hasWhetstone(p)) {
            p.displayClientMessage(Component.translatable("message.huntcraft.weapon.no_whetstone").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        int slot = findTarget(p);
        if (slot == NONE) {
            p.displayClientMessage(Component.translatable("message.huntcraft.weapon.nothing_to_sharpen").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (p.isUsingItem()) p.stopUsingItem();
        int dur = duration(p);
        s.sharpenSlot = slot;
        s.sharpenTotal = dur;
        s.sharpenLeft = dur;
        HunterData.get(p).sharpenTicks = dur;
        s.markDirty();
        HunterWeaponItem.playSound(p, HCSounds.SHARPEN.get(), 1.0F, 1.0F);
    }

    public static void cancel(ServerPlayer p, WeaponState s) {
        if (s.sharpenLeft <= 0) return;
        s.sharpenLeft = 0;
        s.sharpenSlot = NONE;
        HunterData.get(p).sharpenTicks = 0;
        s.markDirty();
    }

    public static void tick(ServerPlayer p, WeaponState s) {
        if (s.sharpenLeft <= 0) return;
        ItemStack st = stackAt(p, s.sharpenSlot);
        if (!(st.getItem() instanceof HunterWeaponItem w) || HunterData.get(p).isDodging()) {
            cancel(p, s);
            return;
        }
        s.sharpenLeft--;
        HunterData.get(p).sharpenTicks = s.sharpenLeft;
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 2, false, false));
        if (s.sharpenLeft % 8 == 4) {
            HunterWeaponItem.playSound(p, HCSounds.SHARPEN.get(), 0.8F, 0.9F + p.getRandom().nextFloat() * 0.3F);
            WeaponFx.send(p, ParticleTypes.CRIT, WeaponFx.hand(p).add(0, -0.3, 0), 4, 0.15, 0.1, 0.15, 0.15);
        }
        if (s.sharpenLeft == 0) {
            w.setSharpnessPoints(st, w.maxSharpness(st, p));
            s.sharpenSlot = NONE;
            s.markDirty();
            HunterWeaponItem.playSound(p, WeaponSounds.SHARPEN_DONE.get(), 0.8F, 1.6F);
            WeaponFx.send(p, ParticleTypes.ELECTRIC_SPARK, WeaponFx.hand(p), 10, 0.2, 0.2, 0.2, 0.1);
            p.displayClientMessage(Component.translatable("message.huntcraft.weapon.sharpened", st.getHoverName()).withStyle(ChatFormatting.AQUA), true);
        }
    }
}
