package com.huntcraft.gear.weapon;

import com.huntcraft.core.DamageKind;
import com.huntcraft.core.WeaponClass;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 狩猟笛 Hunting Horn: blunt. Every attack adds a note to a 4-note queue (left click = note 1, right click = note 2,
 * sneak + left click = note 3). Sneak + right click performs: songs found in the queue buff the performer and nearby
 * hunters (see {@link HornSongs}).
 */
public class HuntingHornItem extends HunterWeaponItem {
    public HuntingHornItem(WeaponLine line, Properties props) {
        super(WeaponClass.HUNTING_HORN, line, props);
    }

    @Override
    protected float onLeftClick(Player p, ItemStack stack) {
        float mv = nextComboMV(p);
        if (!p.level().isClientSide) addNote(p, p.isShiftKeyDown() ? 3 : 1);
        return mv;
    }

    public void addNote(Player p, int note) {
        WeaponState s = WeaponState.get(p);
        s.addNote(note);
        SoundEvent snd = switch (note) {
            case 1 -> WeaponSounds.HORN_NOTE1.get();
            case 2 -> WeaponSounds.HORN_NOTE2.get();
            default -> WeaponSounds.HORN_NOTE3.get();
        };
        // pitch follows the note colour so each horn sounds a little different
        float pitch = 0.75F + 0.08F * line.notes[Math.max(0, Math.min(2, note - 1))];
        playSound(p, snd, 0.9F, pitch);
        WeaponFx.send(p, ParticleTypes.NOTE, p.position().add(0, 2.2, 0), 1, 0.2, 0.1, 0.2, line.notes[note - 1] / 8.0);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack stack = p.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(stack);
        if (p.isShiftKeyDown()) {
            if (!level.isClientSide) perform((ServerPlayer) p);
            p.getCooldowns().addCooldown(this, 30);
        } else {
            if (!level.isClientSide) {
                ServerPlayer sp = (ServerPlayer) p;
                special(sp, () -> WeaponHits.cone(sp, swingRange() + 0.2, 110F, 0.38F, (t, ctx) -> ctx.kind = DamageKind.BLUNT));
                addNote(sp, 2);
                swingSound(sp, 1.0F);
                WeaponFx.sweep(sp, 1.8, 2);
            }
            p.getCooldowns().addCooldown(this, 14);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private void perform(ServerPlayer p) {
        WeaponState s = WeaponState.get(p);
        playSound(p, WeaponSounds.HORN_SONG.get(), 1.2F, 1.0F);
        special(p, () -> WeaponHits.area(p, p.position().add(0, 1.0, 0), 2.8, 0.28F, (t, ctx) -> ctx.kind = DamageKind.BLUNT));
        WeaponFx.send(p, ParticleTypes.NOTE, p.position().add(0, 1.6, 0), 12, 1.2, 0.6, 1.2, 1.0);
        HornSongs.perform(p, line, s);
        s.clearNotes();
    }

    @Override
    protected void appendClassInfo(ItemStack stack, List<Component> tooltip) {
        super.appendClassInfo(stack, tooltip);
        tooltip.add(Component.translatable("tooltip.huntcraft.weapon.songs").withStyle(ChatFormatting.GRAY));
        for (HornSongs.Song song : HornSongs.songsFor(line)) {
            tooltip.add(Component.literal(" ").append(HornSongs.describe(line, song)));
        }
    }
}
