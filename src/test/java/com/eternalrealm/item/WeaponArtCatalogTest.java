package com.eternalrealm.item;

import com.eternalrealm.combat.HitFrame;
import com.eternalrealm.combat.WeaponProfile;
import com.eternalrealm.combat.WeaponType;
import com.eternalrealm.job.SkillEffect;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The weapon arts of every weapon (the Z key): every named, tier and starter weapon resolves to its own art, no two
 * arts share an id or a look, the damage budget keeps tier arts below named arts below boss arts, and every art is
 * translated.
 */
public class WeaponArtCatalogTest {
    /** Simple particles of the game the looks may name ({@code v:<name>}). */
    private static final Set<String> VANILLA = Set.of("end_rod", "crit", "enchanted_hit", "cloud", "poof", "smoke",
            "large_smoke", "flame", "small_flame", "soul_fire_flame", "soul", "sculk_soul", "snowflake",
            "electric_spark", "firework", "cherry_leaves", "spore_blossom_air", "white_ash", "ash", "squid_ink",
            "glow", "bubble", "splash", "rain", "dripping_water", "falling_water", "nautilus", "note", "heart",
            "happy_villager", "composter", "item_slime", "lava", "falling_lava", "dripping_honey", "dragon_breath",
            "portal", "reverse_portal", "witch", "enchant", "wax_on", "wax_off", "damage_indicator", "mycelium");

    @Test
    public void everyWeaponHasItsOwnArt() {
        Set<String> used = new HashSet<>();
        for (WeaponDesign design : Armory.weapons()) {
            WeaponProfile profile = DesignProfiles.of(design);
            String art = profile.skill().id();
            assertTrue(design.id() + " falls back to " + art, art.startsWith("art_"));
            assertTrue("art used twice: " + art, used.add(art));
            if (design.isBossWeapon()) {
                assertEquals(design.art(), art);
            } else {
                assertEquals(GeneratedArts.forDesign(design.id()).orElseThrow().id(), art);
                assertEquals(design.type(), GeneratedArts.byId(art).orElseThrow().type());
            }
        }
        assertEquals(189, used.size());
        for (ItemTier tier : ItemTier.values()) {
            for (WeaponType type : WeaponType.values()) {
                WeaponProfile profile = TierProfiles.of(tier, type);
                String art = profile.skill().id();
                assertEquals(GeneratedArts.forTier(tier, type).id(), art);
                assertTrue("art used twice: " + art, used.add(art));
                assertEquals(type, profile.type());
                assertTrue(WeaponArts.byId(art).isPresent());
            }
        }
        assertEquals(189 + 42, used.size());
        assertEquals(222, GeneratedArts.all().size());
    }

    @Test
    public void noTwoArtsLookAlike() {
        Map<String, String> signatures = new HashMap<>();
        Set<String> families = new HashSet<>();
        for (WeaponArt art : GeneratedArts.all()) {
            ArtLook look = GeneratedArts.look(art.id()).orElseThrow();
            String other = signatures.put(look.signature(), art.id());
            assertTrue(art.id() + " looks like " + other, other == null);
            assertTrue("family used twice: " + look.family(), families.add(look.family()));
            assertTrue(GeneratedArts.zoneLook(look.zoneLook()).isPresent());
            for (String particle : List.of(look.particle(), look.accentParticle())) {
                if (particle.startsWith("v:")) {
                    assertTrue(art.id() + ": unknown particle " + particle, VANILLA.contains(particle.substring(2)));
                }
            }
            for (SkillEffect effect : art.effects()) {
                if (effect instanceof SkillEffect.Zone zone) {
                    assertEquals(look.zoneLook(), zone.look());
                }
            }
        }
    }

