package com.clockworkdiver.client.gui;

import com.clockworkdiver.clock.ClockData;
import com.clockworkdiver.customer.CustomerProfile;
import com.clockworkdiver.network.C2S;
import com.clockworkdiver.network.ModNetwork;
import com.clockworkdiver.network.S2C;
import com.clockworkdiver.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** Talking to a customer: a typewriter dialogue box with their portrait. */
public class DialogueScreen extends Screen {
    private final int entityId;
    private final int mode;
    private final CustomerProfile profile;
    @Nullable
    private final ClockData clock;
    private final int reward;
    private final List<Component> lines = new ArrayList<>();
    private int line;
    private float chars;
    private int ticks;
    private BrassButton primary;
    private BrassButton secondary;
    private int boxX, boxY, boxW, boxH;

    public DialogueScreen(int entityId, int mode, String profileId, @Nullable ClockData clock, int reward) {
        super(Component.translatable(CustomerProfile.byId(profileId).nameKey()));
        this.entityId = entityId;
        this.mode = mode;
        this.profile = CustomerProfile.byId(profileId);
        this.clock = clock;
        this.reward = reward;
        switch (mode) {
            case S2C.DIALOGUE_OFFER -> {
                lines.add(Component.translatable(profile.lineKey("offer.0")));
                lines.add(Component.translatable(profile.lineKey("offer.1")));
                if (clock != null) {
                    lines.add(Component.translatable("dialogue.clockworkdiver.offer.clock", Component.translatable(clock.type.nameKey()),
                            clock.defects.size(), reward));
                    if (clock.tutorial) lines.add(Component.translatable("dialogue.clockworkdiver.offer.tutorial"));
                }
            }
            case S2C.DIALOGUE_WAITING -> lines.add(Component.translatable(profile.lineKey("wait")));
            case S2C.DIALOGUE_DELIVER -> lines.add(Component.translatable(profile.lineKey("deliver")));
            default -> {
                lines.add(Component.translatable(profile.lineKey("thanks.0")));
                lines.add(Component.translatable(profile.lineKey("thanks.1")));
                lines.add(Component.translatable("dialogue.clockworkdiver.reward", reward));
            }
        }
    }

    @Override
    protected void init() {
        boxW = Math.min(width - 130, 380);
        boxH = 92;
        boxX = (width - boxW) / 2 + 50;
        boxY = height - boxH - 14;
        int bw = 120;
        Component primaryLabel = switch (mode) {
            case S2C.DIALOGUE_OFFER -> Component.translatable("gui.clockworkdiver.accept");
            case S2C.DIALOGUE_WAITING -> Component.translatable("gui.clockworkdiver.repair_bench");
            case S2C.DIALOGUE_DELIVER -> Component.translatable("gui.clockworkdiver.deliver");
            default -> Component.translatable("gui.clockworkdiver.bye");
        };
        primary = addRenderableWidget(new BrassButton(boxX + boxW - bw - 10, boxY - 26, bw, 20, primaryLabel, b -> primaryAction()));
        primary.glow = mode != S2C.DIALOGUE_THANKS;
        secondary = addRenderableWidget(new BrassButton(boxX + boxW - bw * 2 - 16, boxY - 26, bw, 20,
                Component.translatable("gui.clockworkdiver.close"), b -> onClose()));
        updateButtons();
    }

    private boolean finished() {
        return line >= lines.size() - 1 && chars >= lines.get(lines.size() - 1).getString().length();
    }

    private void updateButtons() {
        boolean done = finished();
        primary.visible = done;
        secondary.visible = done && mode != S2C.DIALOGUE_THANKS;
    }

    /** Also used by the CI screenshot tour. */
    public void primaryAction() {
        switch (mode) {
            case S2C.DIALOGUE_OFFER -> {
                ModNetwork.sendToServer(new C2S.AcceptCommission(entityId));
                if (clock != null && minecraft != null) minecraft.setScreen(new RepairPromptScreen(clock, true));
                else onClose();
            }
            case S2C.DIALOGUE_WAITING -> {
                ModNetwork.sendToServer(new C2S.GoToBench());
                onClose();
            }
            case S2C.DIALOGUE_DELIVER -> {
                ModNetwork.sendToServer(new C2S.DeliverClock(entityId));
                onClose();
            }
            default -> onClose();
        }
    }

    /** Skips the typewriter to the end of the conversation (CI tour). */
    public void skipAll() {
        line = lines.size() - 1;
        chars = 9999;
        updateButtons();
    }

    private void advance() {
        int len = lines.get(line).getString().length();
        if (chars < len) {
            chars = len;
        } else if (line < lines.size() - 1) {
            line++;
            chars = 0;
        }
        updateButtons();
    }

    @Override
    public void tick() {
        ticks++;
        chars += 1.7f;
        updateButtons();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        advance();
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER) {
            if (finished() && primary.visible) primaryAction();
            else advance();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, height / 3, width, height, 0x00000000, 0xC8000000);
        int px = boxX - 104, py = boxY - 30, pw = 96, ph = boxH + 30;
        Gfx.begin(g);
        Gfx.brassFrame(px, py, px + pw, py + ph, 5, 0xFF2A1E14);
        Gfx.rectV(px + 6, py + 6, px + pw - 6, py + ph - 6, 0xFF4A3420, 0xFF1E140C);
        Gfx.brassFrame(boxX, boxY, boxX + boxW, boxY + boxH, 5, 0xF0EADCB8);
        Gfx.brassFrame(boxX + 8, boxY - 14, boxX + 16 + font.width(title) + 16, boxY + 4, 3, 0xFF3A2410);
        Gfx.end();

        if (minecraft != null && minecraft.level != null) {
            Entity e = minecraft.level.getEntity(entityId);
            if (e instanceof LivingEntity living) {
                g.enableScissor(px + 6, py + 6, px + pw - 6, py + ph - 6);
                InventoryScreen.renderEntityInInventoryFollowsMouse(g, px + pw / 2, py + ph - 10, 44, (float) (px + pw / 2) - mouseX,
                        (float) (py + ph - 10 - 70) - mouseY, living);
                g.disableScissor();
            }
        }
        g.drawString(font, title, boxX + 16, boxY - 9, 0xFFFFE0A0, false);

        String full = lines.get(line).getString();
        String shown = full.substring(0, Math.min(full.length(), (int) chars));
        List<FormattedCharSequence> wrapped = font.split(FormattedText.of(shown), boxW - 26);
        int y = boxY + 12;
        for (FormattedCharSequence seq : wrapped) {
            g.drawString(font, seq, boxX + 13, y, 0xFF2A1A0C, false);
            y += 11;
        }
        if (!finished() && chars >= full.length() && (ticks / 8) % 2 == 0) {
            g.drawString(font, "▼", boxX + boxW - 16, boxY + boxH - 14, 0xFF7A3A10, false);
        }
        g.drawString(font, Component.literal((line + 1) + "/" + lines.size()).withStyle(ChatFormatting.ITALIC), boxX + boxW - 36, boxY + 6,
                0xFF8A7050, false);

        if (clock != null && (mode == S2C.DIALOGUE_OFFER || mode == S2C.DIALOGUE_DELIVER)) {
            ItemStack stack = new ItemStack(ModItems.BROKEN_CLOCK.get());
            clock.writeTo(stack);
            g.pose().pushPose();
            g.pose().translate(boxX + boxW - 46, boxY + boxH - 44, 0.0f);
            g.pose().scale(2.0f, 2.0f, 1.0f);
            g.renderItem(stack, 0, 0);
            g.pose().popPose();
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
