package jp.satsujinki.game;

import net.minecraft.util.RandomSource;

/** Random fictional contestant names. */
public final class Names {
    private static final String[] SURNAMES = {
            "佐藤", "鈴木", "高橋", "田中", "伊藤", "渡辺", "山本", "中村", "小林", "加藤",
            "吉田", "山田", "佐々木", "山口", "松本", "井上", "木村", "林", "斎藤", "清水",
            "山崎", "森", "池田", "橋本", "阿部", "石川", "前田", "藤田", "小川", "岡田",
            "後藤", "長谷川", "村上", "近藤", "石井", "坂本", "遠藤", "青木", "藤井", "西村",
            "黒田", "赤城", "鬼頭", "血原", "鉄村", "針谷"};
    private static final String[] MALE = {
            "健一", "大輔", "翔太", "拓也", "直樹", "和也", "誠", "剛", "隆", "浩二",
            "達也", "亮", "雄一", "俊介", "慎吾", "勇気", "修", "茂", "正樹", "哲也",
            "金次郎", "権蔵", "鉄男", "竜二"};
    private static final String[] FEMALE = {
            "美咲", "愛", "由美", "陽子", "恵", "真由美", "彩", "優子", "奈々", "沙織",
            "智子", "麻衣", "亜美", "舞", "瞳", "里奈", "香織", "明美", "千尋", "玲奈"};

    private Names() {
    }

    public static String random(boolean female, RandomSource r) {
        String[] given = female ? FEMALE : MALE;
        return SURNAMES[r.nextInt(SURNAMES.length)] + " " + given[r.nextInt(given.length)];
    }
}
