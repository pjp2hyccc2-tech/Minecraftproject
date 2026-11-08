package com.eternalrealm;

import com.eternalrealm.combat.WeaponProfile;
import com.eternalrealm.combat.WeaponProfiles;
import com.eternalrealm.combat.WeaponType;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Guards the mod resources that only fail at runtime inside Minecraft.
 *
 * <p>Found by the CI GameTest server: without {@code pack.mcmeta} Forge 47.x rejects the mod's pack ("Missing metadata
 * in pack mod:eternalrealm"), so translations, the GameTest structure and all future data silently disappear.</p>
 */
public class ResourcesTest {
    private static final Path RESOURCES = Paths.get("src", "main", "resources");

    @Test
    public void packMetadataExistsWithTheFormatOfMinecraft1201() throws IOException {
        Path packMeta = RESOURCES.resolve("pack.mcmeta");
        assertTrue("pack.mcmeta is required by Forge 47.x", Files.isRegularFile(packMeta));
        JsonObject pack = JsonParser.parseString(Files.readString(packMeta, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("pack");
        assertEquals("1.20.1 uses pack format 15 for resource and data packs", 15, pack.get("pack_format").getAsInt());
        assertTrue(pack.has("description"));
    }

    /** Registry names of the weapon items (ERItems); kept here because this test runs without Forge. */
    private static final List<String> WEAPON_ITEMS =
            List.of("iron_longsword", "iron_greatsword", "iron_twin_blades", "iron_spear", "staff", "longbow");

    @Test
    public void gameTestStructuresArePresent() {
        assertTrue(Files.isRegularFile(RESOURCES.resolve("data/eternalrealm/structures/empty.nbt")));
        assertTrue(Files.isRegularFile(RESOURCES.resolve("data/eternalrealm/structures/arena.nbt")));
    }

    @Test
    public void everyWeaponItemHasAModelATextureAndAName() throws IOException {
        Set<String> english = keys(RESOURCES.resolve("assets/eternalrealm/lang/en_us.json"));
        for (String item : WEAPON_ITEMS) {
            Path model = RESOURCES.resolve("assets/eternalrealm/models/item/" + item + ".json");
            assertTrue("missing model " + model, Files.isRegularFile(model));
            JsonObject json = JsonParser.parseString(Files.readString(model, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("eternalrealm:item/" + item, json.getAsJsonObject("textures").get("layer0").getAsString());
            Path texture = RESOURCES.resolve("assets/eternalrealm/textures/item/" + item + ".png");
            assertTrue("missing texture " + texture, Files.isRegularFile(texture));
            byte[] png = Files.readAllBytes(texture);
            assertTrue("not a PNG: " + texture, png.length > 8 && (png[1] & 0xFF) == 'P' && png[2] == 'N' && png[3] == 'G');
            assertTrue("missing item name for " + item, english.contains("item.eternalrealm." + item));
        }
        assertTrue(english.contains("itemGroup.eternalrealm"));
    }

    @Test
    public void everyCombatTextIsTranslated() throws IOException {
        Set<String> english = keys(RESOURCES.resolve("assets/eternalrealm/lang/en_us.json"));
        for (WeaponType type : WeaponType.values()) {
            assertTrue(type.translationKey(), english.contains(type.translationKey()));
        }
        for (WeaponProfile profile : WeaponProfiles.all()) {
            assertTrue(profile.skill().translationKey(), english.contains(profile.skill().translationKey()));
        }
        for (com.eternalrealm.combat.RequestOutcome outcome : com.eternalrealm.combat.RequestOutcome.values()) {
            outcome.messageKey().ifPresent(key -> assertTrue(key, english.contains(key)));
        }
        for (String key : List.of("key.eternalrealm.heavy_attack", "key.eternalrealm.dodge", "key.eternalrealm.weapon_skill",
                "hud.eternalrealm.combat.parried", "hud.eternalrealm.combat.dodged", "hud.eternalrealm.combat.guarded",
                "hud.eternalrealm.combat.guard_broken", "hud.eternalrealm.combat.ready", "hud.eternalrealm.combat.cooldown",
                "hud.eternalrealm.combat.skill", "hud.eternalrealm.combat.combo", "hud.eternalrealm.combat.charge_max",
                "screen.eternalrealm.character.combat1", "screen.eternalrealm.character.combat2",
                "commands.eternalrealm.character.combat")) {
            assertTrue(key, english.contains(key));
        }
    }

    @Test
    public void englishAndJapaneseDefineTheSameKeys() throws IOException {
        Set<String> english = keys(RESOURCES.resolve("assets/eternalrealm/lang/en_us.json"));
        Set<String> japanese = keys(RESOURCES.resolve("assets/eternalrealm/lang/ja_jp.json"));
        assertTrue("no translations found", english.size() > 20);
        assertEquals(english, japanese);
    }

    private static Set<String> keys(Path langFile) throws IOException {
        JsonElement root = JsonParser.parseString(Files.readString(langFile, StandardCharsets.UTF_8));
        return new TreeSet<>(root.getAsJsonObject().keySet());
    }

    @Test
    public void everyJobTextIsTranslated() throws IOException {
        Set<String> english = keys(RESOURCES.resolve("assets/eternalrealm/lang/en_us.json"));
        Set<String> japanese = keys(RESOURCES.resolve("assets/eternalrealm/lang/ja_jp.json"));
        List<String> required = new java.util.ArrayList<>();
        for (com.eternalrealm.job.JobType job : com.eternalrealm.job.JobType.values()) {
            required.add(job.translationKey());
            required.add(job.descriptionKey());
        }
        for (com.eternalrealm.job.JobRole role : com.eternalrealm.job.JobRole.values()) {
            required.add(role.translationKey());
        }
        for (com.eternalrealm.job.SkillDefinition skill : com.eternalrealm.job.Jobs.allSkills().values()) {
            required.add(skill.translationKey());
            required.add(skill.descriptionKey());
        }
        for (com.eternalrealm.combat.DamageType type : com.eternalrealm.combat.DamageType.values()) {
            required.add(type.translationKey());
        }
        for (com.eternalrealm.job.SkillTree.InvestResult result : com.eternalrealm.job.SkillTree.InvestResult.values()) {
            required.add(result.translationKey());
        }
        for (int slot = 1; slot <= 4; slot++) {
            required.add("key.eternalrealm.job_skill_" + slot);
        }
        for (String effect : List.of("attack_up", "defense_up", "flame_weapon", "bleed")) {
            required.add("effect.eternalrealm." + effect);
            Path icon = RESOURCES.resolve("assets/eternalrealm/textures/mob_effect/" + effect + ".png");
            assertTrue("missing effect icon " + icon, Files.isRegularFile(icon));
        }
        for (String key : required) {
            assertTrue("en: " + key, english.contains(key));
            assertTrue("ja: " + key, japanese.contains(key));
        }
    }
}
