package com.clockworkdiver.client.gui;

import com.clockworkdiver.client.ClientState;
import com.clockworkdiver.clock.ClockData;
import com.clockworkdiver.customer.CustomerEntity;
import com.clockworkdiver.customer.CustomerProfile;
import com.clockworkdiver.network.C2S;
import com.clockworkdiver.network.ModNetwork;
import com.clockworkdiver.network.S2C;
import com.clockworkdiver.registry.ModEntities;
import com.clockworkdiver.registry.ModItems;
import com.clockworkdiver.story.StoryQuest;
import com.clockworkdiver.story.StoryQuests;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Talking to a customer: a typewriter dialogue box with the speaker's portrait. Story lines are written as
 * {@code speaker|text}: a cast id, {@code me} (the player), {@code narr} (narration) or {@code note} (a written note).
 */
public class DialogueScreen extends Screen {
    private static final String ME = "me";
    private static final String NARRATION = "narr";
    private static final String NOTE = "note";
    private static final String SYSTEM = "sys";

    private record Line(String speaker, String text) {}

    private final int entityId;
    private final int mode;
    private final CustomerProfile profile;
    @Nullable
    private final ClockData clock;
    @Nullable
    private final StoryQuest quest;
    private final int reward;
    private final List<Line> lines = new ArrayList<>();
    private final Map<String, LivingEntity> portraits = new HashMap<>();
    private int line;
    private float chars;
    private int ticks;
    private boolean closing;
    private BrassButton primary;
    private BrassButton secondary;
    private int boxX, boxY, boxW, boxH;

    public DialogueScreen(int entityId, int mode, String profileId, @Nullable ClockData clock, int reward, String extra) {
        super(Component.translatable(CustomerProfile.byId(profileId).nameKey()));
        this.entityId = entityId;
        this.mode = mode;
        this.profile = CustomerProfile.byId(profileId);
        this.clock = clock;
        this.reward = reward;
        this.quest = clock != null && clock.isStory() ? StoryQuests.byId(clock.story) : null;
        String me = profile.id;
        switch (mode) {
            case S2C.DIALOGUE_OFFER -> {
                if (quest != null) {
                    story(quest.key("offer"), me);
                    sys(Component.translatable("dialogue.clockworkdiver.offer.story", Component.translatable(clock.nameKey()),
                            clock.faultCount(), reward).getString());
                } else {
                    add(me, profile.lineKey("offer.0"));
                    add(me, profile.lineKey("offer.1"));
                    if (clock != null) {
                        sys(Component.translatable("dialogue.clockworkdiver.offer.clock", Component.translatable(clock.type.nameKey()),
                                clock.defects.size(), reward).getString());
                        if (clock.tutorial) sys(Component.translatable("dialogue.clockworkdiver.offer.tutorial").getString());
                    }
                }
            }
            case S2C.DIALOGUE_WAITING -> add(me, quest != null ? quest.key("wait") : profile.lineKey("wait"));
            case S2C.DIALOGUE_DELIVER -> add(me, quest != null ? quest.key("deliver") : profile.lineKey("deliver"));
            case S2C.DIALOGUE_CHATTER -> add(me, Language.getInstance().has(extra) ? extra : "dialogue.clockworkdiver.chatter");
            default -> {
                if (quest != null) {
                    story(quest.key("thanks"), me);
                } else {
                    add(me, profile.lineKey("thanks.0"));
                    add(me, profile.lineKey("thanks.1"));
                }
                sys(Component.translatable("dialogue.clockworkdiver.reward", reward).getString());
                if (quest != null && (ClientState.keepsakes & (1 << (quest.number - 1))) != 0) {
                    sys(Component.translatable("dialogue.clockworkdiver.keepsake", Component.translatable(quest.key("keepsake"))).getString());
                }
            }
        }
        if (lines.isEmpty()) lines.add(new Line(me, "..."));
    }

