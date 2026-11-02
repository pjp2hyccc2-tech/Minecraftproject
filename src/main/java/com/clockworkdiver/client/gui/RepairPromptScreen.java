package com.clockworkdiver.client.gui;

import com.clockworkdiver.clock.ClockData;
import com.clockworkdiver.network.C2S;
import com.clockworkdiver.network.ModNetwork;
import com.clockworkdiver.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Shown right after receiving a clock (and when using the clock item): the big 「修理台」 button that takes
 * the player to the repair bench.
 */
public class RepairPromptScreen extends Screen {
    private final ClockData clock;
    private final boolean received;
    private int ticks;

    public RepairPromptScreen(ClockData clock, boolean received) {
        super(Component.translatable("gui.clockworkdiver.repair_bench"));
        this.clock = clock;
        this.received = received;
    }

    @Override
    protected void init() {
        int bw = 170, bh = 38;
        BrassButton bench = new BrassButton((width - bw) / 2, height / 2 + 46, bw, bh, Component.translatable("gui.clockworkdiver.repair_bench"),
                b -> goToBench());
        bench.glow = true;
        bench.textScale = 2.0f;
        addRenderableWidget(bench);
        addRenderableWidget(new BrassButton((width - 80) / 2, height / 2 + 92, 80, 18, Component.translatable("gui.clockworkdiver.later"),
                b -> onClose()));
    }

    /** Also used by the CI screenshot tour. */
    public void goToBench() {
        ModNetwork.sendToServer(new C2S.GoToBench());
        onClose();
    }

    @Override
    public void tick() {
        ticks++;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float t = ticks + partialTick;
        g.fillGradient(0, 0, width, height, 0xB0100804, 0xD0100804);
        int cw = Math.min(300, width - 40), ch = 150;
        int x0 = (width - cw) / 2, y0 = height / 2 - 100;
        Gfx.begin(g);
        Gfx.brassFrame(x0, y0, x0 + cw, y0 + ch, 6, 0xFFEADCB8);
        if (received) {
            float p = Math.min(1.0f, t / 12.0f);
            for (int i = 0; i < 12; i++) {
                float a = i / 12.0f * (float) Math.PI * 2.0f + t * 0.02f;
                float r1 = 22 + 26 * p;
                Gfx.tri(x0 + 46, y0 + 70, x0 + 46 + (float) Math.cos(a - 0.12f) * r1, y0 + 70 + (float) Math.sin(a - 0.12f) * r1,
                        x0 + 46 + (float) Math.cos(a + 0.12f) * r1, y0 + 70 + (float) Math.sin(a + 0.12f) * r1, Gfx.withAlpha(0xFFE6A0, 70));
            }
        }
        Gfx.circleGradient(x0 + 46, y0 + 70, 30, 0x60FFFFFF, 0x00FFFFFF);
        Gfx.end();

        ItemStack stack = new ItemStack(ModItems.BROKEN_CLOCK.get());
        clock.writeTo(stack);
        g.pose().pushPose();
        g.pose().translate(x0 + 46 - 24, y0 + 70 - 24 + (float) Math.sin(t * 0.1f) * 2.0f, 0.0f);
        g.pose().scale(3.0f, 3.0f, 1.0f);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();

        Component title = Component.translatable(received ? "gui.clockworkdiver.received" : "gui.clockworkdiver.commission").withStyle(ChatFormatting.BOLD);
        g.drawString(font, title, x0 + 86, y0 + 14, 0xFF3A2410, false);
        g.drawString(font, Component.translatable(clock.nameKey()), x0 + 86, y0 + 30, 0xFF7A3A10, false);
        g.drawString(font, Component.translatable("gui.clockworkdiver.owner", Component.translatable(clock.profile.nameKey())), x0 + 86, y0 + 42,
                0xFF5A4630, false);
        g.drawString(font, Component.translatable("gui.clockworkdiver.progress", clock.foundCount(), clock.defects.size(), clock.repairedCount(),
                clock.faultCount()), x0 + 86, y0 + 56, 0xFF5A4630, false);
        List<FormattedCharSequence> help = font.split(Component.translatable(clock.allRepaired() ? "gui.clockworkdiver.prompt.done"
                : "gui.clockworkdiver.prompt.help"), cw - 100);
        int y = y0 + 74;
        for (FormattedCharSequence line : help) {
            g.drawString(font, line, x0 + 86, y, 0xFF3A2410, false);
            y += 10;
        }
        super.render(g, mouseX, mouseY, partialTick);
        float bob = (float) Math.sin(t * 0.25f) * 3.0f;
        g.drawCenteredString(font, Component.literal("▼"), width / 2, (int) (height / 2 + 32 + bob), 0xFFFFD27A);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
