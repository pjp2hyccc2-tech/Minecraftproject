package com.eternalrealm.npc;

import com.eternalrealm.monster.MonsterScaling;
import com.eternalrealm.world.field.Habitats;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The four legendary travellers (docs/modules/travellers.md): in a single-player world they go their own rounds
 * (hunting at the habitats, shopping in the capital, resting at the inn); a player who meets one can ask them along,
 * and up to all four join the player's party and adventure with them. Pure data and rules: who they are, how strong,
 * their moves, how they choose them, what they say, where each goes next.
 *
 * <p>Their strength follows their titles: they stand at the level cap, with health and toughness no player has, and
 * their blows are measured against whatever they fight ({@link #damage}: a multiple of a level-matched player's
 * hit at the enemy's level), so a common monster falls to one of their skills wherever the player is. A boss takes
 * {@link #BOSS_SHARE} of that: the legends help, the fight stays the player's.</p>
 */
public final class Travellers {
    /** Translation key prefix: names, titles, lines, shouts, move names. */
    public static final String LANG = "traveller.eternalrealm.";
    /** Lines a traveller has for each place (talking to them on their round). */
    public static final int LINES_PER_PLACE = 3;
    /** Lines of the first meeting. */
    public static final int MEET_LINES = 3;
    /** Lines a companion has when the player talks to them. */
    public static final int PARTY_LINES = 4;
    /** Most of the time a traveller hunts at a habitat within this distance of the player (blocks). */
    public static final double NEAR_PLAYER = 240.0;
    /** Share of field trips that go near the player. */
    static final double NEAR_SHARE = 0.6;
    /** They stand at the level cap. */
    public static final int LEVEL = MonsterScaling.MAX_LEVEL;
    /** Share of their damage a boss takes. */
    public static final double BOSS_SHARE = 0.35;
    /** Ticks a fallen traveller stays down before standing up again by itself. */
    public static final int DOWN_TICKS = 20 * 15;
    /** Share of their health a traveller stands up with (by itself; the sister's light raises them fully). */
    public static final double STAND_UP_HEALTH = 0.4;
    /** Share of their maximum health they regain each second out of a fight. */
    public static final double REST_REGEN = 0.02;

    /** What a traveller does in a party. */
    public enum Role {
        /** Great spells from afar. */
        CASTER,
        /** Takes the blows, holds the enemies. */
        TANK,
        /** Cuts through everything up close. */
        BLADE,
        /** Heals, purifies, raises the fallen. */
        HEALER
    }

    /** The four of them. */
    public enum Traveller {
        /** 流離の魔女: once defended the kingdom alone. */
        ROSALIND("rosalind", Role.CASTER, "mage", 640.0, 0.35, 8.0, 0.55, 24),
        /** 竜退けの騎士: drove a dragon off with an iron sword. */
        SIEGWALD("siegwald", Role.TANK, "knight", 1000.0, 0.15, 2.6, 0.55, 22),
        /** 踏破の魔剣士: the only one who cleared every EX dungeon. */
        KAYLEN("kaylen", Role.BLADE, "spellblade", 720.0, 0.30, 2.4, 0.62, 14),
        /** 救国のシスター: her holy magic ended the plague and raised the kingdom. */
        MARIEL("mariel", Role.HEALER, "priest", 600.0, 0.35, 6.5, 0.55, 26);

        private final String id;
        private final Role role;
        private final String job;
        private final double maxHealth;
        private final double damageTaken;
        private final double range;
        private final double speed;
        private final int attackGap;

        Traveller(String id, Role role, String job, double maxHealth, double damageTaken, double range, double speed,
                  int attackGap) {
            this.id = id;
            this.role = role;
            this.job = job;
            this.maxHealth = maxHealth;
            this.damageTaken = damageTaken;
            this.range = range;
            this.speed = speed;
            this.attackGap = attackGap;
        }

        public String id() {
            return id;
        }

        public Role role() {
            return role;
        }

        /** The job whose colour their party frame wears. */
        public String job() {
            return job;
        }

        /** Maximum health (the attribute). */
        public double maxHealth() {
            return maxHealth;
        }

        /** Share of a monster's blow they take (their armour, their skill, their legend). */
        public double damageTaken() {
            return damageTaken;
        }

        /** How far from their enemy they stand to fight (blocks). */
        public double range() {
            return range;
        }

        /** Movement speed when they run (navigation speed modifier). */
        public double speed() {
            return speed;
        }

        /** Ticks between the end of one ordinary attack and the next. */
        public int attackGap() {
            return attackGap;
        }

        /** The hero body (tools/art_entities/hero_cast.py); its portrait has the same id. */
        public String appearance() {
            return "traveller_" + id;
        }

        /** The name (and dialogue speaker: the portrait is the key's last part, the appearance). */
        public String nameKey() {
            return LANG + appearance();
        }

        /** 流離の魔女 and so on. */
        public String titleKey() {
            return LANG + id + ".title";
        }

        public List<Move> moves() {
            return Arrays.stream(Move.values()).filter(move -> move.owner() == this).toList();
        }

        public Move move(Kind kind, boolean second) {
            for (Move move : Move.values()) {
                if (move.owner() == this && move.kind() == kind && move.second() == second) {
                    return move;
                }
            }
            throw new IllegalStateException(this + " has no " + kind + (second ? " (second)" : ""));
        }

        public static Optional<Traveller> byId(String id) {
            return Arrays.stream(values()).filter(w -> w.id.equals(id)).findFirst();
        }
    }

    /** What kind of move. */
    public enum Kind {
        /** Ordinary attacks (two, alternating). */
        ATTACK,
        /** Signature skills (two each). */
        SKILL,
        /** The finishing art (必殺), once a minute or so. */
        ULTIMATE
    }

    /**
     * Every move of the four: its animation slot ({@link HeroBodies}), its strength per blow (a multiple of a
     * level-matched player's hit), reach and area, the cooldown.
     */
    public enum Move {
        ROS_STAR_SHOT(Traveller.ROSALIND, Kind.ATTACK, false, "star_shot", HeroBodies.ATTACK, 1.5, 12.0, 1.5, 0),
        ROS_STARDUST(Traveller.ROSALIND, Kind.ATTACK, true, "stardust", HeroBodies.ATTACK_B, 1.2, 12.0, 6.0, 0),
        /** 流星雨: a circle in the sky over the enemy, stars fall in four waves. */
        ROS_STARFALL(Traveller.ROSALIND, Kind.SKILL, false, "starfall", HeroBodies.SKILL_A, 1.4, 13.0, 4.5, 240),
        /** 星縛りの鎖: constellations join the enemies and bind them where they stand. */
        ROS_STAR_BIND(Traveller.ROSALIND, Kind.SKILL, true, "star_bind", HeroBodies.SKILL_B, 2.0, 12.0, 5.5, 300),
        /** 千夜の天蓋: the sky of a thousand nights opens over the field and its stars fall. */
        ROS_THOUSAND_NIGHTS(Traveller.ROSALIND, Kind.ULTIMATE, false, "thousand_nights", HeroBodies.ULTIMATE, 3.0,
                14.0, 10.0, 1200),
        SIEG_CLEAVE(Traveller.SIEGWALD, Kind.ATTACK, false, "cleave", HeroBodies.ATTACK, 1.8, 3.2, 3.2, 0),
        SIEG_RISING(Traveller.SIEGWALD, Kind.ATTACK, true, "rising_chop", HeroBodies.ATTACK_B, 1.3, 3.2, 3.4, 0),
        /** 不動の誓い: the sword driven into the ground; every enemy around turns on him, nothing moves him. */
        SIEG_OATH(Traveller.SIEGWALD, Kind.SKILL, false, "oath", HeroBodies.SKILL_A, 1.0, 6.0, 10.0, 300),
        /** 旋風斬: two full turns with the blade held out. */
        SIEG_WHIRLWIND(Traveller.SIEGWALD, Kind.SKILL, true, "whirlwind", HeroBodies.SKILL_B, 1.6, 3.6, 4.2, 200),
        /** 竜退けの一閃: the cut that turned the dragon: a wave of fire and steel down a line. */
        SIEG_DRAGON_STROKE(Traveller.SIEGWALD, Kind.ULTIMATE, false, "dragon_stroke", HeroBodies.ULTIMATE, 8.0, 10.0,
                16.0, 1200),
        KAY_SLASH(Traveller.KAYLEN, Kind.ATTACK, false, "slash", HeroBodies.ATTACK, 1.6, 3.0, 3.0, 0),
        KAY_CROSS(Traveller.KAYLEN, Kind.ATTACK, true, "cross_thrust", HeroBodies.ATTACK_B, 1.3, 3.0, 3.2, 0),
        /** 瞬閃: gone and behind the enemy, the cut already made. */
        KAY_FLASH_STEP(Traveller.KAYLEN, Kind.SKILL, false, "flash_step", HeroBodies.SKILL_A, 3.5, 10.0, 2.5, 160),
        /** 幻刃陣: the phantom blades leave his back and fall on the enemies. */
        KAY_PHANTOM_ARRAY(Traveller.KAYLEN, Kind.SKILL, true, "phantom_array", HeroBodies.SKILL_B, 1.0, 10.0, 5.0,
                240),
        /** 無尽剣界: cut after cut from every side, then the blade goes home and everything cut falls apart. */
        KAY_INFINITE_BLADES(Traveller.KAYLEN, Kind.ULTIMATE, false, "infinite_blades", HeroBodies.ULTIMATE, 1.2, 12.0,
                10.0, 1100),
        MAR_HOLY_BOLT(Traveller.MARIEL, Kind.ATTACK, false, "holy_bolt", HeroBodies.ATTACK, 1.2, 10.0, 1.2, 0),
        MAR_LIGHT_LANCE(Traveller.MARIEL, Kind.ATTACK, true, "light_lance", HeroBodies.ATTACK_B, 1.6, 10.0, 1.8, 0),
        /** 癒しの聖域: a circle of light that heals and purifies everyone in it for six seconds. */
        MAR_SANCTUARY(Traveller.MARIEL, Kind.SKILL, false, "sanctuary", HeroBodies.SKILL_A, 0.06, 6.0, 6.0, 360),
        /** 浄化の光柱: pillars of light rise under the enemies (the undead burn twice). */
        MAR_PURIFICATION(Traveller.MARIEL, Kind.SKILL, true, "purification", HeroBodies.SKILL_B, 2.2, 10.0, 8.0, 220),
        /** 王国再誕の祈り: wings of light; every ally healed and raised, every enemy struck and stilled. */
        MAR_KINGDOM_REBORN(Traveller.MARIEL, Kind.ULTIMATE, false, "kingdom_reborn", HeroBodies.ULTIMATE, 3.0, 16.0,
                12.0, 1800);

        private final Traveller owner;
        private final Kind kind;
        private final boolean second;
        private final String id;
        private final int slot;
        private final double power;
        private final double reach;
        private final double radius;
        private final int cooldownTicks;

        Move(Traveller owner, Kind kind, boolean second, String id, int slot, double power, double reach, double radius,
             int cooldownTicks) {
            this.owner = owner;
            this.kind = kind;
            this.second = second;
            this.id = id;
            this.slot = slot;
            this.power = power;
            this.reach = reach;
            this.radius = radius;
            this.cooldownTicks = cooldownTicks;
        }

        public Traveller owner() {
            return owner;
        }

        public Kind kind() {
            return kind;
        }

        /** The second attack or skill of its kind. */
        public boolean second() {
            return second;
        }

        public String id() {
            return id;
        }

        /** The animation slot ({@link HeroBodies}). */
        public int slot() {
            return slot;
        }

        /**
         * Strength of each blow (multiple of a level-matched player's light hit); for the sanctuary the share of
         * an ally's health it heals each second.
         */
        public double power() {
            return power;
        }

        /** How far the enemy may be for the move to be used (blocks). */
        public double reach() {
            return reach;
        }

        /** The area it covers (radius, or the length of the dragon stroke's line). */
        public double radius() {
            return radius;
        }

        public int cooldownTicks() {
            return cooldownTicks;
        }

        /** Whether it helps allies instead of striking (the sanctuary). */
        public boolean heals() {
            return this == MAR_SANCTUARY;
        }

        /** Length of its animation (ticks). */
        public int lengthTicks() {
            return Math.max(1, Math.round(HeroBodies.length(owner.appearance(), slot) * 20.0F));
        }

        /** The ticks (from the start) at which its blows land. */
        public int[] hitTicks() {
            float[] hits = HeroBodies.hits(owner.appearance(), slot);
            int[] ticks = new int[hits.length];
            for (int i = 0; i < hits.length; i++) {
                ticks[i] = Math.round(hits[i] * 20.0F);
            }
            return ticks;
        }

        /** The move's name (shown with the call). */
        public String nameKey() {
            return LANG + "move." + id;
        }

        /** What the traveller calls out when using it. */
        public String shoutKey() {
            return LANG + owner.id() + ".shout." + id;
        }
    }

    /** Where a traveller is. */
    public enum Place {
        /** Hunting at a habitat. */
        FIELD,
        /** At the item shop of the capital. */
        SHOP,
        /** Resting in the inn's lobby. */
        INN;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Where a traveller is and until when.
     *
     * @param habitat the habitat's id in the field, "" in town
     * @param until   game time (ticks) when it moves on
     */
    public record Plan(Place place, String habitat, long until) {
        public Plan {
            if (place == null || habitat == null) {
                throw new IllegalArgumentException("a plan needs a place and a habitat id (\"\" in town)");
            }
            if ((place == Place.FIELD) == habitat.isEmpty()) {
                throw new IllegalArgumentException("a habitat in the field and only there: " + place + " " + habitat);
            }
        }
    }

    private Travellers() {
    }

    // ------------------------------------------------------------------ strength

    /**
     * Damage of one blow of {@code move} on an enemy of {@code level}: a level-matched player's light hit times the
     * move's power (a critical blow times {@code critical}); a boss takes {@link #BOSS_SHARE} of it.
     */
    public static double damage(Move move, int level, boolean boss, double critical) {
        int clamped = Math.max(1, Math.min(MonsterScaling.MAX_LEVEL, level));
        double amount = MonsterScaling.referenceLightHit(clamped) * move.power() * Math.max(1.0, critical);
        return boss ? amount * BOSS_SHARE : amount;
    }

    /** What a monster's blow (its damage against a player) does to a traveller. */
    public static double damageTaken(Traveller traveller, double blow, boolean guarding) {
        double taken = Math.max(0.0, blow) * traveller.damageTaken();
        return guarding ? taken * 0.3 : taken;
    }

    // ------------------------------------------------------------------ tactics

    /**
     * What a traveller sees when choosing a move.
     *
     * @param enemiesNear  enemies within the area of its moves around its target
     * @param bigTarget    the target is a boss or an elite
     * @param worstAlly    the lowest health share among the party (player and companions; 1 when nobody is hurt)
     * @param alliesHurt   allies under 70 % health
     * @param allyDown     a companion or the player has fallen
     * @param attackersOfOthers enemies attacking someone other than this traveller (the knight's cue)
     */
    public record Situation(double distance, int enemiesNear, boolean bigTarget, double worstAlly, int alliesHurt,
                            boolean allyDown, int attackersOfOthers) {
    }

    /**
     * The move to use now, if any: the ultimate when it is ready and worth it, a skill when one is ready and fits,
     * otherwise the next ordinary attack (they alternate). {@code ready} tells whether a move's cooldown is over.
     * Empty when the target is out of reach (heals do not need a target in reach).
     */
    public static Optional<Move> choose(Traveller who, Situation s, java.util.function.Predicate<Move> ready,
                                        boolean secondAttack) {
        Move ultimate = who.move(Kind.ULTIMATE, false);
        Move skillA = who.move(Kind.SKILL, false);
        Move skillB = who.move(Kind.SKILL, true);
        if (who == Traveller.MARIEL) {
            if (ready.test(ultimate) && (s.allyDown() || s.worstAlly() < 0.35 || s.alliesHurt() >= 2)) {
                return Optional.of(ultimate);
            }
            if (ready.test(skillA) && (s.worstAlly() < 0.7 || s.allyDown())) {
                return Optional.of(skillA);
            }
        }
        if (s.distance() > reachOf(who, s)) {
            return Optional.empty();
        }
        if (who != Traveller.MARIEL && ready.test(ultimate) && s.distance() <= ultimate.reach()
                && (s.enemiesNear() >= 3 || s.bigTarget() || s.worstAlly() < 0.4)) {
            return Optional.of(ultimate);
        }
        if (who == Traveller.MARIEL && ready.test(ultimate) && s.bigTarget() && s.enemiesNear() >= 3) {
            return Optional.of(ultimate);
        }
        if (who == Traveller.SIEGWALD && ready.test(skillA) && (s.attackersOfOthers() >= 1 || s.enemiesNear() >= 3)) {
            return Optional.of(skillA);
        }
        for (Move skill : who == Traveller.SIEGWALD ? List.of(skillB) : who == Traveller.MARIEL ? List.of(skillB)
                : List.of(skillA, skillB)) {
            if (ready.test(skill) && s.distance() <= skill.reach()) {
                return Optional.of(skill);
            }
        }
        Move attack = who.move(Kind.ATTACK, secondAttack);
        return s.distance() <= attack.reach() ? Optional.of(attack) : Optional.empty();
    }

    private static double reachOf(Traveller who, Situation s) {
        double reach = 0.0;
        for (Move move : who.moves()) {
            if (!move.heals()) {
                reach = Math.max(reach, move.reach());
            }
        }
        return reach;
    }

    // ------------------------------------------------------------------ the schedule

    /** Where a traveller goes when its time at {@code finished} is up: town after the field, out after the inn. */
    public static Place nextPlace(Place finished, double roll) {
        return switch (finished) {
            case FIELD -> roll < 0.5 ? Place.SHOP : Place.INN;
            case SHOP -> roll < 0.5 ? Place.INN : Place.FIELD;
            case INN -> Place.FIELD;
        };
    }

    /**
     * How long a traveller stays (ticks): 6-10 minutes in the field, 2-4 in the shop, 3-6 at the inn (6-10 at night).
     */
    public static long stay(Place place, boolean night, double roll) {
        double r = Math.max(0.0, Math.min(1.0, roll));
        return switch (place) {
            case FIELD -> minutes(6.0 + 4.0 * r);
            case SHOP -> minutes(2.0 + 2.0 * r);
            case INN -> night ? minutes(6.0 + 4.0 * r) : minutes(3.0 + 3.0 * r);
        };
    }

    private static long minutes(double minutes) {
        return Math.round(minutes * 60.0 * 20.0);
    }

    /**
     * The habitat of a field trip: most of the time one near the player (the travellers happen to travel where the
     * player does), otherwise any; never the one it just left.
     */
    public static Optional<Habitats.Habitat> pickHabitat(List<Habitats.Habitat> all, String last, double playerX,
                                                         double playerZ, Random random) {
        List<Habitats.Habitat> near = new ArrayList<>();
        List<Habitats.Habitat> other = new ArrayList<>();
        for (Habitats.Habitat habitat : all) {
            if (habitat.id().equals(last)) {
                continue;
            }
            (habitat.distanceTo(playerX, playerZ) <= NEAR_PLAYER ? near : other).add(habitat);
        }
        List<Habitats.Habitat> from = !near.isEmpty() && (other.isEmpty() || random.nextDouble() < NEAR_SHARE)
                ? near : other;
        return from.isEmpty() ? Optional.empty() : Optional.of(from.get(random.nextInt(from.size())));
    }

    // ------------------------------------------------------------------ the party

    /** Following: they keep to a place around the player: behind, on either side (blocks from the player). */
    public static double[] formation(int index) {
        return switch (Math.floorMod(index, 4)) {
            case 0 -> new double[]{-2.2, 1.6};
            case 1 -> new double[]{2.2, 1.6};
            case 2 -> new double[]{-1.2, 3.2};
            default -> new double[]{1.2, 3.2};
        };
    }

    /** Beyond this a companion stops walking and steps to the player's side (blocks). */
    public static final double CATCH_UP = 24.0;
    /** Companions fight enemies this close to the player (blocks). */
    public static final double GUARD_RADIUS = 14.0;
    /** ... and leave a fight when the player is this far. */
    public static final double LEASH = 22.0;

    // ------------------------------------------------------------------ what they say

    /** The lines of a talk at {@code place} (one of {@link #LINES_PER_PLACE}, picked by {@code index}). */
    public static String talkKey(Traveller traveller, Place place, int index) {
        return LANG + traveller.id() + "." + place.id() + "." + (Math.floorMod(index, LINES_PER_PLACE) + 1);
    }

    /** A companion's line when the player talks to them. */
    public static String partyKey(Traveller traveller, int index) {
        return LANG + traveller.id() + ".party." + (Math.floorMod(index, PARTY_LINES) + 1);
    }

    /** The lines of the first meeting, in order. */
    public static List<String> meetKeys(Traveller traveller) {
        List<String> keys = new ArrayList<>();
        for (int i = 1; i <= MEET_LINES; i++) {
            keys.add(LANG + traveller.id() + ".meet." + i);
        }
        return keys;
    }

    /** Asking them along, their answer, parting, falling, standing up. */
    public static String lineKey(Traveller traveller, String what) {
        return LANG + traveller.id() + "." + what;
    }

    // ------------------------------------------------------------------ stored lists ("rosalind,mariel")

    /** Travellers from a stored list; unknown ids are dropped. */
    public static Set<Traveller> parse(String stored) {
        Set<Traveller> out = new LinkedHashSet<>();
        if (stored == null || stored.isBlank()) {
            return out;
        }
        for (String id : stored.split(",")) {
            Traveller.byId(id.trim()).ifPresent(out::add);
        }
        return out;
    }

    public static String format(Set<Traveller> travellers) {
        return travellers.stream().map(Traveller::id).collect(Collectors.joining(","));
    }
}
