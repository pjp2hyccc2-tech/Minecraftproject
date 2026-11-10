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
            if (json.has("textures")) {
                assertEquals("eternalrealm:item/" + item, json.getAsJsonObject("textures").get("layer0").getAsString());
            } else {
                // paired weapons (twin blades): the icon of tier 1 through the separate-transforms parent
                String icon = textureOf("eternalrealm:item/" + item, 0);
                assertTrue("missing icon " + icon, Files.isRegularFile(RESOURCES.resolve("assets/eternalrealm/textures/"
                        + icon.substring("eternalrealm:".length()) + ".png")));
            }
            Path texture = RESOURCES.resolve("assets/eternalrealm/textures/item/" + item + ".png");
            assertTrue("missing texture " + texture, Files.isRegularFile(texture));
            byte[] png = Files.readAllBytes(texture);
            assertTrue("not a PNG: " + texture, png.length > 8 && (png[1] & 0xFF) == 'P' && png[2] == 'N' && png[3] == 'G');
            assertTrue("missing item name for " + item, english.contains("item.eternalrealm." + item));
        }
        assertTrue(english.contains("itemGroup.eternalrealm"));
    }

    /**
     * Every registered item of the item module (70 equipment bases, materials, consumables) resolves to an existing
     * texture through its model (directly, or through the art's parent model), and has a name.
     */
    @Test
    public void everyEquipmentMaterialAndConsumableItemHasArtAndAName() throws IOException {
        Set<String> english = keys(RESOURCES.resolve("assets/eternalrealm/lang/en_us.json"));
        Set<String> japanese = keys(RESOURCES.resolve("assets/eternalrealm/lang/ja_jp.json"));
        java.util.List<String> paths = new java.util.ArrayList<>();
        for (com.eternalrealm.item.EquipmentBase base : com.eternalrealm.item.EquipmentBase.all()) {
            paths.add(base.itemPath());
        }
        for (String id : com.eternalrealm.item.Materials.all()) {
            paths.add(id.substring(id.indexOf(':') + 1));
        }
        for (com.eternalrealm.item.Consumable consumable : com.eternalrealm.item.Consumable.values()) {
            paths.add(consumable.itemId().substring(consumable.itemId().indexOf(':') + 1));
        }
        for (String item : paths) {
            String texture = textureOf("eternalrealm:item/" + item, 0);
            Path png = RESOURCES.resolve("assets/eternalrealm/textures/" + texture.substring("eternalrealm:".length())
                    + ".png");
            assertTrue("missing texture " + png + " for " + item, Files.isRegularFile(png));
            assertTrue("missing English name for " + item, english.contains("item.eternalrealm." + item));
            assertTrue("missing Japanese name for " + item, japanese.contains("item.eternalrealm." + item));
        }
        for (int tier = 1; tier <= 7; tier++) {
            for (int layer = 1; layer <= 2; layer++) {
                Path worn = RESOURCES.resolve("assets/eternalrealm/textures/models/armor/er_tier" + tier + "_layer_"
                        + layer + ".png");
                assertTrue("missing worn armor " + worn, Files.isRegularFile(worn));
            }
        }
        for (com.eternalrealm.job.JobType job : com.eternalrealm.job.JobType.values()) {
            if (!job.isChosen()) {
                continue;
            }
            for (int layer = 1; layer <= 2; layer++) {
                Path worn = RESOURCES.resolve("assets/eternalrealm/textures/models/armor/er_job_" + job.id()
                        + "_layer_" + layer + ".png");
                assertTrue("missing job outfit " + worn, Files.isRegularFile(worn));
            }
            assertTrue(Files.isRegularFile(RESOURCES.resolve("assets/eternalrealm/textures/gui/job/" + job.id()
                    + ".png")));
        }
    }

    /** The layer0 texture of a model, following eternalrealm parents. */
    private static String textureOf(String model, int depth) throws IOException {
        assertTrue("model parent chain too deep at " + model, depth < 6);
        Path file = RESOURCES.resolve("assets/eternalrealm/models/" + model.substring("eternalrealm:".length())
                + ".json");
        assertTrue("missing model " + file, Files.isRegularFile(file));
        JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        if (json.has("textures") && json.getAsJsonObject("textures").has("layer0")) {
            return json.getAsJsonObject("textures").get("layer0").getAsString();
        }
        if ("forge:separate_transforms".equals(json.has("loader") ? json.get("loader").getAsString() : "")) {
            // icon in the inventory, another model in the hands: both must resolve
            String held = textureOf(json.getAsJsonObject("base").get("parent").getAsString(), depth + 1);
            assertTrue("missing held texture " + held, Files.isRegularFile(RESOURCES.resolve("assets/eternalrealm/textures/"
                    + held.substring("eternalrealm:".length()) + ".png")));
            return textureOf(json.getAsJsonObject("perspectives").getAsJsonObject("gui").get("parent").getAsString(),
                    depth + 1);
        }
        String parent = json.get("parent").getAsString();
        assertTrue(model + " has no texture and no mod parent", parent.startsWith("eternalrealm:"));
        return textureOf(parent, depth + 1);
    }

    @Test
    public void everyParticleAndSkillIconExists() throws IOException {
        for (String particle : java.util.List.of("ember", "frost", "heal", "holy", "rune", "shadow", "slash", "spark")) {
            Path definition = RESOURCES.resolve("assets/eternalrealm/particles/" + particle + ".json");
            assertTrue("missing particle definition " + definition, Files.isRegularFile(definition));
            JsonObject json = JsonParser.parseString(Files.readString(definition, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            for (com.google.gson.JsonElement frame : json.getAsJsonArray("textures")) {
                String name = frame.getAsString().substring("eternalrealm:".length());
                assertTrue("missing particle frame " + name, Files.isRegularFile(
                        RESOURCES.resolve("assets/eternalrealm/textures/particle/" + name + ".png")));
            }
        }
        for (com.eternalrealm.job.SkillDefinition skill : com.eternalrealm.job.Jobs.allSkills().values()) {
            assertTrue("missing skill icon " + skill.id(), Files.isRegularFile(
                    RESOURCES.resolve("assets/eternalrealm/textures/gui/skill/" + skill.id() + ".png")));
        }
        for (String effect : java.util.List.of("attack_up", "defense_up", "flame_weapon", "bleed", "haste", "frozen",
                "guard_up", "cursed", "stunned", "poisoned", "weakened", "vulnerable", "crit_up", "lifesteal",
                "frost_weapon", "venom_weapon", "thunder_weapon", "evasion", "thorns")) {
            assertTrue("missing effect icon " + effect, Files.isRegularFile(
                    RESOURCES.resolve("assets/eternalrealm/textures/mob_effect/" + effect + ".png")));
        }
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
        for (int slot = 1; slot <= com.eternalrealm.job.SkillTree.LOADOUT_SLOTS; slot++) {
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
