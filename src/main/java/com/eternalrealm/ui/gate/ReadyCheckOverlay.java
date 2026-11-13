package com.eternalrealm.ui.gate;

import com.eternalrealm.client.ClientUiState;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The ready check on the HUD while its screen is closed: the dungeon and difficulty, every member's 準備OK / 準備中,
 * this player's answer with how to give it, the time left or the countdown; and, for a few seconds after it ends, how
 * it ended. Top centre, below the boss bar when one is up.
 */
public final class ReadyCheckOverlay implements IGuiOverlay {
    private static final int WIDTH = 220;
    private static final String[] DIFF_IDS = {"normal", "hard", "ex"};
    private static final int[] DIFF_TEXT = {0xFF8CE08C, 0xFFFF8A6A, 0xFFD6A0FF};

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft minecraft = gui.getMinecraft();
        if (minecraft.options.hideGui || minecraft.player == null || minecraft.screen instanceof ReadyCheckScreen) {
            return;
        }
        int top = 4;
        if (com.eternalrealm.client.boss.BossHudOverlay.active()) {
            com.eternalrealm.hud.Rect boss = com.eternalrealm.ui.hud.HudFrame.layout().boss();
            top = boss.isEmpty() ? 48 : boss.y() + boss.height() + 4;
        }
        int x = (screenWidth - WIDTH) / 2;
        Font font = minecraft.font;
        RenderSystem.enableBlend();
        Optional<JsonObject> state = ReadyCheckClient.state();
        if (state.isEmpty()) {
            Optional<Component> end = ReadyCheckClient.recentEnd();
            if (end.isPresent()) {
                float fade = Mth.clamp((ReadyCheckClient.END_SHOWN_MS - ReadyCheckClient.endAge()) / 600.0F, 0.0F,
                        1.0F);
                boolean go = "go".equals(ReadyCheckClient.recentOutcome());
                graphics.fill(x, top, x + WIDTH, top + 16, GateArt.argb(0x101426, 0.82F * fade));
                graphics.renderOutline(x, top, WIDTH, 16, GateArt.argb(go ? 0x8CE08C : 0xD2AE5A, fade));
                Component text = go ? Component.translatable("screen.eternalrealm.ready.went") : end.get();
                GateArt.fitted(graphics, font, text, x + 6, top + 4, WIDTH - 12,
                        GateArt.argb(go ? 0xDFFFE4 : 0xFFE6B0, Math.max(0.1F, fade)), 1.0F, 0.6F);
            }
            RenderSystem.disableBlend();
            return;
        }
        JsonObject data = state.get();
        List<ReadyCheckClient.Member> members = ReadyCheckClient.members();
        int height = 42;
        graphics.fill(x, top, x + WIDTH, top + height, 0xC8101426);
        graphics.renderOutline(x, top, WIDTH, height, 0xFFD2AE5A);
        graphics.fill(x + 1, top + 1, x + WIDTH - 1, top + 2, 0x60FFE9B0);

        // the dungeon and difficulty
        int difficulty = Math.max(0, java.util.Arrays.asList(DIFF_IDS).indexOf(
                ClientUiState.stringOr(data, "difficulty", "normal")));
        Component level = Component.translatable("difficulty.eternalrealm." + DIFF_IDS[difficulty]);
        Component head = Component.translatable("screen.eternalrealm.ready.title");
        GateArt.text(graphics, font, head, x + 5, top + 4, GateArt.GOLD, 1.0F);
        int levelWidth = font.width(level);
        GateArt.text(graphics, font, level, x + WIDTH - 5 - levelWidth, top + 4, DIFF_TEXT[difficulty], 1.0F);
        int nameX = x + 9 + font.width(head);
        GateArt.fitted(graphics, font, Component.translatable(ClientUiState.stringOr(data, "name", "")), nameX, top + 4,
                x + WIDTH - 9 - levelWidth - nameX, GateArt.TEXT, 1.0F, 0.6F);

        // everyone's answer
        float mx = x + 5;
        int shown = 0;
        for (ReadyCheckClient.Member member : members) {
            float nameWidth = Math.min(font.width(member.name()), 60) * 0.75F;
            if (mx + 11 + nameWidth > x + WIDTH - 20) {
                GateArt.text(graphics, font, Component.literal("+" + (members.size() - shown)), mx, top + 17,
                        GateArt.MUTED, 0.75F);
                break;
            }
            ReadyCheckClient.drawMark(graphics, (int) mx, top + 15, member.ready());
            GateArt.fitted(graphics, font, Component.literal(member.name()), mx + 10, top + 17, 60 * 0.75F,
                    member.ready() ? GateArt.READY : GateArt.WAITING, 0.75F, 0.75F);
            mx += 10 + nameWidth + 6;
            shown++;
        }

        // this player's answer, how to give it, and the time
        long ticks = ReadyCheckClient.ticksLeft();
        if (ReadyCheckClient.countdownNow()) {
            long seconds = Math.max(1, (ticks + 19) / 20);
            GateArt.centered(graphics, font, Component.translatable("screen.eternalrealm.ready.countdown", seconds),
                    x + WIDTH / 2.0F, top + 29, GateArt.READY, 1.0F);
        } else {
            boolean ready = ReadyCheckClient.mine().orElse(false);
            long seconds = ticks / 20;
            Component time = Component.literal(String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60));
            int timeWidth = font.width(time);
            GateArt.text(graphics, font, time, x + WIDTH - 5 - timeWidth, top + 29,
                    seconds < 20 ? GateArt.WARN : GateArt.MUTED, 1.0F);
            Component mine = Component.translatable(ready ? "screen.eternalrealm.ready.hud_ready"
                    : "screen.eternalrealm.ready.hud_waiting");
            GateArt.fitted(graphics, font, mine, x + 5, top + 29, WIDTH - 14 - timeWidth,
                    ready ? GateArt.READY : GateArt.WAITING, 1.0F, 0.6F);
        }
        RenderSystem.disableBlend();
    }
}
