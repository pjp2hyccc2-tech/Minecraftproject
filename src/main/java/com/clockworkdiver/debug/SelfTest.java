package com.clockworkdiver.debug;

import com.clockworkdiver.ClockworkDiver;
import com.clockworkdiver.block.MechanismBlockEntity;
import com.clockworkdiver.block.ToothSocketBlock;
import com.clockworkdiver.block.TuningBellBlock;
import com.clockworkdiver.clock.ClockData;
import com.clockworkdiver.clock.ClockType;
import com.clockworkdiver.clock.CommissionGenerator;
import com.clockworkdiver.clock.Defect;
import com.clockworkdiver.clock.DefectType;
import com.clockworkdiver.clock.DiveMods;
import com.clockworkdiver.customer.CustomerEntity;
import com.clockworkdiver.customer.CustomerProfile;
import com.clockworkdiver.data.PlayerProgress;
import com.clockworkdiver.data.WorldProgress;
import com.clockworkdiver.entity.BoltProjectile;
import com.clockworkdiver.entity.CogGremlin;
import com.clockworkdiver.entity.TimeEater;
import com.clockworkdiver.interior.DiveManager;
import com.clockworkdiver.interior.ZoneBuilder;
import com.clockworkdiver.interior.ZoneLayout;
import com.clockworkdiver.registry.ModBlocks;
import com.clockworkdiver.registry.ModDimensions;
import com.clockworkdiver.registry.ModEntities;
import com.clockworkdiver.story.StoryManager;
import com.clockworkdiver.story.StoryQuest;
import com.clockworkdiver.story.StoryQuests;
import com.clockworkdiver.workshop.WorkshopBuilder;
import com.clockworkdiver.workshop.WorkshopManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * CI only (-Dclockworkdiver.selftest=true): on a dedicated server, builds a workshop and every repair
 * chamber, checks the story data, the language files and a full play-through of the story's progress,
 * spawns every entity and lets them tick for ten seconds, then stops the server and writes
 * {@code run/selftest-report.txt}.
 */
@Mod.EventBusSubscriber(modid = ClockworkDiver.MODID)
public final class SelfTest {
    private static final boolean ENABLED = Boolean.getBoolean("clockworkdiver.selftest");
    private static final List<String> LINES = new ArrayList<>();
    private static boolean failed;
    private static int ticks = -1;
    private static CustomerEntity customer;
    private static double customerStartDist;
    private static final List<Entity> SPAWNED = new ArrayList<>();

    private SelfTest() {}

    private static void check(String name, boolean ok) {
        LINES.add((ok ? "ok   " : "FAIL ") + name);
        if (!ok) failed = true;
    }

    private interface Check {
        void run() throws Exception;
    }

    private static void section(String name, Check body) {
        long t0 = System.nanoTime();
        try {
            body.run();
            LINES.add("     (" + name + " took " + (System.nanoTime() - t0) / 1_000_000 + " ms)");
        } catch (Throwable e) {
            failed = true;
            LINES.add("FAIL " + name + " threw " + e);
            ClockworkDiver.LOGGER.error("Self test section {} failed", name, e);
        }
    }

