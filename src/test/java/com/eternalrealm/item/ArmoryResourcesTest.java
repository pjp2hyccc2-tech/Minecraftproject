package com.eternalrealm.item;

import com.eternalrealm.combat.WeaponType;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The assets of the armory: every design has its icon, model and (for weapons) its in-hand look, every tier item
 * routes the design's custom model data to it, every name, lore line, set, weapon art and tooltip text is translated
 * in Japanese and English, and no name borrows a famous weapon or skill name from elsewhere.
 */
public class ArmoryResourcesTest {
    private static final Path ASSETS = Paths.get("src/main/resources/assets/eternalrealm");

    /** Famous names that must not appear in any design, set or art name (lower-case, whole words for latin). */
    private static final List<String> BORROWED = List.of(
            "excalibur", "masamune", "murasame", "kusanagi", "gungnir", "mjolnir", "longinus", "durandal", "ragnarok",
            "laevateinn", "gram", "balmung", "caladbolg", "frostmourne", "ashbringer", "keyblade", "monado",
            "master sword", "buster sword", "soul edge", "grand cross", "arcane missiles", "frost nova", "lay on hands",
            "エクスカリバー", "正宗", "村正", "草薙", "グングニル", "ミョルニル", "ロンギヌス", "デュランダル", "ラグナロク",
            "レーヴァテイン", "バルムンク", "カラドボルグ", "マスターソード", "バスターソード", "グランドクロス",
            "アーケインミサイル", "フロストノヴァ");

    @Test
    public void everyDesignHasItsIconAndModel() throws IOException {
        for (Design design : Armory.designs()) {
            String id = design.id();
            assertTrue("texture of " + id, Files.isRegularFile(ASSETS.resolve("textures/item/named/" + id + ".png")));
            JsonObject model = json(ASSETS.resolve("models/item/named/" + id + ".json"));
            if (design instanceof WeaponDesign weapon && weapon.type() == WeaponType.DUAL_BLADES) {
                // the pair in the inventory, one blade in the hand
                assertEquals("forge:separate_transforms", model.get("loader").getAsString());
                assertTrue(Files.isRegularFile(ASSETS.resolve("textures/item/named/" + id + "_held.png")));
                assertTrue(Files.isRegularFile(ASSETS.resolve("models/item/named/" + id + "_held.json")));
                assertTrue(Files.isRegularFile(ASSETS.resolve("models/item/named/" + id + "_icon.json")));
            } else {
                assertEquals("eternalrealm:item/named/" + id,
                        model.getAsJsonObject("textures").get("layer0").getAsString());
            }
        }
    }

    @Test
    public void tierItemsRouteEveryDesignToItsModel() throws IOException {
        for (Design design : Armory.designs()) {
            String item = design.base().itemPath();
            JsonObject model = json(ASSETS.resolve("models/item/" + item + ".json"));
            JsonArray overrides = model.getAsJsonArray("overrides");
            assertTrue(item + " has overrides", overrides != null && overrides.size() > 0);
            // the last override whose custom_model_data is <= the design's model wins: it must be the design's
            String chosen = null;
            int last = Integer.MIN_VALUE;
            for (JsonElement element : overrides) {
                JsonObject override = element.getAsJsonObject();
                JsonObject predicate = override.getAsJsonObject("predicate");
                if (!predicate.has("custom_model_data")) {
                    continue;
                }
                int value = predicate.get("custom_model_data").getAsInt();
                assertTrue(item + ": overrides ascend", value >= last);
                last = value;
                if (value <= design.model()) {
                    chosen = override.get("model").getAsString();
                }
            }
            assertEquals(item + " for " + design.id(), "eternalrealm:item/named/" + design.id(), chosen);
        }
    }

    @Test
    public void everySetHasItsWornTextures() {
        for (ArmorSet set : Armory.sets()) {
            for (int layer = 1; layer <= 2; layer++) {
                assertTrue(set.id() + " layer " + layer, Files.isRegularFile(
                        ASSETS.resolve("textures/models/armor/er_set_" + set.id() + "_layer_" + layer + ".png")));
            }
        }
    }

