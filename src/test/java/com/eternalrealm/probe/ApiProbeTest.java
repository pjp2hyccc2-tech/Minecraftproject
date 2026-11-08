package com.eternalrealm.probe;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.fail;

/** TEMPORARY: dumps vanilla API signatures/sources in CI (no local Minecraft). Passes when Minecraft is absent. */
public class ApiProbeTest {
    private static boolean minecraftPresent() {
        try {
            Class.forName("net.minecraft.world.entity.Entity", false, ApiProbeTest.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String signatures(String... classNames) {
        StringBuilder out = new StringBuilder();
        for (String name : classNames) {
            try {
                Class<?> type = Class.forName(name, false, ApiProbeTest.class.getClassLoader());
                out.append("\n=== ").append(Modifier.toString(type.getModifiers())).append(' ').append(type.getName());
                if (type.getGenericSuperclass() != null) {
                    out.append(" extends ").append(type.getGenericSuperclass().getTypeName());
                }
                out.append(" implements ").append(Arrays.stream(type.getGenericInterfaces())
                        .map(java.lang.reflect.Type::getTypeName).collect(Collectors.joining(", "))).append('\n');
                for (Constructor<?> c : type.getDeclaredConstructors()) {
                    out.append("  C ").append(Modifier.toString(c.getModifiers())).append(" (")
                            .append(Arrays.stream(c.getGenericParameterTypes()).map(java.lang.reflect.Type::getTypeName)
                                    .collect(Collectors.joining(", "))).append(")\n");
                }
                for (Field f : type.getDeclaredFields()) {
                    out.append("  F ").append(Modifier.toString(f.getModifiers())).append(' ')
                            .append(f.getGenericType().getTypeName()).append(' ').append(f.getName()).append('\n');
                }
                for (Method m : type.getDeclaredMethods()) {
                    if (m.isSynthetic()) {
                        continue;
                    }
                    out.append("  M ").append(Modifier.toString(m.getModifiers())).append(' ')
                            .append(m.getGenericReturnType().getTypeName()).append(' ').append(m.getName()).append('(')
                            .append(Arrays.stream(m.getGenericParameterTypes()).map(java.lang.reflect.Type::getTypeName)
                                    .collect(Collectors.joining(", "))).append(")\n");
                }
            } catch (Throwable t) {
                out.append("\n=== ").append(name).append(" FAILED: ").append(t).append('\n');
            }
        }
        return out.toString();
    }

    private static List<Path> sourceJars() {
        List<Path> found = new ArrayList<>();
        for (Path root : List.of(Paths.get(System.getProperty("user.home"), ".gradle"), Paths.get("build"),
                Paths.get(System.getProperty("user.home"), ".m2"))) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> stream = Files.walk(root)) {
                stream.filter(p -> {
                    String n = p.getFileName().toString();
                    return n.endsWith(".jar") && (n.contains("sources") || n.contains("src"))
                            && (n.contains("forge") || n.contains("minecraft") || n.contains("client") || n.contains("joined"));
                }).forEach(found::add);
            } catch (IOException | RuntimeException e) {
                // keep what was found
            }
        }
        return found;
    }

    private static String sources(String... entries) {
        StringBuilder out = new StringBuilder();
        List<Path> jars = sourceJars();
        out.append("\nSOURCE JARS: ");
        for (Path jar : jars) {
            try {
                out.append(jar).append(" (").append(Files.size(jar)).append(")\n");
            } catch (IOException e) {
                out.append(jar).append('\n');
            }
        }
        for (String spec : entries) {
            String[] parts = spec.split("\\|");
            String path = parts[0];
            boolean done = false;
            for (Path jar : jars) {
                try (ZipFile zip = new ZipFile(jar.toFile())) {
                    ZipEntry entry = zip.getEntry(path);
                    if (entry == null) {
                        continue;
                    }
                    String text;
                    try (InputStream in = zip.getInputStream(entry)) {
                        text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    }
                    out.append("\n##### ").append(path).append(" from ").append(jar.getFileName()).append('\n');
                    String[] lines = text.split("\n", -1);
                    if (parts.length == 1) {
                        out.append(text);
                    } else {
                        for (int k = 1; k + 1 < parts.length; k += 2) {
                            String needle = parts[k];
                            int count = Integer.parseInt(parts[k + 1]);
                            for (int i = 0; i < lines.length; i++) {
                                if (lines[i].contains(needle)) {
                                    out.append("--- ").append(needle).append(" @").append(i + 1).append('\n');
                                    for (int j = i; j < Math.min(lines.length, i + count); j++) {
                                        out.append(lines[j]).append('\n');
                                    }
                                }
                            }
                        }
                    }
                    done = true;
                    break;
                } catch (IOException | RuntimeException e) {
                    out.append("\n(read error ").append(jar).append(": ").append(e).append(")\n");
                }
            }
            if (!done) {
                out.append("\n##### ").append(path).append(" NOT FOUND\n");
            }
        }
        return out.toString();
    }

    @Test
    public void probeClientModelApi() {
        if (!minecraftPresent()) {
            return;
        }
        fail("PROBE-A" + signatures(
                "net.minecraft.client.model.HierarchicalModel", "net.minecraft.client.model.EntityModel",
                "net.minecraft.client.model.Model", "net.minecraft.client.model.geom.ModelPart",
                "net.minecraft.client.model.geom.PartPose", "net.minecraft.client.model.geom.ModelLayerLocation",
                "net.minecraft.client.model.geom.builders.CubeListBuilder",
                "net.minecraft.client.model.geom.builders.PartDefinition",
                "net.minecraft.client.model.geom.builders.MeshDefinition",
                "net.minecraft.client.model.geom.builders.LayerDefinition",
                "net.minecraft.client.model.geom.builders.CubeDeformation",
                "net.minecraft.client.animation.AnimationDefinition",
                "net.minecraft.client.animation.AnimationDefinition$Builder",
                "net.minecraft.client.animation.AnimationChannel",
                "net.minecraft.client.animation.AnimationChannel$Targets",
                "net.minecraft.client.animation.AnimationChannel$Interpolations",
                "net.minecraft.client.animation.AnimationChannel$Target",
                "net.minecraft.client.animation.AnimationChannel$Interpolation",
                "net.minecraft.client.animation.Keyframe", "net.minecraft.client.animation.KeyframeAnimations"));
    }

    @Test
    public void probeClientRenderApi() {
        if (!minecraftPresent()) {
            return;
        }
        fail("PROBE-B" + signatures(
                "net.minecraft.client.renderer.entity.MobRenderer",
                "net.minecraft.client.renderer.entity.LivingEntityRenderer",
                "net.minecraft.client.renderer.entity.EntityRenderer",
                "net.minecraft.client.renderer.entity.EntityRendererProvider$Context",
                "net.minecraft.client.renderer.entity.ThrownItemRenderer",
                "net.minecraft.client.renderer.entity.layers.RenderLayer",
                "net.minecraft.client.renderer.entity.layers.EyesLayer",
                "net.minecraft.client.renderer.entity.layers.EnergySwirlLayer",
                "net.minecraft.client.renderer.entity.RenderLayerParent",
                "net.minecraftforge.client.event.EntityRenderersEvent$RegisterLayerDefinitions",
                "net.minecraftforge.client.event.EntityRenderersEvent$RegisterRenderers",
                "net.minecraft.client.renderer.entity.layers.WardenEmissiveLayer"));
    }

    @Test
    public void probeRenderTypeApi() {
        if (!minecraftPresent()) {
            return;
        }
        fail("PROBE-C" + signatures("net.minecraft.client.renderer.RenderType",
                "net.minecraft.client.renderer.texture.OverlayTexture",
                "net.minecraft.client.renderer.LightTexture"));
    }

    @Test
    public void probeCommonEntityApi() {
        if (!minecraftPresent()) {
            return;
        }
        fail("PROBE-D" + signatures(
                "net.minecraft.world.entity.AnimationState", "net.minecraft.world.entity.Entity",
                "net.minecraft.world.entity.LivingEntity", "net.minecraft.world.entity.Mob",
                "net.minecraft.world.entity.PathfinderMob", "net.minecraft.world.entity.monster.Monster",
                "net.minecraft.world.entity.monster.RangedAttackMob", "net.minecraft.world.entity.EntityType$Builder",
                "net.minecraft.world.entity.EntityDimensions"));
    }

    @Test
    public void probeAiAndMiscApi() {
        if (!minecraftPresent()) {
            return;
        }
        fail("PROBE-E" + signatures(
                "net.minecraft.world.entity.ai.goal.Goal", "net.minecraft.world.entity.ai.goal.MeleeAttackGoal",
                "net.minecraft.world.entity.ai.goal.RangedAttackGoal",
                "net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal",
                "net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal",
                "net.minecraft.world.entity.ai.goal.LookAtPlayerGoal",
                "net.minecraft.world.entity.ai.goal.RandomLookAroundGoal",
                "net.minecraft.world.entity.ai.goal.FloatGoal",
                "net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal",
                "net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal",
                "net.minecraft.world.entity.ai.control.FlyingMoveControl",
                "net.minecraft.world.entity.ai.control.MoveControl",
                "net.minecraft.world.entity.ai.navigation.FlyingPathNavigation",
                "net.minecraft.world.entity.ai.attributes.AttributeSupplier$Builder",
                "net.minecraft.world.entity.ai.attributes.Attributes",
                "net.minecraft.world.entity.projectile.ThrowableItemProjectile",
                "net.minecraft.world.entity.projectile.ThrowableProjectile",
                "net.minecraft.world.entity.projectile.Projectile",
                "net.minecraft.network.syncher.SynchedEntityData", "net.minecraft.network.syncher.EntityDataSerializers",
                "net.minecraft.gametest.framework.GameTestHelper",
                "net.minecraftforge.event.entity.EntityAttributeCreationEvent",
                "net.minecraft.world.damagesource.DamageSources"));
    }

    @Test
    public void probeVanillaSourcesClient() {
        if (!minecraftPresent()) {
            return;
        }
        fail("PROBE-F" + sources(
                "net/minecraft/client/model/HierarchicalModel.java",
                "net/minecraft/client/model/WardenModel.java",
                "net/minecraft/client/model/CamelModel.java",
                "net/minecraft/client/model/geom/ModelPart.java",
                "net/minecraft/client/animation/KeyframeAnimations.java",
                "net/minecraft/client/animation/AnimationChannel.java",
                "net/minecraft/client/renderer/entity/LivingEntityRenderer.java",
                "net/minecraft/client/renderer/entity/WardenRenderer.java",
                "net/minecraft/client/renderer/entity/layers/WardenEmissiveLayer.java",
                "net/minecraft/client/renderer/entity/layers/EyesLayer.java",
                "net/minecraft/client/renderer/entity/layers/EnergySwirlLayer.java",
                "net/minecraft/client/renderer/entity/WitherBossRenderer.java",
                "net/minecraft/client/renderer/entity/layers/WitherArmorLayer.java"));
    }

    @Test
    public void probeVanillaSourcesCommon() {
        if (!minecraftPresent()) {
            return;
        }
        fail("PROBE-G" + sources(
                "net/minecraft/world/entity/AnimationState.java",
                "net/minecraft/world/entity/LivingEntity.java|void tickDeath|12|public void handleEntityEvent|20|public EntityDimensions getDimensions|6|public float getScale|4|public void die(|40|protected boolean isImmobile|4|public boolean canBeAffected|6|public boolean isPushable|4",
                "net/minecraft/world/entity/Mob.java|public void checkDespawn|30|public boolean removeWhenFarAway|4|public boolean requiresCustomPersistence|4|protected boolean shouldDespawnInPeaceful|4|protected PathNavigation createNavigation|4|public void handleEntityEvent|12|protected Mob(|24",
                "net/minecraft/world/entity/Entity.java|protected Component getTypeName|4|public void refreshDimensions|24|public boolean causeFallDamage|4|public void onSyncedDataUpdated(EntityDataAccessor|6|public boolean isInvulnerableTo|6",
                "net/minecraft/world/entity/projectile/ThrowableItemProjectile.java",
                "net/minecraft/world/entity/projectile/Snowball.java",
                "net/minecraft/world/entity/monster/warden/Warden.java|AnimationState|3|public void handleEntityEvent|40|public void tick()|40|public void onSyncedDataUpdated|20",
                "net/minecraft/world/entity/ai/goal/RangedAttackGoal.java",
                "net/minecraft/world/entity/ai/goal/MeleeAttackGoal.java",
                "net/minecraft/world/entity/ai/control/FlyingMoveControl.java",
                "net/minecraft/world/entity/monster/Vex.java|class VexMoveControl|50|public Vex(|12|protected void registerGoals|14",
                "net/minecraft/world/entity/animal/allay/Allay.java|public Allay(|10|protected PathNavigation createNavigation|10|public void travel|24|createAttributes|10",
                "net/minecraft/gametest/framework/GameTestHelper.java|public <E extends Entity> E spawn(|12|public void killAllEntities|10|public void succeedWhen|12|public void runAfterDelay|6",
                "net/minecraft/gametest/framework/GameTestServer.java|LevelSettings|4|Difficulty|2"));
    }
}
