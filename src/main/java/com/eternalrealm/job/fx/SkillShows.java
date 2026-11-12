package com.eternalrealm.job.fx;

import com.eternalrealm.combat.ActionDefinition;
import com.eternalrealm.combat.HitFrame;
import com.eternalrealm.combat.HitShape;
import com.eternalrealm.combat.ProjectileSpec;
import com.eternalrealm.job.JobType;
import com.eternalrealm.job.Jobs;
import com.eternalrealm.job.SkillDefinition;
import com.eternalrealm.job.SkillEffect;
import com.eternalrealm.job.SkillKind;
import com.eternalrealm.job.SkillTheme;
import com.eternalrealm.job.fx.Cue.Kind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The shows of the job skills (owner, 2026-11-12: 「全部のアクティブスキルと必殺技の視覚的効果、エフェクトを作り直して。
 * 高クオリティで派手に」), composed from what each skill really does: every hit frame gets the light of its shape
 * (crescents for cuts, turns for spins, spear streaks and beams, shockwaves, quakes, crystals, pillars, lightning)
 * at the exact tick it hits, every effect its sign (healing light, blessings, shields, dashes, leaps, challenges,
 * mist, clocks...), every named visual its signature (novas, sky circles, rains, vortices, chains, breath...), all in
 * the theme's colours and the job's style of magic circle, varied by the skill's own seed. Ultimates add their own
 * grand finale ({@link Ultimates}). Built once per skill and kept.
 */
public final class SkillShows {
    private static final Map<String, SkillShow> CACHE = new ConcurrentHashMap<>();

    private SkillShows() {
    }

    /** The show of the job skill whose action is {@code actionId}. */
    public static Optional<SkillShow> forAction(String actionId) {
        if (actionId == null) {
            return Optional.empty();
        }
        SkillShow cached = CACHE.get(actionId);
        if (cached != null) {
            return Optional.of(cached);
        }
        return Jobs.skillOfAction(actionId).filter(SkillDefinition::isUsable).map(SkillShows::of);
    }

    public static SkillShow of(SkillDefinition skill) {
        return CACHE.computeIfAbsent(skill.action().id(), id -> build(skill));
    }

    /** The job's style of magic circle. */
    static int style(JobType job) {
        return switch (job) {
            case KNIGHT -> Painter.STYLE_KNIGHT;
            case ASSASSIN -> Painter.STYLE_ASSASSIN;
            case MAGE -> Painter.STYLE_MAGE;
            case RANGER -> Painter.STYLE_RANGER;
            case PRIEST -> Painter.STYLE_PRIEST;
            case SPELLBLADE -> Painter.STYLE_SPELLBLADE;
            default -> 0;
        };
    }

    static long seedOf(String id) {
        long h = 1125899906842597L;
        for (int i = 0; i < id.length(); i++) {
            h = 31 * h + id.charAt(i);
        }
        return Brush.mixSeed(h, 17);
    }

    static SkillShow build(SkillDefinition skill) {
        Gen g = new Gen(skill);
        g.windup();
        g.strikes();
        g.effects();
        g.visuals();
        if (skill.kind() == SkillKind.ULTIMATE) {
            Ultimates.add(g);
        }
        return g.show();
    }

    /** The composer of one skill's show. */
    static final class Gen {
        final SkillDefinition skill;
        final ActionDefinition action;
        final List<HitFrame> frames;
        final int[] times;
        final long seed;
        final boolean ultimate;
        final int style;
        final SkillTheme theme;
        final List<Cue> cues = new ArrayList<>();
        final List<SkillShow.Shake> shakes = new ArrayList<>();
        /** The index (in {@link #cues}) of the strike drawn for each hit frame. */
        final Map<Integer, Integer> strikeOf = new HashMap<>();
        int count;

        Gen(SkillDefinition skill) {
            this.skill = skill;
            this.action = skill.action();
            this.frames = action.frames();
            this.times = new int[frames.size()];
            for (int i = 0; i < times.length; i++) {
                times[i] = frames.get(i).offsetTicks();
            }
            this.seed = seedOf(skill.id());
            this.ultimate = skill.kind() == SkillKind.ULTIMATE;
            this.style = style(skill.job());
            this.theme = skill.theme();
        }

        Cue.Builder cue(Kind kind, double start, double peak, double end) {
            return Cue.of(kind, Math.max(0.0, start), Math.max(0.0, peak), end).seed(Brush.mixSeed(seed, ++count));
        }

        int put(Cue.Builder builder) {
            cues.add(builder.build());
            return cues.size() - 1;
        }

        void shake(double time, float intensity, int ticks, float kick) {
            shakes.add(new SkillShow.Shake(time, intensity, ticks, kick));
        }

