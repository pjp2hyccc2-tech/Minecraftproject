package com.eternalrealm.story;

import java.util.Optional;

/**
 * The chapters of the main story (docs/STORY.md): the prologue, one chapter per dungeon, the final battle and the
 * epilogue. The current chapter follows the server's story progress: {@link #ofProgress(int)}.
 */
public enum Chapter {
    PROLOGUE("prologue", StoryArea.ARCELIA, null, null),
    CHAPTER_1("1", StoryArea.ARCELIA, "d1", "b1"),
    CHAPTER_2("2", StoryArea.ELDER_FOREST, "d2", "b2"),
    CHAPTER_3("3", StoryArea.SANDRIA_DESERT, "d3", "b3"),
    CHAPTER_4("4", StoryArea.NORDIA_SNOWFIELD, "d4", "b4"),
    CHAPTER_5("5", StoryArea.NORDIA_SNOWFIELD, "d5", "b5"),
    CHAPTER_6("6", StoryArea.DRAGONIA_VOLCANO, "d6", "b6"),
    CHAPTER_7("7", StoryArea.CELESTIA, "d7", "b7"),
    CHAPTER_8("8", StoryArea.TOWER_OF_THE_END, "d8", "b8"),
    FINALE("finale", StoryArea.TOWER_OF_THE_END, "final", "final"),
    EPILOGUE("epilogue", StoryArea.ARCELIA, null, null);

    private final String id;
    private final StoryArea area;
    private final String dungeonId;
    private final String bossId;

    Chapter(String id, StoryArea area, String dungeonId, String bossId) {
        this.id = id;
        this.area = area;
        this.dungeonId = dungeonId;
        this.bossId = bossId;
    }

    public String id() {
        return id;
    }

    /** E.g. 「第一章　地の底の王都」. */
    public String titleKey() {
        return StoryCatalog.KEY_PREFIX + "chapter." + id + ".title";
    }

    /** One sentence for the journal. */
    public String synopsisKey() {
        return StoryCatalog.KEY_PREFIX + "chapter." + id + ".synopsis";
    }

    public StoryArea area() {
        return area;
    }

    /** The dungeon of the chapter ("final" for the raid); empty for the prologue and the epilogue. */
    public Optional<String> dungeonId() {
        return Optional.ofNullable(dungeonId);
    }

    /** The boss whose first server-wide kill ends the chapter; empty for the prologue and the epilogue. */
    public Optional<String> bossId() {
        return Optional.ofNullable(bossId);
    }

    /**
     * The chapter being played at a story progress: 0 → chapter 1 (the prologue is the opening scene of chapter 1's
     * time), 7 → chapter 8, 8 → the finale, 9 and above → the epilogue.
     */
    public static Chapter ofProgress(int storyProgress) {
        Check.range("storyProgress", storyProgress, 0, Integer.MAX_VALUE);
        return values()[Math.min(storyProgress, StoryProgress.MAX) + 1];
    }

    public static Optional<Chapter> ofBoss(String bossId) {
        for (Chapter chapter : values()) {
            if (chapter.bossId != null && chapter.bossId.equals(bossId)) {
                return Optional.of(chapter);
            }
        }
        return Optional.empty();
    }
}
