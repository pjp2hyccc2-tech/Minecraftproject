package com.huntcraft.gear.weapon;

import com.huntcraft.data.HunterData;
import com.huntcraft.registry.HCEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Hunting horn songs. Each horn line has its own note colour set and 5-6 songs. A song is a pattern of notes
 * (1..3); performing (sneak + right click) plays every song found in the 4-note queue and buffs the performer and
 * nearby hunters. Performing a song again while its buff is active upgrades it (encore).
 */
public final class HornSongs {
    public static final int SELF_IMPROVEMENT = 0;
    public static final int ATTACK_UP = 1;
    public static final int DEFENSE_UP = 2;
    public static final int HEALTH_S = 3;
    public static final int HEALTH_L = 4;
    public static final int STAMINA = 5;
    public static final int EARPLUGS = 6;
    public static final int EFFECT_COUNT = 7;

    public static final String[] EFFECT_KEYS = {"self_improvement", "attack_up", "defense_up", "health_s", "health_l", "stamina", "earplugs"};
    public static final int[] EFFECT_COLORS = {0x9BE7FF, 0xFF6A4A, 0xF2C04A, 0x7CF07C, 0x3CDC6A, 0xFFE23D, 0xC8A8FF};
    /** Note colours: white, purple, red, blue, yellow, green, cyan, orange. */
    public static final int[] NOTE_RGB = {0xF4F4F4, 0xB04CFF, 0xE8402A, 0x3C7BEA, 0xF2D93B, 0x5BCB3C, 0x5FE0E8, 0xF08A24};
    public static final String[] NOTE_KEYS = {"white", "purple", "red", "blue", "yellow", "green", "cyan", "orange"};
    public static final double RANGE = 16.0;

    private HornSongs() {
    }

    public record Song(int[] pattern, int effect) {
    }

    private static Song s(int effect, int... pattern) {
        return new Song(pattern, effect);
    }

    /** Song list of a horn line (patterns use note numbers 1..3). */
    public static List<Song> songsFor(WeaponLine line) {
        return switch (line) {
            case IRON -> List.of(s(SELF_IMPROVEMENT, 1, 1), s(ATTACK_UP, 2, 3, 2), s(DEFENSE_UP, 3, 3, 1), s(HEALTH_S, 2, 2, 1),
                    s(STAMINA, 1, 3, 1, 3), s(EARPLUGS, 3, 2, 3, 1));
            case BONE -> List.of(s(SELF_IMPROVEMENT, 1, 1), s(ATTACK_UP, 2, 1, 2), s(HEALTH_S, 3, 2, 1), s(HEALTH_L, 3, 3, 2, 2),
                    s(STAMINA, 1, 3, 1));
            case IGNIVALD -> List.of(s(SELF_IMPROVEMENT, 1, 1), s(ATTACK_UP, 2, 2, 3), s(DEFENSE_UP, 3, 1, 3), s(HEALTH_S, 1, 2, 3),
                    s(EARPLUGS, 3, 3, 1, 1));
            case FROSTGARM -> List.of(s(SELF_IMPROVEMENT, 1, 1), s(DEFENSE_UP, 2, 2, 1), s(HEALTH_S, 3, 2, 3), s(HEALTH_L, 2, 3, 3, 2),
                    s(STAMINA, 1, 3, 1, 2), s(ATTACK_UP, 3, 1, 2));
            case VOLTRIX -> List.of(s(SELF_IMPROVEMENT, 1, 1), s(STAMINA, 2, 3, 2), s(ATTACK_UP, 3, 3, 2), s(HEALTH_S, 3, 1, 3),
                    s(EARPLUGS, 2, 1, 2, 1));
            case GRAVADON -> List.of(s(SELF_IMPROVEMENT, 1, 1), s(HEALTH_S, 3, 3, 1), s(HEALTH_L, 3, 2, 3, 2), s(DEFENSE_UP, 2, 2, 3),
                    s(EARPLUGS, 1, 3, 1), s(STAMINA, 2, 1, 2, 1));
            case AZRAGOS -> List.of(s(SELF_IMPROVEMENT, 1, 1), s(ATTACK_UP, 2, 2, 1), s(DEFENSE_UP, 3, 1, 3), s(EARPLUGS, 3, 3, 2),
                    s(STAMINA, 1, 2, 1, 2), s(HEALTH_L, 2, 3, 2, 3));
        };
    }

    /** Every song whose pattern appears in the queue (oldest .. newest). */
    public static List<Song> match(WeaponLine line, int[] notes, int count) {
        List<Song> out = new ArrayList<>();
        for (Song song : songsFor(line)) {
            int[] pat = song.pattern();
            for (int start = 0; start + pat.length <= count; start++) {
                boolean ok = true;
                for (int i = 0; i < pat.length; i++) {
                    if (notes[start + i] != pat[i]) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    out.add(song);
                    break;
                }
            }
        }
        // longest songs first, at most 3 per performance
        out.sort((a, b) -> b.pattern().length - a.pattern().length);
        return out.size() > 3 ? new ArrayList<>(out.subList(0, 3)) : out;
    }

    /** RGB of note n (1..3) for a horn line. */
    public static int noteColor(WeaponLine line, int note) {
        if (note < 1 || note > 3) return 0x555555;
        return NOTE_RGB[line.notes[note - 1]];
    }