        int first() {
            for (int i = 0; i < frames.size(); i++) {
                if (frames.get(i).damaging() || i == frames.size() - 1) {
                    return times[i];
                }
            }
            return action.durationTicks() / 2;
        }

        /** The time of effect frame {@code frame} ({@link SkillEffect#ON_START}: the start). */
        int at(int frame) {
            return frame < 0 || frame >= times.length ? 0 : times[frame];
        }

        double rnd(long i) {
            return Brush.unit(seed, i + 1000);
        }

        boolean hasVisual(String kind) {
            return skill.effects().stream().anyMatch(e -> e instanceof SkillEffect.Visual v && v.kind().equals(kind));
        }

        boolean has(Class<? extends SkillEffect> type) {
            return skill.effects().stream().anyMatch(type::isInstance);
        }

        Optional<SkillEffect.Zone> zone() {
            return skill.effects().stream().filter(e -> e instanceof SkillEffect.Zone).map(e -> (SkillEffect.Zone) e)
                    .findFirst();
        }

        Optional<ProjectileSpec> fromAbove() {
            return frames.stream().filter(HitFrame::isProjectile).map(HitFrame::projectile)
                    .filter(ProjectileSpec::fromAbove).findFirst();
        }

        /** Hit frame {@code frame}'s reach (0 for effect and projectile frames). */
        double reach(int frame) {
            if (frame < 0 || frame >= frames.size()) {
                return 0.0;
            }
            HitFrame f = frames.get(frame);
            return f.damaging() && !f.isProjectile() ? f.shape().reach() : 0.0;
        }

        double ahead(int frame) {
            if (frame < 0 || frame >= frames.size()) {
                return 0.0;
            }
            HitFrame f = frames.get(frame);
            return f.damaging() && !f.isProjectile() ? f.shape().offsetForward() : 0.0;
        }

        // ------------------------------------------------------------------ the wind-up

        void windup() {
            int first = first();
            if (first < 3 && !ultimate) {
                return;
            }
            boolean caster = skill.job() == JobType.MAGE || skill.job() == JobType.PRIEST
                    || frames.stream().anyMatch(HitFrame::isProjectile) && skill.job() != JobType.RANGER;
            if (ultimate) {
                put(cue(Kind.SIGIL, 0, Math.max(4, first * 0.6), first + 12).at(Cue.Anchor.ORIGIN)
                        .a(2.2 + rnd(1) * 0.8).n(5 + (int) (rnd(2) * 4)).variant(style));
                put(cue(Kind.CHARGE, 0, first, first + 2).a(1.4));
                put(cue(Kind.MOTES, 0, first * 0.7, first + 6).a(3.5).n(18).variant(1));
                put(cue(Kind.HELIX, 0, first, first + 8).a(1.1).b(2.6).n(3));
            } else if (caster || hasVisual("cast")) {
                put(cue(Kind.CAST, 0, first, first + 4).a(0.9 + 0.2 * rnd(3)).variant(style));
            } else if (first >= 6) {
                put(cue(Kind.CHARGE, 0, first, first + 1).a(0.8));
                if (theme == SkillTheme.EARTH || theme == SkillTheme.HOLY || first >= 12) {
                    put(cue(Kind.SIGIL, 0, first * 0.7, first + 6).at(Cue.Anchor.ORIGIN).a(1.3).variant(style)
                            .n(5 + (int) (rnd(4) * 3)));
                }
            }
        }

        // ------------------------------------------------------------------ hit frames

        void strikes() {
            int arcIndex = 0;
            int lineIndex = 0;
            int spinIndex = 0;
            for (int i = 0; i < frames.size(); i++) {
                HitFrame f = frames.get(i);
                int t = times[i];
                if (!f.damaging()) {
                    continue;
                }
                if (f.isProjectile()) {
                    projectile(i, f.projectile(), t);
                    continue;
                }
                HitShape shape = f.shape();
                boolean heavy = ultimate || f.multiplier() >= 2.0;
                switch (shape.kind()) {
                    case ARC -> {
                        strikeOf.put(i, put(arc(i, t, shape, arcIndex++, heavy, runLength(i, HitShape.Kind.ARC))));
                        if (heavy && f.multiplier() >= 2.4) {
                            put(cue(Kind.IMPACT, t, t, t + 6).frame(i).a(1.0).b(shape.reach() * 0.8));
                        }
                    }
                    case LINE -> {
                        boolean beam = shape.reach() >= 8.0 || shape.width() >= 1.6;
                        if (beam) {
                            strikeOf.put(i, put(cue(Kind.BEAM, t - 3, t, t + 9).frame(i).a(shape.reach())
                                    .b(Math.max(0.8, shape.width()))));
                        } else {
                            strikeOf.put(i, put(cue(Kind.THRUST, t - 2, t, t + 7).frame(i).a(shape.reach() + 0.6)
                                    .b(Math.max(0.6, shape.width())).variant(lineIndex % 2 == 0 ? 0 : 1)));
                        }
                        lineIndex++;
                    }
                    case CIRCLE -> {
                        if (shape.reach() < 1.0) {
                            continue;
                        }
                        circle(i, t, shape, f, spinIndex++, runLength(i, HitShape.Kind.CIRCLE), heavy);
                    }
                }
            }
        }