    @Test
    public void tierArtsBelowNamedArtsBelowBossArts() {
        Map<WeaponType, double[]> tier = new EnumMap<>(WeaponType.class);
        Map<WeaponType, double[]> named = new EnumMap<>(WeaponType.class);
        for (WeaponArt art : GeneratedArts.all()) {
            double burst = ArtBudget.burst(art) / ArtBudget.typeBurst(art.type());
            double rate = ArtBudget.perSecond(art) / ArtBudget.typePerSecond(art.type());
            boolean isTier = GeneratedArts.isTierArt(art.id());
            if (isTier) {
                assertTrue(art.id() + " burst " + burst, burst >= 1.05 && burst <= 1.30);
                assertTrue(art.id() + " rate " + rate, rate >= 0.90 && rate <= 1.12);
            } else {
                assertTrue(art.id() + " burst " + burst, burst >= 1.35 && burst <= 1.90);
                assertTrue(art.id() + " rate " + rate, rate >= 0.95 && rate <= 1.22);
            }
            double[] range = (isTier ? tier : named).computeIfAbsent(art.type(), t -> new double[] {9.0, 0.0});
            range[0] = Math.min(range[0], burst);
            range[1] = Math.max(range[1], burst);
        }
        for (WeaponArt boss : WeaponArts.all()) {
            double burst = ArtBudget.burst(boss) / ArtBudget.typeBurst(boss.type());
            double rate = ArtBudget.perSecond(boss) / ArtBudget.typePerSecond(boss.type());
            assertTrue(boss.id() + " burst " + burst, burst >= 1.95);
            assertTrue(boss.id() + " rate " + rate, rate >= 0.8 && rate <= 1.5);
            assertTrue(boss.id() + " must beat every named art", burst > named.get(boss.type())[1]);
        }
        for (WeaponType type : WeaponType.values()) {
            assertTrue(type + ": named arts above tier arts", named.get(type)[0] > tier.get(type)[1]);
        }
    }

    @Test
    public void artsAreVariedAndFollowTheirElement() {
        Set<Class<?>> kinds = new HashSet<>();
        Set<ArtForge.Form> forms = new HashSet<>();
        for (WeaponArt art : GeneratedArts.all()) {
            assertFalse(art.id(), art.action().frames().isEmpty());
            assertTrue(art.id(), art.action().frames().stream().anyMatch(HitFrame::damaging));
            assertTrue(art.id() + " cooldown", art.action().cooldownTicks() >= 100
                    && art.action().cooldownTicks() <= 600);
            for (SkillEffect effect : art.effects()) {
                kinds.add(effect.getClass());
            }
        }
        for (ArtTable.Row row : ArtTable.ROWS) {
            forms.add(ArtForge.Form.valueOf(row.form()));
        }
        assertEquals("every form is used", ArtForge.Form.values().length, forms.size());
        assertTrue("effect kinds used: " + kinds, kinds.size() >= 12);
        for (WeaponDesign design : Armory.weapons()) {
            if (design.isBossWeapon()) {
                continue;
            }
            WeaponArt art = GeneratedArts.forDesign(design.id()).orElseThrow();
            design.element().affliction().filter(kind -> !kind.instant()).ifPresent(kind ->
                    assertTrue(design.id() + " should inflict " + kind, art.effects().stream()
                            .anyMatch(e -> e instanceof SkillEffect.Afflict a && a.kind() == kind)));
            assertEquals(design.element().theme(), art.theme());
        }
    }

    @Test
    public void everyArtIsTranslated() throws IOException {
        Map<String, String> ja = lang("ja_jp");
        Map<String, String> en = lang("en_us");
        Set<String> names = new HashSet<>();
        for (WeaponArt art : GeneratedArts.all()) {
            for (String key : List.of(art.translationKey(), art.descriptionKey())) {
                assertNotNull("ja " + key, ja.get(key));
                assertNotNull("en " + key, en.get(key));
                assertFalse(ja.get(key).isBlank() || en.get(key).isBlank());
            }
            assertTrue("name used twice: " + ja.get(art.translationKey()), names.add(ja.get(art.translationKey())));
            // the skill bar shows the art's own icon
            assertTrue("icon of " + art.id(), Files.isRegularFile(Paths.get(
                    "src/main/resources/assets/eternalrealm/textures/gui/skill/" + art.id() + ".png")));
        }
    }

    private static Map<String, String> lang(String name) throws IOException {
        JsonObject object = JsonParser.parseString(Files.readString(Paths.get(
                "src/main/resources/assets/eternalrealm/lang/" + name + ".json"), StandardCharsets.UTF_8))
                .getAsJsonObject();
        Map<String, String> out = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            out.put(entry.getKey(), entry.getValue().getAsString());
        }
        return out;
    }
}
