package com.eternalrealm.job;

import com.eternalrealm.character.CharacterRules;
import com.eternalrealm.combat.DamageType;
import com.eternalrealm.combat.HitFrame;
import com.eternalrealm.combat.HitShape;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The skill catalogue as a whole: the shape of every tree, its prerequisites, reachability within the level cap, the
 * point budget, variety of the skills, the priest's attack magic, translations, icons and visuals.
 */
public class SkillCatalogTest {
    private static final Path RESOURCES = Paths.get("src", "main", "resources");
    private static final CharacterRules RULES = CharacterRules.defaults();
    /** Visual kinds the platform layer draws ({@code SkillVisuals} and the named effects of the first skills). */
    private static final Set<String> VISUALS = Set.of("cast", "nova", "pillar", "aura", "shield", "smite", "quake",
            "storm", "vortex", "mist", "blink", "burst", "ray", "rain", "sigil", "impact", "roar", "slash", "spin",
            "thrust", "breath", "chains", "lightning", "heal", "blessing", "sanctuary", "war_cry", "iron_wall", "smoke",
            "shadow", "flame_blade", "judgment", "slam_small", "judgment_charge", "meteor_cast", "arrow_rain",
            "blade_storm", "rift", "arcane_slash");

    @Test
    public void everyTreeFillsItsThreeBranchesOfSixTiers() {
        for (JobDefinition job : Jobs.selectable()) {
            Set<Integer> cells = new HashSet<>();
            for (SkillDefinition skill : job.skills()) {
                assertTrue(cells.add(skill.gridColumn() * 10 + skill.tier()));
            }
            assertEquals(54, cells.size());
            Map<SkillKind, Integer> kinds = new HashMap<>();
            for (SkillDefinition skill : job.skills()) {
                kinds.merge(skill.kind(), 1, Integer::sum);
            }
            assertTrue(job.type() + " actives " + kinds, kinds.getOrDefault(SkillKind.ACTIVE, 0) >= 28);
            assertTrue(job.type() + " passives " + kinds, kinds.getOrDefault(SkillKind.PASSIVE, 0) >= 14);
            assertTrue(job.type() + " ultimates " + kinds, kinds.getOrDefault(SkillKind.ULTIMATE, 0) >= 5);
            for (int branch = 0; branch < 3; branch++) {
                int b = branch;
                assertTrue(job.type() + " branch " + branch + " needs an ultimate",
                        job.skills().stream().anyMatch(s -> s.branch() == b && s.kind() == SkillKind.ULTIMATE));
            }
        }
    }

    @Test
    public void prerequisitesAreEarlierNodesOfTheSameJobAndBranchMostly() {
        int crossBranch = 0;
        for (SkillDefinition skill : Jobs.allSkills().values()) {
            for (Prerequisite prerequisite : skill.prerequisites()) {
                SkillDefinition parent = Jobs.skill(prerequisite.skillId()).orElseThrow();
                assertEquals(skill.id(), skill.job(), parent.job());
                assertTrue(skill.id() + " after " + parent.id(), parent.tier() < skill.tier());
                assertTrue(skill.id(), parent.requiredLevel() <= skill.requiredLevel());
                assertTrue(skill.id(), prerequisite.level() <= parent.maxLevel());
                if (parent.branch() != skill.branch()) {
                    crossBranch++;
                }
            }
            if (!skill.isInnate() && skill.tier() > 1) {
                assertFalse(skill.id() + " needs a prerequisite", skill.prerequisites().isEmpty());
            }
            assertEquals(skill.id(), Tree.TIER_LEVELS[skill.tier() - 1], skill.requiredLevel());
        }
        assertTrue("a few nodes join two branches", crossBranch >= 1 && crossBranch <= 12);
    }

