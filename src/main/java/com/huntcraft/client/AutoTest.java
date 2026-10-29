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
            add("spawn_" + e.id(), mc -> showMonster(mc, e.id(), idx, null), 50);
            add("frame_" + e.id(), AutoTest::frameCurrent, 20);
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
                if (refCow != null) refCow.discard();
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
        buildCombatSteps();
    }

    /** Hunter combat animations: third person shots of every weapon mid-swing, a dodge roll and first person. */
    private static void buildCombatSteps() {
        String[][] weapons = {
                {"great_sword", "gs_combo1", "13"}, {"long_sword", "ls_combo1", "7"}, {"sword_and_shield", "sns_combo3", "6"},
                {"dual_blades", "db_combo3", "6"}, {"hammer", "hm_combo1", "10"}, {"hunting_horn", "hh_combo1", "11"},
                {"lance", "ln_combo1", "7"}, {"bow", "bow_kick", "7"}};
        add("combat_setup", mc -> {
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
            server(mc, sp -> {
                ServerLevel lvl = sp.server.overworld();
                BlockPos ground = lvl.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(3000, 0, 3000));
                sp.getAbilities().flying = false;
                sp.onUpdateAbilities();
                sp.teleportTo(lvl, ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, 180, 0);
                HuntMonster m = MonsterRegistry.spawn(lvl, "boarfang", ground.offset(2, 0, -2), Rank.LOW, null);
                if (m != null) {
                    m.setNoAi(true);
                    m.setMonsterScale(1.0F);
                    m.setYRot(0);
                    m.yBodyRot = 0;
                    m.yHeadRot = 0;
                    current = m;
                }
            });
        }, 60);
        for (String[] w : weapons) {
            String cls = w[0], action = w[1];
            int hitTick = Integer.parseInt(w[2]);
            add("equip_" + cls, mc -> server(mc, sp -> {
                net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(HuntCraft.id("bone_" + cls));
                sp.getInventory().setItem(sp.getInventory().selected, item == null ? net.minecraft.world.item.ItemStack.EMPTY : new net.minecraft.world.item.ItemStack(item));
                sp.setYRot(180);
                sp.setYHeadRot(180);
                sp.yBodyRot = 180;
            }), 24);
            shot("combat_" + cls + "_stance", false);
            add("attack_" + cls, mc -> server(mc, sp -> com.huntcraft.gear.weapon.action.CombatActions.start(sp, action)), hitTick);
            shot("combat_" + cls + "_swing", false);
            add("wait_" + cls, mc -> {
            }, 30);
        }
        add("combat_back_view", mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK), 5);
        add("attack_gs_back", mc -> server(mc, sp -> com.huntcraft.gear.weapon.action.CombatActions.start(sp, "gs_combo2")), 11);
        shot("combat_great_sword_back", true);
        add("wait_gs_back", mc -> {
        }, 24);
        add("dodge", mc -> {
            if (mc.player != null && mc.level != null) {
                ClientState.DODGES.put(mc.player.getId(), new ClientState.Dodge(mc.level.getGameTime(), 0));
            }
        }, 4);
        shot("combat_dodge_roll", false);
        add("sheathe_potion", mc -> server(mc, sp -> sp.getInventory().setItem(sp.getInventory().selected,
                new net.minecraft.world.item.ItemStack(com.huntcraft.registry.HCItems.POTION.get()))), 20);
        shot("combat_sheathed_back", false);
        add("combat_first_person", mc -> {
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            server(mc, sp -> {
                net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(HuntCraft.id("bone_great_sword"));
                if (item != null) sp.getInventory().setItem(sp.getInventory().selected, new net.minecraft.world.item.ItemStack(item));
            });
        }, 24);
        shot("combat_first_person_stance", true);
        add("attack_fp", mc -> server(mc, sp -> com.huntcraft.gear.weapon.action.CombatActions.start(sp, "gs_combo1")), 12);
        shot("combat_first_person_swing", true);
        add("combat_cleanup", mc -> server(mc, sp -> {
            if (current != null) current.discard();
            current = null;
        }), 5);
    }

    private static HuntMonster current;
    private static net.minecraft.world.entity.Entity refCow;

    /** Logs lighting info of the current monster and re-positions the camera exactly in front of it. */
    private static void frameCurrent(Minecraft mc) {
        HuntMonster sm = current;
        if (sm == null || mc.level == null) return;
        net.minecraft.world.entity.Entity ce = mc.level.getEntity(sm.getId());
        if (ce != null) {
            BlockPos probe = BlockPos.containing(ce.getEyePosition());
            log(String.format("light %s: sky=%d block=%d raw=%d at %s, clientPos=%s, playerPos=%s", sm.profile().id,
                    mc.level.getBrightness(net.minecraft.world.level.LightLayer.SKY, probe),
                    mc.level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, probe),
                    mc.level.getMaxLocalRawBrightness(probe), probe, ce.position(), mc.player.position()));
        } else {
            log("client entity missing for " + sm.profile().id);
        }
        mc.options.hideGui = true;
        server(mc, sp -> {
            HuntMonster m = current;
            if (m == null) return;
            double size = Math.max(m.getBbWidth(), m.getBbHeight());
            if (m.profile().large) size *= 2.0;
            double dist = 2.5 + size * 1.55;
            sp.teleportTo((ServerLevel) m.level(), m.getX() + size * 0.35, m.getY() + m.getBbHeight() * 0.55 + size * 0.1, m.getZ() + dist, 170, 8);
        });
    }

    private static void showMonster(Minecraft mc, String id, int idx, String anim) {
        server(mc, sp -> {
            ServerLevel lvl = sp.serverLevel();
            double baseX = 2000 + idx * 64;
            lvl.getChunk(((int) baseX) >> 4, 0);
            BlockPos ground = lvl.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(baseX, 0, 0));
            HuntMonster m = MonsterRegistry.spawn(lvl, id, ground, Rank.LOW, null);
            if (m == null) return;
            m.setNoAi(true);
            m.setMonsterScale(1.0F);
            net.minecraft.world.entity.animal.Cow cow = net.minecraft.world.entity.EntityType.COW.create(lvl);
            if (cow != null) {
                cow.moveTo(ground.getX() + 0.5 + m.getBbWidth() * 0.5 + 1.5, ground.getY(), ground.getZ() + 2.5, 0, 0);
                cow.setNoAi(true);
                lvl.addFreshEntity(cow);
                refCow = cow;
            }
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
