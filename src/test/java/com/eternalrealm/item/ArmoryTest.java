package com.eternalrealm.item;

import com.eternalrealm.boss.BossDefinition;
import com.eternalrealm.boss.Bosses;
import com.eternalrealm.combat.ActionDefinition;
import com.eternalrealm.combat.HitFrame;
import com.eternalrealm.combat.WeaponProfile;
import com.eternalrealm.combat.WeaponProfiles;
import com.eternalrealm.combat.WeaponType;
import com.eternalrealm.core.data.CorruptDataException;
import com.eternalrealm.core.data.IncompatibleDataException;
import com.eternalrealm.dungeon.DungeonDefinition;
import com.eternalrealm.dungeon.Dungeons;
import com.eternalrealm.job.PassiveTotals;
import com.eternalrealm.job.SkillEffect;
import com.eternalrealm.monster.Monsters;
import com.eternalrealm.world.field.FieldThreats;
import com.google.gson.JsonObject;
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
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** The named designs of the armory: catalogue shape, stable ids, drop hooks, recipes, stats, profiles and arts. */
public class ArmoryTest {
    /** Level bands of the fields (docs/modules/armory.md). */
    private static final int[][] BANDS = {
            {5, 15}, {10, 20}, {15, 25}, {20, 30}, {30, 40}, {40, 50}, {50, 50}, {10, 20}, {15, 25}, {20, 30}};

    // ------------------------------------------------------------------ catalogue

    @Test
    public void everyFieldHasThreeNamedWeaponsOfEveryTypeInItsLevelBand() {
        Map<String, Integer> count = new HashMap<>();
        int field = 0;
        for (WeaponDesign weapon : Armory.weapons()) {
            if (weapon.isBossWeapon()) {
                continue;
            }
            field++;
            count.merge(weapon.field() + "/" + weapon.type(), 1, Integer::sum);
            int[] band = BANDS[weapon.field() - 1];
            assertTrue(weapon.id() + " level " + weapon.level(), weapon.level() >= band[0] && weapon.level() <= band[1]);
            assertEquals(weapon.id(), ItemTier.highestFor(weapon.level()), weapon.base().tier());
            assertTrue(weapon.id(), weapon.level() >= weapon.base().requiredLevel());
        }
        assertEquals(180, field);
        for (int f = 1; f <= 10; f++) {
            for (WeaponType type : WeaponType.values()) {
                assertEquals("field " + f + " " + type, Integer.valueOf(3), count.get(f + "/" + type));
            }
        }
        for (WeaponType type : WeaponType.values()) {
            long perType = Armory.weapons().stream().filter(w -> !w.isBossWeapon() && w.type() == type).count();
            assertEquals(type.toString(), 30, perType);
        }
    }

    @Test
    public void thirtyArmorSetsOfFourPiecesInTheFieldBands() {
        assertEquals(30, Armory.sets().size());
        assertEquals(120, Armory.armorPieces().size());
        Map<Integer, Integer> perField = new HashMap<>();
        Map<ArmorStyle, Integer> perStyle = new EnumMap<>(ArmorStyle.class);
        for (ArmorSet set : Armory.sets()) {
            perField.merge(set.field(), 1, Integer::sum);
            perStyle.merge(set.style(), 1, Integer::sum);
            int[] band = BANDS[set.field() - 1];
            assertTrue(set.id(), set.level() >= band[0] && set.level() <= band[1]);
            assertEquals(4, set.pieces().size());
            for (ArmorPiece piece : set.pieces()) {
                assertEquals(piece.armorSlot(), piece.base().slot());
                assertEquals(set.tier(), piece.base().tier());
                assertEquals(set.id() + "_" + piece.armorSlot().id(), piece.id());
            }
        }
        for (int f = 1; f <= 10; f++) {
            assertEquals("sets of field " + f, Integer.valueOf(3), perField.get(f));
        }
        for (ArmorStyle style : ArmorStyle.values()) {
            assertEquals(style.toString(), Integer.valueOf(10), perStyle.get(style));
        }
    }