    @Test
    public void requiredLevelsUseTheTierGates() {
        assertEquals(List.of(1, 8, 18, 24, 30, 40), java.util.Arrays.stream(Tree.TIER_LEVELS).boxed().toList());
        for (SkillDefinition skill : Jobs.allSkills().values()) {
            assertTrue(skill.requiredLevel() <= RULES.levelCap());
        }
    }

    /** With unlimited points at the level cap, learning in any order eventually learns every node to its maximum. */
    @Test
    public void everyNodeIsReachableWithinTheLevelCap() {
        for (JobDefinition job : Jobs.selectable()) {
            Map<String, Integer> investments = new HashMap<>();
            boolean progress = true;
            while (progress) {
                progress = false;
                for (SkillDefinition skill : job.skills()) {
                    while (SkillTree.canInvest(job.type(), RULES.levelCap(), investments, 1, skill.id())
                            == SkillTree.InvestResult.OK) {
                        investments.merge(skill.id(), 1, Integer::sum);
                        progress = true;
                    }
                }
            }
            for (SkillDefinition skill : job.skills()) {
                assertEquals(skill.id(), skill.maxLevel(), SkillTree.levelOf(skill, RULES.levelCap(), investments));
            }
            assertTrue(SkillTree.isValid(job.type(), RULES.levelCap(), investments));
            int spent = investments.values().stream().mapToInt(Integer::intValue).sum();
            assertEquals(SkillTree.pointsToMaster(job.type()), spent);
        }
    }

    /**
     * The point economy: every node can be reached with the points earned around the time its tier opens, a level-50
     * character masters a good part of a tree (more than a third) but never all of it.
     */
    @Test
    public void pointBudget() {
        for (JobDefinition job : Jobs.selectable()) {
            int master = SkillTree.pointsToMaster(job.type());
            int atCap = RULES.earnedSkillPoints(RULES.levelCap());
            assertTrue(job.type() + ": " + atCap + " of " + master, atCap < master);
            assertTrue(job.type() + ": " + atCap + " of " + master, atCap >= master * 0.35);
            for (SkillDefinition skill : job.skills()) {
                // learnable at most one level after its tier opens (level 1 has no points yet)
                int cost = unlockCost(skill, new HashMap<>());
                int level = Math.min(RULES.levelCap(), skill.requiredLevel() + 1);
                assertTrue(skill.id() + " costs " + cost, cost <= RULES.earnedSkillPoints(level));
            }
        }
        assertEquals(108, RULES.earnedSkillPoints(50));
        assertEquals(20, RULES.earnedSkillPoints(10));
        assertEquals(0, RULES.earnedSkillPoints(1));
    }

    /** Points to learn {@code skill} at level 1 with the cheapest prerequisite levels (shared prerequisites once). */
    private static int unlockCost(SkillDefinition skill, Map<String, Integer> needed) {
        need(skill.id(), 1, needed);
        int total = 0;
        for (Map.Entry<String, Integer> entry : needed.entrySet()) {
            total += Jobs.skill(entry.getKey()).orElseThrow().pointsFor(entry.getValue());
        }
        return total;
    }

    private static void need(String id, int level, Map<String, Integer> needed) {
        if (needed.getOrDefault(id, 0) >= level) {
            return;
        }
        needed.put(id, level);
        for (Prerequisite prerequisite : Jobs.skill(id).orElseThrow().prerequisites()) {
            need(prerequisite.skillId(), prerequisite.level(), needed);
        }
    }

    @Test
    public void everyActiveHasAKnownVisual() {
        for (SkillDefinition skill : Jobs.allSkills().values()) {
            if (!skill.isUsable()) {
                continue;
            }
            boolean any = false;
            for (SkillEffect effect : skill.effects()) {
                if (effect instanceof SkillEffect.Visual visual) {
                    assertTrue(skill.id() + ": unknown visual " + visual.kind(), VISUALS.contains(visual.kind()));
                    any = true;
                }
            }
            assertTrue(skill.id() + " has no visual", any);
        }
    }

