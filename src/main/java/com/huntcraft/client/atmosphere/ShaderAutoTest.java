package com.huntcraft.client.atmosphere;

import com.huntcraft.HuntCraft;
import com.huntcraft.core.HCDimensions;
import com.huntcraft.core.HuntHooks;
import com.huntcraft.core.Rank;
import com.huntcraft.entity.monster.HuntMonster;
import com.huntcraft.entity.monster.MonsterRegistry;
import com.huntcraft.entity.monster.attack.MonsterAttack;
import com.huntcraft.quest.QuestCamps;
import com.huntcraft.quest.QuestMap;
import com.huntcraft.quest.QuestRegions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * CI visual test for the shader pack (enabled with -Dhuntcraft.autotest.shaders=true, run by the "shaders"
 * workflow job with Embeddium + Oculus under Xvfb/llvmpipe): creates a flat creative world, photographs the
 * overworld, two monsters (day / night glow / lightning), the village and the three quest maps, logs which shader
 * pack Oculus has active, then quits. Screenshots: run-shaders/screenshots/shader_*.png.
 * This is a small, independent copy of the mechanism in {@link com.huntcraft.client.AutoTest}.
 */
@Mod.EventBusSubscriber(modid = HuntCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ShaderAutoTest {
    private static final boolean ENABLED = Boolean.getBoolean("huntcraft.autotest.shaders");
    private static int stage = 0;
    private static int wait = 0;
    private static int totalTicks = 0;
    private static final List<Step> STEPS = new ArrayList<>();
    private static int stepIndex = 0;
    private static final List<String> LOG = new ArrayList<>();
    private static HuntMonster current;

    private record Step(String name, Consumer<Minecraft> action, int waitAfter, boolean screenshot) {
    }

    private ShaderAutoTest() {
    }

    @SubscribeEvent
    public static void onScreen(ScreenEvent.Init.Post event) {
        if (!ENABLED || stage != 0 || !(event.getScreen() instanceof TitleScreen)) return;
        stage = 1;
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        log("installer: " + ShaderPackInstaller.status());
        log("title screen: " + ShaderPackInstaller.activePackInfo());
        try {
            String name = "hc_shadertest";
            LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.NORMAL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
            WorldOptions options = new WorldOptions(20261030L, false, false);
            mc.createWorldOpenFlows().createFreshLevel(name, settings, options,
                    ra -> ra.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            log("world creation requested");
        } catch (Exception e) {
            log("world creation failed: " + e);
            HuntCraft.LOGGER.error("shader autotest world creation failed", e);
            finish(mc);
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END || stage < 1) return;
        Minecraft mc = Minecraft.getInstance();
        if (++totalTicks > 20 * 60 * 25) {
            log("global timeout");
            finish(mc);
            return;
        }
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
        if (stage == 1) {
            stage = 2;
            wait = 160;
            buildSteps();
            log("world loaded, " + STEPS.size() + " steps; " + ShaderPackInstaller.activePackInfo());
            return;
        }
        if (stage != 2) return;
        if (wait > 0) {
            wait--;
            return;
        }
        if (stepIndex >= STEPS.size()) {
            finish(mc);
            return;
        }
        Step s = STEPS.get(stepIndex);
        if (s.screenshot) {
            mc.options.hideGui = true;
            try {
                Screenshot.grab(mc.gameDirectory, s.name + ".png", mc.getMainRenderTarget(), c -> {
                });
                log("shot " + s.name + " (" + ShaderPackInstaller.activePackInfo() + ")");
            } catch (Exception e) {
                log("screenshot failed " + s.name + ": " + e);
            }
            stepIndex++;
            wait = 6;
            return;
        }
        try {
            s.action.accept(mc);
            log("step " + s.name);
        } catch (Exception e) {
            log("step failed " + s.name + ": " + e);
            HuntCraft.LOGGER.error("shader autotest step {} failed", s.name, e);
        }
        stepIndex++;
        wait = s.waitAfter;
    }

    private static void buildSteps() {
        STEPS.clear();
        add("setup", mc -> server(mc, sp -> {
            cmd(sp, "time set 2500");
            cmd(sp, "gamerule doDaylightCycle false");
            cmd(sp, "gamerule doMobSpawning false");
            cmd(sp, "gamerule doWeatherCycle false");
            cmd(sp, "weather clear");
            fly(sp);
            // a small scene: a leaf cube with a trunk (god rays), a pond (reflections), a lantern, a campfire, lava
            cmd(sp, "fill 2 -60 -6 6 -56 -2 minecraft:oak_leaves");
            cmd(sp, "fill 3 -61 -5 5 -61 -3 minecraft:oak_log");
            cmd(sp, "fill -8 -61 -8 -2 -61 -2 minecraft:water");
            cmd(sp, "setblock -3 -60 3 minecraft:lantern");
            cmd(sp, "setblock 3 -60 3 minecraft:campfire");
            cmd(sp, "fill -2 -61 6 2 -61 8 minecraft:lava");
            cmd(sp, "fill -12 -60 -12 -10 -58 12 minecraft:stone_bricks");
            cmd(sp, "tp @s 0 -56 14 180 20");
        }), 80);
        shot("shader_00_overworld");
        // the pond, the lantern, the campfire and the lava from above (yaw 135 looks towards -x/-z)
        add("look_down", mc -> server(mc, sp -> cmd(sp, "tp @s 9 -54 10 135 35")), 30);
        shot("shader_01_overworld_scene");
        // golden hour, looking west through the leaves towards the low sun: volumetric light / god rays
        add("sunset", mc -> server(mc, sp -> {
            cmd(sp, "time set 12400");
            cmd(sp, "tp @s 14 -58 -4 90 -6");
        }), 60);
        shot("shader_02_overworld_sunset");
        add("sunset_water", mc -> server(mc, sp -> cmd(sp, "tp @s 4 -57 2 60 18")), 30);
        shot("shader_03_overworld_sunset_water");
        add("morning", mc -> server(mc, sp -> cmd(sp, "time set 2500")), 20);

        // monsters: ignivald (reference, fire) by day; voltrix (glow parts, lightning) by night
        add("spawn_ignivald", mc -> showMonster(mc, "ignivald", 0), 60);
        shot("shader_monster_ignivald");
        add("attack_ignivald", mc -> server(mc, sp -> attack(sp, "fireball", "fireball_triple", "breath_sweep")), 18);
        shot("shader_monster_ignivald_attack");
        add("clear_ignivald", mc -> server(mc, sp -> clearMonster()), 5);

        add("spawn_voltrix", mc -> showMonster(mc, "voltrix", 1), 60);
        shot("shader_monster_voltrix");
        add("night", mc -> server(mc, sp -> cmd(sp, "time set 14500")), 40);
        shot("shader_monster_voltrix_night");
        add("attack_voltrix", mc -> server(mc, sp -> attack(sp, "lightning", "discharge", "thunder_orbs")), 30);
        shot("shader_monster_voltrix_lightning");
        add("clear_voltrix", mc -> server(mc, sp -> {
            clearMonster();
            cmd(sp, "time set 2500");
        }), 20);

        // village
        add("goto_village", mc -> server(mc, sp -> {
            HuntHooks.toVillage.accept(sp);
            fly(sp);
        }), 320);
        shot("shader_village");
        add("turn_village", mc -> turn(mc, 120), 30);
        shot("shader_village_2");
        add("turn_village2", mc -> turn(mc, 120), 30);
        shot("shader_village_3");
        add("village_overview", mc -> server(mc, sp -> {
            fly(sp);
            sp.teleportTo(sp.serverLevel(), sp.getX(), sp.getY() + 14.0, sp.getZ(), sp.getYRot() + 60.0F, 32.0F);
        }), 50);
        shot("shader_village_overview");

        // quest maps: from the designed base camp (camp yaw), then an aerial overview of the hunting ground
        for (ResourceKey<Level> dim : List.of(HCDimensions.VERDANT_HILLS, HCDimensions.FROSTPEAK_RANGE, HCDimensions.EMBER_CALDERA)) {
            String n = dim.location().getPath();
            add("goto_" + n, mc -> server(mc, sp -> gotoCamp(sp, dim, 0.0, 0.0, 4.0F)), 320);
            shot("shader_map_" + n);
            add("turn_" + n, mc -> turn(mc, 150), 30);
            shot("shader_map_" + n + "_2");
            add("overview_" + n, mc -> server(mc, sp -> gotoCamp(sp, dim, 0.0, 26.0, 34.0F)), 60);
            shot("shader_map_" + n + "_overview");
        }

        // the final boss in the caldera: dark red sky + its emissive glow
        add("spawn_azragos", mc -> server(mc, sp -> showMonsterHere(sp, "azragos")), 70);
        shot("shader_monster_azragos_caldera");
        add("attack_azragos", mc -> server(mc, sp -> attack(sp, "breath_beam", "orb_volley", "breath_sweep", "supernova")), 20);
        shot("shader_monster_azragos_attack");
        add("clear_azragos", mc -> server(mc, sp -> clearMonster()), 5);
    }

    private static void fly(ServerPlayer sp) {
        sp.getAbilities().mayfly = true;
        sp.getAbilities().flying = true;
        sp.onUpdateAbilities();
    }

    /** Teleports to the base camp of region 0 of the quest map (fallback: the region centre), yawTurn/up/pitch offsets. */
    private static void gotoCamp(ServerPlayer sp, ResourceKey<Level> dim, double yawTurn, double up, float pitch) {
        ServerLevel lvl = sp.server.getLevel(dim);
        if (lvl == null) {
            log("dimension missing: " + dim.location());
            return;
        }
        QuestMap map = QuestMap.of(dim);
        BlockPos center = QuestRegions.center(0);
        BlockPos camp = map == null ? new BlockPos(8, 0, 8) : QuestCamps.campPos(map, center);
        float yaw = map == null ? 225.0F : QuestCamps.campYaw(map);
        QuestCamps.loadArea(lvl, camp.getX(), camp.getZ(), 48);
        int ground = lvl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, camp.getX(), camp.getZ());
        double y = Math.max(camp.getY(), ground) + 0.1 + up;
        y = Math.max(y, lvl.getMinBuildHeight() + 5);
        sp.teleportTo(lvl, camp.getX() + 0.5, y, camp.getZ() + 0.5, yaw + (float) yawTurn, pitch);
        fly(sp);
        log("camp " + dim.location().getPath() + " at " + camp + " yaw " + yaw + " ground " + ground);
    }

    /** Spawns a monster ~22 blocks in front of the player (current dimension) and frames it. */
    private static void showMonsterHere(ServerPlayer sp, String id) {
        ServerLevel lvl = sp.serverLevel();
        float yaw = sp.getYRot();
        Vec3 dir = new Vec3(-Math.sin(Math.toRadians(yaw)), 0.0, Math.cos(Math.toRadians(yaw)));
        BlockPos at = BlockPos.containing(sp.getX() + dir.x * 22.0, 0, sp.getZ() + dir.z * 22.0);
        lvl.getChunk(at.getX() >> 4, at.getZ() >> 4);
        BlockPos ground = new BlockPos(at.getX(), lvl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()), at.getZ());
        HuntMonster m = MonsterRegistry.spawn(lvl, id, ground, Rank.LOW, null);
        if (m == null) {
            log("monster missing: " + id);
            return;
        }
        m.setNoAi(true);
        m.setMonsterScale(1.0F);
        float face = yaw + 180.0F + 25.0F;
        m.setYRot(face);
        m.yBodyRot = face;
        m.yHeadRot = face;
        current = m;
        double size = Math.max(m.getBbWidth(), m.getBbHeight());
        if (m.profile().large) size *= 2.2;
        double dist = 2.5 + size * 1.2;
        double camY = ground.getY() + m.getBbHeight() * 0.7 + size * 0.15;
        sp.teleportTo(lvl, ground.getX() + 0.5 - dir.x * dist, camY, ground.getZ() + 0.5 - dir.z * dist, yaw, 10.0F);
        fly(sp);
        log("monster " + id + " at " + ground);
    }

    private static void turn(Minecraft mc, float degrees) {
        if (mc.player != null) mc.player.setYRot(mc.player.getYRot() + degrees);
    }

    private static void cmd(ServerPlayer sp, String command) {
        sp.server.getCommands().performPrefixedCommand(sp.server.createCommandSourceStack().withSuppressedOutput().withPermission(4), command);
    }

    private static void attack(ServerPlayer sp, String... names) {
        HuntMonster m = current;
        if (m == null || m.getAttacks().isEmpty()) return;
        List<MonsterAttack> atk = m.getAttacks();
        MonsterAttack chosen = null;
        for (String n : names) {
            for (MonsterAttack a : atk) {
                if (a.name.equals(n)) {
                    chosen = a;
                    break;
                }
            }
            if (chosen != null) break;
        }
        if (chosen == null) chosen = atk.get(Math.min(1, atk.size() - 1));
        if (chosen.airOnly) m.setFlying(true);
        m.startAttack(chosen, sp);
        log("attack " + chosen.name);
    }

    private static void clearMonster() {
        if (current != null) current.discard();
        current = null;
    }

    private static void showMonster(Minecraft mc, String id, int idx) {
        server(mc, sp -> {
            ServerLevel lvl = sp.serverLevel();
            double baseX = 2000 + idx * 64;
            lvl.getChunk(((int) baseX) >> 4, 0);
            BlockPos ground = lvl.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(baseX, 0, 0));
            HuntMonster m = MonsterRegistry.spawn(lvl, id, ground, Rank.LOW, null);
            if (m == null) {
                log("monster missing: " + id);
                return;
            }
            m.setNoAi(true);
            m.setMonsterScale(1.0F);
            m.setYRot(35);
            m.yBodyRot = 35;
            m.yHeadRot = 35;
            current = m;
            // a few props so the monster casts shadows onto something
            cmd(sp, "fill " + (int) (baseX - 10) + " " + (ground.getY() - 1) + " -10 " + (int) (baseX + 10) + " " + (ground.getY() - 1) + " 10 minecraft:grass_block");
            double size = Math.max(m.getBbWidth(), m.getBbHeight());
            if (m.profile().large) size *= 2.2;
            double dist = 2.2 + size * 1.25;
            double camY = ground.getY() + m.getBbHeight() * 0.75 + size * 0.15;
            sp.teleportTo(lvl, baseX + 0.5 + size * 0.3, camY, ground.getZ() + 0.5 + dist, 172, 12);
        });
    }

    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) return;
        java.util.UUID id = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(id);
            if (sp != null) {
                try {
                    action.accept(sp);
                } catch (Exception e) {
                    log("server action failed: " + e);
                    HuntCraft.LOGGER.error("shader autotest server action failed", e);
                }
            }
        });
    }

    private static void add(String name, Consumer<Minecraft> action, int waitAfter) {
        STEPS.add(new Step(name, action, waitAfter, false));
    }

    private static void shot(String name) {
        STEPS.add(new Step(name, mc -> {
        }, 0, true));
    }

    private static synchronized void log(String s) {
        LOG.add(s);
        HuntCraft.LOGGER.info("[SHADERTEST] {}", s);
    }

    private static void finish(Minecraft mc) {
        stage = 99;
        log("final: " + ShaderPackInstaller.activePackInfo());
        try {
            File dir = new File(mc.gameDirectory, "huntcraft_autotest");
            dir.mkdirs();
            try (FileWriter w = new FileWriter(new File(dir, "shader_autotest_log.txt"))) {
                for (String s : LOG) w.write(s + "\n");
            }
        } catch (Exception ignored) {
        }
        HuntCraft.LOGGER.info("[SHADERTEST] finished, stopping client");
        mc.stop();
    }
}
