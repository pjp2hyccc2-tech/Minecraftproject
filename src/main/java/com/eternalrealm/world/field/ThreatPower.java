package com.eternalrealm.world.field;

import com.eternalrealm.monster.MonsterScaling;

/**
 * How strong a ヌシ (lord) and a 災厄 (calamity) are (owner, 2026-11-14: 「ヌシと災厄も弱すぎる。攻撃の種類とHPなども
 * 増やして、(レベル50のプレイヤー)1人でも倒せるけど5人くらい集まらないと倒せないレベルにして」).
 *
 * <ul>
 *     <li><b>Sized for five</b>: both are built for a party of {@value #SIZED_FOR} players of their level. A smaller
 *     party faces the same health (a calamity counts its fighters as at least five), so one player can win, but it
 *     takes about five times as long against the same blows.</li>
 *     <li><b>Level sync upwards</b>: they fight at least at the level of the strongest player who comes to fight them
 *     ({@link #level}). Players at the level of the field meet the fight it was made for; a level-50 player in the
 *     first field meets a level-50 lord, not a pushover. A lord takes the level of whoever is near when it appears,
 *     or of a stronger player's first blow before anyone has hurt it; a calamity takes the strongest fighter's level
 *     when its fight begins. Rewards follow the level fought.</li>
 *     <li><b>Health</b>: in the boss module's balance model (a level-matched player lands
 *     {@value #HITS_PER_SECOND} reference light hits a second, {@code MonsterScaling.referenceLightHit}) five players
 *     need about {@value #LORD_SECONDS} seconds for a lord before its armor (about five minutes with it; a player
 *     alone about twenty-five). Calamities keep their catalog health (boss.FieldBosses), which the five-player
 *     party size already puts at about six minutes for five.</li>
 * </ul>
 * Pure.
 */
public final class ThreatPower {
    /** The party a lord and a calamity are sized for. */
    public static final int SIZED_FOR = 5;
    /** Reference light hits a second of one fighter (the boss balance model). */
    public static final double HITS_PER_SECOND = 1.5;
    /** Seconds five level-matched players need for a lord in the model, before its armor. */
    public static final double LORD_SECONDS = 240.0;
    /** A lord's blows over its template's (it was 1.35). */
    public static final double LORD_DAMAGE = 1.8;
    /** Experience and gold of a lord's rewards, as multiples of its template's at the level fought (it was 2). */
    public static final double LORD_REWARD = 6.0;
    /** A calamity's blows over its catalog's. */
    public static final double CALAMITY_DAMAGE = 1.5;

    private ThreatPower() {
    }

    /** The level a lord or calamity fights at: its own, or the strongest player's if that is higher (at most 100). */
    public static int level(int own, int strongestPlayer) {
        int base = Math.max(1, Math.min(MonsterScaling.MAX_LEVEL, own));
        return Math.max(base, Math.min(MonsterScaling.MAX_LEVEL, strongestPlayer));
    }

    /** A lord's health at {@code level}: five players' worth of {@link #LORD_SECONDS} seconds of reference hits. */
    public static double lordHealth(int level) {
        return MonsterScaling.referenceLightHit(level) * HITS_PER_SECOND * SIZED_FOR * LORD_SECONDS;
    }

    /**
     * Seconds {@code players} level-matched players need for {@code health} with armor {@code armor} at {@code level}
     * in the model (tests and docs).
     */
    public static double seconds(double health, double armor, int level, int players) {
        double hit = MonsterScaling.damageAfterArmor(MonsterScaling.referenceLightHit(level),
                Math.min(MonsterScaling.MAX_ARMOR, armor));
        return health / (Math.max(1, players) * HITS_PER_SECOND * hit);
    }
}
