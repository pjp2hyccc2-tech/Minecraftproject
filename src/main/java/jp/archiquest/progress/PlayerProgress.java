package jp.archiquest.progress;

import jp.archiquest.knowledge.Subject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * プレイヤーの学習データ。プレイヤーNBT（ForgeData/archiquest）に保存。
 * 死亡・リスポーン時は PlayerEvent.Clone でコピーする。
 */
public final class PlayerProgress {
    public static final String KEY = "archiquest";

    private PlayerProgress() {}

    public static CompoundTag data(Player p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(KEY, Tag.TAG_COMPOUND)) root.put(KEY, new CompoundTag());
        return root.getCompound(KEY);
    }

    private static CompoundTag stats(Player p) {
        CompoundTag t = data(p);
        if (!t.contains("stats", Tag.TAG_COMPOUND)) t.put("stats", new CompoundTag());
        return t.getCompound("stats");
    }

    public static void copy(Player from, Player to) {
        CompoundTag src = from.getPersistentData();
        if (src.contains(KEY, Tag.TAG_COMPOUND)) {
            to.getPersistentData().put(KEY, src.getCompound(KEY).copy());
        }
    }

    // ---------------------------------------------------------------- points & rank

    public static int points(Player p) {
        return data(p).getInt("points");
    }

    /** ポイント加算。称号が上がったら演出する */
    public static void addPoints(ServerPlayer p, int amount) {
        if (amount == 0) return;
        CompoundTag t = data(p);
        int before = t.getInt("points");
        int after = Math.max(0, before + amount);
        t.putInt("points", after);
        Ranks.Rank rb = Ranks.of(before);
        Ranks.Rank ra = Ranks.of(after);
        if (ra.minPoints() > rb.minPoints()) Ranks.announceRankUp(p, ra);
    }

    // ---------------------------------------------------------------- quiz stats

    public static void recordAnswer(Player p, Subject s, boolean correct) {
        CompoundTag st = stats(p);
        st.putInt(s.id + "_a", st.getInt(s.id + "_a") + 1);
        if (correct) st.putInt(s.id + "_c", st.getInt(s.id + "_c") + 1);
        CompoundTag t = data(p);
        int streak = correct ? t.getInt("streak") + 1 : 0;
        t.putInt("streak", streak);
        if (streak > t.getInt("bestStreak")) t.putInt("bestStreak", streak);
    }

    public static int answered(Player p, Subject s) {
        return stats(p).getInt(s.id + "_a");
    }

    public static int correct(Player p, Subject s) {
        return stats(p).getInt(s.id + "_c");
    }

    public static int totalAnswered(Player p) {
        int n = 0;
        for (Subject s : Subject.values()) n += answered(p, s);
        return n;
    }

    public static int totalCorrect(Player p) {
        int n = 0;
        for (Subject s : Subject.values()) n += correct(p, s);
        return n;
    }

    public static int streak(Player p) {
        return data(p).getInt("streak");
    }

    public static int bestStreak(Player p) {
        return data(p).getInt("bestStreak");
    }

    /** 3問以上解いた科目のうち、正答率が一番低い科目 */
    @Nullable
    public static Subject weakestSubject(Player p) {
        Subject worst = null;
        double worstRate = 2;
        for (Subject s : Subject.values()) {
            int a = answered(p, s);
            if (a < 3) continue;
            double r = (double) correct(p, s) / a;
            if (r < worstRate) {
                worstRate = r;
                worst = s;
            }
        }
        return worst;
    }

    // ---------------------------------------------------------------- exam

    public static void recordExam(Player p, int percent, boolean passed) {
        CompoundTag t = data(p);
        t.putInt("examsTaken", t.getInt("examsTaken") + 1);
        if (passed) t.putInt("examsPassed", t.getInt("examsPassed") + 1);
        if (percent > t.getInt("bestExam")) t.putInt("bestExam", percent);
    }

    public static int examsTaken(Player p) {
        return data(p).getInt("examsTaken");
    }

    public static int examsPassed(Player p) {
        return data(p).getInt("examsPassed");
    }

    public static int bestExam(Player p) {
        return data(p).getInt("bestExam");
    }

    // ---------------------------------------------------------------- flags

    public static boolean adviceOff(Player p) {
        return data(p).getBoolean("adviceOff");
    }

    public static void setAdviceOff(Player p, boolean off) {
        data(p).putBoolean("adviceOff", off);
    }

    public static boolean welcomed(Player p) {
        return data(p).getBoolean("welcomed");
    }

    public static void setWelcomed(Player p) {
        data(p).putBoolean("welcomed", true);
    }

    // ---------------------------------------------------------------- challenges

    public static String challenge(Player p) {
        return data(p).getString("challenge");
    }

    public static void setChallenge(Player p, String id) {
        if (id == null || id.isEmpty()) data(p).remove("challenge");
        else data(p).putString("challenge", id);
    }

    public static List<String> doneChallenges(Player p) {
        String raw = data(p).getString("doneChallenges");
        if (raw.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(raw.split(",")));
    }

    public static boolean challengeDone(Player p, String id) {
        return doneChallenges(p).contains(id);
    }

    public static void markChallengeDone(Player p, String id) {
        List<String> list = doneChallenges(p);
        if (!list.contains(id)) list.add(id);
        data(p).putString("doneChallenges", String.join(",", list));
    }
}
