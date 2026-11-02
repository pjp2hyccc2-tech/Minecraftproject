package com.meikon.game;

/**
 * The notes (手記). Text lives in the lang files: note.meikon.&lt;id&gt;.title / .body ("%1$s" = player name,
 * "%2$s" = the candle order of this game).
 */
public final class Notes {
    public static final int COUNT = 12;
    public static final int INVITATION = 1;      // 招待状 (shown by the red envelope)
    public static final int MAID_1 = 2;          // 女中の日記 一
    public static final int LIGHT_HINT = 3;      // 灯りの心得 (lantern / shadows)
    public static final int MAID_2 = 4;          // 女中の日記 二
    public static final int MOTHER = 5;          // 母の手記 (mukasari ema)
    public static final int PRIEST = 6;          // 住職の書付 (seven bells, enkiri)
    public static final int DOLLS = 7;           // 人形のこと
    public static final int CLOSET = 8;          // 押入れの中で
    public static final int CANDLES = 9;         // 仏間の作法 (candle order)
    public static final int WARDS = 10;          // 塩とおはじき
    public static final int SAKAZUKI = 11;       // 盃を交わすな
    public static final int CHILD = 12;          // 子供の絵

    private Notes() {}
}
