package com.eternalrealm.npc;

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
 * The travelling adventurers (docs/modules/travellers.md): four original travellers who turn up in a single-player
 * world, hunting at the habitats, shopping in the capital and resting at the inn. Pure data and rules: who they are,
 * how they fight, what they say, and where each goes next.
 */
public final class Travellers {
    /** Translation key prefix: names, lines and shouts. */
    public static final String LANG = "traveller.eternalrealm.";
    /** Lines a traveller has for each place (talking to them). */
    public static final int LINES_PER_PLACE = 3;
    /** Lines of the first meeting. */
    public static final int MEET_LINES = 2;
    /** Most of the time a traveller hunts at a habitat within this distance of the player (blocks). */
    public static final double NEAR_PLAYER = 240.0;
    /** Share of field trips that go near the player. */
    static final double NEAR_SHARE = 0.6;

    /** How a traveller fights. */
    public enum Style {
        /** Bolts of light from a staff, keeps a middle distance. */
        CASTER,
        /** Arrows and a hawk from afar. */
        ARCHER,
        /** Halberd sweeps up close. */
        KNIGHT,
        /** Songs of the sea at a middle distance; heals. */
        SINGER
    }

    /** The four of them. */
    public enum Traveller {
        BOLD("bold", Style.CASTER, 5.5, 34, 0.07),
        MIRKA("mirka", Style.ARCHER, 8.5, 28, 0.06),
        GRAUS("graus", Style.KNIGHT, 2.6, 30, 0.09),
        SELE("sele", Style.SINGER, 6.5, 36, 0.06);

        private final String id;
        private final Style style;
        private final double range;
        private final int basicTicks;
        private final double basicDamage;

        Traveller(String id, Style style, double range, int basicTicks, double basicDamage) {
            this.id = id;
            this.style = style;
            this.range = range;
            this.basicTicks = basicTicks;
            this.basicDamage = basicDamage;
        }

        public String id() {
            return id;
        }

        public Style style() {
            return style;
        }

        /** The NPC body (tools/art_entities/npc_cast_travellers.py); its portrait has the same id. */
        public String appearance() {
            return "traveller_" + id;
        }

        /** The name (and dialogue speaker: the portrait is the key's last part, the appearance). */
        public String nameKey() {
            return LANG + appearance();
        }

        /** How far from its enemy it stands to fight (blocks). */
        public double range() {
            return range;
        }

        /** Ticks between its ordinary attacks. */
        public int basicTicks() {
            return basicTicks;
        }

        /** Share of the enemy's maximum health one ordinary attack takes. */
        public double basicDamage() {
            return basicDamage;
        }

        public List<Skill> skills() {
            return Arrays.stream(Skill.values()).filter(skill -> skill.owner() == this).toList();
        }

        public static Optional<Traveller> byId(String id) {
            return Arrays.stream(values()).filter(w -> w.id.equals(id)).findFirst();
        }
    }

    /** The signature skills, two each. */
    public enum Skill {
        /** Stars light up around the enemy, lines of light join them, and they burst. */
        STAR_CHART(Traveller.BOLD, "star_chart", 240, 0.24, 4.5),
        /** A needle of light turns once around him like a compass and cuts what it passes. */
        COMPASS_RAY(Traveller.BOLD, "compass_ray", 200, 0.18, 6.5),
        /** The hawk climbs out of sight and stoops on the enemy in a golden streak. */
        FALCON_STOOP(Traveller.MIRKA, "falcon_stoop", 220, 0.32, 1.8),
        /** A fan of feathered arrows. */
        FEATHER_GALE(Traveller.MIRKA, "feather_gale", 180, 0.17, 9.0),
        /** He strikes the bell on his back: rings of sound roll out and throw enemies back. */
        BELL_TOLL(Traveller.GRAUS, "bell_toll", 200, 0.15, 6.5),
        /** A great bell of light falls on the enemy. */
        GREAT_BELL(Traveller.GRAUS, "great_bell", 300, 0.34, 3.5),
        /** Waves roll out from her song and wash enemies back. */
        SURF_SONG(Traveller.SELE, "surf_song", 200, 0.17, 9.0),
        /** Bubbles rise around the hurt: players near her heal. The share is of their health. */
        BUBBLE_WARD(Traveller.SELE, "bubble_ward", 400, 0.25, 9.0);

        private final Traveller owner;
        private final String id;
        private final int cooldownTicks;
        private final double power;
        private final double radius;

        Skill(Traveller owner, String id, int cooldownTicks, double power, double radius) {
            this.owner = owner;
            this.id = id;
            this.cooldownTicks = cooldownTicks;
            this.power = power;
            this.radius = radius;
        }

        public Traveller owner() {
            return owner;
        }

        public String id() {
            return id;
        }

        public int cooldownTicks() {
            return cooldownTicks;
        }

        /** Share of each enemy's maximum health it takes (of the players' health for a heal). */
        public double power() {
            return power;
        }

        /** Reach of the skill (blocks). */
        public double radius() {
            return radius;
        }

        /** Whether it helps players instead of hitting enemies. */
        public boolean heals() {
            return this == BUBBLE_WARD;
        }

        /** What the traveller calls out when using it. */
        public String shoutKey() {
            return LANG + owner.id() + ".skill." + id;
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

    // ------------------------------------------------------------------ what they say

    /** The lines of a talk at {@code place} (one of {@link #LINES_PER_PLACE}, picked by {@code index}). */
    public static String talkKey(Traveller traveller, Place place, int index) {
        return LANG + traveller.id() + "." + place.id() + "." + (Math.floorMod(index, LINES_PER_PLACE) + 1);
    }

    /** The lines of the first meeting, in order. */
    public static List<String> meetKeys(Traveller traveller) {
        List<String> keys = new ArrayList<>();
        for (int i = 1; i <= MEET_LINES; i++) {
            keys.add(LANG + traveller.id() + ".meet." + i);
        }
        return keys;
    }

    // ------------------------------------------------------------------ who a player has met

    /** The travellers a player has met, from the stored list ("bold,sele"); unknown ids are dropped. */
    public static Set<Traveller> parseMet(String stored) {
        Set<Traveller> met = new LinkedHashSet<>();
        if (stored == null || stored.isBlank()) {
            return met;
        }
        for (String id : stored.split(",")) {
            Traveller.byId(id.trim()).ifPresent(met::add);
        }
        return met;
    }

    public static String formatMet(Set<Traveller> met) {
        return met.stream().map(Traveller::id).collect(Collectors.joining(","));
    }
}
