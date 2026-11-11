package com.eternalrealm.testkit.bots;

import com.eternalrealm.character.StatBlock;
import com.eternalrealm.character.StatType;
import com.eternalrealm.combat.ActionDefinition;
import com.eternalrealm.combat.HitFrame;
import com.eternalrealm.combat.WeaponProfile;
import com.eternalrealm.combat.WeaponType;
import com.eternalrealm.item.Consumable;
import com.eternalrealm.job.JobType;
import com.eternalrealm.job.Jobs;
import com.eternalrealm.job.SkillDefinition;
import com.eternalrealm.job.SkillTree;
import com.eternalrealm.world.LevelRange;
import com.eternalrealm.world.field.FieldLayout;
import com.eternalrealm.world.field.Habitats;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * The test bots' choices that do not need a world (docs/modules/testkit.md §4), kept apart so they can be tested
 * offline: which job a bot takes, where its points go, where it hunts, what it buys and how close it fights.
 */
public final class BotPlans {
    /** The jobs in turn: Bot01 is a knight, Bot02 an assassin... so every 30 bots hold five of each. */
    public static final List<JobType> JOBS = List.of(JobType.KNIGHT, JobType.ASSASSIN, JobType.MAGE, JobType.RANGER,
            JobType.PRIEST, JobType.SPELLBLADE);
    /** Health potions a bot likes to carry. */
    public static final int POTIONS_WANTED = 8;
    /** A bot spends at most this share of its gold on potions in one visit. */
    static final double POTION_BUDGET = 0.5;
    /** With fewer free inventory slots than this a bot goes to sell. */
    public static final int FEW_FREE_SLOTS = 8;
    /** After this many hunts a bot likes a rest at the inn. */
    static final int HUNTS_BEFORE_REST = 2;
    /** Ranged fighters stand this far from their target. */
    public static final double RANGED_DISTANCE = 8.0;

    /** What a bot does next in town. */
    public enum Errand {
        SHOP, INN, WANDER, HUNT
    }

    private BotPlans() {
    }

    public static JobType jobOf(int number) {
        if (number < 1) {
            throw new IllegalArgumentException("bot numbers start at 1: " + number);
        }
        return JOBS.get((number - 1) % JOBS.size());
    }

    /**
     * Where {@code unspent} stat points go: as a player of the job would, in the proportions the job grows by (a point
     * more for VIT, to stay alive), each point to the stat furthest behind its share.
     */
    public static Map<StatType, Integer> allocation(StatBlock growth, StatBlock allocated, int unspent) {
        Map<StatType, Integer> plan = new EnumMap<>(StatType.class);
        if (unspent <= 0) {
            return plan;
        }
        StatType[] stats = StatType.values();
        double[] weight = new double[stats.length];
        int[] have = new int[stats.length];
        for (StatType stat : stats) {
            weight[stat.ordinal()] = Math.max(0, growth.get(stat)) + (stat == StatType.VIT ? 1.0 : 0.0);
            have[stat.ordinal()] = Math.max(0, allocated.get(stat));
        }
        for (int point = 0; point < unspent; point++) {
            StatType best = StatType.VIT;
            double bestShare = Double.MAX_VALUE;
            for (StatType stat : stats) {
                if (weight[stat.ordinal()] <= 0.0) {
                    continue;
                }
                double share = (have[stat.ordinal()] + 1) / weight[stat.ordinal()];
                if (share < bestShare) {
                    bestShare = share;
                    best = stat;
                }
            }
            have[best.ordinal()]++;
            plan.merge(best, 1, Integer::sum);
        }
        return plan;
    }

    /**
     * The skill a bot puts its next point into: a new skill it can use while its skill slots are not full, otherwise
     * one it already knows (the skills it uses first), otherwise anything open to it (passives); tree order.
     */
    public static Optional<String> nextSkill(JobType job, int level, Map<String, Integer> investments, int unspent) {
        if (unspent <= 0 || !job.isChosen()) {
            return Optional.empty();
        }
        List<SkillDefinition> open = new ArrayList<>();
        for (SkillDefinition skill : Jobs.get(job).skills()) {
            if (SkillTree.canInvest(job, level, investments, unspent, skill.id()) == SkillTree.InvestResult.OK) {
                open.add(skill);
            }
        }
        if (open.isEmpty()) {
            return Optional.empty();
        }
        long known = Jobs.get(job).skills().stream()
                .filter(skill -> skill.isUsable() && SkillTree.levelOf(skill, level, investments) > 0).count();
        if (known < SkillTree.LOADOUT_SLOTS) {
            Optional<SkillDefinition> fresh = open.stream()
                    .filter(skill -> skill.isUsable() && SkillTree.levelOf(skill, level, investments) == 0).findFirst();
            if (fresh.isPresent()) {
                return Optional.of(fresh.get().id());
            }
        }
        Optional<SkillDefinition> raise = open.stream()
                .filter(skill -> skill.isUsable() && investments.getOrDefault(skill.id(), 0) > 0).findFirst();
        return Optional.of(raise.orElse(open.get(0)).id());
    }

