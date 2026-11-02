package com.birdiecraft.client;

import com.birdiecraft.BirdieCraft;
import com.birdiecraft.config.GolfConfig;
import com.birdiecraft.golf.engine.Club;
import com.birdiecraft.golf.engine.GaugeMath;
import com.birdiecraft.golf.engine.GolfUnits;
import com.birdiecraft.golf.engine.Lie;
import com.birdiecraft.golf.engine.PlayStyle;
import com.birdiecraft.golf.engine.ScoreTerm;
import com.birdiecraft.golf.engine.ShotCalculator;
import com.birdiecraft.golf.engine.ShotMode;
import com.birdiecraft.match.MatchMode;
import com.birdiecraft.match.MatchState;
import com.birdiecraft.match.MatchView;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The golf HUD overlay. */
public final class GolfHud implements IGuiOverlay {
    public static final ResourceLocation HUD = BirdieCraft.id("textures/gui/hud.png");

    private static final int PANEL = 0xB0101828;
    private static final int PANEL_LIGHT = 0xC01C2A40;
    private static final int BORDER = 0xFF6FD3FF;
    private static final int ACCENT = 0xFFFFD34A;

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        Font font = mc.font;
        MatchView m = ClientGolfState.match;
        boolean club = SwingController.holdingClub();

