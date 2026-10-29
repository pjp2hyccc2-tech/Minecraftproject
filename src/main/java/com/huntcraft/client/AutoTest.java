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
import net.minecraft.world.entity.EquipmentSlot;
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
        if (mc.player.isDeadOrDying() || mc.screen instanceof net.minecraft.client.gui.screens.DeathScreen) {
            log("player died at step " + (stepIndex < STEPS.size() ? STEPS.get(stepIndex).name : "?") + " - respawning");
            mc.player.respawn();
            mc.setScreen(null);
            wait = 40;
            return;
        }
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
        buildCombatSteps(i + 1);
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

    // ================================================================== hunter combat animations
    /*
     * Photographed in the flat overworld right next to the monster photo spots (never after a dimension change):
     * the hunter stands on an explicit grass floor, a boarfang sparring partner stands in front of it and the camera
     * is an invisible marker armor stand (Minecraft#setCameraEntity) that can be placed at any angle. Attacks, charges,
     * guards, item use and the dodge go through the real client input path (KeyMapping clicks); the special moves and
     * reactions use the same server actions the weapons start.
     */
    private static final String[][] COMBAT_WEAPONS = {
            // class, hit tick of combo1 (+3 = frame after the impact), special action (server), shot tick, special camera
            {"great_sword", "13", "gs_combo3", "14", "side"},
            {"long_sword", "7", "ls_spirit3", "16", "back"},
            {"sword_and_shield", "5", "sns_bash", "10", "front"},
            {"dual_blades", "5", "db_dance", "13", "front"},
            {"hammer", "10", "hm_charge3", "26", "side"},
            {"hunting_horn", "11", "hh_perform", "20", "front"},
            {"lance", "7", "ln_dash_finish", "8", "side"},
            {"bow", "7", "bow_kick", "8", "side"}};
    private static final java.util.Map<String, double[]> CAMS = java.util.Map.of(
            "front", new double[]{2.4, 1.45, 2.9, 0.95},
            "side", new double[]{3.8, 1.3, 0.5, 0.9},
            "back", new double[]{2.6, 1.6, -2.7, 0.9},
            "close", new double[]{1.7, 1.35, 1.8, 1.05},
            "low", new double[]{2.6, 0.7, 2.4, 0.8});
    private static net.minecraft.world.entity.decoration.ArmorStand camStand;
    private static boolean camLogged;
    private static double combatX, combatY, combatZ;

    private static void buildCombatSteps(int spot) {
        final double bx = 2000 + spot * 64 + 0.5, bz = 0.5;
        add("combat_setup", mc -> {
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            server(mc, sp -> setupArena(sp, bx, bz));
        }, 70);
        add("combat_cam_attach", AutoTest::attachCamera, 6);
        boolean armorOn = false;
        for (String[] w : COMBAT_WEAPONS) {
            String cls = w[0];
            int hit = Integer.parseInt(w[1]);
            String special = w[2];
            int specialShot = Integer.parseInt(w[3]);
            String specialCam = w[4];
            if ("long_sword".equals(cls)) {
                add("combat_armor", mc -> server(mc, AutoTest::equipArmor), 4);
                armorOn = true;
            }
            add("equip_" + cls, mc -> server(mc, sp -> equipWeapon(sp, "bone_" + cls)), 2);
            cam("front");
            add("wait_unsheathe_" + cls, mc -> {
            }, 26);
            shot("combat_" + cls + "_stance", false);
            if (armorOn && "long_sword".equals(cls)) {
                cam("close");
                shot("combat_armor_closeup", false);
                cam("back");
                shot("combat_armor_back", false);
                cam("front");
            }
            click("attack_" + cls, mc -> mc.options.keyAttack, hit + 3);
            shot("combat_" + cls + "_swing", false);
            add("rest_" + cls, mc -> server(mc, AutoTest::resetArena), 30);
            cam(specialCam);
            add("special_" + cls, mc -> server(mc, sp -> com.huntcraft.gear.weapon.action.CombatActions.start(sp, special)), specialShot);
            shot("combat_" + cls + "_special", false);
            add("rest2_" + cls, mc -> server(mc, AutoTest::resetArena), 36);
            switch (cls) {
                case "great_sword" -> {
                    cam("side");
                    add("gs_charge_hold", mc -> mc.options.keyUse.setDown(true), 48);
                    shot("combat_great_sword_charge", false);
                    add("gs_charge_release", mc -> mc.options.keyUse.setDown(false), 11);
                    shot("combat_great_sword_charge_slash", false);
                    add("rest3_gs", mc -> server(mc, AutoTest::resetArena), 30);
                    cam("front");
                    add("gs_guard_sneak", mc -> mc.options.keyShift.setDown(true), 3);
                    add("gs_guard_hold", mc -> mc.options.keyUse.setDown(true), 14);
                    shot("combat_great_sword_guard", false);
                    add("gs_guard_release", mc -> {
                        mc.options.keyUse.setDown(false);
                        mc.options.keyShift.setDown(false);
                    }, 12);
                }
                case "sword_and_shield" -> {
                    add("sns_guard_hold", mc -> mc.options.keyUse.setDown(true), 14);
                    shot("combat_sword_and_shield_guard", false);
                    add("sns_guard_release", mc -> mc.options.keyUse.setDown(false), 12);
                }
                case "lance" -> {
                    cam("front");
                    add("ln_guard_hold", mc -> mc.options.keyUse.setDown(true), 14);
                    shot("combat_lance_guard", false);
                    add("ln_guard_release", mc -> mc.options.keyUse.setDown(false), 12);
                }
                case "hammer" -> {
                    cam("back");
                    add("hm_charge_hold", mc -> mc.options.keyUse.setDown(true), 46);
                    shot("combat_hammer_charge", false);
                    add("hm_charge_release", mc -> mc.options.keyUse.setDown(false), 14);
                    shot("combat_hammer_charge_slam", false);
                    add("rest3_hm", mc -> server(mc, AutoTest::resetArena), 30);
                }
                case "bow" -> {
                    cam("front");
                    add("bow_draw_hold", mc -> mc.options.keyUse.setDown(true), 34);
                    shot("combat_bow_draw", false);
                    add("bow_draw_release", mc -> mc.options.keyUse.setDown(false), 4);
                    shot("combat_bow_shoot", false);
                    add("rest3_bow", mc -> server(mc, AutoTest::resetArena), 20);
                }
                default -> {
                }
            }
        }
        // movement with a drawn weapon
        add("equip_ls_move", mc -> server(mc, sp -> equipWeapon(sp, "bone_long_sword")), 24);
        cam("side");
        add("walk_hold", mc -> mc.options.keyUp.setDown(true), 9);
        shot("combat_walk_armed", false);
        add("run_hold", mc -> {
            mc.options.keySprint.setDown(true);
            if (mc.player != null) mc.player.setSprinting(true);
        }, 8);
        shot("combat_run_armed", false);
        add("move_release", mc -> {
            mc.options.keyUp.setDown(false);
            mc.options.keySprint.setDown(false);
            server(mc, AutoTest::resetArena);
        }, 20);
        // dodge roll (real input: dodge key without movement keys -> backward roll, seen from the side)
        cam("side");
        click("dodge", mc -> HCClientSetup.DODGE, 5);
        shot("combat_dodge_roll", false);
        add("dodge_wait", mc -> {
        }, 3);
        shot("combat_dodge_roll2", false);
        add("dodge_release", mc -> server(mc, AutoTest::resetArena), 20);
        // reactions / misc actions
        cam("side");
        serverAction("flinch", "flinch", 4);
        shot("combat_flinch", false);
        add("rest_flinch", mc -> server(mc, AutoTest::resetArena), 14);
        serverAction("knockdown", "knockdown", 14);
        shot("combat_knockdown", false);
        add("knockdown_up", mc -> {
        }, 20);
        shot("combat_knockdown_getup", false);
        add("rest_kd", mc -> server(mc, AutoTest::resetArena), 16);
        cam("front");
        add("sharpen_start", mc -> server(mc, sp -> {
            net.minecraft.world.item.ItemStack main = sp.getMainHandItem();
            if (main.getItem() instanceof com.huntcraft.gear.weapon.HunterWeaponItem hw) hw.setSharpnessPoints(main, 1);
            sp.getInventory().setItem(8, new net.minecraft.world.item.ItemStack(com.huntcraft.gear.weapon.WeaponsModule.WHETSTONE.get()));
            com.huntcraft.gear.weapon.Sharpening.start(sp);
        }), 16);
        shot("combat_sharpen", false);
        add("rest_sharpen", mc -> server(mc, sp -> {
            com.huntcraft.gear.weapon.Sharpening.cancel(sp, com.huntcraft.gear.weapon.WeaponState.get(sp));
            resetArena(sp);
        }), 10);
        serverAction("carve", "carve", 12);
        shot("combat_carve", false);
        add("rest_carve", mc -> server(mc, AutoTest::resetArena), 16);
        serverAction("victory", "victory", 14);
        shot("combat_victory", false);
        add("rest_victory", mc -> server(mc, AutoTest::resetArena), 34);
        // sheathed weapon on the back + drinking with the weapon sheathed
        cam("back");
        add("sheathe_potion", mc -> server(mc, sp -> {
            int sel = sp.getInventory().selected;
            sp.getInventory().setItem((sel + 1) % 9, sp.getMainHandItem().copy());   // the weapon stays in the hotbar -> shown on the back
            sp.getInventory().setItem(sel, new net.minecraft.world.item.ItemStack(com.huntcraft.registry.HCItems.POTION.get()));
        }), 8);
        shot("combat_sheathe", false);
        add("wait_sheathed", mc -> {
        }, 14);
        shot("combat_sheathed_back", false);
        cam("front");
        add("drink_hold", mc -> mc.options.keyUse.setDown(true), 12);
        shot("combat_drink", false);
        add("drink_release", mc -> {
            mc.options.keyUse.setDown(false);
            server(mc, AutoTest::resetArena);
        }, 10);
        // first person
        add("combat_first_person", mc -> {
            if (mc.player != null) mc.setCameraEntity(mc.player);
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            server(mc, sp -> equipWeapon(sp, "bone_great_sword"));
        }, 26);
        shot("combat_first_person_stance", true);
        click("attack_fp", mc -> mc.options.keyAttack, 15);
        shot("combat_first_person_swing", true);
        add("fp_charge_hold", mc -> mc.options.keyUse.setDown(true), 30);
        shot("combat_first_person_charge", true);
        add("fp_charge_release", mc -> mc.options.keyUse.setDown(false), 34);
        add("fp_ls", mc -> server(mc, sp -> equipWeapon(sp, "bone_long_sword")), 26);
        click("attack_fp_ls", mc -> mc.options.keyAttack, 9);
        shot("combat_first_person_long_sword", true);
        add("fp_look_down", mc -> {
            if (mc.player != null) mc.player.setXRot(35);
        }, 3);
        shot("combat_first_person_look_down", true);
        add("fp_look_reset", mc -> {
            if (mc.player != null) mc.player.setXRot(0);
        }, 2);
        add("combat_cleanup", mc -> {
            if (mc.player != null) mc.setCameraEntity(mc.player);
            server(mc, sp -> {
                if (current != null) current.discard();
                current = null;
                if (camStand != null) camStand.discard();
                camStand = null;
                for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                    sp.setItemSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
                }
                sp.getInventory().setItem(sp.getInventory().selected, net.minecraft.world.item.ItemStack.EMPTY);
                sp.getInventory().setItem((sp.getInventory().selected + 1) % 9, net.minecraft.world.item.ItemStack.EMPTY);
                sp.getInventory().setItem(8, net.minecraft.world.item.ItemStack.EMPTY);
                sp.getAbilities().flying = true;
                sp.onUpdateAbilities();
            });
        }, 10);
    }

    /** Simulates a key click (consumed by Minecraft#handleKeybinds / the mod's key handlers on the next tick). */
    private static void click(String name, java.util.function.Function<Minecraft, net.minecraft.client.KeyMapping> key, int waitAfter) {
        add(name, mc -> net.minecraft.client.KeyMapping.click(key.apply(mc).getKey()), waitAfter);
    }

    private static void serverAction(String name, String action, int waitAfter) {
        add("action_" + name, mc -> server(mc, sp -> com.huntcraft.gear.weapon.action.CombatActions.start(sp, action)), waitAfter);
    }

    /** Moves the camera stand to a preset (relative to the hunter's start position, looking at its chest). */
    private static void cam(String preset) {
        double[] c = CAMS.get(preset);
        add("cam_" + preset, mc -> {
            server(mc, sp -> placeCamera(c[0], c[1], c[2], c[3]));
            if (mc.player != null && mc.getCameraEntity() == mc.player) attachCamera(mc);
        }, 8);
    }

    private static void setupArena(ServerPlayer sp, double bx, double bz) {
        ServerLevel lvl = sp.serverLevel();
        lvl.getChunk(((int) Math.floor(bx)) >> 4, ((int) Math.floor(bz)) >> 4);
        int gy = lvl.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(bx, 0, bz)).getY();
        gy = Math.max(gy, lvl.getMinBuildHeight() + 2);
        // explicit floor: never trust a heightmap in a test world
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                BlockPos fp = new BlockPos((int) Math.floor(bx) + dx, gy - 1, (int) Math.floor(bz) + dz);
                if (lvl.getBlockState(fp).isAir()) lvl.setBlock(fp, net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            }
        }
        combatX = bx;
        combatY = gy;
        combatZ = bz;
        sp.getAbilities().flying = false;
        sp.onUpdateAbilities();
        sp.fallDistance = 0;
        sp.setDeltaMovement(0, 0, 0);
        sp.teleportTo(lvl, bx, gy, bz, 0, 0);
        sp.setYHeadRot(0);
        sp.yBodyRot = 0;
        log(String.format("combat arena at %.1f %d %.1f (floor %s)", bx, gy, bz, lvl.getBlockState(new BlockPos((int) Math.floor(bx), gy - 1, (int) Math.floor(bz))).getBlock()));
        spawnPartner(lvl);
        net.minecraft.world.entity.decoration.ArmorStand st = net.minecraft.world.entity.EntityType.ARMOR_STAND.create(lvl);
        if (st != null) {
            CompoundTag flags = new CompoundTag();
            st.addAdditionalSaveData(flags);
            flags.putBoolean("Marker", true);
            flags.putBoolean("Invisible", true);
            flags.putBoolean("NoBasePlate", true);
            st.readAdditionalSaveData(flags);   // marker: no hitbox, eye height 0 (camera = stand position)
            st.setInvisible(true);
            st.setNoGravity(true);
            st.setInvulnerable(true);
            st.setSilent(true);
            st.moveTo(bx + 2.4, gy + 1.45, bz + 2.9, 0, 0);
            lvl.addFreshEntity(st);
            camStand = st;
            placeCamera(2.4, 1.45, 2.9, 0.95);
        }
    }

    private static void placeCamera(double dx, double dy, double dz, double lookY) {
        net.minecraft.world.entity.decoration.ArmorStand st = camStand;
        if (st == null) return;
        double x = combatX + dx, y = combatY + dy, z = combatZ + dz;
        double tx = combatX - x, ty = combatY + lookY - y, tz = combatZ - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-tx, tz));
        float pitch = (float) -Math.toDegrees(Math.atan2(ty, Math.sqrt(tx * tx + tz * tz)));
        st.moveTo(x, y, z, yaw, pitch);
        st.setYHeadRot(yaw);
        st.setYBodyRot(yaw);
        st.setXRot(pitch);
    }

    private static void attachCamera(Minecraft mc) {
        if (mc.level == null || camStand == null) {
            log("camera stand missing");
            return;
        }
        net.minecraft.world.entity.Entity e = mc.level.getEntity(camStand.getId());
        if (e == null) {
            log("camera stand not synced to the client yet");
            return;
        }
        mc.setCameraEntity(e);
        if (!camLogged) log("camera attached to entity " + e.getId());
        camLogged = true;
    }

    /** The sparring partner: a boarfang with a huge health pool so every hit sparks and it never becomes a carcass. */
    private static void spawnPartner(ServerLevel lvl) {
        if (current != null) current.discard();
        current = null;
        HuntMonster m = MonsterRegistry.spawn(lvl, "boarfang", BlockPos.containing(combatX - 1.0, combatY, combatZ + 2.6), Rank.LOW, null);
        if (m == null) return;
        m.setNoAi(true);
        m.setMonsterScale(1.0F);
        net.minecraft.world.entity.ai.attributes.AttributeInstance hp = m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(50000.0);
        m.setHealth(m.getMaxHealth());
        m.moveTo(combatX - 1.0, combatY, combatZ + 2.6, 180, 0);
        m.setYRot(180);
        m.yBodyRot = 180;
        m.yHeadRot = 180;
        current = m;
    }

    /** Puts the hunter and the sparring partner back on their marks (root motion moves them during attacks). */
    private static void resetArena(ServerPlayer sp) {
        ServerLevel lvl = sp.serverLevel();
        sp.setDeltaMovement(0, 0, 0);
        sp.teleportTo(lvl, combatX, combatY, combatZ, 0, 0);
        sp.setYHeadRot(0);
        sp.yBodyRot = 0;
        sp.setSprinting(false);
        HuntMonster m = current;
        if (m == null || !m.isAlive() || m.isCarcass()) {
            spawnPartner(lvl);
            return;
        }
        m.setHealth(m.getMaxHealth());
        m.setDeltaMovement(0, 0, 0);
        m.moveTo(combatX - 1.0, combatY, combatZ + 2.6, 180, 0);
        m.setYRot(180);
        m.yBodyRot = 180;
        m.yHeadRot = 180;
    }

    private static void equipWeapon(ServerPlayer sp, String id) {
        net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(HuntCraft.id(id));
        sp.getInventory().setItem(sp.getInventory().selected, item == null ? net.minecraft.world.item.ItemStack.EMPTY : new net.minecraft.world.item.ItemStack(item));
        resetArena(sp);
    }

    private static void equipArmor(ServerPlayer sp) {
        com.huntcraft.gear.armor.ArmorSet set = com.huntcraft.gear.armor.ArmorSets.JAGRAPTOR;
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < 4; i++) sp.setItemSlot(slots[i], new net.minecraft.world.item.ItemStack(set.item(i).get()));
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
