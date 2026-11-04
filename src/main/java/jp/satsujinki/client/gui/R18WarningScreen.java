package jp.satsujinki.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import jp.satsujinki.Satsujinki;
import jp.satsujinki.client.ClientState;
import jp.satsujinki.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** 【R18】 age gate shown before the title screen. */
public class R18WarningScreen extends Screen {
    private static final ResourceLocation LOGO = Satsujinki.id("textures/gui/r18_logo.png");
    private static final ResourceLocation DRIPS = Satsujinki.id("textures/gui/blood_drips.png");
    private final Screen parent;
    private int age;
    private boolean played;

    public R18WarningScreen(Screen parent) {
        super(Component.translatable("satsujinki.r18.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int y = this.height - 40;
        this.addRenderableWidget(Button.builder(Component.translatable("satsujinki.r18.accept").withStyle(ChatFormatting.RED), b -> accept())
                .bounds(this.width / 2 - 155, y, 150, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("satsujinki.r18.decline"), b -> this.minecraft.stop())
                .bounds(this.width / 2 + 5, y, 150, 20).build());
        if (!this.played) {
            this.played = true;
            ClientState.playUi(ModSounds.UI_R18.get(), 1.0F, 1.0F);
        }
    }

    public void accept() {
        ClientState.r18Accepted = true;
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public void tick() {
        this.age++;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        g.fill(0, 0, this.width, this.height, 0xFF070000);
        float t = this.age + pt;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // dripping blood along the top edge
        int dripH = (int) Math.min(this.height * 0.45F, 20 + t * 1.5F);
        g.setColor(1.0F, 1.0F, 1.0F, 0.9F);
        g.blit(DRIPS, 0, 0, this.width, dripH, 0.0F, 0.0F, 256, 128, 256, 128);
        // logo
        int lw = Math.min(360, this.width - 40);
        int lh = lw / 2;
        int lx = (this.width - lw) / 2;
        int ly = Math.max(6, this.height / 2 - lh - 46);
        float pulse = 0.85F + 0.15F * Mth.sin(t * 0.12F);
        g.setColor(pulse, pulse, pulse, Math.min(1.0F, t / 20.0F));
        g.blit(LOGO, lx, ly, lw, lh, 0.0F, 0.0F, 512, 256, 512, 256);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();

        int y = ly + lh + 6;
        int cx = this.width / 2;
        g.drawCenteredString(this.font, Component.translatable("satsujinki.r18.line1").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), cx, y, 0xFFFFFF);
        g.drawCenteredString(this.font, Component.translatable("satsujinki.r18.line2"), cx, y + 14, 0xE0C0C0);
        g.drawCenteredString(this.font, Component.translatable("satsujinki.r18.line3"), cx, y + 26, 0xE0C0C0);
        g.drawCenteredString(this.font, Component.translatable("satsujinki.r18.line4"), cx, y + 38, 0xA08080);
        g.drawCenteredString(this.font, Component.translatable("satsujinki.r18.line5").withStyle(ChatFormatting.GRAY), cx, y + 54, 0x909090);
        super.render(g, mouseX, mouseY, pt);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