    private static JsonObject lang(String code) throws IOException {
        try (InputStream in = SelfTest.class.getResourceAsStream("/assets/clockworkdiver/lang/" + code + ".json")) {
            if (in == null) throw new IOException("missing lang " + code);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static boolean solidBelowFree(ServerLevel level, BlockPos p) {
        return !level.getBlockState(p.below()).isAir() && level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir();
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!ENABLED) return;
        MinecraftServer server = event.getServer();
        LINES.add("Clockwork Diver self test");
        ServerLevel ws = server.getLevel(ModDimensions.WORKSHOP);
        ServerLevel interior = server.getLevel(ModDimensions.INTERIOR);
        check("workshop dimension loaded", ws != null);
        check("clock interior dimension loaded", interior != null);
        check("command /clockwork registered", server.getCommands().getDispatcher().getRoot().getChild("clockwork") != null);

        section("commission data", () -> {
            RandomSource rand = RandomSource.create(42);
            int n = 0;
            boolean roundTrip = true;
            for (int rep = 0; rep <= 20; rep++) {
                for (int i = 0; i < 25; i++) {
                    ClockData c = CommissionGenerator.random(rand, rep, n++);
                    ClockData back = ClockData.load(c.save());
                    roundTrip &= back != null && back.save().equals(c.save()) && !c.defects.isEmpty() && c.type != ClockType.GREAT_CLOCK
                            && c.profile.regular;
                }
            }
            check("commission save/load round trip (" + n + " clocks, regular clients only)", roundTrip);
            ClockData tut = CommissionGenerator.tutorial(1);
            check("tutorial clock has 2 defects", tut.defects.size() == 2 && tut.tutorial);
        });

        section("story data", () -> {
            check("story has 20 quests", StoryQuests.count() == 20);
            for (int c = 1; c <= StoryQuests.CHAPTERS; c++) {
                check("chapter " + c + " has 4 quests", StoryQuests.chapter(c).size() == 4);
            }
            boolean allOk = true;
            StringBuilder bad = new StringBuilder();
            for (StoryQuest q : StoryQuests.all()) {
                ClockData c = q.createClock(100 + q.number);
                ClockData back = ClockData.load(c.save());
                Set<String> anchors = new HashSet<>();
                for (Defect d : c.defects) anchors.add(d.x + "," + d.y);
                int frags = 0;
                for (Defect d : c.defects) frags += d.fragCount;
                boolean ok = back != null && back.save().equals(c.save()) && c.isStory() && c.faultCount() >= 1
                        && anchors.size() == c.defects.size() && frags == q.totalFragments() && c.level >= 0
                        && (!q.tower || q.type == ClockType.GREAT_CLOCK) && StoryManager.of(c) == q;
                for (Defect d : c.defects) {
                    if (DiveMods.has(d.mods, DiveMods.TIMED) && d.timeLimit < 60) ok = false;
                    if (d.boss != 0 && d.type != DefectType.NEST) ok = false;
                }
                if (!ok) {
                    allOk = false;
                    bad.append(q.id).append(' ');
                }
            }
            check("story clocks valid and round trip " + bad, allOk);
            check("final quest is the master's", StoryQuests.get(19).client == CustomerProfile.ELIAS);
            check("queen fight is in the Great Clock", StoryQuests.get(18).parts.get(0).boss() == Defect.BOSS_QUEEN);
        });

        section("story play-through", () -> {
            PlayerProgress p = new PlayerProgress(UUID.randomUUID());
            check("story inactive before the tutorial", !StoryManager.active(p));
            p.tutorialDone = true;
            boolean ok = true;
            int keepsakes = 0;
            for (int i = 0; i < StoryQuests.count(); i++) {
                if (!StoryManager.active(p) || StoryManager.current(p) != StoryQuests.get(i)) ok = false;
                ClockData c = StoryManager.commission(p, i + 1);
                if (!c.story.equals(StoryQuests.get(i).id)) ok = false;
                ClockData saved = ClockData.load(c.save());
                if (saved == null) {
                    ok = false;
                    continue;
                }
                for (Defect d : saved.defects) {
                    d.found = true;
                    d.repaired = true;
                }
                if (!saved.allRepaired()) ok = false;
                if (StoryManager.advance(p, saved, 1000L * i) != null) keepsakes++;
                if (p.storyIndex != i + 1) ok = false;
            }
            check("all 20 quests advance in order", ok);
            check("story finished afterwards", StoryManager.finished(p) && !StoryManager.active(p) && StoryManager.current(p) == null);
            check("20 keepsakes handed over", keepsakes == 20 && Integer.bitCount(p.keepsakes) == 20);
            ClockData again = StoryQuests.get(4).createClock(9);
            check("replaying a delivered quest gives nothing new", StoryManager.advance(p, again, 0) == null && p.storyIndex == 20);
            PlayerProgress back = PlayerProgress.load(p.save());
            check("story progress saves", back.storyIndex == 20 && back.keepsakes == p.keepsakes);
        });

        section("language files", () -> {
            JsonObject ja = lang("ja_jp");
            JsonObject en = lang("en_us");
            List<String> missing = new ArrayList<>();
            List<String> keys = new ArrayList<>();
            for (StoryQuest q : StoryQuests.all()) {
                for (String suffix : new String[]{"title", "clock", "clock.desc", "offer.0", "offer.1", "wait", "deliver", "thanks.0", "thanks.1",
                        "summary", "keepsake", "keepsake.desc"}) {
                    keys.add(q.key(suffix));
                }
                for (int i = 0; i < q.totalFragments(); i++) keys.add(q.key("frag." + i));
                if (q.hasClue()) {
                    keys.add(q.key("clue"));
                    keys.add(q.key("clue.desc"));
                }
                for (CustomerProfile extra : q.extras) keys.add("story.clockworkdiver.scene." + StoryManager.sceneOf(q) + "." + extra.id);
            }
            for (int c = 1; c <= StoryQuests.CHAPTERS; c++) {
                keys.add(StoryQuests.chapterKey(c));
                keys.add(StoryQuests.chapterKey(c) + ".sub");
                keys.add(StoryQuests.chapterKey(c) + ".summary");
                keys.add("hud.clockworkdiver.chapter_kanji." + c);
            }
            keys.add("story.clockworkdiver.ending.0");
            for (String m : DiveMods.NAMES) keys.add("mod.clockworkdiver." + m);
            keys.add("mod.clockworkdiver.timed.time");
            for (DefectType t : DefectType.values()) {
                keys.add(t.nameKey());
                keys.add(t.nameKey() + ".desc");
                if (t.divable()) {
                    keys.add(t.objectiveKey());
                    keys.add("hint.clockworkdiver." + t.id + ".0");
                    keys.add("hint.clockworkdiver." + t.id + ".1");
                }
                for (String part : t.parts) keys.add(DefectType.partKey(part));
            }
            for (ClockType t : ClockType.values()) {
                keys.add(t.nameKey());
                keys.add(t.descKey());
            }
            for (CustomerProfile pr : CustomerProfile.values()) {
                keys.add(pr.nameKey());
                if (pr.regular) {
                    for (String l : new String[]{"offer.0", "offer.1", "wait", "deliver", "thanks.0", "thanks.1"}) keys.add(pr.lineKey(l));
                }
            }
            for (int b = 1; b <= 3; b++) keys.add("gui.clockworkdiver.twist.boss." + b);
            for (String k : new String[]{"entity.clockworkdiver.time_eater.shadow", "entity.clockworkdiver.time_eater.shade",
                    "entity.clockworkdiver.time_eater.queen", "title.clockworkdiver.boss.shadow", "title.clockworkdiver.boss.shade.sub",
                    "title.clockworkdiver.boss.queen", "title.clockworkdiver.queen.2", "title.clockworkdiver.queen.3.sub",
                    "objective.clockworkdiver.fragments", "objective.clockworkdiver.dive.fragments_left", "objective.clockworkdiver.find_tower",
                    "objective.clockworkdiver.talk_tower", "objective.clockworkdiver.deliver_tower", "objective.clockworkdiver.return",
                    "hint.clockworkdiver.timed.0", "hint.clockworkdiver.offbeat.0", "hint.clockworkdiver.dark.0", "hint.clockworkdiver.precision.0",
                    "hint.clockworkdiver.fragment.0", "hint.clockworkdiver.clue.0", "hint.clockworkdiver.tower.1", "hint.clockworkdiver.story.1",
                    "hint.clockworkdiver.postgame.1", "message.clockworkdiver.tune_play", "message.clockworkdiver.dust_cleared",
                    "message.clockworkdiver.gate_locked", "message.clockworkdiver.gate_wait", "message.clockworkdiver.gate_peace",
                    "message.clockworkdiver.customer_at_tower", "message.clockworkdiver.story_upgrade", "message.clockworkdiver.time_up",
                    "dialogue.clockworkdiver.offer.story", "dialogue.clockworkdiver.keepsake", "speaker.clockworkdiver.note",
                    "speaker.clockworkdiver.mira", "speaker.clockworkdiver.voice",
                    "gui.clockworkdiver.journal", "gui.clockworkdiver.quest_tag", "gui.clockworkdiver.ending.thanks", "key.clockworkdiver.journal",
                    "hud.clockworkdiver.quest_no", "hud.clockworkdiver.chapter_end", "hud.clockworkdiver.memory", "hud.clockworkdiver.story_line",
                    "item.clockworkdiver.keepsake", "block.clockworkdiver.tuning_bell", "block.clockworkdiver.tower_gate",
                    "tooltip.clockworkdiver.keepsake.from", "story.clockworkdiver.debug.frag", "command.clockworkdiver.story.status"}) {
                keys.add(k);
            }
            for (String k : keys) {
                if (!ja.has(k)) missing.add("ja:" + k);
                if (!en.has(k)) missing.add("en:" + k);
            }
            check("language keys present (" + keys.size() + " checked) " + (missing.size() > 8 ? missing.subList(0, 8) + "..." : missing),
                    missing.isEmpty());
            Set<String> jaKeys = ja.keySet(), enKeys = en.keySet();
            List<String> onlyJa = new ArrayList<>(), onlyEn = new ArrayList<>();
            for (String k : jaKeys) if (!enKeys.contains(k)) onlyJa.add(k);
            for (String k : enKeys) if (!jaKeys.contains(k)) onlyEn.add(k);
            check("ja_jp and en_us have the same keys (" + jaKeys.size() + ") " + onlyJa + onlyEn, onlyJa.isEmpty() && onlyEn.isEmpty());
            Set<String> speakers = new HashSet<>(List.of("me", "narr", "note", "mira", "voice"));
            for (CustomerProfile pr : CustomerProfile.values()) speakers.add(pr.id);
            List<String> badSpeakers = new ArrayList<>();
            for (Map.Entry<String, com.google.gson.JsonElement> e : ja.entrySet()) {
                String k = e.getKey();
                if (!k.startsWith("story.clockworkdiver.q") && !k.startsWith("story.clockworkdiver.scene.")) continue;
                String v = e.getValue().getAsString();
                int bar = v.indexOf('|');
                if (bar > 0 && bar <= 10 && !speakers.contains(v.substring(0, bar))) badSpeakers.add(k);
                if (v.contains("%")) badSpeakers.add(k + " (percent)");
            }
            check("story speakers known " + badSpeakers, badSpeakers.isEmpty());
        });

        if (ws == null || interior == null) {
            ticks = 0;
            return;
        }
        int slot = WorldProgress.get(server).reserveSlot();
        BlockPos wo = WorkshopManager.origin(slot);
        section("workshop build", () -> {
            forceChunks(ws, wo, 3);
            WorkshopBuilder.build(ws, wo);
            check("repair bench placed", ws.getBlockState(wo.offset(WorkshopManager.BENCH)).is(ModBlocks.REPAIR_BENCH.get()));
            check("gear train block entity", ws.getBlockEntity(wo.offset(0, 3, -7)) instanceof MechanismBlockEntity);
            check("tower clock block entity", ws.getBlockEntity(wo.offset(0, 26, 33)) instanceof MechanismBlockEntity);
            check("counter spot is free", ws.getBlockState(wo.offset(WorkshopManager.COUNTER_SPOT)).isAir());
            check("street spawn is free", solidBelowFree(ws, wo.offset(WorkshopManager.STREET_SPAWN)));
            check("archway to the plaza is open", solidBelowFree(ws, wo.offset(0, 0, 29)));
            check("tower gate placed", ws.getBlockState(wo.offset(StoryManager.TOWER_GATE)).is(ModBlocks.TOWER_GATE.get())
                    && ws.getBlockState(wo.offset(StoryManager.TOWER_GATE).above()).is(ModBlocks.TOWER_GATE.get()));
            check("plaza bench spot is free", solidBelowFree(ws, wo.offset(StoryManager.PLAZA_BENCH)));
            check("Pip's spot is free", solidBelowFree(ws, wo.offset(StoryManager.PIP_SPOT)));
            List<String> blocked = new ArrayList<>();
            for (boolean tower : new boolean[]{true, false}) {
                for (Map.Entry<CustomerProfile, BlockPos> e : StoryManager.spots(tower).entrySet()) {
                    if (!solidBelowFree(ws, wo.offset(e.getValue()))) blocked.add((tower ? "tower:" : "finale:") + e.getKey().id);
                }
            }
            check("scene spots are free " + blocked, blocked.isEmpty());
        });
        section("customer spawn", () -> {
            customer = ModEntities.CUSTOMER.get().create(ws);
            check("customer created", customer != null);
            if (customer == null) return;
            BlockPos counter = wo.offset(WorkshopManager.COUNTER_SPOT);
            BlockPos street = wo.offset(WorkshopManager.STREET_SPAWN);
            customer.setup(UUID.randomUUID(), CustomerProfile.ADA, counter, street);
            customer.moveTo(street.getX() + 0.5, street.getY(), street.getZ() + 0.5, 180.0f, 0.0f);
            customer.setState(CustomerEntity.ARRIVING);
            check("customer added", ws.addFreshEntity(customer));
            customerStartDist = Math.sqrt(customer.distanceToSqr(counter.getX() + 0.5, counter.getY(), counter.getZ() + 0.5));
            CustomerEntity friend = ModEntities.CUSTOMER.get().create(ws);
            if (friend != null) {
                BlockPos spot = wo.offset(StoryManager.spots(true).get(CustomerProfile.BOLT));
                friend.setupExtra(UUID.randomUUID(), CustomerProfile.BOLT, spot, StoryManager.SCENE_TOWER);
                friend.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0.0f, 0.0f);
                check("scene friend added", ws.addFreshEntity(friend) && friend.isExtra());
                SPAWNED.add(friend);
            }
        });

        BlockPos base = DiveManager.zoneOrigin(slot);
        for (DefectType type : DefectType.values()) {
            if (!type.divable()) continue;
            BlockPos zo = base.offset(0, 0, type.ordinal() * 72);
            section("zone " + type.id, () -> {
                forceChunks(interior, zo, 2);
                ZoneLayout layout = ZoneBuilder.build(interior, zo, type, 2, 16, false, RandomSource.create(type.ordinal()));
                BlockPos feet = BlockPos.containing(layout.spawn);
                check(type.id + ": spawn has floor and headroom", solidBelowFree(interior, feet));
                check(type.id + ": mechanisms placed (" + layout.mechanisms.size() + ")", !layout.mechanisms.isEmpty()
                        && layout.mechanisms.stream().allMatch(p -> interior.getBlockEntity(p) instanceof MechanismBlockEntity));
                switch (type) {
                    case RUST -> check("rust: clusters placed (" + layout.targets.size() + ")", layout.targets.size() >= 8
                            && layout.targets.stream().allMatch(p -> interior.getBlockState(p).is(ModBlocks.RUST_CLUSTER.get())));
                    case TEETH -> {
                        check("teeth: sockets placed (" + layout.targets.size() + ")", layout.targets.size() >= 3
                                && layout.targets.stream().allMatch(p -> {
                                    BlockState s = interior.getBlockState(p);
                                    return s.is(ModBlocks.TOOTH_SOCKET.get()) && !s.getValue(ToothSocketBlock.FILLED);
                                }));
                        check("teeth: escape wheel missing teeth", interior.getBlockEntity(layout.mainMechanism) instanceof MechanismBlockEntity m
                                && m.missing == layout.targets.size());
                    }
                    case SCREW -> {
                        int h = layout.screwHeight;
                        check("screw: head at height " + h, interior.getBlockState(zo.offset(0, h, 0)).is(ModBlocks.SCREW_HEAD.get()));
                        ZoneBuilder.rebuildScrew(interior, zo, h - 1, 1);
                        check("screw: rebuilt one block lower", interior.getBlockState(zo.offset(0, h - 1, 0)).is(ModBlocks.SCREW_HEAD.get())
                                && interior.getBlockState(zo.offset(0, h + 1, 0)).isAir());
                        ZoneBuilder.rebuildScrew(interior, zo, h, 0);
                        check("screw: rebuilt back up (precision slip)", interior.getBlockState(zo.offset(0, h, 0)).is(ModBlocks.SCREW_HEAD.get()));
                    }
                    case SPRING -> check("spring: crank placed", layout.crank != null
                            && interior.getBlockState(layout.crank).is(ModBlocks.WINDING_CRANK.get()));
                    case NEST -> check("nest: balance wheel", interior.getBlockEntity(layout.mainMechanism) instanceof MechanismBlockEntity m
                            && m.kind == MechanismBlockEntity.BALANCE);
                    case DUST -> check("dust: heaps placed on solid ground (" + layout.targets.size() + ")", layout.targets.size() >= 8
                            && layout.targets.stream().allMatch(p -> interior.getBlockState(p).is(ModBlocks.DUST_PILE.get())
                            && !interior.getBlockState(p.below()).isAir()));
                    case TUNING -> {
                        boolean bells = layout.bells.size() == 4;
                        for (int i = 0; i < layout.bells.size(); i++) {
                            BlockState s = interior.getBlockState(layout.bells.get(i));
                            bells &= s.is(ModBlocks.TUNING_BELL.get()) && s.getValue(TuningBellBlock.NOTE) == i && !s.getValue(TuningBellBlock.LIT);
                        }
                        check("tuning: four bells in note order", bells);
                        BlockPos stand = layout.bells.get(0).offset(3, -1, 3);
                        boolean reach = true;
                        for (BlockPos b : layout.bells) reach &= b.distSqr(stand) <= 4.6 * 4.6;
                        check("tuning: every bell in reach from one spot", reach && solidBelowFree(interior, stand));
                        check("tuning: drum mechanism", layout.mainMechanism != null
                                && interior.getBlockEntity(layout.mainMechanism) instanceof MechanismBlockEntity);
                    }
                    case CLUE -> {
                    }
                }
                check(type.id + ": steam vents (" + layout.vents.size() + ")", !layout.vents.isEmpty()
                        && layout.vents.stream().allMatch(p -> interior.getBlockState(p).is(ModBlocks.STEAM_VENT.get())));
                check(type.id + ": enemy spawn points (" + layout.enemySpawns.size() + ")", layout.enemySpawns.size() >= 6);
            });
        }

        section("dive twists", () -> {
            BlockPos zo = base.offset(0, 0, 9 * 72);
            forceChunks(interior, zo, 2);
            ZoneLayout dark = ZoneBuilder.build(interior, zo, DefectType.RUST, 3, 16, false, RandomSource.create(5), DiveMods.DARK, 0);
            boolean lampsOut = !interior.getBlockState(zo.below()).is(ModBlocks.JEWEL_BEARING.get())
                    && !interior.getBlockState(zo.offset(0, 1, 0)).is(ModBlocks.JEWEL_BEARING.get());
            check("dark chamber has no jewel lamps", lampsOut && dark.targets.size() >= 8);
            ZoneLayout novent = ZoneBuilder.build(interior, zo, DefectType.SPRING, 3, 16, false, RandomSource.create(6), DiveMods.NO_VENTS, 0);
            check("no-vent chamber has no vents", novent.vents.isEmpty() && !interior.getBlockState(zo.offset(7, -1, 0)).is(ModBlocks.STEAM_VENT.get()));
            for (DefectType t : new DefectType[]{DefectType.NEST, DefectType.SCREW, DefectType.TEETH, DefectType.DUST, DefectType.TUNING,
                    DefectType.SPRING, DefectType.RUST}) {
                ZoneLayout frag = ZoneBuilder.build(interior, zo, t, 3, 16, false, RandomSource.create(t.ordinal() + 11), 0, 4);
                boolean ok = frag.fragments.size() == 4;
                for (BlockPos p : frag.fragments) {
                    ok &= interior.getBlockState(p).is(ModBlocks.MEMORY_COG.get()) && !interior.getBlockState(p.below()).isAir()
                            && interior.getBlockState(p.above()).isAir();
                }
                check(t.id + ": memory fragments placed and reachable (" + frag.fragments.size() + ")", ok);
            }
            // Leave a plain chamber behind for the enemies below.
            ZoneBuilder.build(interior, zo, DefectType.NEST, 2, 16, false, RandomSource.create(1));
        });

        section("enemies", () -> {
            BlockPos zo = base;
            List<EntityType<? extends Mob>> types = List.of(ModEntities.RUST_MITE.get(), ModEntities.COG_GREMLIN.get(),
                    ModEntities.DUST_MOTH.get(), ModEntities.TIME_EATER.get());
            int i = 0;
            for (EntityType<? extends Mob> type : types) {
                Mob mob = type.create(interior);
                check("create " + EntityType.getKey(type), mob != null);
                if (mob == null) continue;
                mob.moveTo(zo.getX() - 5.5 + i * 4, zo.getY() + (type == ModEntities.DUST_MOTH.get() ? 4 : 0.2), zo.getZ() + 15.5, 0.0f, 0.0f);
                if (mob instanceof CogGremlin g) g.setHasTooth(true);
                check("spawn " + EntityType.getKey(type), interior.addFreshEntity(mob));
                SPAWNED.add(mob);
                i++;
            }
            BlockPos bz = base.offset(0, 0, 9 * 72);
            for (int v = TimeEater.SHADOW; v <= TimeEater.QUEEN; v++) {
                TimeEater boss = ModEntities.TIME_EATER.get().create(interior);
                if (boss == null) continue;
                boss.setVariant(v);
                boss.moveTo(bz.getX() - 8.5 + v * 5, bz.getY() + 0.2, bz.getZ() + 0.5, 0.0f, 0.0f);
                check("spawn time eater variant " + v, interior.addFreshEntity(boss) && boss.variant() == v);
                SPAWNED.add(boss);
            }
            BoltProjectile bolt = new BoltProjectile(ModEntities.BOLT.get(), interior);
            bolt.moveTo(zo.getX() + 0.5, zo.getY() + 3, zo.getZ() + 6.5);
            bolt.shoot(0.0, -0.2, -1.0, 1.0f, 0.0f);
            check("spawn bolt", interior.addFreshEntity(bolt));
        });
        ticks = 0;
    }

