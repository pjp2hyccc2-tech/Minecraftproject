package com.eternalrealm.story;

/**
 * Something that happened in the game and may start a dialogue ({@link StoryCatalog#dialogue(StoryTrigger)}). The
 * platform creates the trigger, asks the catalog and shows the dialogue to {@link #audience()}.
 *
 * <p>Story progress ({@code storyProgress}) is the number of main bosses defeated server-wide, 0..9
 * ({@link StoryProgress#of}). Values above 9 are treated as 9.</p>
 */
public sealed interface StoryTrigger permits StoryTrigger.FirstLogin, StoryTrigger.AreaEntered,
        StoryTrigger.DungeonEntered, StoryTrigger.BossEncounter, StoryTrigger.BossDefeated, StoryTrigger.AreaUnlocked,
        StoryTrigger.NpcTalk, StoryTrigger.QuestTalk, StoryTrigger.FinalPhase, StoryTrigger.Ending,
        StoryTrigger.DayStarted, StoryTrigger.SeasonEnded {

    FirstLogin FIRST_LOGIN = new FirstLogin();
    Ending ENDING = new Ending();

    Kind kind();

    /** Who should see the dialogue. */
    Audience audience();

    enum Kind {
        FIRST_LOGIN,
        AREA_ENTERED,
        DUNGEON_ENTERED,
        BOSS_ENCOUNTER,
        BOSS_DEFEATED,
        AREA_UNLOCKED,
        NPC_TALK,
        QUEST_TALK,
        FINAL_PHASE,
        ENDING,
        DAY_STARTED,
        SEASON_ENDED
    }

    enum Audience {
        /** The player who caused the trigger. */
        PLAYER,
        /** The party or raid inside the dungeon instance / boss arena. */
        PARTY,
        /** Everyone online. */
        SERVER
    }

    /** A player's first login of the season: the prologue. */
    record FirstLogin() implements StoryTrigger {
        @Override
        public Kind kind() {
            return Kind.FIRST_LOGIN;
        }

        @Override
        public Audience audience() {
            return Audience.PLAYER;
        }
    }

    /**
     * A player entered an area (1..7, {@link StoryArea}) for the first time. Show the area's {@link AreaCard} on every
     * entry.
     */
    record AreaEntered(int areaId) implements StoryTrigger {
        public AreaEntered {
            Check.range("areaId", areaId, 1, 64);
        }

        @Override
        public Kind kind() {
            return Kind.AREA_ENTERED;
        }

        @Override
        public Audience audience() {
            return Audience.PLAYER;
        }
    }

    /** A party started a dungeon run ("d1".."d8") or the final raid ("final"). */
    record DungeonEntered(String dungeonId) implements StoryTrigger {
        public DungeonEntered {
            Check.id("dungeonId", dungeonId);
        }

        @Override
        public Kind kind() {
            return Kind.DUNGEON_ENTERED;
        }

        @Override
        public Audience audience() {
            return Audience.PARTY;
        }
    }

    /** A boss fight began ("b1".."b8", "final"): the boss's opening lines. */
    record BossEncounter(String bossId) implements StoryTrigger {
        public BossEncounter {
            Check.id("bossId", bossId);
        }

        @Override
        public Kind kind() {
            return Kind.BOSS_ENCOUNTER;
        }

        @Override
        public Audience audience() {
            return Audience.PARTY;
        }
    }

    /**
     * A boss was defeated. The first server-wide kill plays the story beat (with a Star Memory) for everyone; later
     * kills only the boss's last words for the party.
     */
    record BossDefeated(String bossId, boolean firstServerKill) implements StoryTrigger {
        public BossDefeated {
            Check.id("bossId", bossId);
        }

        @Override
        public Kind kind() {
            return Kind.BOSS_DEFEATED;
        }

        @Override
        public Audience audience() {
            return firstServerKill ? Audience.SERVER : Audience.PARTY;
        }
    }

    /** An area (2..7) was opened for the server. Area 1 is open from the start and has no unlock dialogue. */
    record AreaUnlocked(int areaId) implements StoryTrigger {
        public AreaUnlocked {
            Check.range("areaId", areaId, 1, 64);
        }

        @Override
        public Kind kind() {
            return Kind.AREA_UNLOCKED;
        }

        @Override
        public Audience audience() {
            return Audience.SERVER;
        }
    }

    /**
     * A player talked to an NPC ({@link StoryNpc} id: a town role, a story character or a quest giver). What it says
     * depends on the server's story progress.
     */
    record NpcTalk(String npcId, int storyProgress) implements StoryTrigger {
        public NpcTalk {
            Check.id("npcId", npcId);
            Check.range("storyProgress", storyProgress, 0, Integer.MAX_VALUE);
        }

        @Override
        public Kind kind() {
            return Kind.NPC_TALK;
        }

        @Override
        public Audience audience() {
            return Audience.PLAYER;
        }
    }

    /**
     * A player talked to a quest giver about a quest of the quest module (e.g. "arcelia_knight_captain",
     * "m1_arrival"): when the quest is offered or its talk objective is completed.
     */
    record QuestTalk(String npcId, String questId) implements StoryTrigger {
        public QuestTalk {
            Check.id("npcId", npcId);
            Check.id("questId", questId);
        }

        @Override
        public Kind kind() {
            return Kind.QUEST_TALK;
        }

        @Override
        public Audience audience() {
            return Audience.PLAYER;
        }
    }

    /**
     * The final boss entered phase 1..4 ({@code BossCommand.PhaseChanged} index + 1; phase 1 is the start of the
     * fight).
     */
    record FinalPhase(int phase) implements StoryTrigger {
        public FinalPhase {
            Check.range("phase", phase, 1, 4);
        }

        @Override
        public Kind kind() {
            return Kind.FINAL_PHASE;
        }

        @Override
        public Audience audience() {
            return Audience.PARTY;
        }
    }

    /**
     * The King of the End fell for the first time on the server: play {@link StoryCatalog#ending()} (timeline and
     * staff roll); {@link StoryCatalog#dialogue} returns its lines without timing.
     */
    record Ending() implements StoryTrigger {
        @Override
        public Kind kind() {
            return Kind.ENDING;
        }

        @Override
        public Audience audience() {
            return Audience.SERVER;
        }
    }

    /**
     * A season day began (day 1 = the season start).
     *
     * @param day           1-based season day
     * @param seasonLength  length of the season in days (the story is written for 14); the last day and the day
     *                      before it get their own lines whatever the length
     * @param storyProgress server story progress; after the final victory the days turn peaceful
     */
    record DayStarted(int day, int seasonLength, int storyProgress) implements StoryTrigger {
        public DayStarted {
            Check.range("day", day, 1, Integer.MAX_VALUE);
            Check.range("seasonLength", seasonLength, 1, Integer.MAX_VALUE);
            Check.range("storyProgress", storyProgress, 0, Integer.MAX_VALUE);
        }

        /** A day of the standard 14-day season before the final victory. */
        public DayStarted(int day) {
            this(day, StoryCatalog.STANDARD_SEASON_DAYS, 0);
        }

        @Override
        public Kind kind() {
            return Kind.DAY_STARTED;
        }

        @Override
        public Audience audience() {
            return Audience.SERVER;
        }
    }

    /**
     * The season is over (the fourteenth night): the farewell with the next-season teaser, or, if nobody defeated the
     * King of the End, the sealing that sets up the next season. The victory epilogue is also played right after the
     * staff roll.
     */
    record SeasonEnded(boolean kingDefeated) implements StoryTrigger {
        @Override
        public Kind kind() {
            return Kind.SEASON_ENDED;
        }

        @Override
        public Audience audience() {
            return Audience.SERVER;
        }
    }
}
