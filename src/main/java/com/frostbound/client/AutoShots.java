package com.frostbound.client;

import com.frostbound.FrostboundMod;
import com.frostbound.core.Difficulty;
import com.frostbound.core.events.EventDef;
import com.frostbound.core.events.Events;
import com.frostbound.world.Dims;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * CI screenshot tour (enabled with {@code -Dfrostbound.autoshots=true}, run under Xvfb): opens the founding
 * screen, creates a FROSTBOUND world, photographs the city, the HUD, every management tab, the heat map, a
 * placement ghost, a building panel, an event, a blizzard and the night, then quits. Shots go to
 * {@code run/screenshots/}, the log to {@code run/frostbound_autoshots.txt}.
 */
public final class AutoShots {
    private static final boolean ENABLED = Boolean.getBoolean("frostbound.autoshots");
    private static int stage;
    private static int wait;
    private static int ticks;
    private static int index;
    private static final List<Step> steps = new ArrayList<>();
    private static final List<String> log = new ArrayList<>();

    private record Step(String name, Consumer<Minecraft> action, int waitAfter, boolean shot) {
    }

    private AutoShots() {
    }

    static void onTitle(TitleScreen title) {
        if (!ENABLED || stage != 0) {
            return;
        }
        stage = 1;
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        mc.setScreen(new FoundingScreen(true, title));
        wait = 40;
        log("founding screen opened");
    }

    static void tick(Minecraft mc) {
        if (!ENABLED || stage == 0) {
            return;
        }
        if (++ticks > 20 * 60 * 15) {
            log("global timeout");
            finish(mc);
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        if (stage == 1) {
            shot(mc, "00_founding_screen");
            FoundingScreen.createWorld(mc, Difficulty.NORMAL, 20, "CI", true, GameType.CREATIVE, 20261101L);
            log("world creation requested");
            stage = 2;
            return;
        }
        if (stage == 2) {
            if (mc.level == null || mc.player == null || !ClientCity.hasCity() || !mc.level.dimension().equals(Dims.FROZEN_WASTES)) {
                return;
            }
            stage = 3;
            wait = 160;
            build();
            log("in city, " + steps.size() + " steps");
            return;
        }
        if (stage != 3) {
            return;
        }
        if (index >= steps.size()) {
            finish(mc);
            return;
        }
        Step s = steps.get(index++);
        if (s.shot()) {
            shot(mc, s.name());
            wait = 3;
            return;
        }
        try {
            s.action().accept(mc);
            log("step " + s.name());
        } catch (Exception e) {
            log("step failed " + s.name() + ": " + e);
            FrostboundMod.LOGGER.error("[FROSTBOUND] autoshot step {} failed", s.name(), e);
        }
        wait = s.waitAfter();
    }

    private static void add(String name, Consumer<Minecraft> action, int waitAfter) {
        steps.add(new Step(name, action, waitAfter, false));
    }

    private static void shotStep(String name) {
        steps.add(new Step(name, null, 0, true));
    }

    private static void command(Minecraft mc, String cmd) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null) {
            server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd));
        }
    }

    private static ManagementScreen ms(Minecraft mc) {
        return mc.screen instanceof ManagementScreen m ? m : null;
    }

    private static void build() {
        steps.clear();
        add("setup", mc -> {
            command(mc, "frostbound advance 3");
            command(mc, "frostbound weather clear");
            if (mc.player != null) {
                mc.player.setYRot(180f);
                mc.player.setXRot(-12f);
            }
        }, 140);
        shotStep("01_city_hud");
        add("look_houses", mc -> {
            if (mc.player != null) {
                mc.player.setYRot(235f);
                mc.player.setXRot(6f);
            }
        }, 60);
        shotStep("02_citizens");
        add("open_management", mc -> mc.setScreen(new ManagementScreen("overview", -1)), 80);
        shotStep("03_management_overview");
        for (ManagementScreen.Tab t : ManagementScreen.Tab.values()) {
            if (t == ManagementScreen.Tab.OVERVIEW) {
                continue;
            }
            add("tab_" + t.name(), mc -> {
                ManagementScreen m = ms(mc);
                if (m != null) {
                    m.showTab(t);
                }
            }, 30);
            shotStep("04_tab_" + t.name().toLowerCase(java.util.Locale.ROOT));
        }
        add("heatmap", mc -> {
            WorldOverlay.showHeat = true;
            ManagementScreen.act("heat_grid");
            ManagementScreen m = ms(mc);
            if (m != null) {
                m.showTab(ManagementScreen.Tab.OVERVIEW);
            }
        }, 50);
        shotStep("05_heatmap");
        add("placement", mc -> {
            WorldOverlay.showHeat = false;
            ManagementScreen m = ms(mc);
            if (m != null) {
                m.showTab(ManagementScreen.Tab.BUILD);
            }
            CompoundTag g = ClientCity.generator();
            WorldOverlay.placing = "tent";
            WorldOverlay.rotation = 0;
            WorldOverlay.hover = new Vec3(g.getInt("x") - 20.5, g.getInt("y"), g.getInt("z") + 22.5);
        }, 30);
        shotStep("06_placement_ghost");
        add("building_panel", mc -> {
            WorldOverlay.placing = null;
            ManagementScreen m = ms(mc);
            ListTag list = ClientCity.buildings();
            for (int i = 0; i < list.size() && m != null; i++) {
                if (list.getCompound(i).getString("type").equals("shelter")) {
                    m.showBuilding(list.getCompound(i).getInt("id"));
                    break;
                }
            }
        }, 30);
        shotStep("07_building_panel");
        add("zoom_out", mc -> {
            CameraController.dist = 150f;
            CameraController.pitch = 70f;
            ManagementScreen m = ms(mc);
            if (m != null) {
                m.showTab(ManagementScreen.Tab.WEATHER);
            }
        }, 60);
        shotStep("08_management_far");
        add("close_management", mc -> mc.setScreen(null), 20);
        add("event", mc -> {
            for (EventDef e : Events.all()) {
                if (!e.scripted && !e.choices.isEmpty()) {
                    command(mc, "frostbound event " + e.id);
                    log("event " + e.id);
                    break;
                }
            }
        }, 60);
        shotStep("09_event");
        add("close_event", mc -> mc.setScreen(null), 10);
        add("blizzard", mc -> {
            command(mc, "frostbound weather blizzard");
            if (mc.player != null) {
                mc.player.setYRot(160f);
                mc.player.setXRot(-5f);
            }
        }, 200);
        shotStep("10_blizzard");
        add("night", mc -> {
            command(mc, "frostbound weather clear");
            command(mc, "frostbound advance 12");
            if (mc.player != null) {
                mc.player.setYRot(180f);
                mc.player.setXRot(-15f);
            }
        }, 160);
        shotStep("11_night");
        add("founding_ingame", mc -> {
        }, 1);
    }

    private static void shot(Minecraft mc, String name) {
        try {
            Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), c -> {
            });
            log("shot " + name);
        } catch (Exception e) {
            log("screenshot failed " + name + ": " + e);
        }
    }

    private static void log(String s) {
        log.add(s);
        FrostboundMod.LOGGER.info("[FROSTBOUND] autoshots: {}", s);
        try {
            Files.writeString(Path.of("frostbound_autoshots.txt"), String.join("\n", log) + "\n", StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static void finish(Minecraft mc) {
        stage = 9;
        log("finished");
        mc.stop();
    }
}
