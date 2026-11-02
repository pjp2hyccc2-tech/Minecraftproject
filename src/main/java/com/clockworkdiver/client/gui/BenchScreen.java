package com.clockworkdiver.client.gui;

import com.clockworkdiver.clock.ClockData;
import com.clockworkdiver.clock.ClockType;
import com.clockworkdiver.clock.Defect;
import com.clockworkdiver.clock.DefectType;
import com.clockworkdiver.clock.DiveMods;
import com.clockworkdiver.network.C2S;
import com.clockworkdiver.network.ModNetwork;
import com.clockworkdiver.network.S2C;
import com.clockworkdiver.registry.ModSounds;
import com.clockworkdiver.workshop.Upgrades;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The repair bench (修理台). The Chrono Spectacles go on, and their lens - which follows the mouse - shows
 * the movement inside the clock. Hold the lens over a glowing part to identify it, select it, and dive:
 * the view zooms in as the player shrinks into the clock.
 */
public class BenchScreen extends Screen {
    public static final int GLASSES_TICKS = 34;
    public static final int FOCUS_TICKS = 16;
    private static final int DIVE_TICKS = 46;
    private static final int DIVE_SEND_AT = 26;

    @Nullable
    private final ClockData clock;
    private final int coins;
    private final int[] levels;
    private final boolean tutorial;
    private final int reputation;

    private int ticks;
    private int glassTicks;
    private float lensX = -1000, lensY = -1000;
    private float lensR;
    private int focusId = -1;
    private int focusTicks;
    private int selectedId = -1;
    private int diveTicks = -1;
    private int flashTicks;
    private Component flashText = Component.empty();
    private boolean upgradeTab;
    /** Set by the CI tour to steer the lens without a mouse. */
    public float forcedLensX = Float.NaN, forcedLensY = Float.NaN;

    private float cx, cy, s;
    private int panelX, panelY, panelW, panelH;
    private BrassButton diveButton;
    private final BrassButton[] upgradeButtons = new BrassButton[3];

    public BenchScreen(S2C.OpenBench msg) {
        super(Component.translatable("gui.clockworkdiver.bench"));
        this.clock = ClockData.load(msg.clock());
        this.coins = msg.coins();
        this.levels = new int[]{msg.wrenchLevel(), msg.steamLevel(), msg.lensLevel()};
        this.tutorial = msg.tutorial();
        this.reputation = msg.reputation();
        this.glassTicks = msg.glassesAnimation() ? 0 : GLASSES_TICKS;
        if (clock != null) {
            for (Defect d : clock.defects) {
                if (d.found && !d.repaired) {
                    selectedId = d.id;
                    break;
                }
            }
        }
        if (clock == null) upgradeTab = true;
    }

    private ClockType type() {
        return clock == null ? ClockType.POCKET_WATCH : clock.type;
    }

    @Override
    protected void init() {
        int clockArea = Math.min(height - 36, (int) (width * 0.56f));
        s = clockArea * 0.40f;
        if (type().tall()) s *= 0.8f;
        cx = 14 + clockArea / 2.0f;
        cy = height / 2.0f + 8;
        panelX = 14 + clockArea + 12;
        panelW = width - panelX - 10;
        panelY = 22;
        panelH = height - 34;
        lensR = s * (0.24f + 0.04f * levels[2]);

        diveButton = addRenderableWidget(new BrassButton(panelX + 8, panelY + panelH - 54, panelW - 16, 22,
                Component.translatable("gui.clockworkdiver.dive"), b -> startDive()));
        diveButton.glow = true;
        int half = (panelW - 22) / 2;
        addRenderableWidget(new BrassButton(panelX + 8, panelY + panelH - 28, half, 18, Component.translatable("gui.clockworkdiver.close"),
                b -> onClose()));
        addRenderableWidget(new BrassButton(panelX + 14 + half, panelY + panelH - 28, half, 18,
                Component.translatable(upgradeTab ? "gui.clockworkdiver.tab.job" : "gui.clockworkdiver.tab.upgrade"), b -> {
                    upgradeTab = !upgradeTab;
                    rebuildWidgets();
                }));
        for (int k = 0; k < 3; k++) {
            final int kind = k;
            int cost = Upgrades.cost(k, levels[k]);
            boolean max = levels[k] >= Upgrades.MAX_LEVEL;
            upgradeButtons[k] = addRenderableWidget(new BrassButton(panelX + panelW - 74, panelY + 50 + k * 46, 64, 18,
                    max ? Component.translatable("gui.clockworkdiver.max") : Component.translatable("gui.clockworkdiver.buy", cost),
                    b -> ModNetwork.sendToServer(new C2S.Upgrade(kind))));
            upgradeButtons[k].active = !max && coins >= cost;
        }
        updateButtons();
    }

