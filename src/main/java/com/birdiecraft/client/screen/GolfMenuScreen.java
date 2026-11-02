package com.birdiecraft.client.screen;

import com.birdiecraft.BirdieCraft;
import com.birdiecraft.client.ClientGolfState;
import com.birdiecraft.client.GolfHud;
import com.birdiecraft.course.CourseTheme;
import com.birdiecraft.golf.engine.Difficulty;
import com.birdiecraft.golf.engine.PlayStyle;
import com.birdiecraft.match.MatchState;
import com.birdiecraft.match.MatchView;
import com.birdiecraft.network.C2SMenuActionPacket;
import com.birdiecraft.network.GolfNetwork;
import com.birdiecraft.network.S2CMenuDataPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Main golf menu: play modes, courses (presets), multiplayer lobbies and play style. */
public class GolfMenuScreen extends Screen {
    private static final ResourceLocation LOGO = BirdieCraft.id("textures/gui/logo.png");

    private enum Tab { PLAY, COURSES, MULTI, STYLE }

    private static Tab tab = Tab.PLAY;
    private static int courseIndex;
    private static int startHole;
    private static int holeCount = 9;
    private static int cpus = 1;
    private static Difficulty difficulty = Difficulty.AMATEUR;
    private static int presetScroll;

    private int panelX, panelY, panelW, panelH;

    public GolfMenuScreen() {
        super(Component.translatable("screen.birdiecraft.menu"));
    }

    /** Switches the open menu to a tab (0 play, 1 courses, 2 multiplayer, 3 style). */
    public static void selectTab(int index) {
        tab = Tab.values()[Math.max(0, Math.min(Tab.values().length - 1, index))];
        if (net.minecraft.client.Minecraft.getInstance().screen instanceof GolfMenuScreen s) s.refresh();
    }

    public void refresh() {
        this.clearWidgets();
        this.init();
    }

    private S2CMenuDataPacket data() {
        return ClientGolfState.menu;
    }

    private void send(int action, String arg, int... ints) {
        GolfNetwork.sendToServer(new C2SMenuActionPacket(action, arg, ints));
    }

    @Override
    protected void init() {
        panelW = Math.min(360, this.width - 20);
        panelH = Math.min(230, this.height - 20);
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;

        int tabW = (panelW - 20) / 4;
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            Button b = Button.builder(Component.translatable("screen.birdiecraft.tab." + t.name().toLowerCase(java.util.Locale.ROOT)), btn -> {
                tab = t;
                refresh();
            }).bounds(panelX + 10 + i * tabW, panelY + 38, tabW - 2, 18).build();
            b.active = tab != t;
            addRenderableWidget(b);
        }

        S2CMenuDataPacket d = data();
        int cx = panelX + 14;
        int cy = panelY + 64;
        int fullW = panelW - 28;

        MatchView m = ClientGolfState.match;
        if (m != null) {
            addRenderableWidget(Button.builder(Component.translatable("screen.birdiecraft.leave"), b -> {
                send(C2SMenuActionPacket.LEAVE, "");
                onClose();
            }).bounds(panelX + panelW - 104, panelY + panelH - 26, 94, 18).build());
        }