    @Test
    public void idsAndModelsAreUniqueAndApartFromTheBaseItems() {
        Set<String> ids = new HashSet<>();
        Set<Integer> models = new HashSet<>();
        for (EquipmentBase base : EquipmentBase.all()) {
            ids.add(base.itemPath());
        }
        for (Design design : Armory.designs()) {
            assertTrue("duplicate id " + design.id(), ids.add(design.id()));
            assertTrue("duplicate model " + design.model(), models.add(design.model()));
            assertTrue(design.id(), design.id().matches("[a-z0-9_]{3,40}"));
            int lo = design instanceof WeaponDesign ? Design.WEAPON_MODELS : Design.ARMOR_MODELS;
            assertTrue(design.id() + " model " + design.model(), design.model() >= lo && design.model() < lo + 1000);
            assertSame(design, Armory.byModel(design.model()).orElseThrow());
            assertEquals(design, Armory.design(design.id()).orElseThrow());
        }
        for (ArmorSet set : Armory.sets()) {
            assertTrue("set id clashes " + set.id(), ids.add(set.id()));
        }
    }

    /** Ids and model numbers are stored in item stacks: a released one never changes (src/test/resources). */
    @Test
    public void releasedIdsKeepTheirModels() throws IOException {
        Path snapshot = Paths.get("src", "test", "resources", "armory", "models.txt");
        int checked = 0;
        for (String line : Files.readAllLines(snapshot, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.trim().split(" ");
            Design design = Armory.design(parts[0]).orElse(null);
            assertNotNull("released design removed: " + parts[0], design);
            assertEquals("model of " + parts[0] + " changed", Integer.parseInt(parts[1]), design.model());
            checked++;
        }
        assertEquals(Armory.designs().size(), checked);
    }

    @Test
    public void profilesStayInTheirRanges() {
        for (WeaponDesign weapon : Armory.weapons()) {
            assertTrue(weapon.id(), weapon.attack() >= 0.9 && weapon.attack() <= 1.3);
            assertTrue(weapon.id(), weapon.crit() <= 0.1 && weapon.speed() <= 0.12);
            boolean ranged = weapon.type() == WeaponType.STAFF || weapon.type() == WeaponType.BOW;
            assertTrue(weapon.id(), ranged ? weapon.reach() == 1.0 : weapon.reach() >= 0.95 && weapon.reach() <= 1.22);
            assertEquals(weapon.id(), weapon.element() != Element.NONE, weapon.proc() > 0.0);
            for (AffixKind kind : weapon.bias().keySet()) {
                assertTrue(weapon.id() + " " + kind, AffixPool.of(EquipmentSlot.WEAPON, weapon.type()).allows(kind));
            }
        }
        // neighbours differ: no two weapons of one field and type share feel and element
        Set<String> seen = new HashSet<>();
        for (WeaponDesign weapon : Armory.weapons()) {
            String key = weapon.field() + weapon.type().id() + weapon.attack() + weapon.crit() + weapon.speed()
                    + weapon.reach() + weapon.element();
            assertTrue("same profile as a neighbour: " + weapon.id(), seen.add(key));
        }
    }

    // ------------------------------------------------------------------ drop hooks

    @Test
    public void everyLordCalamityBossAndDungeonHasItsSignature() {
        for (FieldThreats.Lord lord : FieldThreats.lords()) {
            WeaponDesign weapon = Signatures.lordWeapon(lord.id()).orElse(null);
            assertNotNull("lord " + lord.id(), weapon);
            assertEquals(lord.id(), lord.field(), weapon.field());
            assertFalse(weapon.perks().isEmpty());
        }
        assertEquals(30, FieldThreats.lords().size());
        assertEquals(FieldThreats.lords().size(), Armory.weapons().stream().filter(w -> w.has(DesignSource.LORD)).count());
        for (FieldThreats.Lord lord : FieldThreats.lords()) {
            assertEquals("exactly one signature for " + lord.id(), 1, Armory.weapons().stream()
                    .filter(w -> w.has(DesignSource.LORD) && w.origin().equals(lord.id())).count());
            if (Signatures.lordWieldsWeapon(lord.id())) {
                // archers keep their crossbows: a lord that shows its weapon never carries a bow
                assertNotEquals(lord.id(), WeaponType.BOW, Signatures.lordWeapon(lord.id()).orElseThrow().type());
            }
        }
        assertTrue(Signatures.lordWieldsWeapon("strom"));
        assertFalse(Signatures.lordWieldsWeapon("scorch_eye"));
        assertFalse(Signatures.lordWieldsWeapon("hook"));
        assertFalse(Signatures.lordWieldsWeapon("durn"));
        for (FieldThreats.Calamity calamity : FieldThreats.calamities()) {
            WeaponDesign weapon = Signatures.calamityWeapon(calamity.id()).orElse(null);
            ArmorSet set = Signatures.calamityArmor(calamity.id()).orElse(null);
            assertNotNull("calamity weapon " + calamity.id(), weapon);
            assertNotNull("calamity armor " + calamity.id(), set);
            assertEquals(calamity.field(), weapon.field());
            assertEquals(calamity.field(), set.field());
        }
        Set<String> arts = new HashSet<>();
        for (BossDefinition boss : Bosses.all()) {
            WeaponDesign weapon = Signatures.bossWeapon(boss.id()).orElse(null);
            assertNotNull("boss " + boss.id(), weapon);
            assertTrue(weapon.isBossWeapon());
            assertTrue("art used twice: " + weapon.art(), arts.add(weapon.art()));
            assertTrue(WeaponArts.byId(weapon.art()).isPresent());
        }
        assertEquals(9, arts.size());
        for (DungeonDefinition dungeon : Dungeons.all()) {
            assertFalse("dungeon " + dungeon.id(), Signatures.dungeonDesigns(dungeon.id()).isEmpty());
        }
        assertTrue(Signatures.lordWeapon("nobody").isEmpty());
        assertTrue(Signatures.bossWeapon("").isEmpty());
    }

    @Test
    public void wieldersAreRealMonstersCarryingMonsterDesigns() {
        Set<String> carried = new HashSet<>();
        for (Map.Entry<String, String> entry : Signatures.wielders().entrySet()) {
            assertTrue("unknown template " + entry.getKey(), Monsters.byId(entry.getKey()).isPresent());
            WeaponDesign weapon = Signatures.wielded(entry.getKey()).orElse(null);
            assertNotNull(entry.getValue(), weapon);
            assertTrue(weapon.id() + " is not a monster design", weapon.has(DesignSource.MONSTER));
            int field = Monsters.byId(entry.getKey()).orElseThrow().areaIds().stream().mapToInt(i -> i).min()
                    .orElseThrow();
            assertEquals(entry.getKey(), field, weapon.field());
            carried.add(weapon.id());
        }
        for (WeaponDesign weapon : Armory.weapons()) {
            if (weapon.has(DesignSource.MONSTER)) {
                assertTrue("no monster carries " + weapon.id(), carried.contains(weapon.id()));
            }
        }
        for (FieldThreats.Lord lord : FieldThreats.lords()) {
            if (Signatures.lordWieldsWeapon(lord.id())) {
                assertTrue(lord.id(), Monsters.byId(lord.template()).isPresent());
            }
        }
    }

    @Test
    public void dropPicksAndChances() {
        SplittableRandom random = new SplittableRandom(7);
        for (int field = 1; field <= 10; field++) {
            for (int i = 0; i < 40; i++) {
                int level = Armory.levelOf(field, 0) + random.nextInt(8);
                Design design = ArmoryDrops.fieldDesign(field, level, random).orElseThrow();
                assertTrue(design.sources().contains(DesignSource.FIELD));
                assertEquals(field, design.field());
                assertTrue(design.id(), design.level() <= Math.max(level + ArmoryDrops.FIELD_LEVEL_MARGIN,
                        Armory.levelOf(field, 0)));
            }
        }
        assertTrue(ArmoryDrops.fieldDesign(0, 10, random).isEmpty());
        double previousDesign = 0.0;
        double previousWeapon = 0.0;
        for (ArmoryDrops.Difficulty difficulty : ArmoryDrops.Difficulty.values()) {
            assertTrue(difficulty.designChance() > previousDesign);
            assertTrue(difficulty.bossWeaponChance() > previousWeapon);
            previousDesign = difficulty.designChance();
            previousWeapon = difficulty.bossWeaponChance();
        }
        assertEquals(ArmoryDrops.Difficulty.HARD, ArmoryDrops.Difficulty.of("hard"));
        assertEquals(ArmoryDrops.Difficulty.NORMAL, ArmoryDrops.Difficulty.of("unknown"));
        assertTrue(ArmoryDrops.lordWeaponChance(true) > ArmoryDrops.lordWeaponChance(false));
        assertTrue(ArmoryDrops.calamityPiece("meadow", random).isPresent());
        assertTrue(ArmoryDrops.dungeonDesign("d3", random).isPresent());
    }

    // ------------------------------------------------------------------ recipes

    @Test
    public void everyCraftableDesignHasAValidRecipeFromKnownMaterials() {
        int recipes = 0;
        for (Design design : Armory.designs()) {
            if (!design.sources().contains(DesignSource.CRAFT)) {
                assertTrue(design.id(), Recipes.forDesign(design).isEmpty());
                continue;
            }
            Recipe recipe = Recipes.forDesign(design).orElseThrow(() -> new AssertionError("no recipe " + design.id()));
            recipes++;
            assertEquals(design.level(), recipe.requiredLevel());
            for (String material : recipe.ingredients().keySet()) {
                assertTrue(design.id() + ": unknown material " + material, Materials.isKnown(material));
                assertEquals(design.id() + ": " + material, java.util.OptionalInt.of(design.field()),
                        Materials.areaOf(material));
            }
            Recipe plain = Recipes.forOutput(design.base()).orElseThrow();
            assertTrue(design.id() + " is cheaper than its plain base", recipe.goldCost() >= plain.goldCost());
            Crafting.Check check = Crafting.check(recipe, recipe.ingredients(), recipe.goldCost(), design.level());
            assertTrue(design.id(), check.isOk());
            EquipmentData made = Crafting.craft(recipe, new SplittableRandom(recipes));
            assertEquals(design, made.design());
            assertEquals(design.level(), made.requiredLevel());
        }
        assertEquals(Recipes.designRecipes().size(), recipes);
        assertEquals(70, Recipes.catalog().size());
        int byField = 0;
        for (int field = 1; field <= 10; field++) {
            byField += Recipes.forField(field).size();
        }
        assertEquals(recipes, byField);
        // boss weapons are forged from the field's boss souls (the soul forge)
        Recipe forge = Recipes.forDesign(Signatures.bossWeapon("b6").orElseThrow()).orElseThrow();
        assertEquals(Integer.valueOf(12), forge.ingredients().get("eternalrealm:ancient_soul"));
    }

    // ------------------------------------------------------------------ data and codec

    @Test
    public void designsRoundTripAndUnknownDesignsStayPlainPieces() throws CorruptDataException,
            IncompatibleDataException {
        SplittableRandom random = new SplittableRandom(3);
        for (Design design : Armory.designs()) {
            EquipmentData data = EquipmentGenerator.roll(design, ItemRarity.LEGENDARY, random)
                    .withEnhancement(10).withUniqueId(UUID.randomUUID()).withBoundTo(UUID.randomUUID());
            String json = EquipmentCodec.encode(data);
            assertTrue(json.length() < EquipmentCodec.MAX_ENCODED_LENGTH);
            assertEquals(data, EquipmentCodec.decode(json));
            assertEquals(design, data.withEnhancement(3).withAffixes(data.affixes()).design());
        }
        WeaponDesign weapon = Armory.weapon("garms_maw").orElseThrow();
        EquipmentData data = EquipmentGenerator.roll(weapon, ItemRarity.RARE, random);
        JsonObject json = EquipmentCodec.toJson(data);
        json.addProperty("design", "a_design_from_the_future");
        EquipmentData unknown = EquipmentCodec.fromJson(json);
        assertNull(unknown.design());
        assertEquals(data.base(), unknown.base());
        json.addProperty("design", "meadowlark_blade"); // made on another base
        assertNull(EquipmentCodec.fromJson(json).design());
        json.remove("design");
        assertNull(EquipmentCodec.fromJson(json).design());
        try {
            new EquipmentData(EquipmentSlot.WEAPON, WeaponType.SPEAR, ItemTier.IRON, ItemRarity.COMMON, 0, List.of(),
                    null, null, weapon);
            throw new AssertionError("a design on another base was accepted");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void designLevelIsTheRequirement() {
        WeaponDesign weapon = Armory.weapon("galewild_saber").orElseThrow();
        EquipmentData data = EquipmentData.of(weapon, ItemRarity.COMMON, List.of());
        assertEquals(15, data.requiredLevel());
        assertEquals(ItemTier.SANDSTONE, data.tier());
        UUID me = UUID.randomUUID();
        assertFalse(data.canBeUsedBy(14, me));
        assertTrue(data.canBeUsedBy(15, me));
        assertEquals(0.0, Gear.weaponBonusAttack(data, 14, me), 0.0);
        assertTrue(Gear.weaponBonusAttack(data, 15, me) > 0.0);
    }

    // ------------------------------------------------------------------ stats

    @Test
    public void designStatsFollowTheirProfile() {
        assertEquals(0.0, Armory.weaponBonusAt(1), 1e-9);
        assertEquals(0.4, Armory.weaponBonusAt(30), 1e-9);
        assertEquals(0.6, Armory.weaponBonusAt(44), 1e-9);
        assertEquals(ItemTier.ABYSS.setDefense(), Armory.setDefenseAt(44), 1e-9);
        for (int level = 2; level <= 50; level++) {
            assertTrue(Armory.weaponBonusAt(level) > Armory.weaponBonusAt(level - 1));
            assertTrue(Armory.setDefenseAt(level) > Armory.setDefenseAt(level - 1));
        }
        for (WeaponDesign weapon : Armory.weapons()) {
            EquipmentData data = EquipmentData.of(weapon, ItemRarity.RARE, rareAffixes(weapon));
            double typeAttack = WeaponProfiles.of(weapon.type()).baseAttack();
            double expected = typeAttack * (1.0 + Armory.weaponBonusAt(weapon.level())) * weapon.attack()
                    * ItemRarity.RARE.baseMultiplier();
            assertEquals(weapon.id(), expected, EquipmentStats.baseAttack(data), 1e-9);
            EquipmentStats stats = EquipmentStats.of(data);
            assertEquals(expected - typeAttack, stats.bonusAttack(), 1e-9);
            assertTrue(weapon.id(), stats.modifiers().critChance() >= weapon.crit());
            assertEquals(weapon.speed() + weapon.bonus().attackSpeed(), stats.modifiers().attackSpeed(), 1e-9);
        }
        // a boss weapon beats the plain piece of its own tier
        WeaponDesign king = Signatures.bossWeapon("final").orElseThrow();
        assertTrue(EquipmentStats.baseAttack(EquipmentData.of(king, ItemRarity.COMMON, List.of()))
                > EquipmentStats.baseAttack(EquipmentData.of(king.base(), ItemRarity.COMMON, List.of())));
        // heavy plate defends more than robes of the same level
        ArmorSet plate = Armory.set("endless_night_plate").orElseThrow();
        ArmorSet robe = Armory.set("oblivion_vestment").orElseThrow();
        assertTrue(defense(plate) > defense(robe));
    }

    private static List<Affix> rareAffixes(WeaponDesign weapon) {
        return EquipmentGenerator.roll(weapon, ItemRarity.RARE, new SplittableRandom(weapon.model())).affixes();
    }

    private static double defense(ArmorSet set) {
        double total = 0.0;
        for (ArmorPiece piece : set.pieces()) {
            total += EquipmentStats.baseDefense(EquipmentData.of(piece, ItemRarity.COMMON, List.of()));
        }
        return total;
    }

    @Test
    public void setBonusesNeedTwoAndFourPieces() {
        ArmorSet set = Armory.set("glacier_warden").orElseThrow();
        List<EquipmentData> worn = new ArrayList<>();
        UUID me = UUID.randomUUID();
        GearBonus none = Gear.sum(worn, 50, me);
        worn.add(EquipmentData.of(set.piece(EquipmentSlot.HEAD), ItemRarity.COMMON, List.of()));
        assertTrue(Gear.setBonuses(worn).isEmpty());
        GearBonus one = Gear.sum(worn, 50, me);
        worn.add(EquipmentData.of(set.piece(EquipmentSlot.CHEST), ItemRarity.COMMON, List.of()));
        assertEquals(List.of(set.two()), Gear.setBonuses(worn));
        GearBonus two = Gear.sum(worn, 50, me);
        double pieceDefense = EquipmentStats.baseDefense(worn.get(1));
        assertEquals(one.modifiers().defenseFlat() + pieceDefense + set.two().modifiers().defenseFlat(),
                two.modifiers().defenseFlat(), 1e-6);
        worn.add(EquipmentData.of(set.piece(EquipmentSlot.LEGS), ItemRarity.COMMON, List.of()));
        worn.add(EquipmentData.of(set.piece(EquipmentSlot.FEET), ItemRarity.COMMON, List.of()));
        assertEquals(List.of(set.two(), set.four()), Gear.setBonuses(worn));
        assertTrue(Gear.sum(worn, 50, me).modifiers().guardBonus() > none.modifiers().guardBonus());
        // the four-piece perks are resolved by the server; below the level nothing counts
        PassiveTotals perks = Gear.perks(worn, 50, me);
        assertTrue(perks.lastStandReduction() > 0.0);
        assertFalse(perks.procs().isEmpty());
        assertEquals(PassiveTotals.NONE, Gear.perks(worn, 1, me));
        assertTrue(Gear.setBonuses(worn.subList(0, 1)).isEmpty());
    }

    @Test
    public void weaponPerksAndElementReachTheServerTotals() {
        UUID me = UUID.randomUUID();
        WeaponDesign weapon = Armory.weapon("garms_maw").orElseThrow();
        EquipmentData held = EquipmentData.of(weapon, ItemRarity.COMMON, List.of());
        PassiveTotals perks = Gear.perks(List.of(held), 50, me);
        assertEquals(1, perks.procs().size());
        assertEquals(SkillEffect.AfflictionKind.BLEED, perks.procs().get(0).kind());
        assertEquals(weapon.proc(), perks.procs().get(0).chance(), 1e-9);
        assertTrue(perks.lastStandDamage() > 0.0);
        EquipmentData plain = EquipmentData.of(weapon.base(), ItemRarity.COMMON, List.of());
        assertEquals(PassiveTotals.NONE, Gear.perks(List.of(plain), 50, me));
    }

    // ------------------------------------------------------------------ profiles and arts

    @Test
    public void profilesCarryReachAndBossArts() {
        for (WeaponDesign weapon : Armory.weapons()) {
            WeaponProfile profile = DesignProfiles.of(weapon);
            assertSame(profile, DesignProfiles.byDesign(weapon.id()).orElseThrow());
            WeaponProfile base = WeaponProfiles.of(weapon.type());
            assertEquals(base.type(), profile.type());
            assertEquals(base.baseAttack(), profile.baseAttack(), 0.0);
            assertEquals(base.combo().size(), profile.combo().size());
            for (int i = 0; i < base.combo().size(); i++) {
                assertEquals(base.combo().get(i).id(), profile.combo().get(i).id());
                assertEquals(base.combo().get(i).durationTicks(), profile.combo().get(i).durationTicks());
                HitFrame a = base.combo().get(i).frames().get(0);
                HitFrame b = profile.combo().get(i).frames().get(0);
                if (!a.isProjectile()) {
                    assertEquals(weapon.id(), a.shape().reach() * weapon.reach(), b.shape().reach(), 0.01);
                }
            }
            if (weapon.isBossWeapon()) {
                assertEquals(weapon.art(), profile.skill().id());
                assertTrue(profile.skill().cooldownTicks() > base.skill().cooldownTicks());
            } else {
                // every named weapon brings its own art instead of the type's skill
                assertEquals(GeneratedArts.forDesign(weapon.id()).orElseThrow().id(), profile.skill().id());
                assertNotEquals(base.skill().id(), profile.skill().id());
            }
        }
        assertTrue(DesignProfiles.byDesign("meadow_runner_head").isEmpty());
    }

    @Test
    public void artsAreShowpiecesThatAnnounceThemselves() {
        assertEquals(9, WeaponArts.all().size());
        for (WeaponArt art : WeaponArts.all()) {
            assertSame(art, WeaponArts.byId(art.id()).orElseThrow());
            ActionDefinition action = art.action();
            boolean ownLook = false;
            boolean telegraphed = false;
            for (SkillEffect effect : art.effects()) {
                if (effect instanceof SkillEffect.Visual visual && visual.frame() == SkillEffect.ON_START
                        && visual.kind().equals(art.id())) {
                    ownLook = true;
                }
                if (effect instanceof SkillEffect.Mark || effect instanceof SkillEffect.Zone zone
                        && (zone.delayTicks() >= 10 || zone.look() != null)) {
                    telegraphed = true;
                }
            }
            assertTrue(art.id() + " has no look of its own", ownLook);
            assertTrue(art.id() + " is not telegraphed (marker or delayed area)", telegraphed
                    || art.id().equals("art_shadow_legion"));
            double zones = 0.0;
            for (SkillEffect effect : art.effects()) {
                if (effect instanceof SkillEffect.Zone zone) {
                    zones += zone.damageMultiplier() * zone.pulses();
                }
            }
            assertTrue(art.id() + " hits too softly", art.frameMultiplier() + zones >= 3.0);
            assertTrue(art.id(), action.cooldownTicks() >= 300);
            assertTrue(art.soundId().startsWith("eternalrealm:art."));
            long users = Armory.weapons().stream().filter(w -> art.id().equals(w.art())).count();
            assertEquals(art.id(), 1, users);
        }
    }

    // ------------------------------------------------------------------ tooltip

    @Test
    public void tooltipsNameTheDesignAndShowWhatMakesItSpecial() {
        WeaponDesign weapon = Signatures.bossWeapon("b6").orElseThrow();
        EquipmentData data = EquipmentGenerator.roll(weapon, ItemRarity.EPIC, new SplittableRandom(5)).withEnhancement(4);
        TooltipLine name = EquipmentTooltip.name(data);
        assertEquals(EquipmentTooltip.DESIGN_NAME_ENHANCED, name.translationKey());
        assertEquals(new TooltipLine.Key(weapon.translationKey()), name.args().get(0));
        List<TooltipLine> lines = EquipmentTooltip.lines(data);
        Set<TooltipLine.Kind> kinds = new HashSet<>();
        for (TooltipLine line : lines) {
            kinds.add(line.kind());
        }
        assertTrue(kinds.containsAll(List.of(TooltipLine.Kind.DESIGN, TooltipLine.Kind.TRAIT, TooltipLine.Kind.PERK,
                TooltipLine.Kind.ART, TooltipLine.Kind.LORE)));
        assertTrue(lines.stream().anyMatch(line -> line.translationKey().equals(weapon.loreKey())
                || line.args().contains(new TooltipLine.Key(weapon.loreKey()))));
        ArmorPiece piece = Armory.set("cinder_dancer").orElseThrow().piece(EquipmentSlot.CHEST);
        List<TooltipLine> armor = EquipmentTooltip.lines(EquipmentData.of(piece, ItemRarity.COMMON, List.of()));
        assertEquals(3, armor.stream().filter(line -> line.kind() == TooltipLine.Kind.SET).count());
        assertEquals(EquipmentTooltip.DESIGN_NAME,
                EquipmentTooltip.name(EquipmentData.of(piece, ItemRarity.COMMON, List.of())).translationKey());
    }

    @Test
    public void rollsFollowTheDesignTendencies() {
        WeaponDesign keen = Armory.weapon("wayfarers_oath").orElseThrow();
        assertTrue(keen.bias().containsKey(AffixKind.CRIT_CHANCE));
        int withCrit = 0;
        int plainWithCrit = 0;
        SplittableRandom random = new SplittableRandom(11);
        for (int i = 0; i < 400; i++) {
            if (EquipmentGenerator.roll(keen, ItemRarity.UNCOMMON, random).affix(AffixKind.CRIT_CHANCE).isPresent()) {
                withCrit++;
            }
            if (EquipmentGenerator.roll(keen.base(), ItemRarity.UNCOMMON, random).affix(AffixKind.CRIT_CHANCE)
                    .isPresent()) {
                plainWithCrit++;
            }
        }
        assertTrue(withCrit + " vs " + plainWithCrit, withCrit > plainWithCrit);
        EquipmentData rolled = EquipmentGenerator.roll(keen, ItemRarity.LEGENDARY, random);
        EquipmentData rerolled = Reroll.reroll(rolled, random);
        assertEquals(keen, rerolled.design());
    }
}
