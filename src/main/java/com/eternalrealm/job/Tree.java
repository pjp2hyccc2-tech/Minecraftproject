package com.eternalrealm.job;

import com.eternalrealm.combat.ActionDefinition;
import com.eternalrealm.combat.ActionKind;
import com.eternalrealm.combat.DamageType;
import com.eternalrealm.combat.HitFrame;
import com.eternalrealm.combat.WeaponType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Builder of one job's skill tree, used by the catalogues: {@code tree.active("fireball", 0, 0, 1).cd(5)...}. Ids
 * are {@code <job>_<name>}; prerequisites name skills of the same job without the prefix. The required level of a
 * node defaults to the level of its tier ({@link #TIER_LEVELS}).
 */
final class Tree {
    /** Character level needed for each tier (index tier − 1). The innate skills sit on tiers 1, 2, 3 and 5. */
    static final int[] TIER_LEVELS = {1, 8, 18, 24, 30, 40};

    private final JobType job;
    private final DamageType defaultType;
    private final List<Node> nodes = new ArrayList<>();

    Tree(JobType job, DamageType defaultType) {
        this.job = job;
        this.defaultType = defaultType;
    }

    Node active(String name, int branch, int column, int tier) {
        return add(new Node(name, SkillKind.ACTIVE, branch, column, tier));
    }

    Node ultimate(String name, int branch, int column, int tier) {
        return add(new Node(name, SkillKind.ULTIMATE, branch, column, tier));
    }

    Node passive(String name, int branch, int column, int tier) {
        return add(new Node(name, SkillKind.PASSIVE, branch, column, tier));
    }

    private Node add(Node node) {
        nodes.add(node);
        return node;
    }

    /** Every node in tree order (branch, tier, column). */
    List<SkillDefinition> build() {
        List<SkillDefinition> skills = new ArrayList<>();
        for (Node node : nodes) {
            skills.add(node.build());
        }
        skills.sort(Comparator.comparingInt(SkillDefinition::branch).thenComparingInt(SkillDefinition::tier)
                .thenComparingInt(SkillDefinition::column));
        return skills;
    }

    String id(String name) {
        return job.id() + "_" + name;
    }

    /** One node under construction. */
    final class Node {
        private final String id;
        private final SkillKind kind;
        private final int branch;
        private final int column;
        private final int tier;
        private int requiredLevel;
        private int innateSlot;
        private int maxLevel = Jobs.MAX_SKILL_LEVEL;
        private SkillTheme theme = SkillTheme.STEEL;
        private DamageType type = defaultType;
        private final Set<WeaponType> weapons = EnumSet.noneOf(WeaponType.class);
        private ActionDefinition action;
        private int cooldownTicks;
        private final List<SkillEffect> effects = new ArrayList<>();
        private final List<Prerequisite> prerequisites = new ArrayList<>();
        private final List<PassiveEffect> passives = new ArrayList<>();

        private Node(String name, SkillKind kind, int branch, int column, int tier) {
            this.id = id(name);
            this.kind = kind;
            this.branch = branch;
            this.column = column;
            this.tier = tier;
            this.requiredLevel = TIER_LEVELS[tier - 1];
        }

        /** An innate skill: learned for free at {@code level}, placed in {@code slot} by default. */
        Node innate(int slot, int level) {
            innateSlot = slot;
            requiredLevel = level;
            return this;
        }

        Node level(int level) {
            requiredLevel = level;
            return this;
        }

        Node req(String name, int level) {
            prerequisites.add(new Prerequisite(id(name), level));
            return this;
        }

        Node max(int level) {
            maxLevel = level;
            return this;
        }

        Node theme(SkillTheme value) {
            theme = value;
            return this;
        }

        Node type(DamageType value) {
            type = value;
            return this;
        }

        Node weapons(WeaponType... values) {
            weapons.addAll(List.of(values));
            return this;
        }

        /** Cooldown at level 1 in seconds. */
        Node cd(double seconds) {
            cooldownTicks = (int) Math.round(seconds * 20.0);
            return this;
        }

        /** The action: total length and cancel point in ticks, stamina cost and the hit frames. */
        Node act(int duration, int cancel, double stamina, HitFrame... frames) {
            action = new ActionDefinition(id, ActionKind.SKILL, duration, cancel, List.of(frames), stamina, 0, 0,
                    false);
            return this;
        }

        /** The action: one frame (usually a pause) followed by a series of frames. */
        Node act(int duration, int cancel, double stamina, HitFrame first, HitFrame[] rest) {
            return act(duration, cancel, stamina, Dsl.seq(first, rest));
        }

        Node fx(SkillEffect... values) {
            effects.addAll(List.of(values));
            return this;
        }

        Node bonus(PassiveEffect... values) {
            passives.addAll(List.of(values));
            return this;
        }

        private SkillDefinition build() {
            return new SkillDefinition(id, job, kind, theme, branch, column, tier, requiredLevel, innateSlot, maxLevel,
                    action, kind == SkillKind.PASSIVE ? 0 : cooldownTicks, type, weapons, effects, prerequisites,
                    passives);
        }
    }
}
