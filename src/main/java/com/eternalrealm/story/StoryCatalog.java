package com.eternalrealm.story;

import com.eternalrealm.core.ModInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * The whole story as data: which dialogue a {@link StoryTrigger} plays, area title cards, boss phase barks, the ending
 * and the staff roll. Immutable and shared ({@link #standard()}); the content is defined in {@code StoryScript}, the
 * texts in docs/i18n/story.json.
 *
 * <p><b>Keys.</b> Every line of dialogue {@code <id>} has the key {@code story.eternalrealm.<id>.<n>} (n from 1),
 * except quest dialogues, which use the quest module's scheme {@code dialogue.eternalrealm.<npcId>.<questId>.<n>}.
 * Speakers, chapters, area cards, barks ({@code story.eternalrealm.boss.<bossId>.phase<k>}) and credits
 * ({@code story.eternalrealm.staff.<id>}) have one key each. {@link #translationKeys()} lists them all.</p>
 *
 * <p><b>Dialogue ids.</b> {@code prologue}; {@code area.<slug>.enter|unlocked}; {@code dungeon.<id>.enter};
 * {@code boss.<id>.intro|defeat|first_defeat}; {@code final.phase<k>}; {@code ending};
 * {@code epilogue.victory|unfinished}; {@code day.<1..12>|eve|final|extra|peace|farewell};
 * {@code npc.<npcId>.<progress>}; {@code quest.<npcId>.<questId>}.</p>
 */
public final class StoryCatalog {
    /** Prefix of the story's own translation keys. */
    public static final String KEY_PREFIX = "story." + ModInfo.MOD_ID + ".";
    /** Prefix of the quest dialogue keys (the quest module's scheme). */
    public static final String QUEST_KEY_PREFIX = "dialogue." + ModInfo.MOD_ID + ".";
    /** Pause between two chat lines of a dialogue (~2 s). */
    public static final int LINE_INTERVAL_TICKS = 40;
    /** The story is written for a season of this many days. */
    public static final int STANDARD_SEASON_DAYS = 14;
    /** Day lines numbered by day; the last two days of any season have their own lines (eve, final). */
    static final int NUMBERED_DAYS = 12;

    private static final StoryCatalog STANDARD = StoryScript.content().build();

    private final Map<String, Dialogue> dialogues;
    private final Map<StoryArea, AreaCard> areaCards;
    private final Map<String, DialogueLine> barks;
    private final EndingSequence ending;
    private final SortedSet<String> translationKeys;

    private StoryCatalog(Map<String, Dialogue> dialogues, Map<StoryArea, AreaCard> areaCards,
                         Map<String, DialogueLine> barks, EndingSequence ending) {
        this.dialogues = Collections.unmodifiableMap(new LinkedHashMap<>(dialogues));
        this.areaCards = Collections.unmodifiableMap(new EnumMap<>(areaCards));
        this.barks = Collections.unmodifiableMap(new LinkedHashMap<>(barks));
        this.ending = Objects.requireNonNull(ending, "ending");
        SortedSet<String> keys = new TreeSet<>();
        for (Speaker speaker : Speaker.values()) {
            keys.add(speaker.key());
        }
        for (Chapter chapter : Chapter.values()) {
            keys.add(chapter.titleKey());
            keys.add(chapter.synopsisKey());
        }
        for (AreaCard card : this.areaCards.values()) {
            keys.add(card.titleKey());
            keys.add(card.subtitleKey());
        }
        for (Dialogue dialogue : this.dialogues.values()) {
            for (DialogueLine line : dialogue.lines()) {
                keys.add(line.textKey());
            }
        }
        for (DialogueLine bark : this.barks.values()) {
            keys.add(bark.textKey());
        }
        for (EndingSequence.Credit credit : ending.staffRoll()) {
            keys.add(credit.textKey());
        }
        this.translationKeys = Collections.unmodifiableSortedSet(keys);
    }

    /** The built-in story. */
    public static StoryCatalog standard() {
        return STANDARD;
    }

    /**
     * The dialogue to play for {@code trigger}, or empty if there is none (an id the story does not know, area 1's
     * unlock, a story character who is not in town yet).
     * <ul>
     *     <li>{@link StoryTrigger.NpcTalk}: the NPC's line for the progress, or its latest earlier line; a quest giver
     *         talked to before its first line uses that first line</li>
     *     <li>{@link StoryTrigger.DayStarted}: days 1..12 by number, then the eve and the final day of the season
     *         (whatever its length), "extra" for further days of a long season, and peaceful lines after the
     *         victory</li>
     *     <li>{@link StoryTrigger.Ending}: the lines of {@link #ending()} without timing</li>
     * </ul>
     */
    public Optional<Dialogue> dialogue(StoryTrigger trigger) {
        Objects.requireNonNull(trigger, "trigger");
        if (trigger instanceof StoryTrigger.FirstLogin) {
            return byId("prologue");
        } else if (trigger instanceof StoryTrigger.AreaEntered entered) {
            return StoryArea.byId(entered.areaId()).flatMap(area -> byId("area." + area.slug() + ".enter"));
        } else if (trigger instanceof StoryTrigger.AreaUnlocked unlocked) {
            return StoryArea.byId(unlocked.areaId()).flatMap(area -> byId("area." + area.slug() + ".unlocked"));
        } else if (trigger instanceof StoryTrigger.DungeonEntered entered) {
            return byId("dungeon." + entered.dungeonId() + ".enter");
        } else if (trigger instanceof StoryTrigger.BossEncounter encounter) {
            return byId("boss." + encounter.bossId() + ".intro");
        } else if (trigger instanceof StoryTrigger.BossDefeated defeated) {
            return byId("boss." + defeated.bossId() + (defeated.firstServerKill() ? ".first_defeat" : ".defeat"));
        } else if (trigger instanceof StoryTrigger.NpcTalk talk) {
            return npcDialogue(talk.npcId(), talk.storyProgress());
        } else if (trigger instanceof StoryTrigger.QuestTalk talk) {
            return byId(questDialogueId(talk.npcId(), talk.questId()));
        } else if (trigger instanceof StoryTrigger.FinalPhase phase) {
            return byId("final.phase" + phase.phase());
        } else if (trigger instanceof StoryTrigger.Ending) {
            return byId("ending");
        } else if (trigger instanceof StoryTrigger.DayStarted day) {
            return byId(dayDialogueId(day));
        } else if (trigger instanceof StoryTrigger.SeasonEnded ended) {
            return byId(ended.kingDefeated() ? "epilogue.victory" : "epilogue.unfinished");
        }
        throw new IllegalArgumentException("unknown trigger " + trigger);
    }

    public Optional<Dialogue> byId(String id) {
        return Optional.ofNullable(dialogues.get(id));
    }

    /** Every dialogue in definition order. */
    public List<Dialogue> dialogues() {
        return List.copyOf(dialogues.values());
    }

    public Optional<AreaCard> areaCard(int areaId) {
        return StoryArea.byId(areaId).map(areaCards::get);
    }

    /**
     * A one-line shout of boss {@code bossId} when it enters phase {@code phase} (2 or later; 1-based like
     * {@link StoryTrigger.FinalPhase}). Meant for the action bar. The King of the End uses
     * {@link StoryTrigger.FinalPhase} instead.
     */
    public Optional<DialogueLine> bossPhaseBark(String bossId, int phase) {
        return Optional.ofNullable(barks.get(barkId(bossId, phase)));
    }

    public EndingSequence ending() {
        return ending;
    }

    /** The quests that have a dialogue with {@code npc}, in definition order. */
    public List<String> questIds(StoryNpc npc) {
        String prefix = "quest." + npc.id() + ".";
        List<String> quests = new ArrayList<>();
        for (String id : dialogues.keySet()) {
            if (id.startsWith(prefix)) {
                quests.add(id.substring(prefix.length()));
            }
        }
        return quests;
    }

    /** Every translation key the story uses (speakers, chapters, cards, lines, barks, credits). */
    public SortedSet<String> translationKeys() {
        return translationKeys;
    }

    private Optional<Dialogue> npcDialogue(String npcId, int storyProgress) {
        Optional<StoryNpc> found = StoryNpc.byId(npcId);
        if (found.isEmpty() || !found.get().isPresentAt(storyProgress)) {
            return Optional.empty();
        }
        StoryNpc npc = found.get();
        int progress = Math.min(storyProgress, StoryProgress.MAX);
        for (int at = progress; at >= 0; at--) {
            Dialogue dialogue = dialogues.get(npcDialogueId(npc, at));
            if (dialogue != null) {
                return Optional.of(dialogue);
            }
        }
        if (npc.kind() == StoryNpc.Kind.QUEST_GIVER) {
            for (int at = progress + 1; at <= StoryProgress.MAX; at++) {
                Dialogue dialogue = dialogues.get(npcDialogueId(npc, at));
                if (dialogue != null) {
                    return Optional.of(dialogue);
                }
            }
        }
        return Optional.empty();
    }

    private static String dayDialogueId(StoryTrigger.DayStarted day) {
        boolean last = day.day() >= day.seasonLength();
        if (StoryProgress.isComplete(day.storyProgress())) {
            return last ? "day.farewell" : "day.peace";
        }
        if (last) {
            return "day.final";
        }
        if (day.day() == day.seasonLength() - 1) {
            return "day.eve";
        }
        return day.day() <= NUMBERED_DAYS ? "day." + day.day() : "day.extra";
    }

    static String npcDialogueId(StoryNpc npc, int progress) {
        return "npc." + npc.id() + "." + progress;
    }

    static String questDialogueId(String npcId, String questId) {
        return "quest." + npcId + "." + questId;
    }

    private static String barkId(String bossId, int phase) {
        return bossId + ".phase" + phase;
    }

    /** Collects the content ({@code StoryScript}); keys are derived from the ids (see the class comment). */
    static final class Builder {
        private final Map<String, Dialogue> dialogues = new LinkedHashMap<>();
        private final Map<StoryArea, AreaCard> areaCards = new EnumMap<>(StoryArea.class);
        private final Map<String, DialogueLine> barks = new LinkedHashMap<>();
        private final List<EndingSequence.Beat> beats = new ArrayList<>();
        private final List<EndingSequence.Credit> credits = new ArrayList<>();
        private int creditIntervalTicks = LINE_INTERVAL_TICKS;

        /** A dialogue with one line per speaker; keys {@code story.eternalrealm.<id>.<n>}. */
        Builder dialogue(String id, Speaker... speakers) {
            return add(id, KEY_PREFIX + id, List.of(speakers));
        }

        /** What {@code npc} says at {@code progress}: {@code lineCount} lines by the NPC's speaker. */
        Builder npc(StoryNpc npc, int progress, int lineCount) {
            Check.range("progress", progress, 0, StoryProgress.MAX);
            if (npc.kind() == StoryNpc.Kind.CHARACTER && progress < npc.firstProgress()) {
                throw new IllegalArgumentException(npc.id() + " is not in town at progress " + progress);
            }
            String id = npcDialogueId(npc, progress);
            return add(id, KEY_PREFIX + id, Collections.nCopies(lineCount, npc.speaker()));
        }

        /** What {@code npc} says about quest {@code questId}: keys {@code dialogue.eternalrealm.<npc>.<quest>.<n>}. */
        Builder quest(StoryNpc npc, String questId, int lineCount) {
            Check.id("questId", questId);
            return add(questDialogueId(npc.id(), questId), QUEST_KEY_PREFIX + npc.id() + "." + questId,
                    Collections.nCopies(lineCount, npc.speaker()));
        }

        Builder areaCard(StoryArea area) {
            if (areaCards.put(area, AreaCard.of(area)) != null) {
                throw new IllegalStateException("duplicate area card " + area);
            }
            return this;
        }

        Builder bark(String bossId, int phase, Speaker speaker) {
            Check.id("bossId", bossId);
            Check.range("phase", phase, 2, 16);
            String key = KEY_PREFIX + "boss." + bossId + ".phase" + phase;
            if (barks.put(barkId(bossId, phase), DialogueLine.of(speaker, key)) != null) {
                throw new IllegalStateException("duplicate bark " + key);
            }
            return this;
        }

        /** The next ending line, {@code atSecond} s after the start; key {@code story.eternalrealm.ending.<n>}. */
        Builder beat(int atSecond, Speaker speaker, EndingSequence.Display display) {
            String key = KEY_PREFIX + "ending." + (beats.size() + 1);
            beats.add(new EndingSequence.Beat(atSecond * 20, DialogueLine.of(speaker, key), display));
            return this;
        }

        /** The next staff roll entry; key {@code story.eternalrealm.staff.<id>}. */
        Builder credit(EndingSequence.CreditKind kind, String id) {
            credits.add(new EndingSequence.Credit(kind, KEY_PREFIX + "staff." + Check.id("credit id", id)));
            return this;
        }

        Builder creditInterval(int ticks) {
            creditIntervalTicks = ticks;
            return this;
        }

        StoryCatalog build() {
            EndingSequence ending = new EndingSequence(beats, credits, creditIntervalTicks);
            Map<String, Dialogue> all = new LinkedHashMap<>(dialogues);
            Dialogue endingLines = ending.asDialogue();
            if (all.put(endingLines.id(), endingLines) != null) {
                throw new IllegalStateException("the dialogue id 'ending' is reserved for the ending sequence");
            }
            return new StoryCatalog(all, areaCards, barks, ending);
        }

        private Builder add(String id, String keyPrefix, List<Speaker> speakers) {
            if (speakers.isEmpty()) {
                throw new IllegalArgumentException(id + ": a dialogue needs at least one line");
            }
            List<DialogueLine> lines = new ArrayList<>(speakers.size());
            for (int i = 0; i < speakers.size(); i++) {
                lines.add(DialogueLine.of(speakers.get(i), keyPrefix + "." + (i + 1)));
            }
            if (dialogues.put(id, new Dialogue(id, lines)) != null) {
                throw new IllegalStateException("duplicate dialogue " + id);
            }
            return this;
        }
    }
}