        if (d == null) return;
        switch (tab) {
            case PLAY -> initPlay(d, cx, cy, fullW);
            case COURSES -> initCourses(d, cx, cy, fullW);
            case MULTI -> initMulti(d, cx, cy, fullW);
            case STYLE -> initStyle(d, cx, cy, fullW);
        }
    }

    // ------------------------------------------------------------------ play tab

    private void initPlay(S2CMenuDataPacket d, int x, int y, int w) {
        if (d.courses.isEmpty()) return;
        courseIndex = Math.floorMod(courseIndex, d.courses.size());
        S2CMenuDataPacket.CourseEntry course = d.courses.get(courseIndex);
        holeCount = Math.max(1, Math.min(holeCount, course.holes() - startHole));
        startHole = Math.max(0, Math.min(startHole, course.holes() - 1));

        addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            courseIndex--;
            startHole = 0;
            holeCount = 99;
            refresh();
        }).bounds(x, y, 20, 18).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            courseIndex++;
            startHole = 0;
            holeCount = 99;
            refresh();
        }).bounds(x + w - 20, y, 20, 18).build());

        int row2 = y + 26;
        addRenderableWidget(Button.builder(Component.translatable("screen.birdiecraft.start_hole", startHole + 1), b -> {
            startHole = (startHole + 1) % course.holes();
            holeCount = Math.min(holeCount, course.holes() - startHole);
            refresh();
        }).bounds(x, row2, w / 2 - 2, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.birdiecraft.hole_count", holeCount), b -> {
            int max = course.holes() - startHole;
            int[] steps = {1, 3, 6, 9, 18};
            int next = max;
            for (int st : steps) {
                if (st > holeCount && st <= max) {
                    next = st;
                    break;
                }
            }
            holeCount = holeCount >= max ? 1 : next;
            refresh();
        }).bounds(x + w / 2 + 2, row2, w / 2 - 2, 18).build());

        int row3 = row2 + 24;
        addRenderableWidget(Button.builder(Component.translatable("screen.birdiecraft.cpus", cpus), b -> {
            cpus = cpus >= 3 ? 1 : cpus + 1;
            refresh();
        }).bounds(x, row3, w / 2 - 2, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.birdiecraft.difficulty", Component.translatable(difficulty.translationKey())), b -> {
            difficulty = difficulty.next();
            refresh();
        }).bounds(x + w / 2 + 2, row3, w / 2 - 2, 18).build());

        int row4 = row3 + 30;
        int bw = (w - 8) / 3;
        addRenderableWidget(Button.builder(Component.translatable("screen.birdiecraft.single").withStyle(ChatFormatting.GREEN), b -> {
            send(C2SMenuActionPacket.START_SINGLE, course.id(), startHole, holeCount);
            onClose();
        }).bounds(x, row4, bw, 22).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.birdiecraft.vs_cpu").withStyle(ChatFormatting.GOLD), b -> {
            send(C2SMenuActionPacket.START_VS, course.id(), startHole, holeCount, cpus, difficulty.ordinal());
            onClose();
        }).bounds(x + bw + 4, row4, bw, 22).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.birdiecraft.host").withStyle(ChatFormatting.AQUA), b -> {
            send(C2SMenuActionPacket.CREATE_LOBBY, course.id(), startHole, holeCount, 0, difficulty.ordinal());
            tab = Tab.MULTI;
        }).bounds(x + (bw + 4) * 2, row4, bw, 22).build());
    }

    // ------------------------------------------------------------------ courses tab

    private void initCourses(S2CMenuDataPacket d, int x, int y, int w) {
        List<S2CMenuDataPacket.PresetEntry> presets = d.presets;
        int visible = Math.max(3, (panelH - 100) / 22);
        presetScroll = Math.max(0, Math.min(presetScroll, Math.max(0, presets.size() - visible)));
        for (int i = 0; i < visible && presetScroll + i < presets.size(); i++) {
            S2CMenuDataPacket.PresetEntry p = presets.get(presetScroll + i);
            int ry = y + i * 22;
            if (d.canBuild) {
                Button b = Button.builder(Component.translatable(p.built() ? "screen.birdiecraft.rebuild" : "screen.birdiecraft.build"), btn -> {
                    send(C2SMenuActionPacket.BUILD_PRESET, p.id());
                    onClose();
                }).bounds(x + w - 70, ry, 70, 18).build();
                b.active = !d.building;
                addRenderableWidget(b);
            }
            if (p.built()) {
                int idx = -1;
                for (int k = 0; k < d.courses.size(); k++) if (p.id().equals(d.courses.get(k).presetId())) idx = k;
                final int courseIdx = idx;
                if (courseIdx >= 0) {
                    addRenderableWidget(Button.builder(Component.translatable("screen.birdiecraft.select"), btn -> {
                        courseIndex = courseIdx;
                        startHole = 0;
                        holeCount = 99;
                        tab = Tab.PLAY;
                        refresh();
                    }).bounds(x + w - (d.canBuild ? 128 : 56), ry, 54, 18).build());
                }
            }
        }
        if (presets.size() > visible) {
            addRenderableWidget(Button.builder(Component.literal("▲"), b -> {
                presetScroll = Math.max(0, presetScroll - 1);
                refresh();
            }).bounds(panelX + panelW - 26, panelY + panelH - 48, 16, 16).build());
            addRenderableWidget(Button.builder(Component.literal("▼"), b -> {
                presetScroll++;
                refresh();
            }).bounds(panelX + panelW - 26, panelY + panelH - 30, 16, 16).build());
        }
    }

    // ------------------------------------------------------------------ multiplayer tab

    private void initMulti(S2CMenuDataPacket d, int x, int y, int w) {
        MatchView m = ClientGolfState.match;
        if (m != null && m.state == MatchState.LOBBY) {
            if (m.isHost) {
                addRenderableWidget(Button.builder(Component.translatable("screen.birdiecraft.begin").withStyle(ChatFormatting.GREEN), b -> {
                    send(C2SMenuActionPacket.BEGIN, "");
                    onClose();
                }).bounds(x, y + 110, 110, 20).build());
                addRenderableWidget(Button.builder(Component.translatable("screen.birdiecraft.add_cpu", Component.translatable(difficulty.translationKey())), b ->
                        send(C2SMenuActionPacket.ADD_CPU, "", difficulty.ordinal())).bounds(x + 114, y + 110, 120, 20).build());
                addRenderableWidget(Button.builder(Component.translatable("screen.birdiecraft.difficulty_short"), b -> {
                    difficulty = difficulty.next();
                    refresh();
                }).bounds(x + 238, y + 110, Math.max(40, w - 238), 20).build());
            }
            return;
        }
        int i = 0;
        for (S2CMenuDataPacket.LobbyEntry l : d.lobbies) {
            if (i >= 6) break;
            int ry = y + i * 22;
            addRenderableWidget(Button.builder(Component.translatable("screen.birdiecraft.join"), b -> {
                send(C2SMenuActionPacket.JOIN, "", l.matchId());
                onClose();
            }).bounds(x + w - 60, ry, 60, 18).build());
            i++;
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.birdiecraft.refresh"), b -> send(C2SMenuActionPacket.REQUEST_MENU, ""))
                .bounds(x, panelY + panelH - 26, 80, 18).build());
    }

    // ------------------------------------------------------------------ style tab

    private void initStyle(S2CMenuDataPacket d, int x, int y, int w) {
        int bw = (w - 6) / 2;
        for (PlayStyle s : PlayStyle.VALUES) {
            int i = s.ordinal();
            int bx = x + (i % 2) * (bw + 6);
            int by = y + (i / 2) * 60;
            Button b = Button.builder(Component.translatable(s.translationKey()), btn -> send(C2SMenuActionPacket.SET_STYLE, "", s.ordinal()))
                    .bounds(bx, by, bw, 18).build();
            b.active = d.style != s.ordinal();
            addRenderableWidget(b);
        }
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0xFF6FD3FF);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xF0101828);
        g.fillGradient(panelX, panelY, panelX + panelW, panelY + 34, 0xFF1E5A3A, 0xFF143A28);
        g.blit(LOGO, panelX + panelW / 2 - 64, panelY + 2, 0, 0, 128, 32, 128, 32);

        S2CMenuDataPacket d = data();
        int x = panelX + 14;
        int y = panelY + 64;
        int w = panelW - 28;
        if (d == null) {
            g.drawCenteredString(font, Component.translatable("screen.birdiecraft.loading"), width / 2, y + 30, 0xFFAAAAAA);
        } else {
            switch (tab) {
                case PLAY -> renderPlay(g, d, x, y, w);
                case COURSES -> renderCourses(g, d, x, y, w);
                case MULTI -> renderMulti(g, d, x, y, w);
                case STYLE -> renderStyle(g, d, x, y, w);
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderPlay(GuiGraphics g, S2CMenuDataPacket d, int x, int y, int w) {
        if (d.courses.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("screen.birdiecraft.no_courses"), width / 2, y + 20, 0xFFFFE066);
            g.drawCenteredString(font, Component.translatable("screen.birdiecraft.no_courses_hint"), width / 2, y + 34, 0xFFAAAAAA);
            return;
        }
        S2CMenuDataPacket.CourseEntry c = d.courses.get(Math.floorMod(courseIndex, d.courses.size()));
        g.drawCenteredString(font, c.title(), width / 2, y + 2, 0xFFFFFFFF);
        g.drawCenteredString(font, Component.translatable("screen.birdiecraft.course_info", c.holes(), c.par()), width / 2, y + 11, 0xFFB8E6FF);
        smallText(g, Component.translatable("screen.birdiecraft.play_hint"), x, y + 118, w - 100, 0xFF9A9A9A);
    }

    private void renderCourses(GuiGraphics g, S2CMenuDataPacket d, int x, int y, int w) {
        int visible = Math.max(3, (panelH - 100) / 22);
        for (int i = 0; i < visible && presetScroll + i < d.presets.size(); i++) {
            S2CMenuDataPacket.PresetEntry p = d.presets.get(presetScroll + i);
            int ry = y + i * 22;
            g.fill(x - 2, ry - 2, x + w + 2, ry + 20, i % 2 == 0 ? 0x30FFFFFF : 0x18FFFFFF);
            Component name = Component.translatable("preset.birdiecraft." + p.id());
            g.drawString(font, name, x + 2, ry + 1, p.built() ? 0xFFFFFFFF : 0xFFBBBBBB, true);
            for (int s = 0; s < 5; s++) {
                g.blit(GolfHud.HUD, x + 2 + s * 9, ry + 10, s < p.stars() ? 145 : 154, 0, 9, 9);
            }
            CourseTheme theme = CourseTheme.byId(p.theme());
            Component info = Component.translatable(theme.translationKey()).append("  PAR " + p.par());
            g.drawString(font, info, x + 52, ry + 11, 0xFF8FC8E8, false);
            if (p.built()) g.drawString(font, "✔", x + font.width(name) + 6, ry + 1, 0xFF7CFF6B, true);
        }
        Component note = d.canBuild ? Component.translatable("screen.birdiecraft.build_warning")
                : Component.translatable("screen.birdiecraft.build_need_op");
        smallText(g, note, x, panelY + panelH - 24, w - 130, d.canBuild ? 0xFFFFD34A : 0xFFFF9A6A);
    }

    private void renderMulti(GuiGraphics g, S2CMenuDataPacket d, int x, int y, int w) {
        MatchView m = ClientGolfState.match;
        if (m != null && m.state == MatchState.LOBBY) {
            g.drawString(font, Component.translatable("hud.birdiecraft.lobby", m.courseTitle), x, y, 0xFFFFD34A, true);
            int i = 0;
            for (MatchView.PView p : m.players) {
                int ry = y + 14 + i * 11;
                g.fill(x, ry + 2, x + 5, ry + 7, 0xFF000000 | p.color);
                g.drawString(font, p.name + (p.cpu ? " (CPU)" : ""), x + 9, ry, 0xFFFFFFFF, false);
                i++;
            }
            if (!m.isHost) g.drawString(font, Component.translatable("hud.birdiecraft.lobby_wait", m.hostName), x, y + 112, 0xFFAAAAAA, false);
            return;
        }
        if (d.lobbies.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("screen.birdiecraft.no_lobbies"), width / 2, y + 20, 0xFFAAAAAA);
            g.drawCenteredString(font, Component.translatable("screen.birdiecraft.no_lobbies_hint"), width / 2, y + 34, 0xFF777777);
        }
        int i = 0;
        for (S2CMenuDataPacket.LobbyEntry l : d.lobbies) {
            if (i >= 6) break;
            int ry = y + i * 22;
            g.fill(x - 2, ry - 2, x + w + 2, ry + 20, 0x30FFFFFF);
            g.drawString(font, Component.literal(l.host() + " - ").append(l.course()), x + 2, ry + 1, 0xFFFFFFFF, true);
            g.drawString(font, Component.translatable("screen.birdiecraft.lobby_info", l.players(), l.holes()), x + 2, ry + 10, 0xFF8FC8E8, false);
            i++;
        }
    }

    private void renderStyle(GuiGraphics g, S2CMenuDataPacket d, int x, int y, int w) {
        int bw = (w - 6) / 2;
        for (PlayStyle s : PlayStyle.VALUES) {
            int i = s.ordinal();
            int bx = x + (i % 2) * (bw + 6);
            int by = y + (i / 2) * 60 + 22;
            g.blit(GolfHud.HUD, bx, by, s.ordinal() * 16, 80, 16, 16);
            bar(g, bx + 20, by, bw - 24, Component.translatable("screen.birdiecraft.stat_power"), s.distance, 0.9, 1.1, 0xFFFF6A4A);
            bar(g, bx + 20, by + 11, bw - 24, Component.translatable("screen.birdiecraft.stat_control"), s.control, 0.8, 1.3, 0xFF4AC8FF);
            bar(g, bx + 20, by + 22, bw - 24, Component.translatable("screen.birdiecraft.stat_spin"), s.spin, 0.8, 1.5, 0xFFFFD34A);
            if (d.style == s.ordinal()) g.renderOutline(bx - 2, by - 24, bw + 4, 58, 0xFF7CFF6B);
        }
    }

    /** Draws wrapped text at 75% scale inside the given width. */
    private void smallText(GuiGraphics g, Component text, int x, int y, int maxWidth, int color) {
        float scale = 0.75f;
        int lineW = (int) (maxWidth / scale);
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        int ly = 0;
        for (net.minecraft.util.FormattedCharSequence line : font.split(text, Math.max(40, lineW))) {
            g.drawString(font, line, 0, ly, color, false);
            ly += 10;
        }
        g.pose().popPose();
    }

    private void bar(GuiGraphics g, int x, int y, int w, Component label, double value, double min, double max, int color) {
        g.drawString(font, label, x, y, 0xFFCCCCCC, false);
        int bx = x + 46;
        int bw = Math.max(10, w - 46);
        g.fill(bx, y + 2, bx + bw, y + 7, 0xFF222A36);
        int fill = (int) (bw * Math.max(0.05, Math.min(1.0, (value - min) / (max - min))));
        g.fill(bx, y + 2, bx + fill, y + 7, color);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
