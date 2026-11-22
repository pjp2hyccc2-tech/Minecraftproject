package jp.archiquest.progress;

import jp.archiquest.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import javax.annotation.Nullable;
import java.util.List;

/** 学習ポイントによる称号（ゲーム内の称号で、実際の資格とは関係ありません） */
public final class Ranks {
    public record Rank(int minPoints, String title, ChatFormatting color) {
    }

    public static final List<Rank> ALL = List.of(
            new Rank(0, "建築ビギナー", ChatFormatting.GRAY),
            new Rank(40, "見習い大工", ChatFormatting.WHITE),
            new Rank(120, "図面読みの卵", ChatFormatting.GREEN),
            new Rank(250, "二級建築士の卵", ChatFormatting.DARK_GREEN),
            new Rank(450, "一級建築士の卵", ChatFormatting.AQUA),
            new Rank(700, "一級建築士（ゲーム内称号）", ChatFormatting.BLUE),
            new Rank(1000, "構造の匠", ChatFormatting.GOLD),
            new Rank(1500, "環境デザインの達人", ChatFormatting.LIGHT_PURPLE),
            new Rank(2200, "建築界の巨匠", ChatFormatting.RED)
    );

    private Ranks() {}

    public static Rank of(int points) {
        Rank r = ALL.get(0);
        for (Rank x : ALL) if (points >= x.minPoints()) r = x;
        return r;
    }

    @Nullable
    public static Rank next(int points) {
        for (Rank x : ALL) if (x.minPoints() > points) return x;
        return null;
    }

    public static void announceRankUp(ServerPlayer p, Rank r) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(r.title()).withStyle(r.color())));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("称号アップ！").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.0f);
        Msg.say(p, Component.literal("おめでとう！ 称号が「").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(r.title()).withStyle(r.color(), ChatFormatting.BOLD))
                .append(Component.literal("」になったよ！").withStyle(ChatFormatting.YELLOW)));
    }
}