        /** How many frames of the same shape kind come in a row around frame {@code i}. */
        int runLength(int i, HitShape.Kind kind) {
            int n = 0;
            for (HitFrame f : frames) {
                if (f.damaging() && !f.isProjectile() && f.shape().kind() == kind && f.shape().reach() >= 1.0) {
                    n++;
                }
            }
            return n;
        }

        Cue.Builder arc(int i, int t, HitShape shape, int index, boolean heavy, int run) {
            double half = shape.halfAngleDegrees();
            double tilt;
            boolean reversed;
            if (run <= 1) {
                if (theme == SkillTheme.EARTH || (heavy && half <= 75.0 && rnd(5) < 0.5)) {
                    tilt = 78.0; // brought down overhead
                } else if (half >= 100.0) {
                    tilt = 10.0 + rnd(6) * 14.0;
                } else if (half <= 55.0) {
                    tilt = 56.0 + rnd(7) * 18.0;
                } else {
                    tilt = (rnd(8) < 0.5 ? 1.0 : -1.0) * (36.0 + rnd(8) * 24.0);
                }
                reversed = rnd(9) < 0.35;
            } else {
                double[] pattern = {24.0, -32.0, 55.0, -50.0, 76.0, 14.0, 40.0, -66.0};
                int shift = (int) (rnd(10) * pattern.length);
                tilt = pattern[(index + shift) % pattern.length];
                reversed = index % 2 == 1;
            }
            int sweep = Math.max(1, Math.min(4, t - (i > 0 ? times[i - 1] : 0)));
            int variant = (reversed ? 1 : 0) | (heavy ? 2 : 0);
            return cue(Kind.SLASH, t - sweep, t, t + (heavy ? 9 : 7)).frame(i).a(shape.reach()).b(half).c(tilt)
                    .d(heavy ? 0.7 : 0.0).variant(variant);
        }

        void circle(int i, int t, HitShape shape, HitFrame f, int index, int run, boolean heavy) {
            double r = shape.reach();
            double ahead = shape.offsetForward();
            if (ahead > 0.0) {
                aheadArea(i, t, r, ahead);
                return;
            }
            if (r >= 8.0) {
                put(cue(Kind.BOLTS, t - 3, t, t + 6).frame(i).a(r * 0.8).n(3 + (int) (rnd(11) * 3)).c(12.0));
                put(cue(Kind.SHOCKWAVE, t, t, t + 10).frame(i).a(r).n(1).b(0.5));
                return;
            }
            if (run >= 3 && r <= 5.5) {
                double[] tilts = {0.0, 14.0, -12.0, 24.0, -20.0, 8.0};
                double tilt = tilts[(index + (int) (rnd(12) * tilts.length)) % tilts.length];
                strikeOf.put(i, put(cue(Kind.SPIN, t - 3, t, t + 6).frame(i).a(r).b(0.75 + 0.15 * (index % 3))
                        .c(tilt).variant(index % 2)));
                if (index % 2 == 0) {
                    put(cue(Kind.SHOCKWAVE, t, t, t + 7).frame(i).a(r * 0.9).n(1));
                }
                return;
            }
            switch (theme) {
                case EARTH -> {
                    strikeOf.put(i, put(cue(Kind.QUAKE, t, t, t + 18).frame(i).a(r).n(8 + (int) (rnd(13) * 5))));
                    put(cue(Kind.SHOCKWAVE, t, t, t + 12).frame(i).a(r * 1.25).b(0.7).n(2));
                    shake(t, heavy ? 1.2F : 0.7F, 7, heavy ? 1.2F : 0.6F);
                }
                case FROST -> {
                    strikeOf.put(i, put(cue(Kind.SPIKES, t - 2, t, t + 16).frame(i).a(r).b(1.2 + r * 0.15)
                            .n(10 + (int) r * 2)));
                    put(cue(Kind.SHOCKWAVE, t, t, t + 10).frame(i).a(r * 1.1).n(2));
                }
                case FIRE -> {
                    strikeOf.put(i, put(cue(Kind.NOVA, t - 3, t, t + 12).frame(i).a(r).n(14)));
                    put(cue(Kind.SPIKES, t, t, t + 10).frame(i).a(r * 0.85).b(1.0).n(12));
                }
                case THUNDER -> {
                    strikeOf.put(i, put(cue(Kind.BOLTS, t - 2, t, t + 6).frame(i).a(r).n(3).c(10.0)));
                    put(cue(Kind.SHOCKWAVE, t, t, t + 9).frame(i).a(r).n(2));
                }
                case HOLY, LIFE -> {
                    strikeOf.put(i, put(cue(Kind.NOVA, t - 3, t, t + 12).frame(i).a(r).n(16)));
                    put(cue(Kind.PILLAR, t - 2, t, t + 8).frame(i).a(0.7).b(5.0));
                }
                case BLOOD -> {
                    strikeOf.put(i, put(cue(Kind.NOVA, t - 2, t, t + 10).frame(i).a(r).n(12)));
                    put(cue(Kind.SPIKES, t, t, t + 10).frame(i).a(r * 0.8).b(0.9).n(10));
                }
                case STEEL, WIND -> {
                    strikeOf.put(i, put(cue(Kind.SPIN, t - 3, t, t + 7).frame(i).a(r).b(0.9).c(rnd(14) * 16.0 - 8.0)
                            .variant(index % 2)));
                    put(cue(Kind.SHOCKWAVE, t, t, t + 9).frame(i).a(r).n(2));
                }
                default -> {
                    strikeOf.put(i, put(cue(Kind.NOVA, t - 3, t, t + 11).frame(i).a(r).n(12)));
                    put(cue(Kind.SHOCKWAVE, t, t, t + 8).frame(i).a(r * 0.8).n(1));
                }
            }
        }

