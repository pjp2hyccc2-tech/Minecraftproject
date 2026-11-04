package jp.satsujinki.game;

import jp.satsujinki.Satsujinki;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.level.ServerPlayer;

/** Grants the mod's advancements (all use the minecraft:impossible trigger). */
public final class Adv {
    private Adv() {
    }

    public static void grant(ServerPlayer player, String path) {
        try {
            Advancement adv = player.server.getAdvancements().getAdvancement(Satsujinki.id(path));
            if (adv == null) return;
            AdvancementProgress progress = player.getAdvancements().getOrStartProgress(adv);
            if (progress.isDone()) return;
            for (String criterion : progress.getRemainingCriteria()) {
                player.getAdvancements().award(adv, criterion);
            }
        } catch (Exception e) {
            Satsujinki.LOGGER.warn("Could not grant advancement {}", path, e);
        }
    }
}
