package com.eternalrealm.guild.house.forge;

import com.eternalrealm.estate.EstateKey;
import com.eternalrealm.estate.forge.LedgerService;
import com.eternalrealm.guild.Guild;
import com.eternalrealm.guild.GuildRank;
import com.eternalrealm.guild.house.GuildHouseRules;
import com.eternalrealm.guild.house.StorageLogEntry;
import net.minecraft.server.level.ServerPlayer;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The storage desk of a guild house (ギルド倉庫の帳場; owner, 2026-11-15: 「マイハウスみたいに一括でアイテムを管理できる
 * ギミックを入れて」): the sorting desk's ledger of the inn rooms ({@link LedgerService}), with the guild's rules
 * (docs/modules/guildhouse.md §storage desk):
 * <ul>
 *     <li>it lists and fills the house's storage only: the members' chests ({@value GuildHouseRules#CHEST_PREFIX}…),
 *     barrels and shelves, and lists the vaults ({@value GuildHouseRules#VAULT_PREFIX}…) for the master and the
 *     officers (in a showcase, for the testers there) to take from - "put away" never fills a vault, what goes in
 *     there is put in by hand; never the work stations' furnaces or brewing stands;</li>
 *     <li>everything it moves is written in the guild's storage log like a chest closed by hand (per piece and kind of
 *     thing), and others who have that chest open are not credited with it.</li>
 * </ul>
 */
final class GuildLedger implements LedgerService.Policy {
    private GuildLedger() {
    }

    static void install() {
        LedgerService.setPolicy(EstateKey.GUILD, new GuildLedger());
    }

    @Override
    public boolean uses(ServerPlayer player, EstateKey key, String box) {
        if (box.startsWith(GuildHouseRules.CHEST_PREFIX) || box.startsWith("barrel.") || box.startsWith("shelf.")) {
            return true;
        }
        if (!GuildHouseRules.isVault(box)) {
            return false;
        }
        GuildEstates estates = GuildHouses.estates();
        if (estates.isShowcase(key.owner())) {
            return estates.mayTour(player);
        }
        Optional<Guild> guild = GuildHouses.guildOf(player);
        GuildRank rank = guild.filter(g -> g.id().equals(key.owner())).flatMap(g -> g.rankOf(player.getUUID()))
                .orElse(null);
        return rank != null && GuildHouseRules.mayOpenVault(rank);
    }

    @Override
    public boolean fills(ServerPlayer player, EstateKey key, String box) {
        return !GuildHouseRules.isVault(box) && uses(player, key, box);
    }

    @Override
    public void moved(ServerPlayer player, EstateKey key, List<LedgerService.Moved> moves) {
        for (LedgerService.Moved move : moves) {
            GuildHouseEvents.movedByDesk(move.container(), move.sample(), move.count(), move.deposit());
        }
        if (GuildHouses.estates().isShowcase(key.owner())) {
            return;
        }
        Instant at = Instant.now();
        GuildHouses.get().ifPresent(houses -> {
            for (LedgerService.Moved move : moves) {
                houses.service().log(key.owner(), new StorageLogEntry(at, player.getUUID(),
                        player.getGameProfile().getName(), GuildHouseEvents.itemId(move.sample()), move.count(),
                        move.deposit(), move.box().isEmpty() ? "storage" : move.box()));
            }
        });
    }
}
