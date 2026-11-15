package com.eternalrealm.guild.battle;

import java.util.Optional;

/**
 * A guild's battle record (wins, draws, losses, knock-outs), shown on the guild page. Immutable.
 */
public record BattleRecord(int wins, int draws, int losses, int knockOuts) {
    public static final BattleRecord NONE = new BattleRecord(0, 0, 0, 0);

    public BattleRecord {
        if (wins < 0 || draws < 0 || losses < 0 || knockOuts < 0) {
            throw new IllegalArgumentException("negative record");
        }
    }

    /** The record after a battle that {@code side} fought ({@code winner} empty: a draw). */
    public BattleRecord after(GuildBattle.Side side, Optional<GuildBattle.Side> winner, int knockOutsScored) {
        int kos = (int) Math.min(Integer.MAX_VALUE, (long) knockOuts + Math.max(0, knockOutsScored));
        if (winner.isEmpty()) {
            return new BattleRecord(wins, inc(draws), losses, kos);
        }
        return winner.get() == side ? new BattleRecord(inc(wins), draws, losses, kos)
                : new BattleRecord(wins, draws, inc(losses), kos);
    }

    public int battles() {
        return (int) Math.min(Integer.MAX_VALUE, (long) wins + draws + losses);
    }

    private static int inc(int value) {
        return value == Integer.MAX_VALUE ? value : value + 1;
    }
}