    /** No two actives of a job are the same move: their frames or effects always differ. */
    @Test
    public void activesAreDistinct() {
        for (JobDefinition job : Jobs.selectable()) {
            Set<String> signatures = new HashSet<>();
            Set<String> mechanics = new HashSet<>();
            for (SkillDefinition skill : job.skills()) {
                if (!skill.isUsable()) {
                    continue;
                }
                String signature = skill.action().frames() + "|" + skill.effects().stream()
                        .filter(effect -> !(effect instanceof SkillEffect.Visual)).toList();
                assertTrue(skill.id() + " duplicates another skill", signatures.add(signature));
                for (HitFrame frame : skill.action().frames()) {
                    if (frame.isProjectile()) {
                        mechanics.add(frame.projectile().fromAbove() ? "falling"
                                : frame.projectile().explosionRadius() > 0 ? "explosion"
                                : frame.projectile().count() > 1 ? "fan"
                                : frame.projectile().pierce() > 0 ? "pierce" : "projectile");
                    } else if (frame.damaging()) {
                        mechanics.add(frame.shape().offsetForward() > 0 ? "ahead" : frame.shape().kind().name());
                    }
                }
                for (SkillEffect effect : skill.effects()) {
                    mechanics.add(effect.getClass().getSimpleName());
                }
            }
            assertTrue(job.type() + " mechanics " + mechanics, mechanics.size() >= 12);
        }
    }

    /** The priest's offensive branch: holy attack magic distinct from the mage's elements. */
    @Test
    public void priestHasAttackMagic() {
        List<SkillDefinition> judgment = Jobs.get(JobType.PRIEST).skills().stream()
                .filter(skill -> skill.branch() == 2 && skill.isUsable() && skill.hitCount() > 0).toList();
        assertTrue("damaging judgment skills: " + judgment.size(), judgment.size() >= 9);
        assertTrue(judgment.stream().allMatch(skill -> skill.damageType() == DamageType.HOLY));
        assertTrue("smite from above", has(judgment, frame -> frame.isProjectile() && frame.projectile().fromAbove()));
        assertTrue("light ray", has(judgment, frame -> frame.damaging() && !frame.isProjectile()
                && frame.shape().kind() == HitShape.Kind.LINE));
        assertTrue("radiant burst", has(judgment, frame -> frame.damaging() && !frame.isProjectile()
                && frame.shape().kind() == HitShape.Kind.CIRCLE && frame.shape().offsetForward() == 0));
        assertTrue("piercing lance", has(judgment, frame -> frame.isProjectile() && frame.projectile().pierce() >= 8));
        assertTrue("judgment ultimate", judgment.stream().anyMatch(skill -> skill.kind() == SkillKind.ULTIMATE
                && skill.totalMultiplier() >= 5.0));
        assertTrue("the healing branches stay", Jobs.get(JobType.PRIEST).skills().stream()
                .filter(skill -> skill.branch() == 0).filter(SkillDefinition::isUsable)
                .filter(skill -> skill.effects().stream().anyMatch(effect -> effect instanceof SkillEffect.Heal
                        || effect instanceof SkillEffect.Zone)).count() >= 6);
    }

    private static boolean has(List<SkillDefinition> skills, java.util.function.Predicate<HitFrame> test) {
        return skills.stream().anyMatch(skill -> skill.action().frames().stream().anyMatch(test));
    }