        /** A strike landing ahead of the caster (pillars of flame, crystals, a rain of arrows). */
        void aheadArea(int i, int t, double r, double ahead) {
            switch (theme) {
                case FIRE -> {
                    strikeOf.put(i, put(cue(Kind.PILLAR, t - 3, t, t + 10).frame(i).a(r * 0.45).b(6.0).d(ahead)));
                    put(cue(Kind.SHOCKWAVE, t, t, t + 9).frame(i).a(r).n(2).d(ahead));
                }
                case FROST -> strikeOf.put(i, put(cue(Kind.SPIKES, t - 2, t, t + 14).frame(i).a(r).b(2.0).n(9)
                        .d(ahead)));
                case EARTH -> {
                    strikeOf.put(i, put(cue(Kind.QUAKE, t, t, t + 16).frame(i).a(r).n(8).b(ahead)));
                    shake(t, 0.8F, 6, 0.6F);
                }
                case HOLY -> {
                    strikeOf.put(i, put(cue(Kind.PILLAR, t - 3, t, t + 10).frame(i).a(r * 0.4).b(9.0).d(ahead)));
                    put(cue(Kind.NOVA, t, t, t + 10).frame(i).a(r).n(12).d(ahead));
                }
                case WIND -> {
                    strikeOf.put(i, put(cue(Kind.RAIN, t - 4, t, t + 6).frame(i).a(r).n(3).b(ahead).c(10.0)
                            .variant(1)));
                    put(cue(Kind.SHOCKWAVE, t, t, t + 6).frame(i).a(r * 0.8).n(1).d(ahead));
                }
                default -> {
                    strikeOf.put(i, put(cue(Kind.BURST, t - 2, t, t + 8).frame(i).a(r).b(ahead)));
                    put(cue(Kind.SHOCKWAVE, t, t, t + 8).frame(i).a(r).n(1).d(ahead));
                }
            }
        }

