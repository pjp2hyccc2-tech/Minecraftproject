package com.eternalrealm.ui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.function.Supplier;

/**
 * The adventurer's journal (K): a panel with tabs on the left (status, job, skills, and the pages of later systems).
 * Every page only displays server data and sends requests; nothing is decided on the client.
 */
public abstract class JournalScreen extends Screen {
    protected static final int TAB_WIDTH = 74;
    protected static final int CONTENT_WIDTH = 320;
    protected static final int PANEL_HEIGHT = 244;
    protected static final int PANEL_BACKGROUND = 0xD0101018;
    protected static final int PANEL_BORDER = 0xFF5A4A2A;
    protected static final int TITLE_COLOR = 0xFFE8C66A;
    protected static final int TEXT = 0xFFFFFF;
    protected static final int MUTED = 0x909090;

    /** Pages of the journal, in tab order. */
    public enum Tab {
        STATUS("screen.eternalrealm.tab.status", CharacterScreen::new),
        JOB("screen.eternalrealm.tab.job", JobScreen::new),
        SKILLS("screen.eternalrealm.tab.skills", SkillScreen::new),
        QUESTS("screen.eternalrealm.tab.quests", QuestScreen::new),
        PARTY("screen.eternalrealm.tab.party", PartyScreen::new),
        GUILD("screen.eternalrealm.tab.guild", GuildScreen::new),
        RANKING("screen.eternalrealm.tab.ranking", RankingScreen::new);

        private final String key;
        private final Supplier<JournalScreen> factory;

        Tab(String key, Supplier<JournalScreen> factory) {
            this.key = key;
            this.factory = factory;
        }

        public String key() {
            return key;
        }
    }

    private static Tab lastTab = Tab.STATUS;

    private final Tab tab;
    /** Left edge of the content area (right of the tabs). */
    protected int left;
    protected int top;

    protected JournalScreen(Tab tab) {
        super(Component.translatable(tab.key()));
        this.tab = tab;
    }

    /** Opens the page that was open last. */
    public static void open() {
        Minecraft.getInstance().setScreen(lastTab.factory.get());
    }

    public static void open(Tab tab) {
        lastTab = tab;
        Minecraft.getInstance().setScreen(tab.factory.get());
    }

    @Override
    protected final void init() {
        lastTab = tab;
        int totalWidth = TAB_WIDTH + 8 + CONTENT_WIDTH;
        int panelLeft = (width - totalWidth) / 2;
        left = panelLeft + TAB_WIDTH + 8;
        top = Math.max(8, (height - PANEL_HEIGHT) / 2);
        int y = top;
        for (Tab each : Tab.values()) {
            Button button = Button.builder(Component.translatable(each.key()), b -> open(each))
                    .bounds(panelLeft, y, TAB_WIDTH, 18).build();
            button.active = each != tab;
            addRenderableWidget(button);
            y += 20;
        }
        initPage();
    }

    /** Adds the page's widgets; {@link #left}/{@link #top} are set. */
    protected abstract void initPage();

    /** Draws the page content (after the panel background, before the widgets). */
    protected abstract void renderPage(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    @Override
    public final void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(left - 7, top - 7, left + CONTENT_WIDTH + 7, top + PANEL_HEIGHT + 7, PANEL_BORDER);
        graphics.fill(left - 6, top - 6, left + CONTENT_WIDTH + 6, top + PANEL_HEIGHT + 6, PANEL_BACKGROUND);
        graphics.drawCenteredString(font, title, left + CONTENT_WIDTH / 2, top, TITLE_COLOR);
        renderPage(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** Draws wrapped text; @return the y below it */
    protected int drawWrapped(GuiGraphics graphics, Component text, int x, int y, int maxWidth, int color, int maxLines) {
        List<FormattedCharSequence> lines = font.split(text, maxWidth);
        int count = 0;
        for (FormattedCharSequence line : lines) {
            if (count++ >= maxLines) {
                break;
            }
            graphics.drawString(font, line, x, y, color);
            y += font.lineHeight + 1;
        }
        return y;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
