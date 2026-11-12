package com.eternalrealm.item;

import com.eternalrealm.boss.Bosses;
import com.eternalrealm.combat.WeaponType;
import com.eternalrealm.core.data.CorruptDataException;
import com.eternalrealm.core.data.IncompatibleDataException;
import com.eternalrealm.monster.Monsters;
import com.eternalrealm.story.StoryProgress;
import com.eternalrealm.world.field.FieldThreats;
import com.eternalrealm.world.field.Wanderers;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 金枠の遺宝 (relics): every dungeon boss, calamity, lord and wanderer has its own weapon, armor set, accessory and
 * charm; relics are legendary, ask for their source's level + 5, are the strongest pieces of their level, drop about
 * once in a thousand kills, survive the codec, and every one has its name, lore and art.
 */
public class RelicCatalogTest {
    private static final Path ASSETS = Paths.get("src/main/resources/assets/eternalrealm");

    @Test
    public void everyBossCalamityLordAndWandererHasItsRelics() {
        List<Relics.Source> sources = Relics.sources();
        assertEquals(64, sources.size());
        Map<Relics.SourceKind, Set<String>> targets = new EnumMap<>(Relics.SourceKind.class);
        for (Relics.Source source : sources) {
            targets.computeIfAbsent(source.kind(), kind -> new HashSet<>()).add(source.target());
        }
        assertEquals(Set.copyOf(StoryProgress.MAIN_BOSSES), targets.get(Relics.SourceKind.BOSS));
        assertEquals(FieldThreats.calamities().stream().map(FieldThreats.Calamity::id).collect(Collectors.toSet()),
                targets.get(Relics.SourceKind.CALAMITY));
        assertEquals(FieldThreats.lords().stream().map(FieldThreats.Lord::id).collect(Collectors.toSet()),
                targets.get(Relics.SourceKind.LORD));
        assertEquals(Wanderers.all().stream().map(Wanderers.Wanderer::id).collect(Collectors.toSet()),
                targets.get(Relics.SourceKind.WANDERER));
        Set<String> ids = new HashSet<>();
        Set<Integer> models = new HashSet<>();
        Set<WeaponType> types = new HashSet<>();
        for (Relics.Source source : sources) {
            assertEquals(source, Relics.source(source.kind(), source.target()).orElseThrow());
            assertEquals(source, Relics.source(source.key()).orElseThrow());
            List<Design> designs = source.designs();
            assertEquals(7, designs.size());
            for (Design design : designs) {
                assertTrue(design.id(), design.isRelic());
                assertEquals(design.id(), source.key(), design.origin());
                assertEquals(source, Relics.sourceOf(design).orElseThrow());
                assertEquals(design.id(), source.level() + Relics.LEVEL_BONUS, design.level());
                assertTrue("id twice: " + design.id(), ids.add(design.id()));
                assertTrue("model twice: " + design.model(), models.add(design.model()));
                assertEquals(design, Armory.design(design.id()).orElseThrow());
                assertEquals(design, Armory.byModel(design.model()).orElseThrow());
                assertTrue(design.id(), Armory.designs().stream().noneMatch(d -> d.id().equals(design.id())));
                // a relic asks for its own level whatever its rarity
                EquipmentData legendary = EquipmentGenerator.roll(design, ItemRarity.LEGENDARY,
                        new SplittableRandom(design.model()));
                assertEquals(design.id(), design.level(), legendary.requiredLevel());
                assertTrue(legendary.isRelic());
            }
            assertTrue(source.weapon().model() >= Design.RELIC_WEAPON_MODELS
                    && source.weapon().model() < Design.ARMOR_MODELS);
            assertTrue(source.set().model() >= Design.RELIC_ARMOR_MODELS
                    && source.set().model() < Design.TRINKET_MODELS);
            assertEquals(EquipmentSlot.ACCESSORY, source.accessory().slot());
            assertEquals(EquipmentSlot.CHARM, source.charm().slot());
            assertEquals(EquipmentBase.trinket(EquipmentSlot.ACCESSORY, source.accessory().tier()),
                    source.accessory().base());
            // relic trinkets raise stats by percentages: ×1.25 of a catalogue trinket of their level
            assertFalse(source.accessory().percents().isZero() || source.charm().percents().isZero());
            assertEquals(Math.round(1.25 * Trinkets.charmPercent(source.charm().level()) * 1000.0) / 1000.0,
                    source.charm().percents().get(com.eternalrealm.character.StatType.VIT), 1.0e-9);
            assertTrue(source.accessory().percents().asMap().values().stream().mapToDouble(Double::doubleValue)
                    .max().orElseThrow() >= 1.25 * Trinkets.accessoryPercent(source.accessory().level()) - 1.0e-3);
            types.add(source.weapon().type());
        }
        assertEquals("every weapon type has relics", WeaponType.values().length, types.size());
        assertEquals(448, Relics.designs().size());
    }