    /** Coloured note dots followed by the song name (tooltips / HUD). */
    public static MutableComponent describe(WeaponLine line, Song song) {
        MutableComponent c = Component.literal("");
        for (int n : song.pattern()) {
            c.append(Component.literal("●").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(noteColor(line, n)))));
        }
        c.append(Component.literal(" "));
        c.append(Component.translatable("song.huntcraft." + EFFECT_KEYS[song.effect()]).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(EFFECT_COLORS[song.effect()]))));
        return c;
    }

    // ================================================================== performing
    public static void perform(ServerPlayer performer, WeaponLine line, WeaponState s) {
        List<Song> songs = match(line, s.notes, s.noteCount);
        if (songs.isEmpty()) {
            performer.displayClientMessage(Component.translatable("message.huntcraft.weapon.no_song").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        List<ServerPlayer> party = new ArrayList<>();
        for (ServerPlayer other : performer.serverLevel().players()) {
            if (other.isAlive() && !other.isSpectator() && other.distanceToSqr(performer) <= RANGE * RANGE) party.add(other);
        }
        MutableComponent msg = Component.literal("");
        boolean first = true;
        for (Song song : songs) {
            for (ServerPlayer target : party) {
                boolean self = target == performer;
                if (song.effect() == SELF_IMPROVEMENT && !self) continue;
                apply(target, song.effect());
            }
            if (!first) msg.append(Component.literal(" / "));
            msg.append(Component.translatable("song.huntcraft." + EFFECT_KEYS[song.effect()]).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(EFFECT_COLORS[song.effect()]))));
            first = false;
        }
        for (ServerPlayer target : party) {
            target.displayClientMessage(Component.translatable("message.huntcraft.weapon.song", msg), true);
            WeaponFx.send(target, ParticleTypes.NOTE, target.position().add(0, 2.1, 0), 6, 0.5, 0.2, 0.5, 1.0);
        }
    }

    /** Applies (or upgrades on encore) a song effect to one hunter. */
    public static void apply(ServerPlayer p, int effect) {
        WeaponState s = WeaponState.get(p);
        boolean encore = s.buffTicks[effect] > 0;
        switch (effect) {
            case SELF_IMPROVEMENT -> {
                int lv = encore ? 2 : 1;
                setBuff(s, effect, 1800, lv);
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 1800, lv - 1, false, true));
            }
            case ATTACK_UP, DEFENSE_UP -> setBuff(s, effect, encore ? 2400 : 1800, encore ? 2 : 1);
            case HEALTH_S -> {
                p.heal(4.0F);
                p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0, false, true));
                setBuff(s, effect, 60, 1);
                WeaponFx.send(p, ParticleTypes.HAPPY_VILLAGER, p.position().add(0, 1.0, 0), 10, 0.4, 0.5, 0.4, 0.05);
            }
            case HEALTH_L -> {
                p.heal(8.0F);
                p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, 1, false, true));
                setBuff(s, effect, 60, 1);
                WeaponFx.send(p, ParticleTypes.HEART, p.position().add(0, 1.5, 0), 6, 0.4, 0.4, 0.4, 0.05);
            }
            case STAMINA -> setBuff(s, effect, encore ? 1800 : 1200, encore ? 2 : 1);
            case EARPLUGS -> setBuff(s, effect, 1800, 1);
            default -> {
            }
        }
    }

    private static void setBuff(WeaponState s, int effect, int ticks, int level) {
        s.buffTicks[effect] = Math.max(s.buffTicks[effect], ticks);
        s.buffLevel[effect] = Math.max(s.buffLevel[effect], level);
        s.markDirty();
    }

    /** Server tick for every player: buff timers and continuous effects. */
    public static void tick(ServerPlayer p, WeaponState s) {
        boolean any = false;
        for (int i = 0; i < EFFECT_COUNT; i++) {
            if (s.buffTicks[i] > 0) {
                any = true;
                if (--s.buffTicks[i] == 0) {
                    s.buffLevel[i] = 0;
                    s.markDirty();
                }
            }
        }
        if (!any) return;
        if (s.buffTicks[STAMINA] > 0) {
            HunterData d = HunterData.get(p);
            d.stamina = d.maxStamina;
            d.exhausted = false;
        }
        if (s.buffTicks[EARPLUGS] > 0 && p.hasEffect(HCEffects.ROAR.get())) {
            p.removeEffect(HCEffects.ROAR.get());
        }
        if (p.tickCount % 40 == 0) s.markDirty();
    }

    /** Attack multiplier hook (installed with HuntHooks.addAttackMultiplier). */
    public static double attackMultiplier(Player p) {
        if (p == null || p.level().isClientSide) return 1.0;
        WeaponState s = WeaponState.get(p);
        if (s.buffTicks[ATTACK_UP] <= 0) return 1.0;
        return s.buffLevel[ATTACK_UP] >= 2 ? 1.15 : 1.10;
    }

    /** Incoming damage multiplier from Defense Up. */
    public static float defenseMultiplier(Player p) {
        WeaponState s = WeaponState.get(p);
        if (s.buffTicks[DEFENSE_UP] <= 0) return 1.0F;
        return s.buffLevel[DEFENSE_UP] >= 2 ? 0.75F : 0.85F;
    }
}