    /**
     * Habitats whose monsters suit a character of {@code level}: their levels reach from a little below it to one
     * above. When none do, the nearest in level (a fresh character hunts the weakest kinds, a strong one the
     * strongest).
     */
    public static List<Habitats.Habitat> huntingGrounds(int level, List<Habitats.Habitat> habitats,
                                                         Function<String, Optional<LevelRange>> levels) {
        List<Habitats.Habitat> fitting = new ArrayList<>();
        List<Habitats.Habitat> nearest = new ArrayList<>();
        int nearestGap = Integer.MAX_VALUE;
        for (Habitats.Habitat habitat : habitats) {
            Optional<LevelRange> range = levels.apply(habitat.monster());
            if (range.isEmpty()) {
                continue;
            }
            int min = range.get().min();
            int max = range.get().max();
            if (min <= level + 1 && max >= level - 3) {
                fitting.add(habitat);
                continue;
            }
            int gap = level < min ? min - level : level - max;
            if (gap < nearestGap) {
                nearestGap = gap;
                nearest.clear();
            }
            if (gap == nearestGap) {
                nearest.add(habitat);
            }
        }
        return fitting.isEmpty() ? nearest : fitting;
    }

    /**
     * The waystone a bot travels to for a hunting ground at ({@code x}, {@code z}) in {@code field}: the nearest of
     * that field (of all, if the field has none).
     */
    public static Optional<FieldLayout.Waystone> waystoneFor(int field, double x, double z,
                                                             List<FieldLayout.Waystone> all) {
        FieldLayout.Waystone best = null;
        double bestDistance = Double.MAX_VALUE;
        boolean bestInField = false;
        for (FieldLayout.Waystone waystone : all) {
            boolean inField = waystone.field() == field;
            double dx = waystone.x() - x;
            double dz = waystone.z() - z;
            double distance = dx * dx + dz * dz;
            if ((inField && !bestInField) || (inField == bestInField && distance < bestDistance)) {
                best = waystone;
                bestDistance = distance;
                bestInField = inField;
            }
        }
        return Optional.ofNullable(best);
    }

    /** The strongest health potion a character of {@code level} may drink. */
    public static Consumable bestPotion(int level) {
        Consumable best = Consumable.HEALTH_POTION_SMALL;
        for (Consumable potion : List.of(Consumable.HEALTH_POTION_MEDIUM, Consumable.HEALTH_POTION_LARGE)) {
            if (level >= potion.requiredLevel()) {
                best = potion;
            }
        }
        return best;
    }

    public static boolean isHealthPotion(Consumable consumable) {
        return consumable == Consumable.HEALTH_POTION_SMALL || consumable == Consumable.HEALTH_POTION_MEDIUM
                || consumable == Consumable.HEALTH_POTION_LARGE;
    }

    /** How many potions to buy: up to {@value #POTIONS_WANTED} carried, with at most half the gold. */
    public static int potionsToBuy(int carried, long gold, long price) {
        if (price <= 0 || carried >= POTIONS_WANTED) {
            return 0;
        }
        long affordable = (long) Math.floor(gold * POTION_BUDGET) / price;
        return (int) Math.max(0, Math.min(POTIONS_WANTED - carried, affordable));
    }

    /**
     * What a bot does next in town: sell when the bag is filling up or buy when the potions run low (and it can pay),
     * rest at the inn after a few hunts, now and then stroll about town; otherwise go hunting. {@code roll} is a
     * random number in [0, 1).
     */
    public static Errand nextErrand(int potions, int freeSlots, long gold, long potionPrice, int huntsSinceRest,
                                    double roll) {
        if (freeSlots < FEW_FREE_SLOTS || (potions < 3 && gold >= potionPrice * 3)) {
            return Errand.SHOP;
        }
        if (huntsSinceRest >= HUNTS_BEFORE_REST && roll < 0.6) {
            return Errand.INN;
        }
        return roll >= 0.75 ? Errand.WANDER : Errand.HUNT;
    }

    /** How far the first light attack of a melee weapon reaches (its shortest damaging hit); 2.5 without one. */
    public static double meleeReach(WeaponProfile weapon) {
        if (weapon == null || weapon.combo().isEmpty()) {
            return 2.5;
        }
        ActionDefinition first = weapon.combo().get(0);
        double reach = Double.MAX_VALUE;
        for (HitFrame frame : first.frames()) {
            if (frame.damaging() && frame.projectile() == null) {
                reach = Math.min(reach, frame.shape().reach());
            }
        }
        return reach == Double.MAX_VALUE ? 2.5 : reach;
    }

    public static boolean isRanged(WeaponType type) {
        return type == WeaponType.BOW || type == WeaponType.STAFF;
    }

    /** How close a bot stands to hit: bows and staves from afar, melee weapons a little inside their reach. */
    public static double engageDistance(WeaponType type, double reach) {
        if (type != null && isRanged(type)) {
            return RANGED_DISTANCE;
        }
        return Math.max(1.5, Math.min(4.0, reach * 0.75));
    }
}