    @Test
    public void everyWeaponArtHasItsIconSoundAndTexts() throws IOException {
        JsonObject sounds = json(ASSETS.resolve("sounds.json"));
        Map<String, String> ja = lang("ja_jp");
        Map<String, String> en = lang("en_us");
        for (WeaponArt art : WeaponArts.all()) {
            assertTrue("icon of " + art.id(), Files.isRegularFile(ASSETS.resolve("textures/gui/skill/" + art.id() + ".png")));
            String sound = art.soundId().substring(art.soundId().indexOf(':') + 1);
            assertTrue("sound of " + art.id(), sounds.has(sound));
            for (String key : List.of("action.eternalrealm." + art.id(), art.descriptionKey(),
                    "subtitles.eternalrealm." + sound)) {
                assertTranslated(ja, en, key);
            }
        }
    }

    @Test
    public void everyNameLoreAndTooltipIsTranslated() throws IOException {
        Map<String, String> ja = lang("ja_jp");
        Map<String, String> en = lang("en_us");
        for (Design design : Armory.designs()) {
            assertTranslated(ja, en, design.translationKey());
            assertTranslated(ja, en, design.loreKey());
        }
        for (ArmorSet set : Armory.sets()) {
            assertTranslated(ja, en, set.translationKey());
            assertTranslated(ja, en, set.loreKey());
        }
        for (ArmorStyle style : ArmorStyle.values()) {
            assertTranslated(ja, en, style.translationKey());
        }
        for (Element element : Element.values()) {
            assertTranslated(ja, en, element.translationKey());
        }
        for (DesignSource source : DesignSource.values()) {
            assertTranslated(ja, en, source.translationKey());
        }
        for (String key : EquipmentTooltip.KEYS) {
            assertTranslated(ja, en, key);
        }
        for (String key : List.of("message.eternalrealm.armory.found", "message.eternalrealm.armory.boss_weapon_broadcast",
                "screen.eternalrealm.smith.design_header", "skill_info.eternalrealm.effect.revive",
                "skill_info.eternalrealm.passive.attack_speed")) {
            assertTranslated(ja, en, key);
        }
    }

    @Test
    public void namesAreOriginalAndDistinct() throws IOException {
        Map<String, String> ja = lang("ja_jp");
        Map<String, String> en = lang("en_us");
        List<String> keys = new ArrayList<>();
        for (Design design : Armory.designs()) {
            keys.add(design.translationKey());
        }
        for (ArmorSet set : Armory.sets()) {
            keys.add(set.translationKey());
        }
        for (WeaponArt art : WeaponArts.all()) {
            keys.add("action.eternalrealm." + art.id());
        }
        Map<String, String> seenJa = new HashMap<>();
        Map<String, String> seenEn = new HashMap<>();
        for (String key : keys) {
            String jaName = ja.get(key);
            String enName = en.get(key);
            assertFalse("duplicate ja name " + jaName, seenJa.containsKey(jaName) && !seenJa.get(jaName).equals(key));
            assertFalse("duplicate en name " + enName, seenEn.containsKey(enName) && !seenEn.get(enName).equals(key));
            seenJa.put(jaName, key);
            seenEn.put(enName, key);
        }
        // every skill and design name in the lang files, not only the armory's
        for (Map.Entry<String, String> entry : en.entrySet()) {
            if (isName(entry.getKey())) {
                assertOriginal(entry.getKey(), entry.getValue());
                assertOriginal(entry.getKey(), ja.getOrDefault(entry.getKey(), ""));
            }
        }
    }

    private static boolean isName(String key) {
        return (key.startsWith("design.eternalrealm.") || key.startsWith("armor_set.eternalrealm.")
                || key.startsWith("action.eternalrealm.art_") || key.startsWith("skill.eternalrealm."))
                && !key.endsWith(".lore") && !key.endsWith(".desc");
    }

    private static void assertOriginal(String key, String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String borrowed : BORROWED) {
            boolean latin = borrowed.chars().allMatch(c -> c < 128);
            boolean found = latin ? Pattern.compile("\\b" + Pattern.quote(borrowed) + "\\b").matcher(lower).find()
                    : lower.contains(borrowed);
            assertFalse(key + " borrows \"" + borrowed + "\": " + name, found);
        }
    }

    private static void assertTranslated(Map<String, String> ja, Map<String, String> en, String key) {
        assertTrue("ja " + key, ja.containsKey(key) && !ja.get(key).isBlank());
        assertTrue("en " + key, en.containsKey(key) && !en.get(key).isBlank());
    }

    private static Map<String, String> lang(String name) throws IOException {
        JsonObject object = json(ASSETS.resolve("lang/" + name + ".json"));
        Map<String, String> out = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            out.put(entry.getKey(), entry.getValue().getAsString());
        }
        return out;
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