        void projectile(int i, ProjectileSpec p, int t) {
            boolean arrow = "arrow".equals(p.kind()) || "star_arrow".equals(p.kind());
            if (p.fromAbove()) {
                if (i > 0 && frames.get(i - 1).isProjectile() && frames.get(i - 1).projectile().fromAbove()) {
                    return; // one circle in the sky serves the whole volley
                }
                int last = t;
                for (int k = i; k < frames.size() && frames.get(k).isProjectile()
                        && frames.get(k).projectile().fromAbove(); k++) {
                    last = times[k];
                }
                double r = Math.max(2.0, p.explosionRadius()) + (p.count() - 1) * 0.6;
                put(cue(Kind.SKY_SIGIL, t - 8, t, last + 12).at(Cue.Anchor.AIM).a(r + 0.8).c(12.0 + rnd(15) * 2.0)
                        .n(6 + (int) (rnd(16) * 3)).variant(style));
                put(cue(Kind.SIGIL, t, t + 4, last + 14).at(Cue.Anchor.AIM).a(r).n(5).variant(style));
                return;
            }
            // the launch tells the shots apart: more rings for a volley, a bigger flash for a heavier spell
            int rings = 1 + Math.min(4, p.count());
            if (arrow) {
                strikeOf.put(i, put(cue(Kind.THRUST, t - 1, t, t + 5).frame(i).a(2.6 + p.speed() * 0.4).b(0.35)
                        .variant(1)));
                put(cue(Kind.ROAR, t, t, t + 6).frame(i).a(2.4 + 0.2 * p.pierce()).n(rings).variant(1));
            } else {
                strikeOf.put(i, put(cue(Kind.FLASH, t - 2, t, t + 6).frame(i).a(1.3 + p.explosionRadius() * 0.35
                        + (p.count() - 1) * 0.15).b(0.7)));
                put(cue(Kind.ROAR, t, t, t + 7).frame(i).a(3.0 + 0.2 * p.pierce()).n(rings).variant(1));
            }
        }

        // ------------------------------------------------------------------ effects

        void effects() {
            boolean domed = false;
            for (SkillEffect e : skill.effects()) {
                int f = e.frame();
                int t = at(f);
                if (e instanceof SkillEffect.Heal heal) {
                    put(cue(Kind.HEAL, t, t + 4, t + 24).a(Math.max(2.0, heal.radius())));
                } else if (e instanceof SkillEffect.Buff buff) {
                    switch (buff.kind()) {
                        case FLAME_WEAPON, FROST_WEAPON, VENOM_WEAPON, THUNDER_WEAPON ->
                                put(cue(Kind.WEAPON, t, t + 3, t + 30).palette(weaponTheme(buff.kind())));
                        case INVISIBILITY -> put(cue(Kind.FOG, t, t + 3, t + 22).a(2.2).n(8));
                        default -> put(cue(Kind.AURA, t, t + 3, t + 24).variant(buffVariant(buff.kind()))
                                .a(buff.target() == SkillEffect.BuffTarget.ALLIES ? buff.radius() : 0.0));
                    }
                } else if (e instanceof SkillEffect.Shield shield) {
                    if (!domed) {
                        double r = shield.radius() > 0.0 ? Math.min(4.5, 2.0 + shield.radius() * 0.18) : 1.35;
                        put(cue(Kind.DOME, t, t + 4, t + 28).a(r));
                        domed = true;
                    }
                } else if (e instanceof SkillEffect.Dash dash) {
                    put(cue(Kind.STREAK, t, t + 3, t + 12).a(dash.distance()));
                } else if (e instanceof SkillEffect.Leap leap) {
                    put(cue(Kind.LEAP, t, t + 5, t + 14).a(leap.height() + 1.0));
                } else if (e instanceof SkillEffect.Taunt taunt) {
                    put(cue(Kind.TAUNT, t, t + 2, t + 16).a(Math.min(10.0, taunt.radius())));
                } else if (e instanceof SkillEffect.DropAggro) {
                    put(cue(Kind.FOG, t, t + 3, t + 20).a(2.6).n(10));
                } else if (e instanceof SkillEffect.Afflict afflict && f >= 0) {
                    double r = reach(f);
                    put(cue(Kind.AFFLICT, t, t, t + 12).frame(f).variant(afflict.kind().ordinal())
                            .a(Math.max(0.8, Math.min(2.5, r * 0.4))).b(r <= 0.0 ? 2.5 : Math.min(r * 0.7, 4.0)));
                } else if (e instanceof SkillEffect.Cleanse cleanse) {
                    put(cue(Kind.CLEANSE, t, t, t + 14).a(Math.min(8.0, Math.max(2.0, cleanse.radius()))));
                } else if (e instanceof SkillEffect.Siphon) {
                    put(cue(Kind.SIPHON, t, t + 2, t + 14).frame(Math.max(0, f)).a(2.6));
                } else if (e instanceof SkillEffect.Zone zone) {
                    zoneMark(zone, t);
                } else if (e instanceof SkillEffect.Revive) {
                    put(cue(Kind.PILLAR, t - 2, t + 2, t + 22).a(0.9).b(9.0));
                    put(cue(Kind.WINGS, t, t + 6, t + 22).a(1.8));
                } else if (e instanceof SkillEffect.RestoreStamina) {
                    put(cue(Kind.MOTES, t, t + 3, t + 16).a(1.5).n(12));
                } else if (e instanceof SkillEffect.CooldownCut) {
                    put(cue(Kind.CLOCK, t - 2, t + 4, t + 22).a(1.0).variant(skill.id().contains("rewind") ? 1 : 0));
                } else if (e instanceof SkillEffect.Execute) {
                    int ft = at(f);
                    put(cue(Kind.GLYPH, ft - 2, ft, ft + 10).frame(Math.max(0, f)).variant(9).a(0.8)
                            .b(Math.max(1.6, reach(f) * 0.8)).c(-0.55).palette(SkillTheme.BLOOD));
                }
            }
        }

