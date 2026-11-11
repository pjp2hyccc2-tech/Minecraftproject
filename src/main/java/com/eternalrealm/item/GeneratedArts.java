package com.eternalrealm.item;

import com.eternalrealm.combat.WeaponType;
import com.eternalrealm.job.SkillEffect.AfflictionKind;
import com.eternalrealm.job.SkillTheme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The weapon arts (the Z key) of every named field weapon, every relic weapon (金枠) and every tier weapon, built
 * from {@link ArtTable} by {@link ArtForge}. Built on first use (after the catalogue of designs).
 *
 * <p>The damage budget: one use deals {@code typeBurst × factor} and its burst per second of cooldown is
 * {@code typePerSecond × factor} ({@link ArtBudget}): tier arts 1.08..1.26 × the type skill's burst at its rate (by
 * material), named arts 1.40..1.80 × at 1.05..1.15 × the rate (by level; lords' and calamities' +0.05), the boss arts
 * above them; the relic arts 2.05..2.30 × at 1.15..1.25 × the rate, at least a boss art's strength. The held piece's
 * rarity multiplies the damage and strength of every art when it is used ({@link ItemRarity#power()}).</p>
 */
public final class GeneratedArts {
    /** Burst of the weakest relic art, × the type skill (the strongest: +0.25). */
    public static final double RELIC_BURST = 2.05;

    private record Catalog(Map<String, WeaponArt> byId, Map<String, WeaponArt> byDesign,
                           Map<ItemTier, Map<WeaponType, WeaponArt>> byTier, Map<String, ArtLook> looks,
                           Map<String, ArtLook> zoneLooks, List<WeaponArt> all, java.util.Set<String> relics) {
    }

    private static final class Holder {
        static final Catalog CATALOG = build();
    }

    private GeneratedArts() {
    }

    public static Optional<WeaponArt> byId(String id) {
        return Optional.ofNullable(id == null ? null : Holder.CATALOG.byId().get(id));
    }

    /** The art of a named field weapon (boss weapons bring their own: {@link WeaponArts}). */
    public static Optional<WeaponArt> forDesign(String designId) {
        return Optional.ofNullable(designId == null ? null : Holder.CATALOG.byDesign().get(designId));
    }

    /** The art of a tier weapon (also the six starter weapons: iron). */
    public static WeaponArt forTier(ItemTier tier, WeaponType type) {
        return Holder.CATALOG.byTier().get(tier).get(type);
    }

    public static Optional<ArtLook> look(String artId) {
        return Optional.ofNullable(artId == null ? null : Holder.CATALOG.looks().get(artId));
    }

    /** The look of a ground area ({@code fx_<family>}), if one of these arts made it. */
    public static Optional<ArtLook> zoneLook(String look) {
        return Optional.ofNullable(look == null ? null : Holder.CATALOG.zoneLooks().get(look));
    }

    public static List<WeaponArt> all() {
        return Holder.CATALOG.all();
    }

    /** Whether the art belongs to a relic weapon (金枠). */
    public static boolean isRelicArt(String artId) {
        return artId != null && Holder.CATALOG.relics().contains(artId);
    }

    /** Whether the art belongs to a tier weapon (else to a named or relic weapon). */
    public static boolean isTierArt(String artId) {
        for (Map<WeaponType, WeaponArt> arts : Holder.CATALOG.byTier().values()) {
            for (WeaponArt art : arts.values()) {
                if (art.id().equals(artId)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Catalog build() {
        Map<String, WeaponArt> byId = new HashMap<>();
        Map<String, WeaponArt> byDesign = new HashMap<>();
        Map<ItemTier, Map<WeaponType, WeaponArt>> byTier = new EnumMap<>(ItemTier.class);
        Map<String, ArtLook> looks = new HashMap<>();
        Map<String, ArtLook> zoneLooks = new HashMap<>();
        List<WeaponArt> all = new ArrayList<>();
        java.util.Set<String> relics = new java.util.HashSet<>();
        for (ArtTable.Row row : ArtTable.ROWS) {
            ArtLook look = new ArtLook(row.family(), ArtLook.Shape.valueOf(row.primary()),
                    ArtLook.Shape.valueOf(row.accent()), row.particle(), row.accentParticle(), row.rgb(),
                    row.accentRgb(), ArtLook.Windup.valueOf(row.windup()));
            ArtForge.Form form = ArtForge.Form.valueOf(row.form());
            List<ArtForge.Extra> extras = new ArrayList<>();
            if (!row.extras().isEmpty()) {
                for (String extra : row.extras().split(",")) {
                    extras.add(ArtForge.Extra.valueOf(extra));
                }
            }
            String[] key = row.key().split(":");
            WeaponArt art;
            if (key[0].equals("design") || key[0].equals("relic")) {
                WeaponDesign design = Armory.weapon(key[1])
                        .orElseThrow(() -> new IllegalStateException("Unknown design " + row.key()));
                Element element = design.element();
                double burst;
                double rate;
                if (design.isRelic()) {
                    // relics: at least as strong as the boss arts of their level
                    double progress = Math.max(0.0, Math.min(1.0, (design.level() - 11) / 49.0));
                    burst = RELIC_BURST + 0.25 * progress;
                    rate = 1.15 + 0.10 * progress;
                    relics.add(row.id());
                } else {
                    double progress = Math.max(0.0, Math.min(1.0, (design.level() - 5) / 45.0));
                    boolean signature = design.has(DesignSource.LORD) || design.has(DesignSource.CALAMITY);
                    burst = 1.40 + 0.40 * progress + (signature ? 0.05 : 0.0);
                    rate = 1.05 + 0.10 * progress;
                }
                art = ArtForge.build(new ArtForge.Recipe(row.id(), design.type(), form, element.theme(),
                        element.affliction().orElse(null), Math.max(1, element.ticks()), element.power(), extras,
                        ArtBudget.typeBurst(design.type()) * burst, ArtBudget.typePerSecond(design.type()) * rate,
                        look));
                byDesign.put(design.id(), art);
            } else {
                ItemTier tier = ItemTier.byId(key[1])
                        .orElseThrow(() -> new IllegalStateException("Unknown tier " + row.key()));
                WeaponType type = WeaponType.valueOf(key[2]);
                double burst = 1.08 + 0.03 * tier.ordinal();
                AfflictionKind affliction = row.affliction().isEmpty() ? null : AfflictionKind.valueOf(row.affliction());
                art = ArtForge.build(new ArtForge.Recipe(row.id(), type, form, SkillTheme.valueOf(row.theme()),
                        affliction, Math.max(1, row.afflictionTicks()), row.afflictionPower(), extras,
                        ArtBudget.typeBurst(type) * burst, ArtBudget.typePerSecond(type), look));
                byTier.computeIfAbsent(tier, t -> new EnumMap<>(WeaponType.class)).put(type, art);
            }
            if (byId.put(art.id(), art) != null) {
                throw new IllegalStateException("Duplicate art " + art.id());
            }
            looks.put(art.id(), look);
            zoneLooks.put(look.zoneLook(), look);
            all.add(art);
        }
        for (ItemTier tier : ItemTier.values()) {
            if (!byTier.containsKey(tier) || byTier.get(tier).size() != WeaponType.values().length) {
                throw new IllegalStateException("Tier " + tier + " lacks arts");
            }
        }
        return new Catalog(Collections.unmodifiableMap(byId), Collections.unmodifiableMap(byDesign),
                Collections.unmodifiableMap(byTier), Collections.unmodifiableMap(looks),
                Collections.unmodifiableMap(zoneLooks), Collections.unmodifiableList(all),
                java.util.Set.copyOf(relics));
    }
}
