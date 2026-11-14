package com.eternalrealm.quest.forge;

import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.character.ExperienceSource;
import com.eternalrealm.monster.Monsters;
import com.eternalrealm.player.PlayerDataService;
import com.eternalrealm.quest.AcceptResult;
import com.eternalrealm.quest.DailyReset;
import com.eternalrealm.quest.GameEvent;
import com.eternalrealm.quest.ItemReward;
import com.eternalrealm.quest.Npcs;
import com.eternalrealm.quest.Objective;
import com.eternalrealm.quest.QuestContext;
import com.eternalrealm.quest.QuestDefinition;
import com.eternalrealm.quest.QuestEngine;
import com.eternalrealm.quest.QuestKind;
import com.eternalrealm.quest.QuestLogCodec;
import com.eternalrealm.quest.QuestLogState;
import com.eternalrealm.quest.QuestMessages;
import com.eternalrealm.quest.QuestReward;
import com.eternalrealm.quest.QuestUpdate;
import com.eternalrealm.quest.Quests;
import com.eternalrealm.ranking.forge.RankingService;
import com.eternalrealm.server.UiData;
import com.eternalrealm.world.AreaDefinition;
import com.eternalrealm.world.Areas;
import com.eternalrealm.world.forge.WorldService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Quests of the online players: the quest log lives in the character (extension {@value #EXTENSION}), events from
 * kills, pickups, areas, NPCs, dungeons and bosses advance it, and the journal's quest page accepts, abandons and
 * turns in. Daily quests reset at {@link DailyReset#DEFAULT_RESET_TIME} server time.
 */
public final class QuestService {
    private static final Logger LOG = LoggerFactory.getLogger(QuestService.class);
    public static final String EXTENSION = "quests";
    public static final String CHANNEL = "quests";

    private final MinecraftServer server;
    private final PlayerDataService playerData;
    private final WorldService world;
    private final RankingService rankings;
    private final Clock clock;
    private final ZoneId zone = ZoneId.systemDefault();
    private final QuestEngine engine = new QuestEngine(Quests.catalog());
    private final Map<UUID, QuestLogState> logs = new HashMap<>();
    private final QuestGuides guides;
    private long dayIndex;

    public QuestService(MinecraftServer server, PlayerDataService playerData, WorldService world,
                        RankingService rankings, Clock clock) {
        this.server = Objects.requireNonNull(server, "server");
        this.playerData = Objects.requireNonNull(playerData, "playerData");
        this.world = Objects.requireNonNull(world, "world");
        this.rankings = Objects.requireNonNull(rankings, "rankings");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.dayIndex = currentDay();
        this.guides = new QuestGuides(this, server);
        world.onEnter((player, area) -> event(player, new GameEvent.AreaEntered(area.id())));
    }

    public QuestEngine engine() {
        return engine;
    }

    /** The player chose which quest the yellow dots follow ("" the default, "-" none): the journal's 案内 button. */
    public void guide(ServerPlayer player, String questId) {
        guides.choose(player, questId);
        sendView(player);
    }

    private long currentDay() {
        return DailyReset.dayIndex(clock.instant(), zone, DailyReset.DEFAULT_RESET_TIME);
    }

    // ------------------------------------------------------------------ state

    /** The player's quest log (loaded from the character on first use); {@code null} while the character loads. */
    QuestLogState log(ServerPlayer player) {
        UUID id = player.getUUID();
        QuestLogState state = logs.get(id);
        if (state != null) {
            return state;
        }
        if (!playerData.isActive(id)) {
            return null;
        }
        state = playerData.extension(id, EXTENSION).map(json -> {
            try {
                return QuestLogState.restore(QuestLogCodec.fromJson(JsonParser.parseString(json)), engine.catalog());
            } catch (Exception e) {
                LOG.error("Unreadable quest log of {}; starting a new one", player.getGameProfile().getName(), e);
                return QuestLogState.fresh();
            }
        }).orElseGet(QuestLogState::fresh);
        if (engine.resetDailies(state, dayIndex)) {
            save(player, state);
        }
        logs.put(id, state);
        return state;
    }

    /** The progress of an active quest's objectives (empty if it is not active); for GameTests and admin tools. */
    public List<Integer> progressOf(ServerPlayer player, String questId) {
        QuestLogState state = log(player);
        return state == null || !state.isActive(questId) ? List.of() : state.progress(questId);
    }

    private void save(ServerPlayer player, QuestLogState state) {
        playerData.setExtension(player.getUUID(), EXTENSION, QuestLogCodec.toJson(state.snapshot()).toString());
    }

    private QuestContext context(ServerPlayer player) {
        int level = playerData.snapshot(player.getUUID()).map(CharacterSnapshot::level).orElse(1);
        int area = world.areaOf(player).map(AreaDefinition::id).orElse(0);
        return QuestContext.of(level, world.progress(), dayIndex, area);
    }

    // ------------------------------------------------------------------ actions

    public AcceptResult accept(ServerPlayer player, String questId) {
        QuestLogState state = log(player);
        if (state == null) {
            return AcceptResult.UNKNOWN_QUEST;
        }
        AcceptResult result = engine.accept(state, questId, context(player));
        Component name = questName(questId);
        player.displayClientMessage(Component.translatable(result.messageKey(), name)
                .withStyle(result.isSuccess() ? ChatFormatting.GREEN : ChatFormatting.RED), true);
        if (result.isSuccess()) {
            save(player, state);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN,
                    SoundSource.PLAYERS, 1.0F, 1.0F);
            // objectives that are already met (an area the player stands in) count at once
            world.areaOf(player).ifPresent(area -> event(player, new GameEvent.AreaEntered(area.id())));
        }
        sendView(player);
        return result;
    }

    public void abandon(ServerPlayer player, String questId) {
        QuestLogState state = log(player);
        if (state != null && engine.abandon(state, questId)) {
            save(player, state);
            player.displayClientMessage(Component.translatable(QuestMessages.ABANDONED, questName(questId))
                    .withStyle(ChatFormatting.GRAY), true);
        }
        sendView(player);
    }

    public void turnIn(ServerPlayer player, String questId) {
        QuestLogState state = log(player);
        if (state == null) {
            return;
        }
        if (!engine.isReadyToTurnIn(state, questId)) {
            player.displayClientMessage(Component.translatable(QuestMessages.NOT_READY, questName(questId))
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        QuestReward reward = engine.turnIn(state, questId, dayIndex).orElse(null);
        if (reward == null) {
            return;
        }
        save(player, state);
        if (reward.exp() > 0) {
            playerData.grantExperience(player.getUUID(), reward.exp(), ExperienceSource.QUEST);
        }
        if (reward.gold() > 0) {
            playerData.grantGold(player.getUUID(), reward.gold());
        }
        for (ItemReward item : reward.items()) {
            give(player, item);
        }
        player.sendSystemMessage(Component.translatable(QuestMessages.COMPLETED, questName(questId))
                .withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.translatable(QuestMessages.REWARD, reward.exp(), reward.gold())
                .withStyle(ChatFormatting.YELLOW));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS, 0.8F, 1.6F);
        rankings.onQuestCompleted(player);
        com.eternalrealm.sound.forge.MusicContexts.jingle(player, "quest_complete");
        sendView(player);
    }

    private static void give(ServerPlayer player, ItemReward reward) {
        ResourceLocation id = ResourceLocation.tryParse(reward.itemId());
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        if (item == null) {
            return;
        }
        int left = reward.count();
        while (left > 0) {
            int count = Math.min(left, item.getMaxStackSize());
            ItemStack stack = new ItemStack(item, count);
            com.eternalrealm.item.forge.Bags.give(player, stack); // into the bag (and the pickup feed)
            left -= count;
        }
    }

    /** An active quest whose unfinished "talk to" objective points at {@code npcId}. */
    public java.util.Optional<String> questWaitingForTalk(ServerPlayer player, String npcId) {
        QuestLogState state = log(player);
        if (state == null) {
            return java.util.Optional.empty();
        }
        for (String questId : state.activeQuestIds()) {
            QuestDefinition quest = engine.catalog().byId(questId).orElse(null);
            if (quest == null) {
                continue;
            }
            List<Integer> progress = state.progress(questId);
            for (int i = 0; i < quest.objectives().size(); i++) {
                Objective objective = quest.objectives().get(i);
                int done = i < progress.size() ? progress.get(i) : 0;
                if (objective instanceof Objective.TalkTo talk && talk.npcId().equals(npcId)
                        && done < objective.required()) {
                    return java.util.Optional.of(questId);
                }
            }
        }
        return java.util.Optional.empty();
    }

    /** Active quests of this giver's area that are ready to be turned in. */
    public List<String> readyQuestsOfGiver(ServerPlayer player, String npcId) {
        QuestLogState state = log(player);
        int giverArea = Npcs.byId(npcId).map(npc -> npc.areaId()).orElse(-1);
        if (state == null || giverArea < 0) {
            return List.of();
        }
        List<String> ready = new java.util.ArrayList<>();
        for (String questId : state.activeQuestIds()) {
            QuestDefinition quest = engine.catalog().byId(questId).orElse(null);
            if (quest != null && quest.areaId() == giverArea && engine.isReadyToTurnIn(state, questId)) {
                ready.add(questId);
            }
        }
        return ready;
    }

    /** The next quest this quest giver offers that the player can accept now (main story first). */
    public java.util.Optional<String> nextQuestOfGiver(ServerPlayer player, String npcId) {
        QuestLogState state = log(player);
        if (state == null) {
            return java.util.Optional.empty();
        }
        int giverArea = Npcs.byId(npcId).map(npc -> npc.areaId()).orElse(-1);
        if (giverArea < 0) {
            return java.util.Optional.empty();
        }
        for (QuestDefinition quest : engine.available(state, context(player))) {
            if (quest.areaId() == giverArea && quest.kind() != QuestKind.DAILY) {
                return java.util.Optional.of(quest.id());
            }
        }
        return java.util.Optional.empty();
    }

    // ------------------------------------------------------------------ events

    /** Something the player did; progress is announced and saved. */
    public void event(ServerPlayer player, GameEvent event) {
        QuestLogState state = log(player);
        if (state == null || state.activeCount() == 0) {
            return;
        }
        List<QuestUpdate> updates = engine.onEvent(state, event);
        if (updates.isEmpty()) {
            return;
        }
        save(player, state);
        for (QuestUpdate update : updates) {
            Component quest = questName(update.questId());
            if (update instanceof QuestUpdate.ReadyToTurnIn) {
                player.sendSystemMessage(Component.translatable(QuestMessages.READY_TO_TURN_IN, quest)
                        .withStyle(ChatFormatting.GREEN));
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.8F, 1.4F);
            } else if (update instanceof QuestUpdate.ObjectiveCompleted completed) {
                player.displayClientMessage(Component.translatable(QuestMessages.OBJECTIVE_COMPLETE, quest,
                        objectiveLine(update.questId(), completed.objectiveIndex(), state)).withStyle(ChatFormatting.AQUA),
                        true);
            } else if (update instanceof QuestUpdate.ProgressChanged changed) {
                player.displayClientMessage(Component.translatable(QuestMessages.PROGRESS, quest,
                        objectiveLine(update.questId(), changed.objectiveIndex(), state)).withStyle(ChatFormatting.WHITE),
                        true);
            }
        }
        sendView(player);
    }

    public void tickSecond() {
        guides.tick();
        long day = currentDay();
        if (day == dayIndex) {
            return;
        }
        dayIndex = day;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            QuestLogState state = logs.get(player.getUUID());
            if (state != null && engine.resetDailies(state, day)) {
                save(player, state);
                player.sendSystemMessage(Component.translatable(QuestMessages.DAILY_RESET).withStyle(ChatFormatting.AQUA));
            }
        }
    }

    public void onLogout(UUID playerId) {
        logs.remove(playerId);
        guides.onLogout(playerId);
    }

    // ------------------------------------------------------------------ text

    static Component questName(String questId) {
        return Component.translatable("quest.eternalrealm." + questId);
    }

    private Component objectiveLine(String questId, int index, QuestLogState state) {
        QuestDefinition quest = engine.catalog().byId(questId).orElse(null);
        if (quest == null || index < 0 || index >= quest.objectives().size()) {
            return Component.empty();
        }
        List<Integer> progress = state.progress(questId);
        int done = index < progress.size() ? progress.get(index) : quest.objectives().get(index).required();
        JsonArray args = objectiveArgs(quest.objectives().get(index), done);
        Object[] values = new Object[args.size()];
        for (int i = 0; i < values.length; i++) {
            JsonObject arg = args.get(i).getAsJsonObject();
            values[i] = arg.has("k") ? Component.translatable(arg.get("k").getAsString()) : arg.get("t").getAsString();
        }
        MutableComponent line = Component.translatable(quest.objectives().get(index).translationKey(), values);
        return line;
    }

    /** The format arguments of an objective line: {"k": translation key} or {"t": text}. */
    static JsonArray objectiveArgs(Objective objective, int progress) {
        JsonArray args = new JsonArray();
        if (objective instanceof Objective.KillMonsters kill) {
            if (!kill.anyMonster()) {
                args.add(key(Monsters.byId(kill.target()).map(t -> t.translationKey())
                        .orElse("monster.eternalrealm." + kill.target())));
            }
            if (kill.areaId() > 0) {
                args.add(key(Areas.byId(kill.areaId()).map(AreaDefinition::translationKey).orElse("?")));
            }
        } else if (objective instanceof Objective.CollectItems collect) {
            ResourceLocation id = ResourceLocation.tryParse(collect.itemId());
            Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
            args.add(item == null ? text(collect.itemId()) : key(item.getDescriptionId()));
        } else if (objective instanceof Objective.ClearDungeon dungeon) {
            args.add(key("dungeon.eternalrealm." + dungeon.dungeonId()));
        } else if (objective instanceof Objective.DefeatBoss boss) {
            args.add(key("boss.eternalrealm." + boss.bossId()));
            return args;
        } else if (objective instanceof Objective.ReachArea reach) {
            args.add(key(Areas.byId(reach.areaId()).map(AreaDefinition::translationKey).orElse("?")));
            return args;
        } else if (objective instanceof Objective.TalkTo talk) {
            args.add(key(Npcs.byId(talk.npcId()).map(npc -> npc.translationKey()).orElse("npc.eternalrealm." + talk.npcId())));
            return args;
        }
        args.add(text(Integer.toString(Math.min(progress, objective.required()))));
        args.add(text(Integer.toString(objective.required())));
        return args;
    }

    private static JsonObject key(String key) {
        JsonObject arg = new JsonObject();
        arg.addProperty("k", key);
        return arg;
    }

    private static JsonObject text(String text) {
        JsonObject arg = new JsonObject();
        arg.addProperty("t", text);
        return arg;
    }

    // ------------------------------------------------------------------ journal

    /** The quest page: active quests with their objective lines, then the quests that can be accepted now. */
    public void sendView(ServerPlayer player) {
        QuestLogState state = log(player);
        if (state == null) {
            return;
        }
        JsonObject data = new JsonObject();
        JsonArray active = new JsonArray();
        for (String questId : state.activeQuestIds()) {
            QuestDefinition quest = engine.catalog().byId(questId).orElse(null);
            if (quest == null) {
                continue;
            }
            JsonObject entry = describe(quest);
            entry.addProperty("ready", engine.isReadyToTurnIn(state, questId));
            List<Integer> progress = state.progress(questId);
            JsonArray objectives = new JsonArray();
            for (int i = 0; i < quest.objectives().size(); i++) {
                Objective objective = quest.objectives().get(i);
                int done = i < progress.size() ? progress.get(i) : 0;
                JsonObject line = new JsonObject();
                line.addProperty("key", objective.translationKey());
                line.add("args", objectiveArgs(objective, done));
                line.addProperty("done", done >= objective.required());
                objectives.add(line);
            }
            entry.add("objectives", objectives);
            active.add(entry);
        }
        data.add("active", active);
        JsonArray available = new JsonArray();
        for (QuestDefinition quest : engine.available(state, context(player))) {
            available.add(describe(quest));
            if (available.size() >= 12) {
                break;
            }
        }
        data.add("available", available);
        data.addProperty("max", QuestEngine.MAX_ACTIVE_QUESTS);
        data.addProperty("guided", guides.guidedQuest(player));
        UiData.send(player, CHANNEL, data);
    }

    private static JsonObject describe(QuestDefinition quest) {
        JsonObject entry = new JsonObject();
        entry.addProperty("id", quest.id());
        entry.addProperty("kind", quest.kind().name().toLowerCase(java.util.Locale.ROOT));
        entry.addProperty("main", quest.kind() == QuestKind.MAIN);
        entry.addProperty("area", quest.areaId());
        entry.addProperty("level", quest.prerequisites().minLevel());
        entry.addProperty("exp", quest.reward().exp());
        entry.addProperty("gold", quest.reward().gold());
        return entry;
    }
}
