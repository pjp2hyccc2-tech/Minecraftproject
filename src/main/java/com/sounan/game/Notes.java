package com.sounan.game;

/**
 * The notes. Text lives in the lang files: note.sounan.&lt;id&gt;.title / .body (%1$s = player name, %2$s = captures,
 * %3$s = the date the calendar has jumped to).
 */
public final class Notes {
    public static final int COUNT = 14;
    public static final int START = 1;       // 濡れた手帳の切れ端 (佐伯, 1998)
    public static final int STREAM = 2;      // 沢沿いの書き付け
    public static final int CLIFF = 3;       // 崖下の紙片
    public static final int RIBBONS = 4;     // テープのこと
    public static final int SHOES = 5;       // 行き止まり
    public static final int WATCHED = 6;     // 見られている
    public static final int CHILD = 7;       // 子供の作文 (village)
    public static final int VILLAGE = 8;     // 最後の村人
    public static final int SHRINE = 9;      // 祠のこと (hint: offer sake)
    public static final int LOGBOOK = 10;    // 登山者ノート (hut)
    public static final int SIGNAL = 11;     // 鉄塔の下で
    public static final int TUNNEL = 12;     // 出口
    public static final int RULES = 13;      // 山の掟 (village notice board)
    public static final int MEMORIAL = 14;   // 遭難者慰霊 (trailhead board)

    private Notes() {}
}
