package com.eternalrealm.item.forge;

import com.eternalrealm.config.ERServerConfig;
import com.eternalrealm.item.Design;
import com.eternalrealm.item.EquipmentGenerator;
import com.eternalrealm.item.ItemRarity;
import com.eternalrealm.item.Relics;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * The relic drop (金枠の遺宝): every reward code of a dungeon boss, a calamity, a lord or a wanderer asks once per
 * rewarded player; about one kill in a thousand ({@code relicDropChance}), one calamity kill in a hundred
 * ({@code calamityRelicDropChance}), leaves one of the source's relics, always legendary, and the whole server hears of
 * it ({@link Relics}).
 */
public final class RelicDrops {
    /** Set by tests: the chance used instead of the config (negative: none). */
    private static volatile double forced = -1.0;

    private RelicDrops() {
    }

    /** The chance per rewarded player of a dungeon boss, lord or wanderer (the server config). */
    public static double chance() {
        return chance(Relics.SourceKind.BOSS);
    }

    /**
     * The chance per rewarded player of a source kind: the server config ({@code calamityRelicDropChance} for
     * calamities, {@code relicDropChance} for the rest), {@link Relics#dropChance} before it is loaded.
     */
    public static double chance(Relics.SourceKind kind) {
        if (forced >= 0.0) {
            return forced;
        }
        try {
            return kind == Relics.SourceKind.CALAMITY ? ERServerConfig.CALAMITY_RELIC_DROP_CHANCE.get()
                    : ERServerConfig.RELIC_DROP_CHANCE.get();
        } catch (IllegalStateException e) {
            return Relics.dropChance(kind);
        }
    }

    /** GameTests: makes every roll succeed (1.0), never succeed (0.0), or back to the config (negative). */
    public static void force(double chance) {
        forced = chance;
    }

    /**
     * One roll for {@code player} after a kill of the source {@code kind}/{@code target}: the relic's stack (to add
     * to the player's rewards; already announced), or empty.
     */
    public static Optional<ItemStack> roll(ServerPlayer player, Relics.SourceKind kind, String target,
                                           RandomGenerator random) {
        Relics.Source source = Relics.source(kind, target).orElse(null);
        if (source == null || !Relics.rolls(chance(kind), random)) {
            return Optional.empty();
        }
        ItemStack stack = create(Relics.pick(source, random), random);
        announce(player, stack, source);
        return Optional.of(stack);
    }

    /** A fresh legendary piece of a relic (four affixes, a unique id). */
    public static ItemStack create(Design relic, RandomGenerator random) {
        if (!relic.isRelic()) {
            throw new IllegalArgumentException("Not a relic: " + relic.id());
        }
        return EquipmentStacks.create(EquipmentGenerator.roll(relic, ItemRarity.LEGENDARY, random));
    }

    /** "【金枠】○○が「地下都市の盗賊王」から『簒奪者の細剣』を手に入れた！" to everyone, with a fanfare for the finder. */
    public static void announce(ServerPlayer player, ItemStack stack, Relics.Source source) {
        MinecraftServer server = player.getServer();
        Component message = Component.translatable("message.eternalrealm.relic.broadcast", player.getDisplayName(),
                Component.translatable(source.nameKey()), stack.getDisplayName()).withStyle(ChatFormatting.GOLD);
        if (server != null) {
            server.getPlayerList().broadcastSystemMessage(message, false);
            for (ServerPlayer other : server.getPlayerList().getPlayers()) {
                if (other != player) {
                    other.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 0.6F);
                }
            }
        } else {
            player.sendSystemMessage(message);
        }
        player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
        player.displayClientMessage(Component.translatable("message.eternalrealm.relic.found", stack.getHoverName())
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
    }
}
