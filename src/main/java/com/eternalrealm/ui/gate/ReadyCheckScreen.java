package com.eternalrealm.ui.gate;

import com.eternalrealm.client.ClientUiState;
import com.eternalrealm.ui.GoldFrame;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 出発の準備: the party's ready check, in the gate's look. The dungeon's picture and difficulty, every member with
 * 準備OK or 準備中, the time left (or, once everyone is ready, the countdown), and the button that answers: 準備OK
 * while this player is still 準備中, back to 準備中 once they are ready. The leader can call the check off. Closing the
 * screen keeps the check going (the HUD shows it; the chat's buttons and {@code /dungeon ready} answer it too). The
 * server decides everything ({@code ReadyChecks}).
 */
public final class ReadyCheckScreen extends Screen {
    private static final int W = 300;
    private static final int RIBBON_TOP = -9;
    private static final int PIC_X = 16;
    private static final int PIC_Y = 26;
    private static final int PIC_W = W - 32;
    private static final int PIC_H = 60;
    private static final int STATUS_Y = 92;
    private static final int LIST_Y = 106;
    private static final int ROW_H = 16;
    private static final int ROW_STEP = 17;
    private static final int[] DIFF_TEXT = {0xFF8CE08C, 0xFFFF8A6A, 0xFFD6A0FF};
    private static final String[] DIFF_IDS = {"normal", "hard", "ex"};

    private final long openedAt = Util.getMillis();
    private float scale = 1.0F;
    private float left;
    private float top;
    private int panelH;

    public ReadyCheckScreen() {
        super(Component.translatable("screen.eternalrealm.ready.title"));
    }

    private int columns() {
        return ReadyCheckClient.members().size() > 5 ? 2 : 1;
    }

    private int rows() {
        int count = Math.max(1, ReadyCheckClient.members().size());
        return (count + columns() - 1) / columns();
    }

    private int buttonsY() {
        return LIST_Y + rows() * ROW_STEP + 6;
    }

    private int panelHeight() {
        return buttonsY() + 24 + 20;
    }

    @Override
    protected void init() {
        layout();
    }

    private void layout() {
        panelH = panelHeight();
        float fitW = (width - 8) / (float) W;
        float fitH = (height - 8) / (float) (panelH - RIBBON_TOP);
        scale = Math.min(1.0F, Math.min(fitW, fitH));
        if (scale >= 1.0F) {
            left = Math.round((width - W) / 2.0F);
            top = Math.round((height - (panelH - RIBBON_TOP)) / 2.0F) - RIBBON_TOP;
        } else {
            left = (width - W * scale) / 2.0F;
            top = (height - (panelH - RIBBON_TOP) * scale) / 2.0F - RIBBON_TOP * scale;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        if (!ReadyCheckClient.active()) {
            onClose();
            return;
        }
        if (panelHeight() != panelH) {
            layout();
        }
    }

    private double localX(double mouseX) {
        return (mouseX - left) / scale;
    }

    private double localY(double mouseY) {
        return (mouseY - top) / scale;
    }

    private static boolean inside(double x, double y, int bx, int by, int bw, int bh) {
        return x >= bx && x < bx + bw && y >= by && y < by + bh;
    }

    private int toggleX() {
        return ReadyCheckClient.iAmLeader() ? W / 2 - 96 - 4 : W / 2 - 48;
    }

    private int cancelX() {
        return W / 2 + 6;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            double x = localX(mouseX);
            double y = localY(mouseY);
            if (inside(x, y, toggleX(), buttonsY(), 96, 24)) {
                toggle();
                return true;
            }
            if (ReadyCheckClient.iAmLeader() && inside(x, y, cancelX(), buttonsY() + 2, 74, 20)) {
                ReadyCheckClient.play(SoundEvents.UI_BUTTON_CLICK.value(), 0.8F);
                ReadyCheckClient.callOff();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE) {
            toggle();
            return true;
        }
        if (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void toggle() {
        Optional<Boolean> mine = ReadyCheckClient.mine();
        if (mine.isEmpty()) {
            return;
        }
        ReadyCheckClient.play(SoundEvents.UI_BUTTON_CLICK.value(), mine.get() ? 0.8F : 1.2F);
        ReadyCheckClient.answer(!mine.get());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        Optional<JsonObject> state = ReadyCheckClient.state();
        if (state.isEmpty()) {
            return;
        }
        JsonObject data = state.get();
        double mx = localX(mouseX);
        double my = localY(mouseY);
        float t = (Util.getMillis() - openedAt) / 1000.0F;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(left, top, 0.0F);
        pose.scale(scale, scale, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        GateArt.panel(graphics, W, panelH, t, Math.min(W - 40, panelH - 30));
        GateArt.ribbon(graphics, font, title, W, RIBBON_TOP);

        // the dungeon
        String id = ClientUiState.stringOr(data, "id", "d1");
        GoldFrame.draw(graphics, PIC_X, PIC_Y, PIC_W, PIC_H, (long) (t * 20.0F));
        float u = 4.0F + 4.0F * Mth.sin(t * (float) (Math.PI * 2.0 / 26.0));
        graphics.blit(GateArt.picture(id), PIC_X, PIC_Y, PIC_W, PIC_H, u, 46.0F, 464, 104, GateArt.PIC_TEX_W,
                GateArt.PIC_TEX_H);
        graphics.fillGradient(PIC_X, PIC_Y + PIC_H - 26, PIC_X + PIC_W, PIC_Y + PIC_H, 0x00000000, 0xD0000000);
        int difficulty = Math.max(0, java.util.Arrays.asList(DIFF_IDS).indexOf(
                ClientUiState.stringOr(data, "difficulty", "normal")));
        Component level = Component.translatable("difficulty.eternalrealm." + DIFF_IDS[difficulty]);
        int levelWidth = font.width(level);
        GateArt.atlas(graphics, PIC_X + PIC_W - levelWidth - 28, PIC_Y + PIC_H - 22, GateArt.GEM_U
                + (difficulty * 2 + 1) * 20, 0, 20, 20);
        GateArt.text(graphics, font, level, PIC_X + PIC_W - levelWidth - 6, PIC_Y + PIC_H - 15, DIFF_TEXT[difficulty],
                1.0F);
        GateArt.fitted(graphics, font, Component.translatable(ClientUiState.stringOr(data, "name", "")), PIC_X + 6,
                PIC_Y + PIC_H - 17, PIC_W - levelWidth - 42, GateArt.GOLD, 1.5F, 0.8F);

        // how many are ready, and the time
        List<ReadyCheckClient.Member> members = ReadyCheckClient.members();
        long ticks = ReadyCheckClient.ticksLeft();
        if (ReadyCheckClient.countdownNow()) {
            long seconds = Math.max(1, (ticks + 19) / 20);
            float pulse = 1.0F + 0.12F * Mth.sin(t * 9.0F);
            GateArt.centered(graphics, font, Component.translatable("screen.eternalrealm.ready.countdown", seconds),
                    W / 2.0F, STATUS_Y - 1, GateArt.READY, 1.15F * pulse);
        } else {
            GateArt.text(graphics, font, Component.translatable("screen.eternalrealm.ready.count",
                    ReadyCheckClient.readyCount(), members.size()), PIC_X, STATUS_Y, GateArt.TEXT, 1.0F);
            long seconds = ticks / 20;
            Component time = Component.translatable("screen.eternalrealm.ready.left",
                    String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60));
            GateArt.text(graphics, font, time, PIC_X + PIC_W - font.width(time), STATUS_Y,
                    seconds < 20 ? GateArt.WARN : GateArt.MUTED, 1.0F);
        }

        // the members
        int columns = columns();
        int columnWidth = columns == 1 ? PIC_W : (PIC_W - 8) / 2;
        String me = minecraft != null && minecraft.player != null ? minecraft.player.getUUID().toString() : "";
        for (int i = 0; i < members.size(); i++) {
            ReadyCheckClient.Member member = members.get(i);
            int column = i / rows();
            int x = PIC_X + column * (columnWidth + 8);
            int y = LIST_Y + (i % rows()) * ROW_STEP;
            boolean mine = member.id().toString().equals(me);
            graphics.fill(x, y, x + columnWidth, y + ROW_H, mine ? 0x40FFD866 : 0x38000000);
            if (mine) {
                graphics.renderOutline(x, y, columnWidth, ROW_H, 0x90FFD866);
            }
            if (member.leader()) {
                GateArt.atlas(graphics, x + 3, y + 3, GateArt.MARK_U + 18, GateArt.MARK_V, 9, 9);
            }
            Component status = Component.translatable(member.ready() ? "screen.eternalrealm.ready.ok"
                    : "screen.eternalrealm.ready.waiting");
            float statusWidth = Math.min(font.width(status), 56) + 14;
            GateArt.fitted(graphics, font, Component.literal(member.name()), x + 15, y + 4,
                    columnWidth - 20 - statusWidth, mine ? GateArt.GOLD : GateArt.TEXT, 1.0F, 0.7F);
            int pillX = (int) (x + columnWidth - statusWidth - 3);
            float glow = member.ready() ? 1.0F : 0.75F + 0.25F * Mth.sin(t * 4.0F + i);
            graphics.fill(pillX, y + 2, x + columnWidth - 3, y + ROW_H - 2,
                    member.ready() ? GateArt.argb(0x2E7A40, 0.85F) : GateArt.argb(0x7A5418, 0.85F * glow));
            GateArt.atlas(graphics, pillX + 2, y + 4, GateArt.MARK_U + (member.ready() ? 0 : 9), GateArt.MARK_V, 9, 9);
            GateArt.fitted(graphics, font, status, pillX + 12, y + 4, statusWidth - 14,
                    member.ready() ? 0xFFDFFFE4 : 0xFFFFE6B0, 1.0F, 0.6F);
        }

        // the answer, and (the leader) calling it off
        int by = buttonsY();
        int tx = toggleX();
        Optional<Boolean> mine = ReadyCheckClient.mine();
        boolean ready = mine.orElse(false);
        boolean overToggle = inside(mx, my, tx, by, 96, 24);
        GateArt.atlas(graphics, tx, by, ready ? GateArt.WAIT_BUTTON_U : GateArt.OK_BUTTON_U,
                GateArt.TOGGLE_V + (overToggle ? 24 : 0), 96, 24);
        Component label = Component.translatable(ready ? "screen.eternalrealm.ready.unready"
                : "screen.eternalrealm.ready.ready");
        float size = Math.min(1.0F, 84.0F / Math.max(1, font.width(label)));
        GateArt.centered(graphics, font, label, tx + 48, by + 12 - 4 * size, 0xFFFFF6DE, size);
        if (ReadyCheckClient.iAmLeader()) {
            int cx = cancelX();
            boolean overCancel = inside(mx, my, cx, by + 2, 74, 20);
            GateArt.atlas(graphics, cx, by + 2, GateArt.PILL_U, GateArt.BUTTON_V + (overCancel ? 20 : 0), 74, 20);
            GateArt.centered(graphics, font, Component.translatable("screen.eternalrealm.ready.call_off"), cx + 37,
                    by + 8, GateArt.MUTED, 1.0F);
        }
        GateArt.centered(graphics, font, Component.translatable("screen.eternalrealm.ready.hint"), W / 2.0F, by + 29,
                0xFF8A7E70, 0.75F);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        pose.popPose();

        List<Component> tip = new ArrayList<>();
        if (overToggle) {
            tip.add(Component.translatable(ready ? "screen.eternalrealm.ready.unready_help"
                    : "screen.eternalrealm.ready.ready_help"));
        }
        if (!tip.isEmpty()) {
            graphics.renderComponentTooltip(font, tip, mouseX, mouseY);
        }
        RenderSystem.disableBlend();
    }
}
