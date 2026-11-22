package jp.archiquest.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/** チャット表示まわりのヘルパー */
public final class Msg {
    public static final String SENSEI = "アーキ先生";

    private Msg() {}

    public static MutableComponent prefix() {
        return Component.literal("[" + SENSEI + "] ").withStyle(ChatFormatting.AQUA);
    }

    public static void say(ServerPlayer p, String text) {
        say(p, Component.literal(text));
    }

    public static void say(ServerPlayer p, Component body) {
        p.sendSystemMessage(Component.empty().append(prefix()).append(body));
    }

    public static void send(ServerPlayer p, Component c) {
        p.sendSystemMessage(c);
    }

    public static void send(ServerPlayer p, String text, ChatFormatting... style) {
        p.sendSystemMessage(Component.literal(text).withStyle(style));
    }

    public static void header(ServerPlayer p, String title, ChatFormatting color) {
        p.sendSystemMessage(Component.literal("━━━ " + title + " ━━━").withStyle(color, ChatFormatting.BOLD));
    }

    public static MutableComponent text(String s, ChatFormatting... style) {
        return Component.literal(s).withStyle(style);
    }

    /** クリックするとコマンドを実行するボタン */
    public static MutableComponent button(String label, String command, String hover, ChatFormatting color) {
        return Component.literal("[" + label + "]").withStyle(st -> st.withColor(color)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hover))));
    }

    /** クリックするとチャット欄にコマンドを入力するボタン */
    public static MutableComponent suggest(String label, String command, String hover, ChatFormatting color) {
        return Component.literal("[" + label + "]").withStyle(st -> st.withColor(color)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hover))));
    }

    /** マウスを乗せると説明が出るテキスト */
    public static MutableComponent hover(String text, String hover, ChatFormatting... style) {
        return Component.literal(text).withStyle(style)
                .withStyle(st -> st.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hover))));
    }

    public static MutableComponent row(Component... parts) {
        MutableComponent m = Component.literal(" ");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) m.append(" ");
            m.append(parts[i]);
        }
        return m;
    }

    public static String bar(double ratio, int width) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, ratio)) * width);
        return "■".repeat(filled) + "□".repeat(Math.max(0, width - filled));
    }

    public static String pct(double ratio) {
        return Math.round(ratio * 100) + "%";
    }

    public static String fmt1(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    /** 「1/7.0」のような分数表記（床面積 ÷ 開口面積） */
    public static String fraction(double ratio) {
        if (ratio <= 0) return "0";
        return "1/" + fmt1(1.0 / ratio);
    }

    /** 改行区切りのテキストを1行ずつ送る */
    public static void sendParagraphs(ServerPlayer p, String text, ChatFormatting color) {
        if (text == null) return;
        for (String line : text.split("\n")) {
            if (line.isBlank()) continue;
            p.sendSystemMessage(Component.literal(" " + line.strip()).withStyle(color));
        }
    }
}