    @Test
    public void everySkillIsTranslatedInJapaneseAndEnglish() throws IOException {
        Set<String> english = keys(RESOURCES.resolve("assets/eternalrealm/lang/en_us.json"));
        Set<String> japanese = keys(RESOURCES.resolve("assets/eternalrealm/lang/ja_jp.json"));
        List<String> missing = new ArrayList<>();
        List<String> wanted = new ArrayList<>();
        for (SkillDefinition skill : Jobs.allSkills().values()) {
            wanted.add(skill.translationKey());
            wanted.add(skill.descriptionKey());
        }
        for (JobDefinition job : Jobs.selectable()) {
            for (int branch = 0; branch < 3; branch++) {
                wanted.add(job.branchKey(branch));
            }
        }
        for (SkillKind kind : SkillKind.values()) {
            wanted.add(kind.translationKey());
        }
        for (SkillEffect.BuffKind kind : SkillEffect.BuffKind.values()) {
            wanted.add(kind.translationKey());
        }
        for (SkillEffect.AfflictionKind kind : SkillEffect.AfflictionKind.values()) {
            wanted.add(kind.translationKey());
        }
        for (SkillTree.InvestResult result : SkillTree.InvestResult.values()) {
            wanted.add(result.translationKey());
        }
        for (SkillTree.LoadoutResult result : SkillTree.LoadoutResult.values()) {
            wanted.add(result.translationKey());
        }
        for (String key : wanted) {
            if (!english.contains(key) || !japanese.contains(key)) {
                missing.add(key);
            }
        }
        if (!missing.isEmpty()) {
            fail(missing.size() + " missing translations, e.g. " + missing.subList(0, Math.min(10, missing.size())));
        }
    }

    @Test
    public void everySkillHasASixteenPixelIcon() throws IOException {
        for (SkillDefinition skill : Jobs.allSkills().values()) {
            Path icon = RESOURCES.resolve("assets/eternalrealm/textures/gui/skill/" + skill.id() + ".png");
            assertTrue("missing icon " + icon, Files.isRegularFile(icon));
            try (InputStream in = Files.newInputStream(icon)) {
                byte[] header = in.readNBytes(24);
                int width = ((header[16] & 0xFF) << 24) | ((header[17] & 0xFF) << 16) | ((header[18] & 0xFF) << 8)
                        | (header[19] & 0xFF);
                int height = ((header[20] & 0xFF) << 24) | ((header[21] & 0xFF) << 16) | ((header[22] & 0xFF) << 8)
                        | (header[23] & 0xFF);
                assertEquals(icon.toString(), 16, width);
                assertEquals(icon.toString(), 16, height);
            }
        }
    }

    @Test
    public void skillTextsTakeTheSameArgumentsInBothLanguages() throws IOException {
        JsonObject english = JsonParser.parseString(Files.readString(
                RESOURCES.resolve("assets/eternalrealm/lang/en_us.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject japanese = JsonParser.parseString(Files.readString(
                RESOURCES.resolve("assets/eternalrealm/lang/ja_jp.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        List<String> prefixes = List.of("skill.eternalrealm.", "skill_info.eternalrealm.", "skill_buff.eternalrealm.",
                "skill_affliction.eternalrealm.", "skill_branch.eternalrealm.", "skill_kind.eternalrealm.",
                "message.eternalrealm.skill.", "message.eternalrealm.loadout.", "screen.eternalrealm.skill.");
        int checked = 0;
        for (String key : english.keySet()) {
            if (prefixes.stream().noneMatch(key::startsWith) || !japanese.has(key)) {
                continue;
            }
            assertEquals(key, arguments(english.get(key).getAsString()), arguments(japanese.get(key).getAsString()));
            checked++;
        }
        assertTrue("checked " + checked, checked > 700);
    }

    /** The argument numbers a translation uses ({@code %s} counts up, {@code %2$s} names one; {@code %%} is text). */
    private static List<Integer> arguments(String text) {
        List<Integer> found = new ArrayList<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("%(?:(\\d+)\\$)?([A-Za-z%])").matcher(text);
        int plain = 0;
        while (matcher.find()) {
            if (matcher.group(2).equals("%")) {
                continue;
            }
            assertEquals("only %s is used: " + text, "s", matcher.group(2));
            found.add(matcher.group(1) != null ? Integer.parseInt(matcher.group(1)) : ++plain);
        }
        found.sort(null);
        return found;
    }

    private static Set<String> keys(Path file) throws IOException {
        JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        return json.keySet();
    }
}
