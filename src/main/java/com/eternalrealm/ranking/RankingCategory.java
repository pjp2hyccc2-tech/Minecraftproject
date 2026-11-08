package com.eternalrealm.ranking;

import com.eternalrealm.core.ModInfo;

import java.util.List;
import java.util.Optional;

/**
 * The ranking categories of a season (docs/RANKING_SYSTEM.md, section 1).
 *
 * <p>{@link #id()} is the stable persistence key; never rename or reuse it. The {@link Scope} says what is ranked:
 * players (score from {@link SeasonStats}, see {@link RankingCalculator}), guilds (sum of the members'
 * guild contribution) or boss time-attack runs (fastest clear, see {@link TimeAttackBoard}).</p>
 */
public enum RankingCategory {
    /** 総合: weighted, normalised sum of the other player categories. */
    OVERALL("overall", Scope.PLAYER),
    /** レベル: season EXP. */
    LEVEL("level", Scope.PLAYER),
    /** 戦闘: monster kills, elite kills, damage and PvP wins. */
    COMBAT("combat", Scope.PLAYER),
    /** 探索: areas discovered and distance travelled. */
    EXPLORATION("exploration", Scope.PLAYER),
    /** ボス討伐: bosses defeated. */
    BOSS("boss", Scope.PLAYER),
    /** 生産: items crafted. */
    CRAFTING("crafting", Scope.PLAYER),
    /** ギルド: sum of the members' guild contribution. */
    GUILD("guild", Scope.GUILD),
    /** パーティー: dungeon clears with at least two members. */
    PARTY("party", Scope.PLAYER),
    /** ボスタイムアタック: fastest clear per boss, difficulty and party size. */
    BOSS_TIME_ATTACK("boss_time_attack", Scope.TIME_ATTACK);

    /** What a category ranks. */
    public enum Scope {
        PLAYER, GUILD, TIME_ATTACK
    }

    private static final List<RankingCategory> ALL = List.of(values());
    private static final List<RankingCategory> PLAYER_CATEGORIES =
            ALL.stream().filter(RankingCategory::isPlayerCategory).toList();
    private static final List<RankingCategory> OVERALL_COMPONENTS =
            ALL.stream().filter(RankingCategory::isOverallComponent).toList();

    private final String id;
    private final Scope scope;

    RankingCategory(String id, Scope scope) {
        this.id = id;
        this.scope = scope;
    }

    public String id() {
        return id;
    }

    public Scope scope() {
        return scope;
    }

    public String translationKey() {
        return "ranking_category." + ModInfo.MOD_ID + "." + id;
    }

    /** Ranks players by a score computed from their {@link SeasonStats}. */
    public boolean isPlayerCategory() {
        return scope == Scope.PLAYER;
    }

    /** A player category that feeds the {@link #OVERALL} score. */
    public boolean isOverallComponent() {
        return scope == Scope.PLAYER && this != OVERALL;
    }

    /** Ranks a score (players and guilds), as opposed to a time. */
    public boolean isScoreCategory() {
        return scope != Scope.TIME_ATTACK;
    }

    public static List<RankingCategory> all() {
        return ALL;
    }

    /** OVERALL, LEVEL, COMBAT, EXPLORATION, BOSS, CRAFTING and PARTY, in declaration order. */
    public static List<RankingCategory> playerCategories() {
        return PLAYER_CATEGORIES;
    }

    /** The player categories without OVERALL, in declaration order. */
    public static List<RankingCategory> overallComponents() {
        return OVERALL_COMPONENTS;
    }

    public static Optional<RankingCategory> byId(String id) {
        for (RankingCategory category : ALL) {
            if (category.id.equals(id)) {
                return Optional.of(category);
            }
        }
        return Optional.empty();
    }
}