        void zoneMark(SkillEffect.Zone zone, int t) {
            Cue.Builder sigil = cue(Kind.SIGIL, t - 3, t + 3, t + 16).a(Math.min(6.0, zone.radius())).variant(style)
                    .n(5 + (int) (rnd(20) * 4));
            switch (zone.placement()) {
                case CASTER -> sigil.at(Cue.Anchor.ORIGIN);
                case AHEAD -> sigil.at(Cue.Anchor.ORIGIN).b(zone.distance());
                case LOOK -> sigil.at(Cue.Anchor.AIM);
            }
            put(sigil);
        }

        static SkillTheme weaponTheme(SkillEffect.BuffKind kind) {
            return switch (kind) {
                case FLAME_WEAPON -> SkillTheme.FIRE;
                case FROST_WEAPON -> SkillTheme.FROST;
                case VENOM_WEAPON -> SkillTheme.VENOM;
                default -> SkillTheme.THUNDER;
            };
        }

        static int buffVariant(SkillEffect.BuffKind kind) {
            return switch (kind) {
                case ATTACK_UP -> 1;
                case DEFENSE_UP -> 2;
                case SPEED, EVASION -> 3;
                case REGENERATION -> 4;
                case CRIT_UP -> 10;
                case LIFESTEAL -> 11;
                case THORNS -> 13;
                default -> 0;
            };
        }

        // ------------------------------------------------------------------ named visuals

        void visuals() {
            for (SkillEffect e : skill.effects()) {
                if (!(e instanceof SkillEffect.Visual v)) {
                    continue;
                }
                int f = v.frame();
                int t = at(f);
                visual(v.kind(), f, t);
            }
        }

        /** Marks the strike of frame {@code f} as emphasized (brighter, wider, sparkling); false if it has none. */
        boolean emphasize(int f) {
            Integer index = strikeOf.get(f);
            if (index == null) {
                return false;
            }
            Cue c = cues.get(index);
            cues.set(index, new Cue(c.kind(), c.start(), c.peak(), c.end(), c.anchor(), c.frame(), c.a(), c.b(),
                    c.c(), c.d(), c.n(), c.variant() | 4, c.palette(), c.seed()));
            return true;
        }

        /** Draws every layer anchored on hit frame {@code f} in {@code theme}'s colours (an elemental strike). */
        void paletteFrame(int f, SkillTheme theme) {
            for (int k = 0; k < cues.size(); k++) {
                Cue c = cues.get(k);
                if (c.anchor() == Cue.Anchor.FRAME && c.frame() == f) {
                    cues.set(k, new Cue(c.kind(), c.start(), c.peak(), c.end(), c.anchor(), c.frame(), c.a(), c.b(),
                            c.c(), c.d(), c.n(), c.variant(), theme, c.seed()));
                }
            }
        }

        Cue.Builder near(Kind kind, int f, double start, double peak, double end) {
            Cue.Builder b = cue(kind, start, peak, end);
            return f >= 0 ? b.frame(f) : b;
        }

