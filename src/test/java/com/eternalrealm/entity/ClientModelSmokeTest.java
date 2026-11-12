package com.eternalrealm.entity;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Runs the client model pipeline of every rig outside the game (the CI GameTest server is headless and never loads
 * client classes): bakes the generated body and shield layers, builds the real EnemyModel/BossModel/NpcModel/HeroModel,
 * applies every variant look and evaluates every animation with vanilla KeyframeAnimations at several times.
 *
 * <p>Uses reflection only, so it compiles without Minecraft; it is skipped where the client classes are absent
 * (tools/offline-test.sh) and runs in the Gradle build, where Minecraft is on the test classpath.</p>
 */
public class ClientModelSmokeTest {
    private static final String GEN = "com.eternalrealm.client.entity.model.gen.";
    /** EnemyAnim / BossAnimation slot counts (those classes are not part of the offline build). */
    private static final int ENEMY_SLOTS = 8;
    private static final int BOSS_SLOTS = 11;
    /** NpcModel slots: idle, walk, work, talk. */
    private static final int NPC_SLOTS = 4;
    /** HeroModel slots (HeroBodies.SLOTS): idle, walk, run, ready, talk, two attacks, two skills, ultimate, down, join. */
    private static final int HERO_SLOTS = 12;
    private static final String[][] RIGS = {
            {"HUMANOID", "HumanoidEnemy", "enemy"}, {"BEAST", "BeastEnemy", "enemy"}, {"GOLEM", "GolemEnemy", "enemy"},
            {"FLYER", "FlyerEnemy", "enemy"}, {"KING", "KingBoss", "boss"}, {"TREANT", "TreantBoss", "boss"},
            {"COLOSSUS", "ColossusBoss", "boss"}, {"QUEEN", "QueenBoss", "boss"}, {"DRAGON", "DragonBoss", "boss"},
            {"NPC", "Npc", "npc"}, {"HERO", "Hero", "hero"}};

    private static Class<?> load(String name) {
        try {
            return Class.forName(name, true, ClientModelSmokeTest.class.getClassLoader());
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            return null;
        }
    }

    @Test
    public void everyRigBakesAndAnimates() throws Exception {
        Class<?> rigsClass = load("com.eternalrealm.client.entity.ERRigs");
        if (rigsClass == null || load("net.minecraft.client.model.geom.ModelPart") == null) {
            return; // offline build without client classes
        }
        Class<?> modelPart = load("net.minecraft.client.model.geom.ModelPart");
        Class<?> layerDefinition = load("net.minecraft.client.model.geom.builders.LayerDefinition");
        Class<?> rigTables = load("com.eternalrealm.client.entity.model.RigTables");
        Class<?> rigModel = load("com.eternalrealm.client.entity.model.RigModel");
        Class<?> hierarchical = load("net.minecraft.client.model.HierarchicalModel");
        Class<?> animationDefinition = load("net.minecraft.client.animation.AnimationDefinition");
        Class<?> keyframeAnimations = load("net.minecraft.client.animation.KeyframeAnimations");
        Class<?> vector3f = load("org.joml.Vector3f");
        Method bakeRoot = layerDefinition.getMethod("bakeRoot");
        Method prepare = rigModel.getDeclaredMethod("prepare", String.class);
        prepare.setAccessible(true);
        Method animation = rigModel.getDeclaredMethod("animation", int.class, int.class);
        animation.setAccessible(true);
        Method animate = keyframeAnimations.getMethod("animate", hierarchical, animationDefinition, long.class, float.class, vector3f);
        Method length = animationDefinition.getMethod("lengthInSeconds");
        Method bones = animationDefinition.getMethod("boneAnimations");
        Method descendant = hierarchical.getMethod("getAnyDescendantWithName", String.class);
        Method root = hierarchical.getMethod("root");
        Method allParts = modelPart.getMethod("getAllParts");
        Method variants = rigTables.getMethod("variants");
        Field xRot = modelPart.getField("xRot");
        Field y = modelPart.getField("y");
        Field yScale = modelPart.getField("yScale");
        List<String> problems = new ArrayList<>();
        int evaluated = 0;
        for (String[] rig : RIGS) {
            Object tables = rigsClass.getField(rig[0]).get(null);
            Class<?> geometry = load(GEN + rig[1] + "Geometry");
            Method create = geometry.getMethod("create", float.class);
            Object body = bakeRoot.invoke(create.invoke(null, 0.0F));
            bakeRoot.invoke(create.invoke(null, 0.9F));
            String modelName = switch (rig[2]) {
                case "enemy" -> "EnemyModel";
                case "npc" -> "NpcModel";
                case "hero" -> "HeroModel";
                default -> "BossModel";
            };
            Class<?> modelClass = load("com.eternalrealm.client.entity.model." + modelName);
            Object model;
            if (rig[2].equals("hero")) {
                Constructor<?> constructor = modelClass.getConstructor(modelPart, rigTables);
                model = constructor.newInstance(body, tables);
            } else {
                Constructor<?> constructor = modelClass.getConstructor(modelPart, rigTables, float.class);
                model = constructor.newInstance(body, tables, 1.5F);
            }
            int slots = switch (rig[2]) {
                case "enemy" -> ENEMY_SLOTS;
                case "npc" -> NPC_SLOTS;
                case "hero" -> HERO_SLOTS;
                default -> BOSS_SLOTS;
            };
            for (String variant : (String[]) variants.invoke(tables)) {
                for (int slot = 0; slot < slots; slot++) {
                    int index = (int) prepare.invoke(model, variant);
                    Object definition = animation.invoke(model, index, slot);
                    if (definition == null) {
                        problems.add(rig[0] + "/" + variant + " slot " + slot + ": no animation");
                        continue;
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, ?> boneMap = (Map<String, ?>) bones.invoke(definition);
                    for (String bone : boneMap.keySet()) {
                        if (!((Optional<?>) descendant.invoke(model, bone)).isPresent()) {
                            problems.add(rig[0] + "/" + variant + " slot " + slot + ": unknown bone " + bone);
                        }
                    }
                    float seconds = (float) length.invoke(definition);
                    for (float fraction : new float[]{0.0F, 0.33F, 0.5F, 0.8F, 1.0F}) {
                        prepare.invoke(model, variant);
                        long millis = (long) (seconds * fraction * 1000.0F);
                        animate.invoke(null, model, definition, millis, 1.0F, vector3f.getConstructor().newInstance());
                        evaluated++;
                        try (Stream<?> parts = (Stream<?>) allParts.invoke(root.invoke(model))) {
                            for (Object part : parts.toList()) {
                                float a = xRot.getFloat(part);
                                float b = y.getFloat(part);
                                float c = yScale.getFloat(part);
                                if (!Float.isFinite(a) || !Float.isFinite(b) || !Float.isFinite(c)) {
                                    problems.add(rig[0] + "/" + variant + " slot " + slot + ": non-finite pose");
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!problems.isEmpty()) {
            fail(String.join("\n", problems.subList(0, Math.min(40, problems.size()))));
        }
        assertTrue("too few evaluations: " + evaluated, evaluated > 1000);
        assertEquals("all rigs checked", 11, RIGS.length);
    }
}