    @Test
    public void relicsAskForTheirSourceLevelPlusFive() {
        for (Relics.Source source : Relics.sources()) {
            int level = switch (source.kind()) {
                case BOSS -> Bosses.byId(source.target()).orElseThrow().level();
                case CALAMITY -> Bosses.byId(FieldThreats.calamities().stream()
                        .filter(c -> c.id().equals(source.target())).findFirst().orElseThrow().boss())
                        .orElseThrow().level();
                case WANDERER -> Bosses.byId(Wanderers.all().stream().filter(w -> w.id().equals(source.target()))
                        .findFirst().orElseThrow().boss()).orElseThrow().level();
                case LORD -> Monsters.byId(FieldThreats.lord(source.target()).orElseThrow().template()).orElseThrow()
                        .levels().max();
            };
            assertEquals(source.key(), level, source.level());
            assertEquals(source.key(), level + 5, source.relicLevel());
        }
    }

    @Test
    public void relicsAreTheStrongestPiecesOfTheirLevel() {
        for (WeaponDesign relic : Relics.weapons()) {
            double attack = EquipmentStats.baseAttack(legendary(relic));
            for (ItemRarity rarity : ItemRarity.values()) {
                for (WeaponDesign other : Armory.weapons()) {
                    if (other.type() != relic.type()) {
                        continue;
                    }
                    EquipmentData piece = EquipmentGenerator.roll(other, rarity, new SplittableRandom(1));
                    if (piece.requiredLevel() <= relic.level()) {
                        assertTrue(relic.id() + " should beat " + other.id() + " " + rarity,
                                attack > EquipmentStats.baseAttack(piece));
                    }
                }
                for (ItemTier tier : ItemTier.values()) {
                    EquipmentData piece = EquipmentGenerator.roll(EquipmentBase.weapon(relic.type(), tier), rarity,
                            new SplittableRandom(1));
                    if (piece.requiredLevel() <= relic.level()) {
                        assertTrue(relic.id() + " should beat " + tier + " " + rarity,
                                attack > EquipmentStats.baseAttack(piece));
                    }
                }
            }
        }
        for (ArmorSet relicSet : Relics.sets()) {
            for (ArmorPiece relic : relicSet.pieces()) {
                double defense = EquipmentStats.baseDefense(legendary(relic));
                for (ItemRarity rarity : ItemRarity.values()) {
                    for (ArmorSet other : Armory.sets()) {
                        EquipmentData piece = EquipmentGenerator.roll(other.piece(relic.armorSlot()), rarity,
                                new SplittableRandom(1));
                        if (other.style() == relicSet.style() && piece.requiredLevel() <= relic.level()) {
                            assertTrue(relic.id() + " should outdefend " + other.id() + " " + rarity,
                                    defense > EquipmentStats.baseDefense(piece));
                        }
                    }
                    for (ItemTier tier : ItemTier.values()) {
                        EquipmentData piece = EquipmentGenerator.roll(EquipmentBase.armor(relic.armorSlot(), tier),
                                rarity, new SplittableRandom(1));
                        if (relicSet.style() != ArmorStyle.ROBE && piece.requiredLevel() <= relic.level()) {
                            assertTrue(relic.id() + " should outdefend " + tier + " " + rarity,
                                    defense > EquipmentStats.baseDefense(piece));
                        }
                    }
                }
            }
        }
    }

