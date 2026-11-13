package com.eternalrealm.ui.chat;

import com.eternalrealm.client.chat.ChatLog;
import com.eternalrealm.config.ERClientConfig;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.hud.ChatPanel;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * The small framed chat (ファンタジー風のチャット): while the player is not typing, the latest messages
 * ({@link ChatLog}) in a small night-blue panel with a gold frame, bottom left where the vanilla chat would be, gone
 * ten seconds after the last message ({@link ChatPanel}); while typing, the vanilla chat (scrolling, clicking links and
 * hovering work as always) inside the same frame, and the typing line framed in gold. The client setting
 * {@code chat.fantasyChat} brings the vanilla chat back; {@code chat.lines} sets how many lines the small chat shows.
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FantasyChat {
    private static final Logger LOG = LogUtils.getLogger();
    /** The open chat's frame is drawn over its lines (they are drawn up to z 100). */
    private static final float OVER_CHAT = 200.0F;
    private static boolean broken;
    /** The open chat's ground was drawn under its lines; the frame follows once they are drawn. */
    private static int[] openWindow;

    private FantasyChat() {
    }

    public static boolean enabled() {
        if (broken) {
            return false;
        }
        try {
            return ERClientConfig.FANTASY_CHAT.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    private static int lines() {
        try {
            return ChatPanel.clampLines(ERClientConfig.CHAT_LINES.get());
        } catch (IllegalStateException e) {
            return 5;
        }
    }

    /**
     * The room the small chat takes bottom left, for the HUD layout: {width, height} (crest and margin included), or
     * null when the vanilla chat is drawn.
     */
    public static int[] reserved(Minecraft minecraft, int screenWidth) {
        if (!enabled()) {
            return null;
        }
        ChatComponent chat = minecraft.gui.getChat();
        int pitch = ChatPanel.pitch(ChatPanel.textScale(chat.getScale()), minecraft.options.chatLineSpacing().get());
        return new int[]{ChatPanel.LEFT + ChatPanel.width(screenWidth) + 2, ChatPanel.reservedHeight(lines(), pitch)};
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (!VanillaGuiOverlay.CHAT_PANEL.id().equals(event.getOverlay().id()) || !enabled()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.chatVisibility().get() == ChatVisiblity.HIDDEN) {
            return;
        }
        try {
            GuiGraphics graphics = event.getGuiGraphics();
            int width = event.getWindow().getGuiScaledWidth();
            int height = event.getWindow().getGuiScaledHeight();
            if (minecraft.gui.getChat().isChatFocused()) {
                openWindow = window(minecraft, height);
                ChatArt.back(graphics, openWindow[0], openWindow[1], openWindow[2], openWindow[3], 1.0F, 0x70);
            } else {
                event.setCanceled(true);
                small(minecraft, graphics, width, height);
            }
        } catch (RuntimeException e) {
            broken = true;
            openWindow = null;
            LOG.error("The framed chat is switched off after a rendering error (the vanilla chat is back)", e);
        }
    }

    @SubscribeEvent
    public static void onOverlayPost(RenderGuiOverlayEvent.Post event) {
        int[] window = openWindow;
        if (window == null || !VanillaGuiOverlay.CHAT_PANEL.id().equals(event.getOverlay().id())) {
            return;
        }
        openWindow = null;
        GuiGraphics graphics = event.getGuiGraphics();
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, OVER_CHAT);
        ChatArt.frame(graphics, window[0], window[1], window[2], window[3], 1.0F);
        ChatArt.crest(graphics, window[0] + window[2] / 2, window[1], ChatPanel.CREST_RISE, 1.0F);
        graphics.pose().popPose();
    }

    /** Under the typing line: the night-blue ground (the vanilla dark strip goes over it). */
    @SubscribeEvent
    public static void onScreenPre(ScreenEvent.Render.Pre event) {
        if (event.getScreen() instanceof ChatScreen screen && enabled()) {
            ChatArt.back(event.getGuiGraphics(), 0, screen.height - 16, screen.width, 16, 1.0F, 0xA0);
        }
    }

    /** Around the typing line: the gold frame. */
    @SubscribeEvent
    public static void onScreenPost(ScreenEvent.Render.Post event) {
        if (event.getScreen() instanceof ChatScreen screen && enabled()) {
            GuiGraphics graphics = event.getGuiGraphics();
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, OVER_CHAT);
            ChatArt.input(graphics, 0, screen.height - ChatArt.INPUT_HEIGHT, screen.width);
            graphics.pose().popPose();
        }
    }

    /**
     * Where the open (vanilla) chat draws its page, framed: {x, y, width, height}. A full page always, so the frame
     * never cuts through lines (the vanilla chat holds lines this one never sees, such as the conversation log).
     */
    private static int[] window(Minecraft minecraft, int screenHeight) {
        ChatComponent chat = minecraft.gui.getChat();
        double scale = chat.getScale();
        int chatWidth = Mth.ceil(chat.getWidth() / scale);
        int right = (int) Math.ceil((chatWidth + 12) * scale);
        int lineHeight = (int) (9.0 * (minecraft.options.chatLineSpacing().get() + 1.0));
        int bottom = screenHeight - ChatPanel.BOTTOM_OFFSET;
        int top = bottom - (int) Math.ceil(chat.getLinesPerPage() * lineHeight * scale);
        return new int[]{0, top - 3, right + 3, bottom - top + 6};
    }

    /** The small chat: the latest lines, bottom up, framed; nothing once the last message has faded. */
    private static void small(Minecraft minecraft, GuiGraphics graphics, int screenWidth, int screenHeight) {
        ChatComponent chat = minecraft.gui.getChat();
        Font font = minecraft.font;
        double scale = ChatPanel.textScale(chat.getScale());
        int pitch = ChatPanel.pitch(scale, minecraft.options.chatLineSpacing().get());
        int panelWidth = ChatPanel.width(screenWidth);
        int wrap = ChatPanel.wrapWidth(panelWidth, scale);
        int maxLines = lines();
        int now = minecraft.gui.getGuiTicks();
        List<ChatLog.Entry> entries = new ArrayList<>();
        List<ChatPanel.Message> messages = new ArrayList<>();
        int counted = 0;
        for (ChatLog.Entry entry : ChatLog.newestFirst()) {
            int age = now - entry.tick();
            if (age >= ChatPanel.SHOW_TICKS || counted >= maxLines) {
                break;
            }
            int size = entry.lines(font, wrap).size();
            entries.add(entry);
            messages.add(new ChatPanel.Message(size, age));
            counted += size;
        }
        List<ChatPanel.Line> lines = ChatPanel.visible(messages, maxLines);
        if (lines.isEmpty()) {
            return;
        }
        double opacity = minecraft.options.chatOpacity().get() * 0.9 + 0.1;
        float panelAlpha = (float) (ChatPanel.panelAlpha(lines) * opacity);
        int height = ChatPanel.height(lines.size(), pitch);
        int x = ChatPanel.LEFT;
        int bottom = screenHeight - ChatPanel.BOTTOM_OFFSET;
        int y = bottom - height;
        ChatArt.back(graphics, x, y, panelWidth, height, panelAlpha, 0xB0);
        int textX = x + ChatPanel.FRAME + ChatPanel.PADDING;
        int textBottom = bottom - ChatPanel.FRAME - ChatPanel.PADDING;
        float glyph = (float) (9.0 * scale);
        for (int i = 0; i < lines.size(); i++) {
            ChatPanel.Line line = lines.get(i);
            int alpha = (int) Math.round(255.0 * line.alpha() * opacity);
            if (alpha <= 3) {
                continue; // the font draws a nearly transparent colour as opaque
            }
            List<FormattedCharSequence> wrapped = entries.get(line.message()).lines(font, wrap);
            if (line.line() >= wrapped.size()) {
                continue;
            }
            float top = textBottom - (i + 1) * pitch + (pitch - glyph) / 2.0F;
            graphics.pose().pushPose();
            graphics.pose().translate(textX, top, 0.0F);
            graphics.pose().scale((float) scale, (float) scale, 1.0F);
            graphics.drawString(font, wrapped.get(line.line()), 0, 0, 0xFFFFFF | alpha << 24, true);
            graphics.pose().popPose();
        }
        ChatArt.frame(graphics, x, y, panelWidth, height, panelAlpha);
        ChatArt.crest(graphics, x + panelWidth / 2, y, ChatPanel.CREST_RISE, panelAlpha);
    }
}