    private static void forceChunks(ServerLevel level, BlockPos center, int radiusChunks) {
        int cx = center.getX() >> 4, cz = center.getZ() >> 4;
        for (int x = cx - radiusChunks; x <= cx + radiusChunks; x++) {
            for (int z = cz - radiusChunks; z <= cz + radiusChunks + 1; z++) level.setChunkForced(x, z, true);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (!ENABLED || ticks < 0 || event.phase != TickEvent.Phase.END) return;
        ticks++;
        if (ticks == 220) {
            section("after 11 seconds of ticking", () -> {
                if (customer != null) {
                    BlockPos counter = customer.counterPos();
                    double d = counter == null ? 99 : Math.sqrt(customer.distanceToSqr(counter.getX() + 0.5, counter.getY(), counter.getZ() + 0.5));
                    check(String.format("customer walked to the counter (%.1f -> %.1f blocks, state %d)", customerStartDist, d, customer.state()),
                            customer.state() == CustomerEntity.WAITING || d < customerStartDist - 4.0);
                }
                int alive = 0;
                for (Entity e : SPAWNED) if (e.isAlive()) alive++;
                check("entities still alive and ticking (" + alive + "/" + SPAWNED.size() + ")", alive == SPAWNED.size());
                boolean bossOk = SPAWNED.stream().anyMatch(e -> e instanceof TimeEater t && t.isAlive() && t.variant() == TimeEater.NORMAL && !t.isEnraged());
                check("time eater ticking", bossOk);
                boolean queenBig = SPAWNED.stream().anyMatch(e -> e instanceof TimeEater t && t.variant() == TimeEater.QUEEN
                        && t.getBbWidth() > 3.0f);
                check("queen is bigger than her kin", queenBig);
            });
            finish(event.getServer());
        }
    }

    private static void finish(MinecraftServer server) {
        LINES.add("SELFTEST RESULT: " + (failed ? "FAIL" : "PASS"));
        try {
            Files.write(Path.of("selftest-report.txt"), LINES, StandardCharsets.UTF_8);
        } catch (IOException e) {
            ClockworkDiver.LOGGER.error("Could not write self test report", e);
        }
        ClockworkDiver.LOGGER.info("Clockwork Diver self test: {}", failed ? "FAIL" : "PASS");
        server.halt(false);
    }
}