    /** Adds every numbered line under {@code prefix} ({@code prefix.0}, {@code prefix.1}, ...). */
    private void story(String prefix, String defaultSpeaker) {
        Language lang = Language.getInstance();
        for (int i = 0; i < 40 && lang.has(prefix + "." + i); i++) add(defaultSpeaker, prefix + "." + i);
    }

    private void add(String defaultSpeaker, String key) {
        String raw = Language.getInstance().getOrDefault(key);
        int bar = raw.indexOf('|');
        if (bar > 0 && bar <= 10) lines.add(new Line(raw.substring(0, bar), raw.substring(bar + 1)));
        else lines.add(new Line(defaultSpeaker, raw));
    }

    private void sys(String text) {
        lines.add(new Line(SYSTEM, text));
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
        primary.glow = mode != S2C.DIALOGUE_THANKS && mode != S2C.DIALOGUE_CHATTER;
        secondary = addRenderableWidget(new BrassButton(boxX + boxW - bw * 2 - 16, boxY - 26, bw, 20,
                Component.translatable("gui.clockworkdiver.close"), b -> onClose()));
        updateButtons();
    }

    private boolean finished() {
        return line >= lines.size() - 1 && chars >= lines.get(lines.size() - 1).text().length();
    }

    private void updateButtons() {
        boolean done = finished();
        primary.visible = done;
        secondary.visible = done && mode != S2C.DIALOGUE_THANKS && mode != S2C.DIALOGUE_CHATTER;
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

    /** CI tour: shows line {@code n} in full. */
    public void showLine(int n) {
        line = Math.max(0, Math.min(lines.size() - 1, n));
        chars = 9999;
        updateButtons();
    }

    public int lineCount() {
        return lines.size();
    }

    @Override
    public void onClose() {
        if (mode == S2C.DIALOGUE_THANKS && quest != null && !closing && minecraft != null) {
            closing = true;
            if (quest.number == StoryQuests.count()) {
                minecraft.setScreen(new EndingScreen(true));
                return;
            }
            if (quest.closesChapter()) {
                ClientState.card(S2C.TRANSITION_CHAPTER_END, String.valueOf(quest.chapter), StoryQuests.chapterKey(quest.chapter));
            }
        }
        super.onClose();
    }

    private void advance() {
        int len = lines.get(line).text().length();
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

    // ------------------------------------------------------------------ speakers

    @Nullable
    private Component speakerName(String speaker) {
        return switch (speaker) {
            case ME -> minecraft != null && minecraft.player != null ? minecraft.player.getName() : Component.translatable("speaker.clockworkdiver.me");
            case NOTE -> Component.translatable("speaker.clockworkdiver.note");
            case NARRATION, SYSTEM -> null;
            default -> CustomerProfile.exists(speaker) ? Component.translatable(CustomerProfile.byId(speaker).nameKey()) : Component.literal(speaker);
        };
    }

    @Nullable
    private LivingEntity portrait(String speaker) {
        if (minecraft == null || minecraft.level == null) return null;
        if (ME.equals(speaker)) return minecraft.player;
        if (!CustomerProfile.exists(speaker)) return null;
        CustomerProfile who = CustomerProfile.byId(speaker);
        if (who == profile) {
            Entity e = minecraft.level.getEntity(entityId);
            if (e instanceof LivingEntity living && e.isAlive()) return living;
        }
        return portraits.computeIfAbsent(speaker, id -> {
            CustomerEntity stand = ModEntities.CUSTOMER.get().create(minecraft.level);
            if (stand != null) stand.setProfileForPortrait(who);
            return stand;
        });
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Line current = lines.get(line);
        Component name = speakerName(current.speaker());
        boolean narration = NARRATION.equals(current.speaker()) || SYSTEM.equals(current.speaker());
        boolean note = NOTE.equals(current.speaker());
        g.fillGradient(0, height / 3, width, height, 0x00000000, 0xC8000000);
        int px = boxX - 104, py = boxY - 30, pw = 96, ph = boxH + 30;
        Gfx.begin(g);
        Gfx.brassFrame(px, py, px + pw, py + ph, 5, 0xFF2A1E14);
        Gfx.rectV(px + 6, py + 6, px + pw - 6, py + ph - 6, 0xFF4A3420, 0xFF1E140C);
        int boxFill = note ? 0xF0F4ECD4 : narration ? 0xF0D8CCAE : 0xF0EADCB8;
        Gfx.brassFrame(boxX, boxY, boxX + boxW, boxY + boxH, 5, boxFill);
        if (name != null) Gfx.brassFrame(boxX + 8, boxY - 14, boxX + 16 + font.width(name) + 16, boxY + 4, 3, note ? 0xFF4A3A28 : 0xFF3A2410);
        LivingEntity who = portrait(current.speaker());
        if (who == null) {
            float cx = px + pw / 2.0f, cy = py + ph / 2.0f;
            if (note) {
                // A folded note with lines of handwriting.
                Gfx.quad(cx - 26, cy - 34, cx + 22, cy - 38, cx + 28, cy + 30, cx - 22, cy + 34, 0xFFEFE4C6);
                for (int i = 0; i < 6; i++) Gfx.line(cx - 18, cy - 24 + i * 9, cx + 16, cy - 26 + i * 9, 1.0f, 0xFF7A6448);
                Gfx.circle(cx + 14, cy + 22, 5, 0xFF8A1E1E);
            } else {
                Gfx.gear(cx, cy, 20, 26, 14, ticks * 0.02f, 0xFF8C6424, 5, 0);
                Gfx.circle(cx, cy, 6, 0xFF2A1E14);
            }
        }
        Gfx.end();

        if (who != null) {
            g.enableScissor(px + 6, py + 6, px + pw - 6, py + ph - 6);
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, px + pw / 2, py + ph - 10, 44, (float) (px + pw / 2) - mouseX,
                    (float) (py + ph - 10 - 70) - mouseY, who);
            g.disableScissor();
        }
        if (name != null) g.drawString(font, name, boxX + 16, boxY - 9, 0xFFFFE0A0, false);

        String full = current.text();
        String shown = full.substring(0, Math.min(full.length(), (int) chars));
        List<FormattedCharSequence> wrapped = font.split(FormattedText.of(shown), boxW - 26);
        int y = boxY + 12;
        int ink = SYSTEM.equals(current.speaker()) ? 0xFF6A3A10 : narration ? 0xFF4A3A2A : note ? 0xFF3A2A4A : 0xFF2A1A0C;
        for (FormattedCharSequence seq : wrapped) {
            g.drawString(font, seq, boxX + 13, y, ink, false);
            y += 11;
        }
        if (!finished() && chars >= full.length() && (ticks / 8) % 2 == 0) {
            g.drawString(font, "▼", boxX + boxW - 16, boxY + boxH - 14, 0xFF7A3A10, false);
        }
        g.drawString(font, Component.literal((line + 1) + "/" + lines.size()).withStyle(ChatFormatting.ITALIC), boxX + 12, boxY + boxH - 14,
                0xFF8A7050, false);
        if (quest != null) {
            Component tag = Component.translatable("gui.clockworkdiver.quest_tag", quest.number, Component.translatable(quest.titleKey()));
            g.drawString(font, tag, boxX + boxW - 12 - font.width(tag), boxY + boxH - 14, 0xFF8A6A40, false);
        }

        if (clock != null && (mode == S2C.DIALOGUE_OFFER || mode == S2C.DIALOGUE_DELIVER)) {
            ItemStack stack = new ItemStack(ModItems.BROKEN_CLOCK.get());
            clock.writeTo(stack);
            g.pose().pushPose();
            g.pose().translate(boxX + boxW - 46, boxY + boxH - 50, 0.0f);
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