        if (m != null) {
            if (m.state == MatchState.LOBBY) {
                renderLobby(g, font, m, width, height);
            } else {
                renderHoleInfo(g, font, m, width);
                renderWind(g, font, m, width, mc.player.getYRot());
                renderLeaderboard(g, font, m, width);
                if (GolfConfig.SHOW_HOLE_MAP.get() && ClientGolfState.holeMap != null && !BallCam.isActive()) {
                    renderMap(g, m, height, mc.player.getYRot());
                }
                renderTurn(g, font, m, width);
            }
        }
        boolean showSwing = club && (m == null ? !BallCam.isActive() : m.isMyTurn() || SwingController.isActive());
        if (showSwing && !BallCam.isActive()) {
            renderClubPanel(g, font, width, height, m != null);
            renderSpin(g, font, width, height);
            renderGauge(g, font, width, height);
        }
        renderFlightReadout(g, font, width, height);
        if (m == null && club && !BallCam.isActive()) {
            g.drawString(font, Component.translatable("hud.birdiecraft.practice"), 6, 6, ACCENT, true);
        }
        boolean card = false;
        if (m != null) {
            long since = System.currentTimeMillis() - ClientGolfState.stateSinceMs;
            card = ClientGolfState.scorecardToggled
                    || (m.state == MatchState.HOLE_END && since > 2600)
                    || (m.state == MatchState.FINISHED && since > 3600);
        }
        HudMessages.render(g, font, width, height, card);
        if (card) renderScorecard(g, font, m, width, height);
        if (BallCam.isActive()) {
            g.drawString(font, Component.translatable("hud.birdiecraft.ballcam"), width - font.width(Component.translatable("hud.birdiecraft.ballcam")) - 6,
                    height - 14, 0xAAFFFFFF, true);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 1, y, x + w - 1, y + h, PANEL);
        g.fill(x, y + 1, x + w, y + h - 1, PANEL);
        g.fill(x + 1, y, x + w - 1, y + 1, 0x60FFFFFF);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, 0x40000000);
    }

    private static void drawScaled(GuiGraphics g, Font font, Component text, int x, int y, float scale, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, text, 0, 0, color, true);
        g.pose().popPose();
    }

    private static String toPar(int d) {
        return d == 0 ? "E" : (d > 0 ? "+" + d : String.valueOf(d));
    }

    // ------------------------------------------------------------------ top-left: hole info

    private void renderHoleInfo(GuiGraphics g, Font font, MatchView m, int width) {
        int x = 4, y = 4, w = 128, h = 46;
        panel(g, x, y, w, h);
        g.fill(x, y, x + 3, y + h, BORDER);
        drawScaled(g, font, Component.translatable("hud.birdiecraft.hole", m.currentHoleNumber()), x + 8, y + 5, 1.5f, 0xFFFFFFFF);
        g.drawString(font, Component.translatable("hud.birdiecraft.par_yards", m.currentPar(), m.currentYards()), x + 8, y + 20, 0xFFB8E6FF, true);
        MatchView.PView me = m.me();
        if (me != null) {
            Component stroke = me.holed ? Component.translatable("hud.birdiecraft.holed")
                    : Component.translatable("hud.birdiecraft.stroke", me.cur + 1);
            g.drawString(font, stroke, x + 8, y + 32, ACCENT, true);
        }
        Component course = m.courseTitle;
        int cw = font.width(course);
        if (cw > w - 70) {
            g.pose().pushPose();
            g.pose().translate(x + w - 4, y + 6, 0);
            g.pose().scale(0.6f, 0.6f, 1f);
            g.drawString(font, course, -font.width(course), 0, 0xFFCCCCCC, true);
            g.pose().popPose();
        } else {
            g.pose().pushPose();
            g.pose().translate(x + w - 4, y + 6, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            g.drawString(font, course, -font.width(course), 0, 0xFFCCCCCC, true);
            g.pose().popPose();
        }
    }

    // ------------------------------------------------------------------ top-right: wind

    private void renderWind(GuiGraphics g, Font font, MatchView m, int width, float yaw) {
        int cx = width - 30;
        int cy = 30;
        g.blit(HUD, cx - 24, cy - 24, 0, 0, 48, 48);
        // north marker
        float north = 180f - yaw;
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(north));
        g.drawString(font, "N", -2, -23, 0xFFFF6060, false);
        g.pose().popPose();
        if (m.windSpeed > 0.05f) {
            float rel = m.windYaw - yaw;
            g.pose().pushPose();
            g.pose().translate(cx, cy, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(rel));
            float s = 0.7f + Math.min(0.5f, m.windSpeed / 20f);
            g.pose().scale(s, s, 1f);
            g.blit(HUD, -8, -16, 48, 0, 16, 32);
            g.pose().popPose();
        }
        String txt = String.format("%.1f m/s", m.windSpeed);
        int col = m.windSpeed < 3 ? 0xFF9CFF8A : (m.windSpeed < 6 ? 0xFFFFE066 : 0xFFFF7A5A);
        g.drawString(font, txt, cx - font.width(txt) / 2, cy + 26, col, true);
    }

    // ------------------------------------------------------------------ right: leaderboard

    private void renderLeaderboard(GuiGraphics g, Font font, MatchView m, int width) {
        List<MatchView.PView> list = new ArrayList<>(m.players);
        list.sort(Comparator.comparingInt((MatchView.PView p) -> p.toPar(m.pars) * 100 + (p.finished ? 0 : 1)));
        int w = 112;
        int x = width - w - 4;
        int y = 74;
        int rowH = 11;
        panel(g, x, y, w, 6 + rowH * list.size());
        int row = 0;
        for (MatchView.PView p : list) {
            int ry = y + 4 + row * rowH;
            boolean turn = p.id == m.turnId;
            if (turn) g.fill(x + 1, ry - 1, x + w - 1, ry + rowH - 1, 0x40FFD34A);
            g.fill(x + 4, ry + 2, x + 9, ry + 7, 0xFF000000 | p.color);
            String name = p.name.length() > 10 ? p.name.substring(0, 10) : p.name;
            g.drawString(font, name, x + 12, ry, p.id == m.myId ? 0xFFFFE066 : 0xFFFFFFFF, true);
            String tp = toPar(p.toPar(m.pars));
            int d = p.toPar(m.pars);
            int tpCol = d < 0 ? 0xFFFF6A6A : (d == 0 ? 0xFFFFFFFF : 0xFF7FB8FF);
            g.drawString(font, tp, x + w - 30 - font.width(tp), ry, tpCol, true);
            String cur = p.holed ? "✔" : (p.cur > 0 ? String.valueOf(p.cur) : "-");
            g.drawString(font, cur, x + w - 8 - font.width(cur), ry, 0xFFBBBBBB, true);
            row++;
        }
    }

    // ------------------------------------------------------------------ left: hole map

    private void renderMap(GuiGraphics g, MatchView m, int height, float yaw) {
        HoleMapTexture tex = ClientGolfState.holeMap;
        var map = tex.map;
        int top = 56;
        int availH = height - top - 78;
        if (availH < 40) return;
        float scale = Math.min(1.6f, (float) availH / map.rows);
        int w = (int) (map.cols * scale);
        int h = (int) (map.rows * scale);
        int x = 6;
        panel(g, x - 3, top - 3, w + 6, h + 6);
        g.pose().pushPose();
        g.pose().translate(x, top, 0);
        g.pose().scale(scale, scale, 1f);
        g.blit(tex.location, 0, 0, 0f, 0f, map.cols, map.rows, map.cols, map.rows);
        g.pose().popPose();
        // markers
        double[] cup = map.toMap(m.cup.getX() + 0.5, m.cup.getZ() + 0.5);
        int cupX = x + (int) (cup[0] * scale);
        int cupY = top + h - (int) (cup[1] * scale);
        g.blit(HUD, cupX - 1, cupY - 9, 113, 0, 8, 10);
        for (MatchView.PView p : m.players) {
            if (p.holed) continue;
            Vec3 pos = new Vec3(p.bx, p.by, p.bz);
            for (BallFlight f : ClientGolfState.flights) {
                if (!f.practice && f.participantId == p.id && f.started()) pos = f.pos(0f);
            }
            double[] mp = map.toMap(pos.x, pos.z);
            int px = x + (int) (mp[0] * scale);
            int py = top + h - (int) (mp[1] * scale);
            if (px < x - 2 || px > x + w + 2 || py < top - 2 || py > top + h + 2) continue;
            g.fill(px - 2, py - 2, px + 2, py + 2, 0xFF000000);
            g.fill(px - 1, py - 1, px + 1, py + 1, 0xFF000000 | p.color);
        }
        // aim line from my ball to the landing preview
        MatchView.PView me = m.me();
        Vec3 land = LandingPreview.full();
        if (me != null && land != null && m.isMyTurn()) {
            double[] a = map.toMap(me.bx, me.bz);
            double[] b = map.toMap(land.x, land.z);
            for (int i = 0; i <= 20; i++) {
                double t = i / 20.0;
                int px = x + (int) ((a[0] + (b[0] - a[0]) * t) * scale);
                int py = top + h - (int) ((a[1] + (b[1] - a[1]) * t) * scale);
                if (i % 2 == 0) g.fill(px, py, px + 1, py + 1, 0xFFFFFFFF);
            }
            int lx = x + (int) (b[0] * scale);
            int ly = top + h - (int) (b[1] * scale);
            g.renderOutline(lx - 2, ly - 2, 5, 5, 0xFFFFFFFF);
        }
    }

    // ------------------------------------------------------------------ centre-top: turn info

    private void renderTurn(GuiGraphics g, Font font, MatchView m, int width) {
        Component text = null;
        int color = 0xFFFFFFFF;
        switch (m.state) {
            case INTRO -> text = Component.translatable("hud.birdiecraft.get_ready");
            case PLAYING -> {
                MatchView.PView p = m.player(m.turnId);
                if (p != null) {
                    if (p.id == m.myId) {
                        text = Component.translatable("hud.birdiecraft.your_turn");
                        color = 0xFF7CFF6B;
                    } else if (p.cpu) {
                        String dots = ".".repeat((int) (System.currentTimeMillis() / 300 % 4));
                        text = Component.translatable("hud.birdiecraft.cpu_thinking", p.name).append(dots);
                        color = 0xFFFFE066;
                    } else {
                        text = Component.translatable("hud.birdiecraft.turn_of", p.name);
                    }
                    if (m.mode == MatchMode.MULTI && m.turnTicksLeft > 0 && m.turnTicksLeft < 20 * 20) {
                        text = text.copy().append("  " + (m.turnTicksLeft / 20) + "s");
                    }
                }
            }
            case BALL_MOVING -> {
            }
            default -> {
            }
        }
        if (text != null) {
            int tw = font.width(text);
            panel(g, width / 2 - tw / 2 - 6, 4, tw + 12, 14);
            g.drawString(font, text, width / 2 - tw / 2, 7, color, true);
        }
    }

    // ------------------------------------------------------------------ bottom-left: club panel

    private void renderClubPanel(GuiGraphics g, Font font, int width, int height, boolean inMatch) {
        int w = 132, h = 58;
        int x = 4;
        int y = height - h - 26;
        panel(g, x, y, w, h);
        g.fill(x, y, x + 3, y + h, ACCENT);
        Club club = SwingController.club;
        g.blit(HUD, x + 6, y + 5, club.family.ordinal() * 16, 64, 16, 16);
        drawScaled(g, font, Component.literal(club.label), x + 26, y + 5, 1.7f, 0xFFFFFFFF);
        PlayStyle style = SwingController.style();
        Lie lie = SwingController.lie;
        String dist;
        if (club.isPutter()) {
            dist = ShotCalculator.PUTT_RANGES_YARDS[SwingController.puttRange] + "Y";
        } else {
            double max = ShotCalculator.maxCarryBlocks(club, SwingController.mode, style);
            dist = GolfUnits.yardsRounded(max) + "Y";
        }
        g.drawString(font, dist, x + w - 6 - font.width(dist), y + 8, 0xFFB8E6FF, true);
        // mode badge
        Component modeText = club.isPutter() ? Component.translatable("hud.birdiecraft.putt_range")
                : Component.translatable(SwingController.mode.translationKey());
        int modeCol = SwingController.mode == ShotMode.POWER ? 0xFFFF5A5A : (SwingController.mode == ShotMode.APPROACH ? 0xFF7FD6FF : 0xFFDDDDDD);
        g.drawString(font, modeText, x + 8, y + 24, modeCol, true);
        if (inMatch && !club.isPutter()) {
            String ps = "PS x" + SwingController.powerShotsLeft();
            g.drawString(font, ps, x + w - 6 - font.width(ps), y + 24, 0xFFFF9A6A, true);
        }
        // lie
        g.blit(HUD, x + 6, y + 37, lie.ordinal() * 16, 48, 16, 16);
        Component lieName = Component.translatable(lie.translationKey());
        g.drawString(font, lieName, x + 26, y + 36, 0xFFFFFFFF, true);
        int lo = (int) Math.round(lie.minPower(club) * 100);
        int hi = (int) Math.round(lie.maxPower(club) * 100);
        String range = lo == hi ? lo + "%" : lo + "-" + hi + "%";
        int rc = hi >= 100 ? 0xFF9CFF8A : (lo >= 80 ? 0xFFFFE066 : 0xFFFF7A5A);
        g.drawString(font, range, x + 26, y + 46, rc, true);
        g.blit(HUD, x + w - 22, y + 38, style.ordinal() * 16, 80, 16, 16);
    }

    // ------------------------------------------------------------------ bottom-right: spin

    private void renderSpin(GuiGraphics g, Font font, int width, int height) {
        if (SwingController.club.isPutter()) return;
        int x = width - 44;
        int y = height - 66;
        panel(g, x - 4, y - 4, 44, 50);
        g.blit(HUD, x + 2, y, 64, 0, 32, 32);
        int dx = SwingController.spinX * 6;
        int dy = -SwingController.spinY * 6;
        g.blit(HUD, x + 2 + 13 + dx, y + 13 + dy, 96, 0, 6, 6);
        Component label;
        if (SwingController.spinY > 0) label = Component.translatable("hud.birdiecraft.topspin");
        else if (SwingController.spinY < 0) label = Component.translatable("hud.birdiecraft.backspin");
        else label = Component.translatable("hud.birdiecraft.spin");
        g.pose().pushPose();
        g.pose().translate(x + 18, y + 35, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, label, -font.width(label) / 2, 0, 0xFFDDDDDD, true);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ bottom-centre: swing gauge

    private static final double G_MIN = -0.30;
    private static final double G_MAX = 1.06;

    private void renderGauge(GuiGraphics g, Font font, int width, int height) {
        // fit between the club panel (left) and the spin panel (right); stack above them if too narrow
        int left = 142;
        int right = width - 52;
        int w = Math.min(240, right - left - 8);
        int h = 12;
        int x;
        int y;
        if (w >= 120) {
            x = left + (right - left - w) / 2;
            y = height - 52;
        } else {
            w = Math.min(240, width - 20);
            x = width / 2 - w / 2;
            y = height - 120;
        }
        Club club = SwingController.club;
        double zone = SwingController.zoneHalf;

        // frame
        g.fill(x - 3, y - 3, x + w + 3, y + h + 3, 0xE0000000);
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, SwingController.mode == ShotMode.POWER ? 0xFFB02020 : 0xFF2E4A6A);
        g.fill(x, y, x + w, y + h, 0xFF0B1220);
        // power area gradient: green -> yellow -> red
        int x0 = gx(x, w, 0);
        int x50 = gx(x, w, 0.5);
        int x100 = gx(x, w, 1.0);
        hGradient(g, x0, y + 1, x50, y + h - 1, 0x5530C060, 0x55E0D040);
        hGradient(g, x50, y + 1, x100, y + h - 1, 0x55E0D040, 0x55E05030);
        // ticks
        for (int i = 1; i <= 4; i++) {
            int tx = gx(x, w, i * 0.25);
            g.fill(tx, y, tx + 1, y + h, i == 4 ? 0xFFFFFFFF : 0x80FFFFFF);
        }
        // impact zone and perfect window
        int zl = gx(x, w, -zone);
        int zr = gx(x, w, zone);
        g.fill(zl, y - 1, zr, y + h + 1, 0xA066CCFF);
        double pw = zone * GaugeMath.PERFECT_WINDOW;
        int pl = gx(x, w, -pw);
        int pr = Math.max(pl + 1, gx(x, w, pw));
        g.fill(pl, y - 1, pr, y + h + 1, 0xFFFFF0A0);

        // pin marker: power needed for the flag distance
        double pin = SwingController.pinDistance();
        if (pin > 0) {
            double need = GaugeMath.powerForDistance(club, SwingController.mode, SwingController.lie, SwingController.style(),
                    SwingController.puttRange, pin);
            if (need > 0 && need <= G_MAX) {
                int px = gx(x, w, need);
                g.blit(HUD, px - 1, y - 12, 113, 0, 8, 10);
            }
        }

        SwingController.Phase phase = SwingController.phase;
        if (phase == SwingController.Phase.RISING || phase == SwingController.Phase.RETURNING) {
            double pos = SwingController.gaugePosition();
            double fillTo = phase == SwingController.Phase.RISING ? pos : SwingController.power;
            int fx = gx(x, w, Math.max(0, fillTo));
            hGradient(g, x0, y + 2, fx, y + h - 2, 0xFF46E070, fillTo > 0.8 ? 0xFFFF5A3A : 0xFFFFE04A);
            int mx = gx(x, w, Math.max(G_MIN, pos));
            g.fill(mx - 1, y - 3, mx + 1, y + h + 3, 0xFFFFFFFF);
            g.blit(HUD, mx - 4, y - 11, 104, 0, 9, 9);
            if (phase == SwingController.Phase.RETURNING) {
                String pct = Math.round(SwingController.power * 100) + "%";
                g.drawString(font, pct, fx - font.width(pct) / 2, y + h + 4, 0xFFFFFFFF, true);
            }
        } else if (phase == SwingController.Phase.SENT || System.currentTimeMillis() - SwingController.lastShotMs < 1500) {
            int fx = gx(x, w, SwingController.power);
            g.fill(x0, y + 2, fx, y + h - 2, 0x8846E070);
            double impactPos = SwingController.lastDeviation * zone;
            int ix = gx(x, w, impactPos);
            g.fill(ix - 1, y - 3, ix + 1, y + h + 3, 0xFFFF6A3A);
            if (Math.abs(SwingController.lastDeviation) <= GaugeMath.PERFECT_WINDOW) {
                g.blit(HUD, ix - 8, y - 4, 129, 0, 16, 16);
            }
        }

        // labels
        Component hint;
        if (!SwingController.canSwing() && phase == SwingController.Phase.IDLE) {
            hint = Component.translatable("hud.birdiecraft.wait");
        } else {
            hint = switch (phase) {
                case IDLE -> Component.translatable("hud.birdiecraft.click_start");
                case RISING -> Component.translatable("hud.birdiecraft.click_power");
                case RETURNING -> Component.translatable("hud.birdiecraft.click_impact");
                default -> Component.empty();
            };
        }
        g.drawString(font, hint, x + w / 2 - font.width(hint) / 2, y - 24, 0xFFDDDDDD, true);
        if (pin > 0) {
            MatchView m = ClientGolfState.match;
            Vec3 o = SwingController.origin();
            int elev = m != null ? (int) Math.round((m.cup.getY() + 1 - o.y) * GolfUnits.YARDS_PER_BLOCK) : 0;
            String e = elev == 0 ? "" : (elev > 0 ? "  ▲" + elev + "Y" : "  ▼" + (-elev) + "Y");
            Component pinText = Component.translatable("hud.birdiecraft.pin", GolfUnits.yardsRounded(pin)).append(e);
            g.drawString(font, pinText, x + w - font.width(pinText), y - 13, 0xFFFFFFFF, true);
        }
        if (SwingController.mode == ShotMode.POWER && !club.isPutter()) {
            Component ps = Component.translatable("shotmode.birdiecraft.power");
            g.drawString(font, ps, x, y - 13, 0xFFFF5A5A, true);
        }
    }

    private static int gx(int x, int w, double pos) {
        return x + (int) Math.round((pos - G_MIN) / (G_MAX - G_MIN) * w);
    }

    private static void hGradient(GuiGraphics g, int x1, int y1, int x2, int y2, int from, int to) {
        if (x2 <= x1) return;
        int steps = Math.max(1, (x2 - x1) / 3);
        for (int i = 0; i < steps; i++) {
            int a = x1 + (x2 - x1) * i / steps;
            int b = x1 + (x2 - x1) * (i + 1) / steps;
            g.fill(a, y1, b, y2, lerpColor(from, to, (i + 0.5f) / steps));
        }
    }

    private static int lerpColor(int a, int b, float t) {
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    // ------------------------------------------------------------------ flight read-out

    private void renderFlightReadout(GuiGraphics g, Font font, int width, int height) {
        BallFlight mine = null;
        for (BallFlight f : ClientGolfState.flights) {
            if ((f.mine || BallCam.isActive()) && f.started() && !f.club.isPutter()) mine = f;
        }
        if (mine != null && !mine.finished()) {
            int yards = GolfUnits.yardsRounded(mine.distanceSoFar(0f));
            Component t = Component.literal(yards + "Y");
            int tw = (int) (font.width(t) * 2f);
            drawScaled(g, font, t, width / 2 - tw / 2, 24, 2f, 0xFFFFFFFF);
        }
        BallFlight p = ClientGolfState.lastPractice;
        if (p != null) {
            Component t = Component.translatable("hud.birdiecraft.practice_result", p.club.label,
                    GolfUnits.yardsRounded(p.carry), GolfUnits.yardsRounded(p.total), Component.translatable(p.restLie.translationKey()));
            int tw = font.width(t);
            panel(g, width / 2 - tw / 2 - 6, 22, tw + 12, 14);
            g.drawString(font, t, width / 2 - tw / 2, 25, 0xFF9CFFEA, true);
        }
    }

    // ------------------------------------------------------------------ lobby

    private void renderLobby(GuiGraphics g, Font font, MatchView m, int width, int height) {
        int w = 200;
        int h = 40 + m.players.size() * 11;
        int x = width / 2 - w / 2;
        int y = 30;
        panel(g, x, y, w, h);
        Component title = Component.translatable("hud.birdiecraft.lobby", m.courseTitle);
        g.drawString(font, title, x + w / 2 - font.width(title) / 2, y + 5, ACCENT, true);
        int row = 0;
        for (MatchView.PView p : m.players) {
            int ry = y + 18 + row * 11;
            g.fill(x + 8, ry + 2, x + 13, ry + 7, 0xFF000000 | p.color);
            String label = p.name + (p.cpu ? " (CPU)" : "");
            g.drawString(font, label, x + 16, ry, 0xFFFFFFFF, true);
            Component st = Component.translatable(PlayStyle.byId(p.style).translationKey());
            g.drawString(font, st, x + w - 8 - font.width(st), ry, 0xFFAAAAAA, true);
            row++;
        }
        Component hint = m.isHost ? Component.translatable("hud.birdiecraft.lobby_host") : Component.translatable("hud.birdiecraft.lobby_wait", m.hostName);
        g.drawString(font, hint, x + w / 2 - font.width(hint) / 2, y + h - 12, 0xFFBBBBBB, true);
    }

    // ------------------------------------------------------------------ scorecard

    private void renderScorecard(GuiGraphics g, Font font, MatchView m, int width, int height) {
        int n = m.holeCount;
        int nameW = 70;
        int cellW = n > 9 ? 14 : 17;
        int totW = 30;
        int w = nameW + cellW * n + totW * 2 + 8;
        int rows = 2 + m.players.size();
        int rowH = 13;
        int h = 22 + rows * rowH + 6;
        int x = width / 2 - w / 2;
        int y = height / 2 - h / 2 + 20;
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF6FD3FF);
        g.fill(x, y, x + w, y + h, 0xF0101828);
        Component title = m.state == MatchState.FINISHED ? Component.translatable("hud.birdiecraft.final_results")
                : Component.translatable("hud.birdiecraft.scorecard");
        g.drawString(font, title, x + w / 2 - font.width(title) / 2, y + 6, ACCENT, true);
        int ty = y + 20;
        // header rows
        g.fill(x + 2, ty, x + w - 2, ty + rowH * 2, PANEL_LIGHT);
        g.drawString(font, Component.translatable("hud.birdiecraft.sc_hole"), x + 6, ty + 3, 0xFFB8E6FF, false);
        g.drawString(font, Component.translatable("hud.birdiecraft.sc_par"), x + 6, ty + rowH + 3, 0xFFB8E6FF, false);
        int totalPar = 0;
        for (int i = 0; i < n; i++) {
            int cx = x + nameW + i * cellW;
            String hn = String.valueOf(m.holeNumbers[i]);
            boolean current = i == m.holeIdx && m.state != MatchState.FINISHED;
            if (current) g.fill(cx, ty, cx + cellW, ty + rowH * rows, 0x30FFD34A);
            g.drawString(font, hn, cx + cellW / 2 - font.width(hn) / 2, ty + 3, 0xFFFFFFFF, false);
            String pr = String.valueOf(m.pars[i]);
            g.drawString(font, pr, cx + cellW / 2 - font.width(pr) / 2, ty + rowH + 3, 0xFFDDDDDD, false);
            totalPar += m.pars[i];
        }
        int tx = x + nameW + n * cellW;
        g.drawString(font, "TOT", tx + 4, ty + 3, 0xFFFFFFFF, false);
        g.drawString(font, String.valueOf(totalPar), tx + 6, ty + rowH + 3, 0xFFDDDDDD, false);
        g.drawString(font, "+/-", tx + totW + 6, ty + 3, 0xFFFFFFFF, false);

        List<MatchView.PView> list = new ArrayList<>(m.players);
        if (m.state == MatchState.FINISHED) list.sort(Comparator.comparingInt(MatchView.PView::total));
        int r = 0;
        for (MatchView.PView p : list) {
            int ry = ty + rowH * (2 + r);
            if (r % 2 == 1) g.fill(x + 2, ry, x + w - 2, ry + rowH, 0x20FFFFFF);
            g.fill(x + 6, ry + 4, x + 11, ry + 9, 0xFF000000 | p.color);
            String name = (m.state == MatchState.FINISHED ? (r + 1) + ". " : "") + p.name;
            if (font.width(name) > nameW - 16) name = font.plainSubstrByWidth(name, nameW - 18);
            g.drawString(font, name, x + 14, ry + 3, p.id == m.myId ? 0xFFFFE066 : 0xFFFFFFFF, false);
            for (int i = 0; i < n && i < p.strokes.length; i++) {
                int s = p.strokes[i];
                if (s <= 0) continue;
                int cx = x + nameW + i * cellW;
                int d = s - m.pars[i];
                int col = 0xFF000000 | ScoreTerm.colorForDiff(d);
                String ss = String.valueOf(s);
                if (d < 0) g.renderOutline(cx + 2, ry + 1, cellW - 4, rowH - 2, col);
                else if (d >= 2) g.fill(cx + 2, ry + 1, cx + cellW - 2, ry + rowH - 1, 0x403050C0);
                g.drawString(font, ss, cx + cellW / 2 - font.width(ss) / 2, ry + 3, col, false);
            }
            String tot = String.valueOf(p.total());
            g.drawString(font, tot, tx + 6, ry + 3, 0xFFFFFFFF, false);
            int d = p.toPar(m.pars);
            g.drawString(font, toPar(d), tx + totW + 6, ry + 3, d < 0 ? 0xFFFF6A6A : (d == 0 ? 0xFFFFFFFF : 0xFF7FB8FF), false);
            r++;
        }
        if (m.state == MatchState.FINISHED) {
            Component hint = Component.translatable("hud.birdiecraft.results_hint");
            g.drawString(font, hint, x + w / 2 - font.width(hint) / 2, y + h + 6, 0xFFAAAAAA, true);
        }
    }
}
