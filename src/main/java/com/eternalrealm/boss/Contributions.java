package com.eternalrealm.boss;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Who did what in one boss fight: damage dealt to the boss, damage taken, healing, falls, the first strike and the
 * killing blow. Feeds the damage ranking shown during the fight and picks the MVP at the end with a Ragnarok-style
 * score ({@link #score}): damage counts most, holding the boss's attention (damage taken) and keeping the party up
 * (healing) count too, the first strike and the killing blow give a bonus and every fall costs a little.
 */
public final class Contributions {
    /** Score weights (a perfect solo fight: 60 + 20 + 20 + 4 + 8). */
    public static final double DAMAGE_POINTS = 60.0;
    public static final double TAKEN_POINTS = 20.0;
    public static final double HEALING_POINTS = 20.0;
    public static final double FIRST_STRIKE_POINTS = 4.0;
    public static final double LAST_HIT_POINTS = 8.0;
    public static final double FALL_PENALTY = 2.0;
    public static final double MAX_FALL_PENALTY = 6.0;

    /** One participant's totals (a snapshot). */
    public record Line(UUID id, String name, String job, double damage, double taken, double healing, int falls,
                       boolean firstStrike, boolean lastHit, double share, double score) {
    }

    private static final class Entry {
        final UUID id;
        String name;
        String job;
        double damage;
        double taken;
        double healing;
        int falls;

        Entry(UUID id, String name, String job) {
            this.id = id;
            this.name = name;
            this.job = job;
        }
    }

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private UUID firstStrike;
    private UUID lastHit;

    private Entry entry(UUID id, String name, String job) {
        Objects.requireNonNull(id, "id");
        Entry entry = entries.computeIfAbsent(id, key -> new Entry(key, name == null ? "?" : name,
                job == null ? "none" : job));
        if (name != null && !name.isEmpty()) {
            entry.name = name;
        }
        if (job != null && !job.isEmpty()) {
            entry.job = job;
        }
        return entry;
    }

    /** Damage dealt to the boss (the first one to land a hit gets the first strike). */
    public void damage(UUID id, String name, String job, double amount) {
        if (!(amount > 0.0)) {
            return;
        }
        entry(id, name, job).damage += amount;
        if (firstStrike == null) {
            firstStrike = id;
        }
    }

    public void taken(UUID id, String name, String job, double amount) {
        if (amount > 0.0) {
            entry(id, name, job).taken += amount;
        }
    }

    public void healing(UUID id, String name, String job, double amount) {
        if (amount > 0.0) {
            entry(id, name, job).healing += amount;
        }
    }

    public void fell(UUID id, String name, String job) {
        entry(id, name, job).falls++;
    }

    /** The killing blow. */
    public void lastHit(UUID id) {
        if (id != null && entries.containsKey(id)) {
            lastHit = id;
        }
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public double totalDamage() {
        double total = 0.0;
        for (Entry entry : entries.values()) {
            total += entry.damage;
        }
        return total;
    }

    /** Fraction of all damage dealt by {@code id} (0 when nobody hit yet). */
    public double share(UUID id) {
        Entry entry = entries.get(id);
        double total = totalDamage();
        return entry == null || total <= 0.0 ? 0.0 : entry.damage / total;
    }

    /** The MVP score of {@code id} (0 for someone who did nothing). */
    public double score(UUID id) {
        Entry entry = entries.get(id);
        if (entry == null) {
            return 0.0;
        }
        double damage = 0.0;
        double taken = 0.0;
        double healing = 0.0;
        for (Entry other : entries.values()) {
            damage += other.damage;
            taken += other.taken;
            healing += other.healing;
        }
        double score = (damage > 0.0 ? DAMAGE_POINTS * entry.damage / damage : 0.0)
                + (taken > 0.0 ? TAKEN_POINTS * entry.taken / taken : 0.0)
                + (healing > 0.0 ? HEALING_POINTS * entry.healing / healing : 0.0)
                + (id.equals(firstStrike) ? FIRST_STRIKE_POINTS : 0.0)
                + (id.equals(lastHit) ? LAST_HIT_POINTS : 0.0)
                - Math.min(MAX_FALL_PENALTY, entry.falls * FALL_PENALTY);
        return Math.max(0.0, score);
    }

    /** Everyone, most damage first (ties: earlier first). */
    public List<Line> byDamage() {
        List<Line> lines = lines();
        lines.sort(Comparator.comparingDouble(Line::damage).reversed());
        return lines;
    }

    /** Everyone, best MVP score first (ties: more damage first). */
    public List<Line> byScore() {
        List<Line> lines = lines();
        lines.sort(Comparator.comparingDouble(Line::score).reversed()
                .thenComparing(Comparator.comparingDouble(Line::damage).reversed()));
        return lines;
    }

    /** 1-based place of {@code id} in the damage ranking, 0 if absent. */
    public int rank(UUID id) {
        List<Line> lines = byDamage();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).id().equals(id)) {
                return i + 1;
            }
        }
        return 0;
    }

    /** The MVP: the best score among those who hurt the boss. */
    public Optional<Line> mvp() {
        return byScore().stream().filter(line -> line.damage() > 0.0).findFirst();
    }

    public Optional<Line> line(UUID id) {
        return lines().stream().filter(line -> line.id().equals(id)).findFirst();
    }

    private List<Line> lines() {
        double total = totalDamage();
        List<Line> lines = new ArrayList<>(entries.size());
        for (Entry entry : entries.values()) {
            lines.add(new Line(entry.id, entry.name, entry.job, entry.damage, entry.taken, entry.healing, entry.falls,
                    entry.id.equals(firstStrike), entry.id.equals(lastHit), total > 0.0 ? entry.damage / total : 0.0,
                    score(entry.id)));
        }
        return lines;
    }
}
