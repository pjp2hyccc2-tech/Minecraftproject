package com.sounan.client;

import com.sounan.Sounan;
import com.sounan.client.gui.EndingScreen;
import com.sounan.client.gui.EnvelopeScreen;
import com.sounan.client.gui.JournalScreen;
import com.sounan.game.Closet;
import com.sounan.game.GameManager;
import com.sounan.game.Mansion;
import com.sounan.game.Room;
import net.minecraft.FileUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * CI only ({@code -Dmeikon.shots=true}, Xvfb + software GL): plays a scripted tour of the game and saves
 * screenshots into {@code run/screenshots/}. With {@code -Dmeikon.shots.shaders=true} (Oculus run) it takes a
 * shorter tour with longer waits. Log: {@code run/meikon_autoshots.txt}.
 */
public final class AutoShots {
    private static final boolean ENABLED = Boolean.getBoolean("meikon.shots");
    private static final boolean SHADERS = Boolean.getBoolean("meikon.shots.shaders");
    private static final int MUL = SHADERS ? 3 : 1;
    private static int stage;
    private static int wait;
    private static int ticks;
    private static int index;
    private static final List<Step> STEPS = new ArrayList<>();
    private static final List<String> LOG = new ArrayList<>();

    private record Step(String name, Consumer<Minecraft> action, int waitAfter, boolean shot) {}

    private AutoShots() {}

    static void onTitle(TitleScreen title) {
        if (!ENABLED || stage != 0) return;
        stage = 1;
        wait = 60;
        Minecraft.getInstance().options.pauseOnLostFocus = false;
        log("title screen (shaders=" + SHADERS + ")");
    }