    @Test
    public void aboutOneKillInAThousandLeavesARelic() {
        SplittableRandom random = new SplittableRandom(2026);
        int hits = 0;
        int rolls = 1_000_000;
        for (int i = 0; i < rolls; i++) {
            if (Relics.rolls(Relics.DROP_CHANCE, random)) {
                hits++;
            }
        }
        assertTrue("relics in a million kills: " + hits, hits >= 850 && hits <= 1150);
        assertFalse(Relics.rolls(0.0, random));
        assertTrue(Relics.rolls(1.0, random));
        // which relic: the weapon, the accessory, the charm a quarter each, each armor piece a sixteenth
        Relics.Source source = Relics.source("final").orElseThrow();
        Map<String, Integer> counts = new HashMap<>();
        int picks = 160_000;
        for (int i = 0; i < picks; i++) {
            counts.merge(Relics.pick(source, random).id(), 1, Integer::sum);
        }
        assertEquals(7, counts.size());
        for (Design design : source.designs()) {
            double share = counts.get(design.id()) / (double) picks;
            double expected = design instanceof ArmorPiece ? 1.0 / 16 : 0.25;
            assertEquals(design.id(), expected, share, 0.01);
        }
    }

    @Test
    public void relicPiecesSurviveTheCodec() throws CorruptDataException, IncompatibleDataException {
        for (Design design : Relics.source("l_garm").orElseThrow().designs()) {
            EquipmentData piece = legendary(design).withEnhancement(3)
                    .withUniqueId(java.util.UUID.nameUUIDFromBytes(design.id().getBytes(StandardCharsets.UTF_8)));
            EquipmentData read = EquipmentCodec.decode(EquipmentCodec.encode(piece));
            assertEquals(piece, read);
            assertTrue(read.isRelic());
        }
    }

    @Test
    public void everyRelicIsNamedAndDrawn() throws IOException {
        Map<String, String> ja = lang("ja_jp");
        Map<String, String> en = lang("en_us");
        List<String> keys = new ArrayList<>();
        for (Relics.Source source : Relics.sources()) {
            keys.add(source.nameKey());
            keys.add(source.kind().translationKey());
            keys.add(source.set().translationKey());
            keys.add(source.set().loreKey());
            for (Design design : source.designs()) {
                keys.add(design.translationKey());
                keys.add(design.loreKey());
            }
            WeaponArt art = GeneratedArts.forDesign(source.weapon().id()).orElseThrow();
            keys.add(art.translationKey());
            keys.add(art.descriptionKey());
            for (String path : List.of("textures/item/named/" + source.weapon().id() + ".png",
                    "models/item/named/" + source.weapon().id() + ".json",
                    "textures/models/armor/er_set_" + source.set().id() + "_layer_1.png",
                    "textures/models/armor/er_set_" + source.set().id() + "_layer_2.png",
                    "textures/item/trinket/" + source.accessory().id() + ".png",
                    "models/item/trinket/" + source.accessory().id() + ".json",
                    "textures/item/trinket/" + source.charm().id() + ".png",
                    "models/item/trinket/" + source.charm().id() + ".json")) {
                assertTrue("missing " + path, Files.isRegularFile(ASSETS.resolve(path)));
            }
            for (ArmorPiece piece : source.set().pieces()) {
                assertTrue(piece.id(), Files.isRegularFile(ASSETS.resolve("textures/item/named/" + piece.id()
                        + ".png")));
            }
        }
        for (TrinketKind kind : TrinketKind.values()) {
            keys.add(kind.translationKey());
        }
        for (EquipmentBase base : EquipmentBase.trinkets()) {
            keys.add("item.eternalrealm." + base.itemPath());
            keys.add(base.nounKey());
            String model = Files.readString(ASSETS.resolve("models/item/" + base.itemPath() + ".json"));
            for (TrinketDesign trinket : Relics.trinkets()) {
                if (trinket.base().equals(base)) {
                    assertTrue(base.itemPath() + " lacks the override of " + trinket.id(),
                            model.contains("\"custom_model_data\": " + trinket.model()));
                }
            }
        }
        keys.addAll(EquipmentTooltip.KEYS);
        for (String key : keys) {
            assertTrue("ja " + key, ja.containsKey(key));
            assertTrue("en " + key, en.containsKey(key));
        }
    }

    private static EquipmentData legendary(Design design) {
        return EquipmentGenerator.roll(design, ItemRarity.LEGENDARY, new SplittableRandom(design.model()));
    }

    static Map<String, String> lang(String name) throws IOException {
        JsonObject json = JsonParser.parseString(Files.readString(ASSETS.resolve("lang/" + name + ".json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
        Map<String, String> out = new HashMap<>();
        json.entrySet().forEach(entry -> out.put(entry.getKey(), entry.getValue().getAsString()));
        return out;
    }
}