    private void updateButtons() {
        boolean job = !upgradeTab && clock != null;
        diveButton.visible = job && diveTicks < 0;
        Defect sel = clock == null ? null : clock.defect(selectedId);
        diveButton.active = sel != null && sel.found && !sel.repaired && sel.type.divable() && lensReady();
        if (clock != null && clock.allRepaired()) {
            diveButton.setMessage(Component.translatable("gui.clockworkdiver.all_done"));
            diveButton.glow = false;
        }
        for (BrassButton b : upgradeButtons) {
            if (b != null) b.visible = upgradeTab && diveTicks < 0;
        }
    }

    private boolean lensReady() {
        return glassTicks >= GLASSES_TICKS;
    }

    // ------------------------------------------------------------------ logic

    @Override
    public void tick() {
        ticks++;
        if (glassTicks < GLASSES_TICKS) glassTicks++;
        if (flashTicks > 0) flashTicks--;
        if (diveTicks >= 0) {
            diveTicks++;
            if (diveTicks == DIVE_SEND_AT) ModNetwork.sendToServer(new C2S.StartDive(selectedId));
            if (diveTicks > DIVE_TICKS + 60) onClose();
            return;
        }
        if (!Float.isNaN(forcedLensX)) {
            lensX = forcedLensX;
            lensY = forcedLensY;
        }
        if (clock != null && lensReady() && !upgradeTab) {
            Defect best = null;
            float bestD = lensR * 0.6f;
            for (Defect d : clock.defects) {
                if (d.found) continue;
                float dx = ClockArt.defectX(d, cx, s) - lensX, dy = ClockArt.defectY(d, cy, s) - lensY;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist < bestD) {
                    bestD = dist;
                    best = d;
                }
            }
            if (best != null) {
                if (best.id == focusId) focusTicks++;
                else {
                    focusId = best.id;
                    focusTicks = 1;
                }
                if (focusTicks >= FOCUS_TICKS) discover(best);
            } else {
                focusId = -1;
                focusTicks = 0;
            }
        }
        updateButtons();
    }

    private void discover(Defect d) {
        d.found = true;
        focusId = -1;
        focusTicks = 0;
        ModNetwork.sendToServer(new C2S.DiscoverDefect(d.id));
        if (d.type == DefectType.CLUE) {
            d.repaired = true;
            flashText = Component.translatable("gui.clockworkdiver.found_clue", Component.translatable(clueKey()));
        } else {
            flashText = Component.translatable("gui.clockworkdiver.found", Component.translatable(d.partKey()), Component.translatable(d.type.nameKey()));
        }
        flashTicks = 60;
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.DISCOVER.get(), 1.0f, 0.8f));
        if (selectedId < 0 || clock == null || clock.defect(selectedId) == null || clock.defect(selectedId).repaired) selectedId = d.id;
        if (d.type == DefectType.CLUE) selectedId = d.id;
    }

    /** Language key of this clock's clue (story clocks). */
    private String clueKey() {
        return clock != null && clock.isStory() ? "story.clockworkdiver." + clock.story + ".clue" : "defect.clockworkdiver.clue";
    }

    private Component partName(Defect d) {
        return d.type == DefectType.CLUE ? Component.translatable(clueKey()) : Component.translatable(d.partKey());
    }

    /** Starts the shrinking animation; the server is asked to start the dive halfway through. */
    public void startDive() {
        if (clock == null || diveTicks >= 0) return;
        Defect sel = clock.defect(selectedId);
        if (sel == null || !sel.found || sel.repaired || !sel.type.divable()) return;
        diveTicks = 0;
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.SHRINK.get(), 1.0f, 1.0f));
        updateButtons();
    }

    /** CI tour helpers. */
    public float defectScreenX(int id) {
        Defect d = clock == null ? null : clock.defect(id);
        return d == null ? cx : ClockArt.defectX(d, cx, s);
    }

    public float defectScreenY(int id) {
        Defect d = clock == null ? null : clock.defect(id);
        return d == null ? cy : ClockArt.defectY(d, cy, s);
    }

    @Nullable
    public ClockData clock() {
        return clock;
    }

    public void select(int id) {
        selectedId = id;
        updateButtons();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (diveTicks >= 0 || clock == null || upgradeTab) return false;
        float pick = Math.max(9.0f, s * 0.09f);
        for (Defect d : clock.defects) {
            if (!d.found) continue;
            float dx = ClockArt.defectX(d, cx, s) - (float) mouseX, dy = ClockArt.defectY(d, cy, s) - (float) mouseY;
            if (dx * dx + dy * dy < pick * pick) {
                select(d.id);
                click();
                return true;
            }
        }
        int rowY = listTop();
        for (Defect d : clock.defects) {
            if (mouseX >= panelX + 6 && mouseX <= panelX + panelW - 6 && mouseY >= rowY && mouseY < rowY + 12) {
                if (d.found) {
                    select(d.id);
                    click();
                }
                return true;
            }
            rowY += 12;
        }
        return false;
    }

    private void click() {
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.2f, 0.5f));
    }

    private int listTop() {
        return panelY + 74;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float time = ticks + partialTick;
        if (Float.isNaN(forcedLensX)) {
            lensX = mouseX;
            lensY = mouseY;
        }
        boolean diving = diveTicks >= 0;
        float diveT = diving ? Math.min(1.0f, (diveTicks + partialTick) / DIVE_TICKS) : 0.0f;
        boolean running = clock == null || clock.allRepaired();
        drawDesk(g, time);

        float zx = cx, zy = cy;
        Defect sel = clock == null ? null : clock.defect(selectedId);
        g.pose().pushPose();
        if (diving && sel != null) {
            zx = ClockArt.defectX(sel, cx, s);
            zy = ClockArt.defectY(sel, cy, s);
            float zoom = (float) Math.pow(45.0, diveT * diveT);
            g.pose().translate(zx + (width / 2.0f - zx) * diveT, zy + (height / 2.0f - zy) * diveT, 0.0f);
            g.pose().mulPose(Axis.ZP.rotation(diveT * diveT * 0.8f));
            g.pose().scale(zoom, zoom, 1.0f);
            g.pose().translate(-zx, -zy, 0.0f);
        }
        Gfx.begin(g);
        Gfx.circleGradient(cx + 5, cy + 8, s * 1.25f, 0x80000000, 0x00000000);
        ClockArt.movement(clock, type(), cx, cy, s, time);
        Gfx.end();

        boolean lensOn = lensReady() && !diving && !upgradeTab && clock != null && !running;
        Gfx.maskOn = lensOn || diving;
        if (diving) {
            Gfx.maskX = zx;
            Gfx.maskY = zy;
            Gfx.maskR = lensR * (0.6f + diveT * 4.0f);
            Gfx.maskFeather = lensR * 0.3f;
        } else {
            Gfx.maskX = lensX;
            Gfx.maskY = lensY;
            Gfx.maskR = lensR;
            Gfx.maskFeather = Math.max(3.0f, lensR * 0.1f);
        }
        Gfx.begin(g);
        ClockArt.exterior(type(), cx, cy, s, time, running);
        Gfx.end();
        ClockArt.numerals(g, font, type(), cx, cy, s);
        Gfx.maskOn = false;

        if (lensOn) drawLens(g, time);
        if (!diving) drawMarkers(g, time);
        g.pose().popPose();

        if (!diving) {
            drawPanel(g, time);
            super.render(g, mouseX, mouseY, partialTick);
            drawBanner(g, time);
            hoverTooltip(g, mouseX, mouseY);
        }
        if (glassTicks < GLASSES_TICKS) drawGlasses(g, (glassTicks + partialTick) / GLASSES_TICKS);
        if (flashTicks > 0 && !diving) {
            float a = Math.min(1.0f, flashTicks / 15.0f);
            Gfx.begin(g);
            int w = font.width(flashText) + 24;
            Gfx.rect(cx - w / 2.0f, 6, cx + w / 2.0f, 24, Gfx.withAlpha(0x3A1408, (int) (220 * a)));
            Gfx.rect(cx - w / 2.0f, 23, cx + w / 2.0f, 24, Gfx.withAlpha(0xFFD27A, (int) (255 * a)));
            Gfx.end();
            g.drawCenteredString(font, flashText, (int) cx, 11, Gfx.withAlpha(0xFFE6A0, Math.max(8, (int) (255 * a))));
        }
        if (diving) drawDiveOverlay(g, diveT);
    }

    private void drawDesk(GuiGraphics g, float time) {
        g.fillGradient(0, 0, width, height, 0xFF2C1C10, 0xFF120A05);
        Gfx.begin(g);
        for (int i = 0; i < height; i += 9) {
            int c = ((i / 9) % 3 == 0) ? 0x14000000 : 0x0AFFE0B0;
            Gfx.rect(0, i, width, i + 1, c);
        }
        Gfx.circleGradient(cx - s * 0.6f, -s * 0.2f, s * 2.4f, 0x40FFD890, 0x00FFD890);
        float matR = s * 1.35f;
        Gfx.circle(cx, cy, matR, 0xFF1E3A2A);
        Gfx.ring(cx, cy, matR - 2, matR, 0xFF2E5A40);
        // Tools on the desk.
        float tx = cx + s * 1.05f, ty = cy + s * 1.0f;
        Gfx.line(tx, ty, tx + s * 0.4f, ty - s * 0.25f, Math.max(1.0f, s * 0.02f), 0xFFC8CCD2);
        Gfx.line(tx + 4, ty + 3, tx + s * 0.4f + 2, ty - s * 0.25f + 3, Math.max(1.0f, s * 0.02f), 0xFFA8ACB2);
        float sx = cx - s * 1.2f, sy = cy + s * 1.05f;
        Gfx.line(sx, sy, sx + s * 0.35f, sy - s * 0.08f, Math.max(1.5f, s * 0.035f), 0xFF8A1E1E);
        Gfx.line(sx + s * 0.35f, sy - s * 0.08f, sx + s * 0.6f, sy - s * 0.14f, Math.max(0.8f, s * 0.012f), 0xFFD0D4DA);
        Gfx.end();
    }

    private void drawLens(GuiGraphics g, float time) {
        Gfx.begin(g);
        Gfx.circleGradient(lensX, lensY, lensR, 0x1A8FE8FF, 0x308FE8FF);
        for (int i = 0; i < 6; i++) {
            float a = time * 0.03f + i * 1.047f;
            float r = lensR * (0.35f + 0.5f * ((i * 0.37f + time * 0.004f) % 1.0f));
            Gfx.circle(lensX + (float) Math.cos(a) * r, lensY + (float) Math.sin(a) * r, Math.max(0.8f, lensR * 0.015f), 0x88DDF6FF);
        }
        float rim = Math.max(3.0f, s * 0.04f);
        Gfx.metalRing(lensX, lensY, lensR, lensR + rim, ClockArt.GOLD);
        Gfx.ring(lensX, lensY, lensR + rim, lensR + rim + 1, 0xFF3A2410);
        Gfx.arc(lensX, lensY, lensR - 1, lensR + 1, (float) Math.PI * 1.1f, (float) Math.PI * 1.35f, 0x90FFFFFF);
        // Spectacle bridge and the second lens edge, so it reads as glasses.
        float bx = lensX + lensR + rim;
        Gfx.line(bx, lensY - lensR * 0.15f, bx + lensR * 0.45f, lensY - lensR * 0.25f, Math.max(1.5f, rim * 0.6f), ClockArt.GOLD);
        Gfx.arc(bx + lensR * 0.45f + lensR, lensY, lensR, lensR + rim, (float) Math.PI * 0.75f, (float) Math.PI * 1.25f, Gfx.alpha(ClockArt.GOLD, 0.8f));
        if (focusId >= 0) {
            float p = Math.min(1.0f, focusTicks / (float) FOCUS_TICKS);
            Gfx.arc(lensX, lensY, lensR + rim + 2, lensR + rim + 5, -(float) Math.PI / 2.0f, -(float) Math.PI / 2.0f + p * (float) Math.PI * 2.0f, 0xFF7CF3FF);
        }
        Gfx.end();
        if (focusId >= 0) {
            g.drawCenteredString(font, Component.translatable("gui.clockworkdiver.analysing"), (int) lensX, (int) (lensY + lensR + 10), 0xFF7CF3FF);
        }
    }

    private void drawMarkers(GuiGraphics g, float time) {
        if (clock == null) return;
        Gfx.begin(g);
        float r = Math.max(5.0f, s * 0.055f);
        for (Defect d : clock.defects) {
            if (!d.found) continue;
            float x = ClockArt.defectX(d, cx, s), y = ClockArt.defectY(d, cy, s);
            boolean sel = d.id == selectedId;
            if (sel) {
                float p = 0.5f + 0.5f * (float) Math.sin(time * 0.3f);
                Gfx.ring(x, y, r * (1.5f + p * 0.3f), r * (1.75f + p * 0.3f), 0xFFFFD27A);
            }
            Gfx.circle(x, y, r + 1.5f, 0xFF1A0E06);
            if (d.type == DefectType.CLUE) {
                Gfx.circleGradient(x, y, r, 0xFFE0B8FF, 0xFF6A2A9A);
                Gfx.ring(x - r * 0.12f, y - r * 0.12f, r * 0.3f, r * 0.45f, 0xFFFFF4E0);
                Gfx.line(x + r * 0.2f, y + r * 0.2f, x + r * 0.55f, y + r * 0.55f, Math.max(1.0f, r * 0.15f), 0xFFFFF4E0);
                continue;
            }
            Gfx.circleGradient(x, y, r, d.repaired ? 0xFF9CFFB0 : 0xFFFF8A70, d.repaired ? 0xFF2E9A4A : 0xFFB0201A);
            Gfx.gear(x, y, r * 0.45f, r * 0.62f, 8, time * (d.repaired ? 0.08f : 0.0f), 0xFFFFF4E0, 0, 0);
        }
        Gfx.end();
    }

    private void hoverTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (clock == null || upgradeTab) return;
        float pick = Math.max(9.0f, s * 0.09f);
        for (Defect d : clock.defects) {
            if (!d.found) continue;
            float dx = ClockArt.defectX(d, cx, s) - mouseX, dy = ClockArt.defectY(d, cy, s) - mouseY;
            if (dx * dx + dy * dy < pick * pick) {
                g.renderTooltip(font, List.of(partName(d).copy().withStyle(ChatFormatting.GOLD),
                        Component.translatable(d.type.nameKey()).withStyle(d.type == DefectType.CLUE ? ChatFormatting.LIGHT_PURPLE
                                : d.repaired ? ChatFormatting.GREEN : ChatFormatting.RED)),
                        java.util.Optional.empty(), mouseX, mouseY);
                return;
            }
        }
    }

    private void drawPanel(GuiGraphics g, float time) {
        Gfx.begin(g);
        Gfx.brassFrame(panelX, panelY, panelX + panelW, panelY + panelH, 5, 0xFFEADCB8);
        Gfx.end();
        int x = panelX + 10, y = panelY + 10, w = panelW - 20;
        int ink = 0xFF2E1C0C, soft = 0xFF6A5236;
        if (upgradeTab) {
            g.drawString(font, Component.translatable("gui.clockworkdiver.upgrades").withStyle(ChatFormatting.BOLD), x, y, ink, false);
            g.drawString(font, Component.translatable("gui.clockworkdiver.coins", coins), x, y + 14, 0xFF8A5A10, false);
            g.drawString(font, Component.translatable("gui.clockworkdiver.reputation", reputation), x, y + 25, soft, false);
            for (int k = 0; k < 3; k++) {
                int ry = panelY + 46 + k * 46;
                g.drawString(font, Component.translatable("upgrade.clockworkdiver." + k), x, ry, ink, false);
                Gfx.begin(g);
                for (int l = 0; l < Upgrades.MAX_LEVEL; l++) {
                    Gfx.circle(x + 5 + l * 11, ry + 16, 4, 0xFF3A2410);
                    Gfx.circle(x + 5 + l * 11, ry + 16, 3, l < levels[k] ? 0xFFF0C860 : 0xFFB8A888);
                }
                Gfx.end();
                List<FormattedCharSequence> desc = font.split(Component.translatable("upgrade.clockworkdiver." + k + ".desc"), w - 80);
                int dy = ry + 24;
                for (FormattedCharSequence line : desc) {
                    g.drawString(font, line, x, dy, soft, false);
                    dy += 9;
                }
            }
            return;
        }
        if (clock == null) {
            g.drawString(font, Component.translatable("gui.clockworkdiver.no_job").withStyle(ChatFormatting.BOLD), x, y, ink, false);
            int dy = y + 16;
            for (FormattedCharSequence line : font.split(Component.translatable("gui.clockworkdiver.no_job.help"), w)) {
                g.drawString(font, line, x, dy, soft, false);
                dy += 10;
            }
            return;
        }
        g.drawString(font, Component.translatable(clock.nameKey()).withStyle(ChatFormatting.BOLD), x, y, ink, false);
        g.drawString(font, Component.translatable("gui.clockworkdiver.owner", Component.translatable(clock.profile.nameKey())), x, y + 12, soft, false);
        int dy = y + 26;
        for (FormattedCharSequence line : font.split(Component.translatable(clock.descKey()), w)) {
            if (dy > listTop() - 22) break;
            g.drawString(font, line, x, dy, 0xFF5A4630, false);
            dy += 9;
        }
        int rowY = listTop() - 12;
        g.drawString(font, Component.translatable("gui.clockworkdiver.parts", clock.foundCount(), clock.defects.size()), x, rowY, 0xFF7A3A10, false);
        rowY += 12;
        for (Defect d : clock.defects) {
            boolean sel = d.id == selectedId;
            if (sel) {
                Gfx.begin(g);
                Gfx.rect(panelX + 6, rowY - 2, panelX + panelW - 6, rowY + 10, 0x40C08A2E);
                Gfx.end();
            }
            Component name = d.found ? partName(d) : Component.translatable("gui.clockworkdiver.unknown_part");
            boolean clue = d.type == DefectType.CLUE && d.found;
            int color = clue ? 0xFF6A2A9A : d.repaired ? 0xFF2E7A3A : d.found ? 0xFF9A2A1A : 0xFF8A7A66;
            String mark = clue ? "◆ " : d.repaired ? "✔ " : d.found ? "✖ " : "? ";
            g.drawString(font, Component.literal(mark).append(name), x, rowY, color, false);
            if (d.found && !d.repaired && (d.mods != 0 || d.fragCount > 0 || d.boss != 0)) {
                g.drawString(font, "⚠", panelX + panelW - 18, rowY, 0xFFB06A10, false);
            }
            rowY += 12;
        }
        rowY += 4;
        Defect sel = clock.defect(selectedId);
        Component detail;
        if (sel != null && sel.found && sel.type == DefectType.CLUE) detail = Component.translatable(clueKey() + ".desc");
        else if (clock.allRepaired()) detail = Component.translatable("gui.clockworkdiver.done_help");
        else if (sel != null && sel.found) detail = Component.translatable("defect.clockworkdiver." + sel.type.id + ".desc");
        else if (clock.foundCount() < clock.defects.size()) detail = Component.translatable("gui.clockworkdiver.find_help");
        else detail = Component.translatable("gui.clockworkdiver.select_help");
        if (sel != null && sel.found && sel.type == DefectType.CLUE) {
            g.drawString(font, Component.translatable("defect.clockworkdiver.clue").withStyle(ChatFormatting.BOLD), x, rowY, 0xFF6A2A9A, false);
            rowY += 11;
        } else if (sel != null && sel.found && !clock.allRepaired()) {
            g.drawString(font, Component.translatable(sel.type.nameKey()).withStyle(ChatFormatting.BOLD), x, rowY, 0xFF9A2A1A, false);
            rowY += 11;
            if (!sel.repaired) {
                // The twists waiting inside this part.
                for (Component twist : twists(sel)) {
                    if (rowY > panelY + panelH - 76) break;
                    g.drawString(font, Component.literal("⚠ ").append(twist), x, rowY, 0xFFB0400A, false);
                    rowY += 10;
                }
            }
        }
        for (FormattedCharSequence line : font.split(detail, w)) {
            if (rowY > panelY + panelH - 66) break;
            g.drawString(font, line, x, rowY, 0xFF4A3420, false);
            rowY += 9;
        }
    }

    private static List<Component> twists(Defect d) {
        java.util.List<Component> out = new java.util.ArrayList<>();
        for (String key : DiveMods.keys(d.mods)) {
            if (key.endsWith(".timed") && d.timeLimit > 0) {
                out.add(Component.translatable(key + ".time", d.timeLimit / 60, String.format("%02d", d.timeLimit % 60)));
            } else {
                out.add(Component.translatable(key));
            }
        }
        if (d.boss != 0) out.add(Component.translatable("gui.clockworkdiver.twist.boss." + d.boss));
        if (d.fragCount > 0) out.add(Component.translatable("gui.clockworkdiver.twist.fragments", d.fragCount));
        return out;
    }

    private void drawBanner(GuiGraphics g, float time) {
        if (clock == null || upgradeTab || !lensReady()) return;
        Component text = null;
        if (clock.foundCount() == 0 && ticks < 400) text = Component.translatable("gui.clockworkdiver.banner.find");
        else if (selectedId >= 0 && clock.foundCount() == clock.defects.size() && !clock.allRepaired() && tutorial) {
            text = Component.translatable("gui.clockworkdiver.banner.dive");
        }
        if (text == null) return;
        float p = 0.6f + 0.4f * (float) Math.sin(time * 0.15f);
        int w = font.width(text) + 20;
        int y = height - 14;
        Gfx.begin(g);
        Gfx.rect(cx - w / 2.0f, y - 4, cx + w / 2.0f, y + 10, Gfx.withAlpha(0x1A0E06, 200));
        Gfx.end();
        g.drawCenteredString(font, text, (int) cx, y, Gfx.withAlpha(0xFFE6A0, (int) (255 * p)));
    }

    private void drawGlasses(GuiGraphics g, float t) {
        float cxS = width / 2.0f;
        float R = width * 0.13f;
        float y, scale, alpha;
        if (t < 0.55f) {
            float e = 1.0f - (float) Math.pow(1.0f - t / 0.55f, 3);
            y = -R * 2.0f + (height * 0.45f + R * 2.0f) * e;
            scale = 1.0f;
            alpha = 1.0f;
        } else {
            float e = (t - 0.55f) / 0.45f;
            y = height * 0.45f;
            scale = 1.0f + e * e * 4.0f;
            alpha = 1.0f - e;
        }
        g.fill(0, 0, width, height, Gfx.withAlpha(0x000000, (int) (120 * alpha)));
        g.pose().pushPose();
        g.pose().translate(cxS, y, 0.0f);
        g.pose().scale(scale, scale, 1.0f);
        Gfx.alphaMul = alpha;
        Gfx.begin(g);
        float gap = R * 1.2f;
        for (int side : new int[]{-1, 1}) {
            float lx = side * gap;
            Gfx.circleGradient(lx, 0, R, 0x408FE8FF, 0x608FE8FF);
            Gfx.metalRing(lx, 0, R, R * 1.12f, ClockArt.GOLD);
            Gfx.arc(lx, 0, R * 0.8f, R * 0.88f, (float) Math.PI * 1.1f, (float) Math.PI * 1.4f, 0xA0FFFFFF);
            Gfx.line(side * (gap + R * 1.1f), -R * 0.2f, side * (gap + R * 2.4f), -R * 0.6f, R * 0.08f, ClockArt.GOLD);
        }
        Gfx.arc(0, R * 0.25f, gap - R * 1.05f, gap - R * 0.92f, (float) Math.PI * 1.15f, (float) Math.PI * 1.85f, ClockArt.GOLD);
        for (int side : new int[]{-1, 1}) {
            for (int i = 0; i < 8; i++) {
                float a = i / 8.0f * (float) Math.PI * 2.0f;
                Gfx.circle(side * gap + (float) Math.cos(a) * R * 1.06f, (float) Math.sin(a) * R * 1.06f, R * 0.025f, 0xFF7A5420);
            }
        }
        Gfx.end();
        Gfx.alphaMul = 1.0f;
        g.pose().popPose();
        if (t > 0.3f && t < 0.9f) {
            float a = Math.min(1.0f, (t - 0.3f) * 6.0f) * Math.min(1.0f, (0.9f - t) * 6.0f);
            g.pose().pushPose();
            g.pose().translate(width / 2.0f, height * 0.75f, 0.0f);
            g.pose().scale(1.6f, 1.6f, 1.0f);
            Component text = Component.translatable("gui.clockworkdiver.glasses_on");
            g.drawString(font, text, -font.width(text) / 2, 0, Gfx.withAlpha(0xFFE6A0, Math.max(8, (int) (255 * a))), true);
            g.pose().popPose();
        }
    }

    private void drawDiveOverlay(GuiGraphics g, float t) {
        float white = Gfx.smoothstep(0.42f, 0.95f, t);
        Gfx.begin(g);
        float maxR = (float) Math.sqrt(width * width + height * height);
        Gfx.circleGradient(width / 2.0f, height / 2.0f, maxR * (0.2f + t), Gfx.withAlpha(0xFFFFFF, (int) (255 * white)),
                Gfx.withAlpha(0xFFF4E0, (int) (255 * Math.min(1.0f, white * 1.4f))));
        for (int i = 0; i < 14; i++) {
            float a = i * 0.449f + t * 6.0f;
            float r = maxR * (0.5f - t * 0.45f) * (0.4f + (i % 5) * 0.15f);
            Gfx.circle(width / 2.0f + (float) Math.cos(a) * r, height / 2.0f + (float) Math.sin(a) * r, 2.0f + i % 3,
                    Gfx.withAlpha(0xFFE6A0, (int) (200 * (1.0f - white))));
        }
        Gfx.end();
        float a = Math.min(1.0f, t * 3.0f);
        g.pose().pushPose();
        g.pose().translate(width / 2.0f, height / 2.0f + 40, 0.0f);
        float sc = 2.0f - t * 1.2f;
        g.pose().scale(sc, sc, 1.0f);
        Component text = Component.translatable("gui.clockworkdiver.shrinking");
        g.drawString(font, text, -font.width(text) / 2, 0, Gfx.withAlpha(white > 0.5f ? 0x6A4A1A : 0xFFE6A0, Math.max(8, (int) (255 * a))), white <= 0.5f);
        g.pose().popPose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