    static void tick(Minecraft mc) {
        if (!ENABLED || stage == 0 || stage == 9) return;
        if (++ticks > 20 * 60 * (SHADERS ? 44 : 30)) {
            log("global timeout");
            finish(mc);
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        switch (stage) {
            case 1 -> {
                if (mc.getOverlay() != null) return;
                shot(mc, "00_title");
                createWorld(mc);
                stage = 2;
            }
            case 2 -> {
                if (mc.level == null || mc.player == null || mc.screen != null) return;
                stage = 3;
                wait = 100 * MUL;
                build();
                log("in world, " + STEPS.size() + " steps");
            }
            case 3 -> {
                if (index >= STEPS.size()) {
                    finish(mc);
                    return;
                }
                Step s = STEPS.get(index++);
                if (s.shot()) {
                    shot(mc, s.name());
                    wait = 3;
                    return;
                }
                try {
                    s.action().accept(mc);
                    log("step " + s.name() + " dim=" + (mc.level == null ? "?" : mc.level.dimension().location())
                            + " pos=" + (mc.player == null ? "?" : mc.player.blockPosition().toShortString())
                            + (SHADERS ? " shaders=" + ShaderCompat.active() : ""));
                } catch (Exception e) {
                    log("step failed " + s.name() + ": " + e);
                    Sounan.LOGGER.error("[sounan] autoshot step {} failed", s.name(), e);
                }
                wait = s.waitAfter() * (s.waitAfter() > 10 ? MUL : 1);
            }
            default -> {
            }
        }
    }

    private static void createWorld(Minecraft mc) {
        try {
            String folder = FileUtil.findAvailableName(mc.getLevelSource().getBaseDir(), "MeikonCI", "");
            GameRules rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            LevelSettings settings = new LevelSettings("MeikonCI", GameType.CREATIVE, false, Difficulty.NORMAL, true, rules,
                    WorldDataConfiguration.DEFAULT);
            WorldOptions options = new WorldOptions(20261102L, false, false);
            mc.createWorldOpenFlows().createFreshLevel(folder, settings, options,
                    ra -> ra.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            log("world creation requested");
        } catch (Exception e) {
            log("could not create world: " + e);
            Sounan.LOGGER.error("[sounan] could not create world", e);
        }
    }

    private static void add(String name, Consumer<Minecraft> action, int waitAfter) {
        STEPS.add(new Step(name, action, waitAfter, false));
    }

    private static void snap(String name) {
        STEPS.add(new Step(name, null, 0, true));
    }

    private static void cmd(Minecraft mc, String command) {
        if (mc.player != null) mc.player.connection.sendCommand(command);
    }

    private static void look(Minecraft mc, float yaw, float pitch) {
        if (mc.player != null) {
            mc.player.setYRot(yaw);
            mc.player.setXRot(pitch);
            mc.player.yRotO = yaw;
            mc.player.xRotO = pitch;
            mc.player.setYHeadRot(yaw);
        }
    }

    private static void tpLook(Minecraft mc, BlockPos p, float yaw, float pitch) {
        cmd(mc, String.format(java.util.Locale.ROOT, "tp @s %.2f %d %.2f %.1f %.1f", p.getX() + 0.5, p.getY(), p.getZ() + 0.5, yaw, pitch));
    }

    private static Mansion mansion() {
        return GameManager.mansion();
    }

    /** Yaw facing the exterior (back) wall of a room. */
    private static float backYaw(Room r) {
        BlockPos c = r.center();
        Direction d;
        if (Math.abs(c.getZ()) >= Math.abs(c.getX())) d = c.getZ() < 0 ? Direction.NORTH : Direction.SOUTH;
        else d = c.getX() < 0 ? Direction.WEST : Direction.EAST;
        return d.toYRot();
    }

    private static void room(String key, String shot, float pitch) {
        add("tp_" + key, mc -> {
            Mansion m = mansion();
            if (m == null) return;
            for (Room r : m.rooms) {
                if (r.key().equals(key)) {
                    float yaw = backYaw(r);
                    Direction back = Direction.fromYRot(yaw);
                    BlockPos stand = r.center().relative(back.getOpposite(), 2);
                    if (key.equals("wedding")) stand = new BlockPos(0, 65, -19);
                    tpLook(mc, stand, yaw, pitch);
                    return;
                }
            }
        }, 70);
        snap(shot);
    }

    private static void build() {
        STEPS.clear();
        add("rules", mc -> {
            cmd(mc, "gamerule doDaylightCycle false");
            cmd(mc, "gamerule doMobSpawning false");
            cmd(mc, "time set 18000");
            cmd(mc, "gamerule sendCommandFeedback false");
        }, 10);
        if (!SHADERS) {
            add("envelope", mc -> mc.setScreen(new EnvelopeScreen(mc.getUser().getName())), 12);
            snap("01_envelope");
            add("close_envelope", mc -> mc.setScreen(null), 5);
        }
        add("start", mc -> cmd(mc, "meikon start"), 90);
        if (!SHADERS) {
            snap("02_intro");
            add("intro_wait", mc -> {
            }, 150);
            snap("03_logo");
        }
        add("prepare", mc -> {
            cmd(mc, "gamemode creative");
            cmd(mc, "meikon debug peaceful true");
        }, 150);
        add("look_altar", mc -> {
            Mansion m = mansion();
            if (m != null) tpLook(mc, m.spawn, m.spawnYaw, 8.0f);
        }, 40);
        snap("04_safe_room");
        add("look_door", mc -> {
            Mansion m = mansion();
            if (m != null) tpLook(mc, m.spawn, m.spawnYaw + 180.0f, 4.0f);
        }, 30);
        snap("05_safe_door");
        add("corridor", mc -> {
            Mansion m = mansion();
            cmd(mc, "meikon debug lantern");
            if (m != null) tpLook(mc, m.safeOutside, 90.0f, 2.0f);
        }, 80);
        snap("06_corridor_lantern");
        add("lantern_off", mc -> cmd(mc, "item replace entity @s weapon.mainhand with air"), 40);
        snap("07_corridor_dark");
        add("courtyard", mc -> tpLook(mc, new BlockPos(0, 65, 11), 180.0f, -18.0f), 80);
        snap("08_courtyard");
        add("moon", mc -> tpLook(mc, new BlockPos(0, 65, -4), 0.0f, -42.0f), 40);
        snap("09_moon");
        add("torii", mc -> tpLook(mc, new BlockPos(0, 65, -4), 180.0f, -8.0f), 40);
        snap("10_torii");
        add("bride_close", mc -> {
            Mansion m = mansion();
            if (m != null) tpLook(mc, m.safeOutside.east(4), 90.0f, -6.0f);
        }, 40);
        add("bride_spawn", mc -> {
            cmd(mc, "meikon debug lantern");
            cmd(mc, "meikon debug spawn bride");
        }, 40);
        snap("11_bride_close");
        add("bride_chase", mc -> cmd(mc, "meikon debug bride chase"), 3);
        snap("12_bride_chase");
        add("bride_far", mc -> {
            cmd(mc, "item replace entity @s weapon.mainhand with air");
            cmd(mc, "meikon debug spawn bride_far");
        }, 40);
        snap("13_bride_far");
        add("bride_away", mc -> {
            cmd(mc, "tp @e[type=meikon:hanayome] 0 65 -22");
            cmd(mc, "meikon debug lantern");
        }, 10);
        room("dolls", "14_doll_room", 2.0f);
        room("butsuma", "15_butsuma", 4.0f);
        room("wedding", "16_wedding_hall", 2.0f);
        room("kitchen", "17_kitchen", 6.0f);
        room("bath", "18_bath", 10.0f);
        room("bedroom", "19_bedroom", 8.0f);
        if (!SHADERS) {
            add("kura", mc -> {
                Mansion m = mansion();
                if (m != null && m.kuraDoor != null) tpLook(mc, m.kuraDoor.south(4), 180.0f, -6.0f);
            }, 40);
            snap("20_kura");
            add("well", mc -> {
                Mansion m = mansion();
                if (m != null && m.well != null) tpLook(mc, m.well.offset(2, 1, 2), 135.0f, 22.0f);
            }, 40);
            snap("21_well");
            add("closet", mc -> {
                Mansion m = mansion();
                if (m == null || m.closets.isEmpty()) return;
                Closet c = m.closets.get(0);
                tpLook(mc, c.inside(), c.facing().toYRot(), 4.0f);
            }, 40);
            add("closet_bride", mc -> {
                Mansion m = mansion();
                if (m == null || m.closets.isEmpty()) return;
                Closet c = m.closets.get(0);
                BlockPos out = c.front().relative(c.facing(), 2);
                cmd(mc, String.format(java.util.Locale.ROOT, "tp @e[type=meikon:hanayome] %.1f %d %.1f %.1f 0", out.getX() + 0.5, out.getY(),
                        out.getZ() + 0.5, c.facing().getOpposite().toYRot()));
                cmd(mc, "meikon debug bride check_closet");
            }, 30);
            snap("22_closet_peek");
            add("note", mc -> cmd(mc, "meikon debug note 2"), 20);
            snap("23_note");
            add("note_candles", mc -> cmd(mc, "meikon debug note 9"), 20);
            snap("24_note_candles");
            add("journal", mc -> mc.setScreen(new JournalScreen()), 15);
            snap("25_journal");
            add("close_screen", mc -> mc.setScreen(null), 5);
        }
        add("hud", mc -> {
            Mansion m = mansion();
            cmd(mc, "meikon debug bells 3");
            if (m != null) tpLook(mc, m.safeOutside.west(2), 270.0f, 4.0f);
        }, 40);
        snap("26_hud");
        add("fear_high", mc -> cmd(mc, "meikon debug fear 96 0.1 300"), 50);
        snap("27_fear_high");
        add("danger", mc -> cmd(mc, "meikon debug fear 85 1.0 300"), 40);
        snap("28_danger");
        add("calm", mc -> cmd(mc, "meikon debug fear 10 0 40"), 50);
        add("title_card", mc -> cmd(mc, "meikon debug event 5 2"), 30);
        snap("29_title_card");
        if (!SHADERS) {
            add("jumpscare", mc -> cmd(mc, "meikon debug event 1 0"), 6);
            snap("30_jumpscare_bride");
            add("js_wait", mc -> {
            }, 60);
            add("jumpscare_doll", mc -> cmd(mc, "meikon debug event 1 1"), 5);
            snap("31_jumpscare_doll");
            add("death", mc -> cmd(mc, "meikon debug event 2 3"), 55);
            snap("32_death");
            add("death_wait", mc -> {
            }, 110);
        }
        add("shadow", mc -> {
            Mansion m = mansion();
            if (m != null) tpLook(mc, m.safeOutside.east(6), 90.0f, 0.0f);
        }, 30);
        add("shadow_spawn", mc -> cmd(mc, "meikon debug spawn shadow"), 30);
        snap("33_kageboshi");
        add("clear", mc -> cmd(mc, "meikon debug clear"), 5);
        add("phantom", mc -> {
            cmd(mc, "tp @e[type=meikon:hanayome] 0 65 -22");
            Mansion m = mansion();
            if (m != null) tpLook(mc, new BlockPos(-16, 65, 14), 180.0f, 0.0f);
        }, 40);
        add("phantom_spawn", mc -> cmd(mc, "meikon debug spawn phantom"), 20);
        snap("34_phantom");
        add("final", mc -> {
            cmd(mc, "meikon debug clear");
            cmd(mc, "meikon debug bells 7");
        }, 80);
        add("final_view", mc -> tpLook(mc, new BlockPos(0, 65, -5), 180.0f, -6.0f), 50);
        snap("35_torii_active");
        if (!SHADERS) {
            // real gameplay: the bride hunts an adventure-mode player, catches her, the player wakes in the lamplight room
            add("hunt_setup", mc -> {
                Mansion m = mansion();
                cmd(mc, "meikon debug bells 3");
                cmd(mc, "meikon debug fear 0 0 0");
                if (m != null) tpLook(mc, m.safeOutside.east(5), 90.0f, 0.0f);
            }, 40);
            add("hunt_go", mc -> {
                cmd(mc, "gamemode adventure");
                cmd(mc, "meikon debug peaceful false");
                cmd(mc, "meikon debug lantern");
                cmd(mc, "meikon debug spawn bride");
            }, 30);
            snap("38_captured");
            add("hunt_wait", mc -> {
            }, 60);
            snap("39_taken");
            add("hunt_after", mc -> {
            }, 120);
            snap("40_back_in_safe_room");
            add("doll_test", mc -> {
                Mansion m = mansion();
                cmd(mc, "tp @e[type=meikon:hanayome] 0 65 -22");
                if (m != null) tpLook(mc, m.safeOutside.east(5), 90.0f, 0.0f);
            }, 40);
            add("doll_behind", mc -> cmd(mc, "summon meikon:ichimatsu ^ ^ ^-3"), 40);
            snap("41_doll_attack");
            add("doll_wait", mc -> {
            }, 40);
            snap("42_doll_after");
            add("hunt_end", mc -> {
                cmd(mc, "gamemode creative");
                cmd(mc, "meikon debug peaceful true");
                cmd(mc, "meikon debug state");
            }, 20);
            add("ending_bad", mc -> {
                EndingScreen e = new EndingScreen(false, mc.getUser().getName());
                e.localOnly = true;
                mc.setScreen(e);
                e.setTicks(9999);
            }, 10);
            snap("36_ending_bad");
            add("ending_good", mc -> {
                EndingScreen e = new EndingScreen(true, mc.getUser().getName());
                e.localOnly = true;
                mc.setScreen(e);
                e.setTicks(9999);
            }, 10);
            snap("37_ending_good");
            add("ending_close", mc -> mc.setScreen(null), 5);
        }
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
        LOG.add(s);
        Sounan.LOGGER.info("[sounan] autoshots: {}", s);
        try {
            Files.writeString(Path.of("meikon_autoshots.txt"), String.join("\n", LOG) + "\n", StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static void finish(Minecraft mc) {
        stage = 9;
        log("finished");
        mc.stop();
    }
}
