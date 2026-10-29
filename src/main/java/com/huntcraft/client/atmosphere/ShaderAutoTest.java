package com.huntcraft.client.atmosphere;

import com.huntcraft.HuntCraft;
import com.huntcraft.core.HCDimensions;
import com.huntcraft.core.HuntHooks;
import com.huntcraft.core.Rank;
import com.huntcraft.entity.monster.HuntMonster;
import com.huntcraft.entity.monster.MonsterRegistry;
import com.huntcraft.entity.monster.attack.MonsterAttack;
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
        if (++totalTicks > 20 * 60 * 20) {
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
            sp.getAbilities().flying = true;
            sp.onUpdateAbilities();
            // a small scene: a few trees' worth of leaves and a pond would be nice, but keep it simple and fast
            cmd(sp, "fill 2 -60 -6 6 -56 -2 minecraft:oak_leaves");
            cmd(sp, "fill 3 -61 -5 5 -61 -3 minecraft:oak_log");
            cmd(sp, "fill -8 -61 -8 -2 -61 -2 minecraft:water");
            cmd(sp, "setblock -3 -60 3 minecraft:lantern");
            cmd(sp, "setblock 3 -60 3 minecraft:campfire");
            cmd(sp, "fill -2 -61 6 2 -61 8 minecraft:lava");
            cmd(sp, "tp @s 0 -56 14 180 20");
        }), 80);
        shot("shader_00_overworld");
        add("look_down", mc -> server(mc, sp -> cmd(sp, "tp @s 9 -54 10 -140 35")), 30);
        shot("shader_01_overworld_scene");

        // monsters: ignivald (reference, fire) by day; voltrix (glow parts, lightning) by night
        add("spawn_ignivald", mc -> showMonster(mc, "ignivald", 0), 60);
        shot("shader_monster_ignivald");
        add("attack_ignivald", mc -> server(mc, sp -> attack(sp, "fire_breath", "fireball", "breath")), 18);
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
        add("goto_village", mc -> server(mc, sp -> HuntHooks.toVillage.accept(sp)), 320);
        shot("shader_village");
        add("turn_village", mc -> turn(mc, 120), 30);
        shot("shader_village_2");
        add("turn_village2", mc -> turn(mc, 120), 30);
        shot("shader_village_3");

        // quest maps
        for (ResourceKey<Level> dim : List.of(HCDimensions.VERDANT_HILLS, HCDimensions.FROSTPEAK_RANGE, HCDimensions.EMBER_CALDERA)) {
            String n = dim.location().getPath();
            add("goto_" + n, mc -> server(mc, sp -> {
                ServerLevel lvl = sp.server.getLevel(dim);
                if (lvl == null) {
                    log("dimension missing: " + dim.location());
                    return;
                }
                int y = lvl.getChunk(0, 0).getHeight(Heightmap.Types.MOTION_BLOCKING, 8, 8) + 12;
                sp.teleportTo(lvl, 8.5, Math.max(y, lvl.getMinBuildHeight() + 5), 8.5, 225, 20);
            }), 320);
            shot("shader_map_" + n);
            add("turn_" + n, mc -> turn(mc, 150), 30);
            shot("shader_map_" + n + "_2");
        }
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
