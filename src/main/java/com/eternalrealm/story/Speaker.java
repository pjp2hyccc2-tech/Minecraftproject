package com.eternalrealm.story;

/**
 * Who says a line. The display name is the translation key {@link #key()} (texts in docs/i18n/story.json); the cast is
 * described in docs/STORY.md. {@link #NARRATOR} lines are shown without a name.
 */
public enum Speaker {
    /** Narration: shown without a speaker name (gray italics). */
    NARRATOR("narrator"),

    // main cast
    /** リリア: Princess of Arcelia, performed the Rite of Summoning. */
    LILIA("lilia"),
    /** 国王ベルトラン十四世: Lilia's father, keeps trying to ride into battle. */
    KING("king"),
    /** 星詠みのノクス: the scholar; secretly the mage who travelled with Kanata a thousand years ago. */
    NOX("nox"),
    /** グスタフ: guildmaster of the Adventurers' Guild. */
    GUSTAV("gustav"),
    /** アデル: captain of the knights (quest NPC arcelia_knight_captain), Wolfram's daughter. */
    ADEL("adel"),

    // quest NPCs of the other areas
    /** ネリス: sage of the Elder Forest (elder_forest_sage). */
    NERIS("neris"),
    /** ハリド: caravan master of Sandria (sandria_caravan_master), Zahra's brother. */
    KHALID("khalid"),
    /** ヘルガ: chieftain of Nordia (nordia_chieftain). */
    HELGA("helga"),
    /** イルザ: dragonsmith of Dragonia (dragonia_blacksmith), Goran's teacher. */
    ILSA("ilsa"),
    /** セレネ: automaton oracle of Celestia (celestia_oracle), Ordo's sister unit. */
    SELENE("selene"),
    /** ギュンター: watcher of the Tower of the End (tower_watcher), Glaus's descendant. */
    GUNTER("gunter"),

    // town service NPCs
    /** ミーナ: Adventurers' Guild receptionist (quest_board). */
    MINA("mina"),
    /** ピム: item shop. */
    PIM("pim"),
    /** ゴラン: blacksmith. */
    GORAN("goran"),
    /** ハーゲン: job master. */
    HAGEN("hagen"),
    /** ルーチェ: auction house. */
    LUCE("luce"),
    /** チコ: courier at the mailbox, with his pigeon Kururu. */
    CHICO("chico"),
    /** マルタ: guild registrar. */
    MARTA("marta"),
    /** ガント: warden of the dungeon gate (gatekeeper). */
    GANT("gant"),
    /** ザイン: arena master. */
    ZAIN("zain"),
    /** クロエ: archivist of the hall of fame. */
    CHLOE("chloe"),

    // bosses and their people
    /** ラズロ: the Bandit King (b1); works off his debt at the guild after his defeat. */
    LASZLO("laszlo"),
    /** One of Laszlo's bandits (d1). */
    BANDIT("bandit"),
    /** 古代樹の守護者 (b2). */
    GUARDIAN("guardian"),
    /** ザフラ: prophet of the Order of Twilight, who awakened the Colossus (b3). */
    ZAHRA("zahra"),
    /** A member of the Order of Twilight (d3). */
    CULTIST("cultist"),
    /** 凍獄の処刑人 (b4) before he is freed. */
    EXECUTIONER("executioner"),
    /** ヴォルフラム: the executioner freed (b4), Gustav's old partner. */
    WOLFRAM("wolfram"),
    /** イングリッド: the Ice Queen (b5). */
    INGRID("ingrid"),
    /** アズラグ: the Dragon King (b6). */
    AZRAG("azrag"),
    /** オルド: the Machine God (b7); speaks in katakana / capitals. */
    ORDO("ordo"),
    /** 終焉の門番 (b8) before he names himself. */
    END_GATEKEEPER("end_gatekeeper"),
    /** グラウス: the knight of the first hero; the gatekeeper (b8). */
    GLAUS("glaus"),
    /** 終焉の王 (final): the End speaking through its vessel. */
    END_KING("end_king"),

    // a thousand years ago (Star Memories) and the truth of the King
    /** カナタ: the first hero summoned to Astoria, the vessel of the End. */
    KANATA("kanata"),
    /** エルミナ: the priestess-princess who summoned Kanata, Lilia's ancestor. */
    ELMINA("elmina"),
    /** The young Nox in the Star Memories (unnamed until chapter 7). */
    YOUNG_MAGE("young_mage");

    private final String id;

    Speaker(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** Translation key of the display name: {@code story.eternalrealm.speaker.<id>}. */
    public String key() {
        return StoryCatalog.KEY_PREFIX + "speaker." + id;
    }
}