        void visual(String kind, int f, int t) {
            double r = reach(f);
            double ahead = ahead(f);
            Optional<SkillEffect.Zone> zone = zone();
            boolean aimed = zone.map(z -> z.placement() == SkillEffect.Placement.LOOK).orElse(false)
                    || fromAbove().isPresent();
            switch (kind) {
                case "cast" -> {
                    if (f >= 0) {
                        put(cue(Kind.CAST, t - 4, t, t + 5).a(0.9).variant(style));
                    }
                }
                case "nova" -> {
                    if (!(strikeOf.containsKey(f) && cues.get(strikeOf.get(f)).kind() == Kind.NOVA && emphasize(f))) {
                        put(near(Kind.NOVA, f, t - 4, t, t + 13).a(r > 1.0 ? r : 5.0).n(16).d(ahead));
                    }
                }
                case "pillar" -> {
                    Cue.Builder b = near(Kind.PILLAR, f, t - 6, t, t + 16).a(ahead > 0.0 ? 0.55 : 0.85).b(8.0)
                            .d(ahead);
                    if (aimed && f >= 0 && ahead <= 0.0) {
                        b = cue(Kind.PILLAR, t - 6, t, t + 16).at(Cue.Anchor.AIM).a(0.7).b(9.0);
                    }
                    put(b);
                }
                case "aura" -> put(cue(Kind.HELIX, t, t + 6, t + 24).a(0.95).b(2.5).n(2 + (int) (rnd(21) * 2)));
                case "shield" -> {
                    if (!has(SkillEffect.Shield.class)) {
                        put(cue(Kind.DOME, t, t + 4, t + 26).a(1.35));
                    }
                    put(cue(Kind.GLYPH, t, t + 3, t + 18).variant(2).a(0.6).c(0.7));
                }
                case "smite" -> {
                    Cue.Builder col = aimed ? cue(Kind.PILLAR, t - 2, t, t + 10).at(Cue.Anchor.AIM)
                            : near(Kind.PILLAR, f, t - 2, t, t + 10).d(Math.max(ahead, 3.0));
                    put(col.a(0.65).b(14.0));
                    Cue.Builder ring = aimed ? cue(Kind.SHOCKWAVE, t, t, t + 10).at(Cue.Anchor.AIM)
                            : near(Kind.SHOCKWAVE, f, t, t, t + 10).d(Math.max(ahead, 3.0));
                    put(ring.a(3.0).n(2).b(0.6));
                }
                case "quake", "slam_small" -> {
                    boolean small = kind.equals("slam_small");
                    put(near(Kind.QUAKE, f, t, t, t + 16).a(small ? 2.4 : Math.max(3.5, r)).n(small ? 6 : 10)
                            .b(ahead > 0.0 ? ahead : (small ? 1.6 : 0.0)));
                    put(near(Kind.SHOCKWAVE, f, t, t, t + 12).a(small ? 2.8 : Math.max(4.0, r * 1.2)).b(0.7).n(2)
                            .d(ahead));
                    shake(t, small ? 0.6F : 1.0F, small ? 5 : 8, small ? 0.4F : 0.9F);
                }
                case "storm", "lightning" -> {
                    Cue.Builder b = aimed && f >= 0 ? cue(Kind.BOLTS, t - 4, t, t + 6).at(Cue.Anchor.AIM)
                            : near(Kind.BOLTS, f, t - 4, t, t + 6).b(ahead);
                    put(b.a(r > 1.0 ? Math.min(r, 9.0) * 0.8 : 3.5).n(kind.equals("lightning") ? 5 : 3).c(11.0));
                }
                case "vortex" -> {
                    Cue.Builder b = aimed ? cue(Kind.VORTEX, t, t + 5, t + 40).at(Cue.Anchor.AIM)
                            : near(Kind.VORTEX, f, t, t + 4, t + 18).d(Math.max(ahead, 1.5));
                    put(b.a(zone.map(SkillEffect.Zone::radius).orElse(3.0)).b(3.0));
                }
                case "mist" -> put(cue(Kind.FOG, t, t + 4, t + 30).a(3.5).n(12));
                case "smoke" -> {
                    put(cue(Kind.FOG, t, t + 2, t + 34).a(4.0).n(16));
                    put(cue(Kind.BURST, t, t, t + 8).a(2.5).n(12));
                }
                case "blink", "shadow" -> {
                    if (!has(SkillEffect.Dash.class)) {
                        put(cue(Kind.STREAK, t, t + 2, t + 10).a(3.0));
                    }
                    put(cue(Kind.BURST, t, t, t + 7).a(1.8).n(10));
                }
                case "burst" -> put(near(Kind.BURST, f, t - 2, t, t + 9).a(r > 1.0 ? Math.min(r, 6.0) : 3.2)
                        .b(ahead));
                case "ray" -> {
                    if (!(f >= 0 && strikeOf.containsKey(f) && cues.get(strikeOf.get(f)).kind() == Kind.BEAM)) {
                        put(near(Kind.BEAM, f, t - 3, t, t + 10).a(Math.max(10.0, r)).b(0.9));
                    } else {
                        emphasize(f);
                    }
                }
                case "rain" -> {
                    if (cues.stream().anyMatch(c -> c.kind() == Kind.RAIN)) {
                        return; // one rain is enough
                    }
                    double radius = zone.map(SkillEffect.Zone::radius).orElse(4.0);
                    int length = zone.map(z -> z.delayTicks() + z.pulses() * z.intervalTicks()).orElse(28);
                    boolean atCaster = zone.map(z -> z.placement() == SkillEffect.Placement.CASTER).orElse(false);
                    Cue.Builder rain = cue(Kind.RAIN, t, t + 2, t + length + 6).a(radius).n(rainDensity(radius))
                            .c(11.0).variant(rainShape());
                    Cue.Builder sky = cue(Kind.SKY_SIGIL, t - 4, t + 2, t + length + 4).a(radius + 0.6).c(11.5)
                            .n(6).variant(style);
                    if (atCaster) {
                        rain.at(Cue.Anchor.CASTER);
                        sky.at(Cue.Anchor.CASTER);
                    } else if (aimed || zone.isPresent()) {
                        rain.at(Cue.Anchor.AIM);
                        sky.at(Cue.Anchor.AIM);
                    } else {
                        rain.at(Cue.Anchor.ORIGIN).b(8.0);
                        sky.at(Cue.Anchor.ORIGIN).d(8.0);
                    }
                    put(rain);
                    if (cues.stream().noneMatch(c -> c.kind() == Kind.SKY_SIGIL)) {
                        put(sky);
                    }
                }
                case "sigil" -> {
                    if (zone.isEmpty()) {
                        put(cue(Kind.SIGIL, t - 4, t, t + 18).at(Cue.Anchor.ORIGIN).a(f < 0 ? 1.4 : 2.4)
                                .n(5 + (int) (rnd(22) * 4)).variant(style));
                    }
                }
                case "impact" -> put(near(Kind.IMPACT, f, t, t, t + 7).a(1.0).b(Math.max(1.2, r * 0.8)));
                case "roar", "war_cry" -> {
                    put(cue(Kind.ROAR, t, t + 2, t + 14).a(8.0).n(4));
                    if (kind.equals("war_cry")) {
                        put(cue(Kind.AURA, t, t + 3, t + 24).variant(1));
                        put(cue(Kind.GLYPH, t, t + 3, t + 20).variant(1).a(0.7));
                    }
                }
                case "slash", "spin", "thrust", "arcane_slash" -> {
                    if (!emphasize(f)) {
                        // a cut thrown as a wave (crescent moon, mana wave, radiant wave): a big crescent at the hand
                        put(near(Kind.SLASH, f, t - 3, t, t + 8).a(3.4).b(75.0).c(kind.equals("spin") ? 0.0 : 12.0)
                                .variant(6));
                    }
                    if (kind.equals("arcane_slash")) {
                        put(near(Kind.RIFT, f, t, t + 2, t + 10).a(2.6).c(35.0).palette(SkillTheme.ARCANE));
                    }
                }
                case "breath" -> put(near(Kind.BREATH, f, t - 1, t + 1, t + 10).a(Math.max(4.0, r)).b(28.0).n(9));
                case "chains" -> put(near(Kind.CHAINS, f, t - 2, t + 2, t + 16).a(Math.max(4.0, Math.min(r, 8.0)))
                        .n(4));
                case "heal", "blessing" -> {
                    put(cue(Kind.HALO, t - 2, t + 3, t + 22).a(0.42));
                    put(cue(Kind.MOTES, t, t + 3, t + 22).a(2.0).n(14));
                    if (kind.equals("blessing")) {
                        put(cue(Kind.WINGS, t, t + 5, t + 22).a(1.5));
                    }
                }
                case "iron_wall" -> {
                    put(cue(Kind.WALLS, t, t + 4, t + 26).a(1.8).b(2.2).n(6));
                    put(cue(Kind.GLYPH, t, t + 3, t + 20).variant(2).a(0.7));
                }
                case "flame_blade" -> put(cue(Kind.HELIX, t, t + 4, t + 20).a(0.7).b(2.2).n(2)
                        .palette(SkillTheme.FIRE));
                case "rift" -> put(near(Kind.RIFT, f, t - 2, t + 2, t + 14).a(5.0).c(70.0));
                case "judgment", "judgment_charge", "meteor_cast", "arrow_rain", "blade_storm", "sanctuary" -> {
                    // ultimates draw these themselves ({@link Ultimates})
                }
                default -> put(near(Kind.BURST, f, t - 2, t, t + 8).a(2.0));
            }
        }

        int rainShape() {
            return switch (theme) {
                case FROST -> 2;
                case FIRE -> 3;
                case BLOOD -> 4;
                case HOLY -> 5;
                case LIFE -> 6;
                case ARCANE -> 7;
                case WIND, STEEL -> 1;
                default -> 0;
            };
        }

        static int rainDensity(double radius) {
            return Math.max(2, (int) Math.round(radius * 0.8));
        }

        SkillShow show() {
            int duration = action.durationTicks();
            double last = duration;
            for (Cue c : cues) {
                last = Math.max(last, c.end());
            }
            int tail = (int) Math.ceil(last - duration) + 1;
            return new SkillShow(skill.id(), skill.job(), theme, ultimate, duration, Math.max(4, tail), times, cues,
                    shakes);
        }
    }
}
