package com.huntcraft.client;

import com.huntcraft.HuntCraft;
import com.huntcraft.core.HCDimensions;
import com.huntcraft.core.HuntHooks;
import com.huntcraft.core.Rank;
import com.huntcraft.entity.monster.HuntMonster;
import com.huntcraft.entity.monster.MonsterRegistry;
import com.huntcraft.entity.monster.attack.MonsterAttack;
import com.huntcraft.network.HCNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
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
 * Development-only visual test (enabled with -Dhuntcraft.autotest=true, used by CI under Xvfb):
 * creates a flat creative world, photographs every monster, the village, the quest maps and the GUIs,
 * then quits. Screenshots go to run/screenshots/.
 */
@Mod.EventBusSubscriber(modid = HuntCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class AutoTest {
    private static final boolean ENABLED = Boolean.getBoolean("huntcraft.autotest");
    private static int stage = 0;
    private static int wait = 0;
    private static int totalTicks = 0;
    private static final List<Step> STEPS = new ArrayList<>();
    private static int stepIndex = 0;
    private static final List<String> LOG = new ArrayList<>();

    private record Step(String name, Consumer<Minecraft> action, int waitAfter, boolean screenshot, boolean hud) {
    }

    private AutoTest() {
    }

    @SubscribeEvent
    public static void onScreen(ScreenEvent.Init.Post event) {
        if (!ENABLED || stage != 0 || !(event.getScreen() instanceof TitleScreen)) return;
        stage = 1;
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        try {
            String name = "hc_autotest";
            LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.NORMAL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
            WorldOptions options = new WorldOptions(20261029L, false, false);
            mc.createWorldOpenFlows().createFreshLevel(name, settings, options,
                    ra -> ra.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            log("world creation requested");
        } catch (Exception e) {
            log("world creation failed: " + e);
            HuntCraft.LOGGER.error("autotest world creation failed", e);
            finish(mc);
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END || stage < 1) return;
        Minecraft mc = Minecraft.getInstance();
        if (++totalTicks > 20 * 60 * 22) {
            log("global timeout");
            finish(mc);
            return;
        }
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
        if (stage == 1) {
            stage = 2;
            wait = 120;
            buildSteps();
            log("world loaded, " + STEPS.size() + " steps");
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
            mc.options.hideGui = !s.hud;
            try {
                Screenshot.grab(mc.gameDirectory, s.name + ".png", mc.getMainRenderTarget(), c -> {
                });
                log("shot " + s.name);
            } catch (Exception e) {
                log("screenshot failed " + s.name + ": " + e);
            }
            stepIndex++;
            wait = 4;
            return;
        }
        try {
            s.action.accept(mc);
            log("step " + s.name);
        } catch (Exception e) {
            log("step failed " + s.name + ": " + e);
            HuntCraft.LOGGER.error("autotest step {} failed", s.name, e);
        }
        stepIndex++;
        wait = s.waitAfter;
    }

    private static void buildSteps() {
        STEPS.clear();
        add("setup", mc -> server(mc, sp -> {
            sp.server.getCommands().performPrefixedCommand(sp.server.createCommandSourceStack().withSuppressedOutput(), "time set 6000");
            sp.server.getCommands().performPrefixedCommand(sp.server.createCommandSourceStack().withSuppressedOutput(), "gamerule doDaylightCycle false");
            sp.server.getCommands().performPrefixedCommand(sp.server.createCommandSourceStack().withSuppressedOutput(), "gamerule doMobSpawning false");
            sp.server.getCommands().performPrefixedCommand(sp.server.createCommandSourceStack().withSuppressedOutput(), "weather clear");
            sp.getAbilities().flying = true;
            sp.onUpdateAbilities();
        }), 40);
        shot("00_hud_overworld", true);
        int i = 0;
        for (MonsterRegistry.Entry e : MonsterRegistry.all()) {
            final int idx = i++;
            add("spawn_" + e.id(), mc -> showMonster(mc, e.id(), idx, null), 70);
            shot("monster_" + e.id(), false);
            MonsterRegistry.Entry entry = e;
            add("attack_" + e.id(), mc -> server(mc, sp -> {
                HuntMonster m = current;
                if (m == null || m.getAttacks().isEmpty()) return;
                List<MonsterAttack> atk = m.getAttacks();
                MonsterAttack a = atk.get(Math.min(atk.size() - 1, 1 + (entry.id().length() % Math.max(1, atk.size() - 1))));
                if (a.airOnly) m.setFlying(true);
                m.startAttack(a, sp);
            }), 14);
            shot("monster_" + e.id() + "_attack", false);
            add("clear_" + e.id(), mc -> server(mc, sp -> {
                if (current != null) current.discard();
                current = null;
            }), 5);
        }
        // village + gui
        add("goto_village", mc -> server(mc, sp -> HuntHooks.toVillage.accept(sp)), 260);
        shot("village_spawn", true);
        add("look_around", mc -> {
            if (mc.player != null) mc.player.setYRot(mc.player.getYRot() + 120);
        }, 40);
        shot("village_view2", false);
        for (String screen : new String[]{"quest_board", "blacksmith", "merchant", "kitchen", "companion", "hunter_notes"}) {
            add("open_" + screen, mc -> HCNetwork.simulateClientAction("screen/" + screen, new CompoundTag()), 30);
            shot("gui_" + screen, true);
            add("close_" + screen, mc -> mc.setScreen(null), 10);
        }
        // quest maps
        for (ResourceKey<Level> dim : new ArrayList<>(List.of(HCDimensions.VERDANT_HILLS, HCDimensions.FROSTPEAK_RANGE, HCDimensions.EMBER_CALDERA))) {
            add("goto_" + dim.location().getPath(), mc -> server(mc, sp -> {
                ServerLevel lvl = sp.server.getLevel(dim);
                if (lvl == null) {
                    log("dimension missing: " + dim.location());
                    return;
                }
                int y = lvl.getChunk(0, 0).getHeight(Heightmap.Types.MOTION_BLOCKING, 8, 8) + 12;
                sp.teleportTo(lvl, 8.5, Math.max(y, lvl.getMinBuildHeight() + 5), 8.5, 225, 20);
            }), 200);
            shot("map_" + dim.location().getPath(), false);
        }
    }

    private static HuntMonster current;

    private static void showMonster(Minecraft mc, String id, int idx, String anim) {
        server(mc, sp -> {
            ServerLevel lvl = sp.serverLevel();
            double baseX = 2000 + idx * 64;
            BlockPos ground = lvl.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(baseX, 0, 0));
            HuntMonster m = MonsterRegistry.spawn(lvl, id, ground, Rank.LOW, null);
            if (m == null) return;
            m.setNoAi(true);
            m.setMonsterScale(1.0F);
            m.setYRot(35);
            m.yBodyRot = 35;
            m.yHeadRot = 35;
            current = m;
            double size = Math.max(m.getBbWidth(), m.getBbHeight());
            if (m.profile().large) size *= 2.2;
            double dist = 2.2 + size * 1.25;
            double camY = ground.getY() + m.getBbHeight() * 0.75 + size * 0.15;
            sp.teleportTo(lvl, baseX + 0.5, camY, ground.getZ() + 0.5 + dist, 180, 12);
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
                    HuntCraft.LOGGER.error("autotest server action failed", e);
                }
            }
        });
    }

    private static void add(String name, Consumer<Minecraft> action, int waitAfter) {
        STEPS.add(new Step(name, action, waitAfter, false, false));
    }

    private static void shot(String name, boolean hud) {
        STEPS.add(new Step(name, mc -> {
        }, 0, true, hud));
    }

    private static synchronized void log(String s) {
        LOG.add(s);
        HuntCraft.LOGGER.info("[AUTOTEST] {}", s);
    }

    private static void finish(Minecraft mc) {
        stage = 99;
        try {
            File dir = new File(mc.gameDirectory, "huntcraft_autotest");
            dir.mkdirs();
            try (FileWriter w = new FileWriter(new File(dir, "autotest_log.txt"))) {
                for (String s : LOG) w.write(s + "\n");
            }
        } catch (Exception ignored) {
        }
        HuntCraft.LOGGER.info("[AUTOTEST] finished, stopping client");
        mc.stop();
    }
}
