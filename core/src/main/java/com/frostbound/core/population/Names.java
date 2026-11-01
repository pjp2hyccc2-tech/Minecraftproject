package com.frostbound.core.population;

import com.frostbound.core.util.Rng;

/** Original name pools for citizens. */
public final class Names {
    private static final String[] MALE = {
        "アルノ", "ベルン", "カイル", "ダリオ", "エミル", "フェリクス", "グスタフ", "ハロルド", "イヴァン", "ヨナス",
        "カスパー", "レオン", "マティアス", "ニコ", "オスカー", "パヴェル", "ルーカス", "セヴェリン", "トマス", "ヴィクトル",
        "ウルリク", "エーリク", "アンセル", "ボリス", "コンラート", "デニス", "ゲオルグ", "ラスムス"
    };
    private static final String[] FEMALE = {
        "アンナ", "ベアタ", "カリナ", "ダリア", "エルザ", "フリーダ", "グレタ", "ハンナ", "イレーネ", "ヨハンナ",
        "カティア", "リディア", "マルタ", "ニーナ", "オルガ", "ペトラ", "ルート", "ソフィア", "テレサ", "ヴェラ",
        "イリス", "ミラ", "エッダ", "リーサ", "アストリッド", "ドーラ", "インガ", "セルマ"
    };
    private static final String[] NEUTRAL = {"サシャ", "ノア", "アリ", "ユーリ", "カミ", "レン", "ロビン", "ジェイ"};
    private static final String[] FAMILY = {
        "ホルト", "ヴォス", "ブラント", "クライン", "ノルド", "ストルム", "ヴァルト", "ラングレン", "エッケル", "ファルク",
        "グラーフ", "ハーゲン", "イェンセン", "コルベ", "リンド", "モーア", "ネス", "オルセン", "ペール", "ロスト",
        "シュタイン", "トール", "ヴェスト", "ヴィク", "ザイデル", "ベック", "ダール", "フロスト", "アイゼン", "ケルン"
    };

    private Names() {
    }

    public static String family(Rng rng) {
        return FAMILY[rng.nextInt(FAMILY.length)];
    }

    public static String given(Rng rng, Citizen.Gender gender) {
        String[] pool = switch (gender) {
            case MALE -> MALE;
            case FEMALE -> FEMALE;
            case OTHER -> NEUTRAL;
        };
        return pool[rng.nextInt(pool.length)];
    }

    public static String full(String given, String family) {
        return given + "・" + family;
    }
}
