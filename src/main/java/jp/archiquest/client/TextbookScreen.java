package jp.archiquest.client;

import jp.archiquest.knowledge.KnowledgeBase;
import jp.archiquest.knowledge.Lesson;
import jp.archiquest.knowledge.Subject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 一級建築士の教科書GUI。
 * 上のタブで科目を選び、目次からレッスンを開く。レッスンはマウスホイール・矢印キーでスクロール。
 */
public class TextbookScreen extends Screen {
    private static Subject savedSubject = Subject.PLANNING;
    @Nullable
    private static String savedLessonId = null;
    private static int savedScroll = 0;

    private static final int PAPER = 0xFFF7F0DF;
    private static final int PAPER_EDGE = 0xFF6D4C41;
    private static final int INK = 0xFF2B2B2B;
    private static final int SUB_INK = 0xFF5D4E3C;
    private static final int LINE_H = 10;

    private Subject subject;
    @Nullable
    private Lesson lesson;
    private int scroll;
    private int indexPage;
    private List<FormattedCharSequence> lines = new ArrayList<>();
    private List<FormattedCharSequence> descLines = new ArrayList<>();
    private int left, top, pw, ph, contentTop, contentBottom, textLeft, textWidth;

    public TextbookScreen() {
        super(Component.literal("一級建築士の教科書"));
        this.subject = savedSubject;
        this.lesson = KnowledgeBase.lesson(savedLessonId);
        this.scroll = this.lesson != null ? savedScroll : 0;
    }

    @Override
    protected void init() {
        pw = Math.min(this.width - 16, 420);
        ph = Math.min(this.height - 16, 260);
        left = (this.width - pw) / 2;
        top = (this.height - ph) / 2;
        textLeft = left + 12;
        textWidth = pw - 30;
        contentTop = top + 44;
        contentBottom = top + ph - 30;

        Subject[] subs = Subject.values();
        int tabW = (pw - 12) / subs.length;
        for (int i = 0; i < subs.length; i++) {
            final Subject s = subs[i];
            Button tab = Button.builder(Component.literal(s.shortLabel), b -> selectSubject(s))
                    .bounds(left + 6 + i * tabW, top + 21, tabW - 2, 16)
                    .tooltip(Tooltip.create(Component.literal(s.examName + "「" + s.label + "」")))
                    .build();
            tab.active = s != subject || lesson != null;
            addRenderableWidget(tab);
        }
        if (lesson == null) initIndex();
        else initRead();
    }

    // ------------------------------------------------------------------ index (目次)

    private void initIndex() {
        descLines = font.split(Component.literal(subject.description), textWidth);
        List<Lesson> ls = KnowledgeBase.lessonsOf(subject);
        int listTop = contentTop + (descLines.size() + 1) * LINE_H + 6;
        int rowH = 20;
        int perPage = Math.max(1, (contentBottom - listTop) / rowH);
        int pages = Math.max(1, (ls.size() + perPage - 1) / perPage);
        indexPage = Mth.clamp(indexPage, 0, pages - 1);
        for (int i = 0; i < perPage; i++) {
            int li = indexPage * perPage + i;
            if (li >= ls.size()) break;
            final Lesson l = ls.get(li);
            addRenderableWidget(Button.builder(Component.literal((li + 1) + ". " + l.title()), b -> openLesson(l))
                    .bounds(left + 10, listTop + i * rowH, pw - 20, 18).build());
        }

        int by = top + ph - 25;
        int x = left + 8;
        if (pages > 1) {
            Button prev = Button.builder(Component.literal("◀"), b -> {
                indexPage--;
                rebuildWidgets();
            }).bounds(x, by, 20, 20).build();
            prev.active = indexPage > 0;
            addRenderableWidget(prev);
            Button next = Button.builder(Component.literal("▶"), b -> {
                indexPage++;
                rebuildWidgets();
            }).bounds(x + 22, by, 20, 20).build();
            next.active = indexPage < pages - 1;
            addRenderableWidget(next);
            x += 48;
        }
        int bw = Math.max(40, (left + pw - 8 - x - 8) / 3);
        addRenderableWidget(Button.builder(Component.literal("クイズに挑戦"), b -> runCommand("archi quiz " + subject.id))
                .bounds(x, by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("AI先生に質問"), b -> openAsk(""))
                .bounds(x + bw + 4, by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("閉じる"), b -> onClose())
                .bounds(x + (bw + 4) * 2, by, bw, 20).build());
    }

    // ------------------------------------------------------------------ read (本文)

    private void initRead() {
        final Lesson cur = lesson;
        if (cur == null) return;
        subject = cur.subject();
        lines = buildLines(cur);
        clampScroll();
        final List<Lesson> ls = KnowledgeBase.lessonsOf(subject);
        final int pos = ls.indexOf(cur);

        int by = top + ph - 25;
        int gap = 4;
        int bw = Math.max(36, (pw - 16 - gap * 4) / 5);
        int x = left + 8;
        addRenderableWidget(Button.builder(Component.literal("目次"), b -> {
            lesson = null;
            scroll = 0;
            rebuildWidgets();
        }).bounds(x, by, bw, 20).build());
        Button prev = Button.builder(Component.literal("◀ 前"), b -> openLesson(ls.get(pos - 1)))
                .bounds(x + (bw + gap), by, bw, 20).build();
        prev.active = pos > 0;
        addRenderableWidget(prev);
        Button next = Button.builder(Component.literal("次 ▶"), b -> openLesson(ls.get(pos + 1)))
                .bounds(x + (bw + gap) * 2, by, bw, 20).build();
        next.active = pos >= 0 && pos < ls.size() - 1;
        addRenderableWidget(next);
        addRenderableWidget(Button.builder(Component.literal("クイズ"), b -> runCommand("archi quiz " + subject.id))
                .bounds(x + (bw + gap) * 3, by, bw, 20)
                .tooltip(Tooltip.create(Component.literal(subject.label + "のクイズに挑戦（チャットに出題）")))
                .build());
        addRenderableWidget(Button.builder(Component.literal("質問"), b -> openAsk(cur.title() + "について "))
                .bounds(x + (bw + gap) * 4, by, bw, 20)
                .tooltip(Tooltip.create(Component.literal("このレッスンについてアーキ先生に質問")))
                .build());
    }

    private List<FormattedCharSequence> buildLines(Lesson l) {
        List<FormattedCharSequence> out = new ArrayList<>();
        MutableComponent title = Component.literal(l.title())
                .withStyle(st -> st.withBold(true).withColor(TextColor.fromRgb(l.subject().rgb)));
        out.addAll(font.split(title, textWidth));
        out.add(FormattedCharSequence.EMPTY);
        section(out, "◆ かみくだき解説", 0x1565C0, l.summary());
        section(out, "◆ マイクラで例えると", 0x2E7D32, l.minecraft());
        if (!l.points().isEmpty()) {
            heading(out, "◆ 試験に出るポイント", 0xC62828);
            for (String p : l.points()) out.addAll(font.split(Component.literal("・" + p), textWidth));
            out.add(FormattedCharSequence.EMPTY);
        }
        section(out, "◆ 覚え方・ゴロ合わせ", 0x6A1B9A, l.mnemonic());
        return out;
    }

    private void heading(List<FormattedCharSequence> out, String text, int rgb) {
        out.addAll(font.split(Component.literal(text).withStyle(st -> st.withBold(true).withColor(TextColor.fromRgb(rgb))), textWidth));
    }

    private void section(List<FormattedCharSequence> out, String head, int rgb, String body) {
        if (body == null || body.isBlank()) return;
        heading(out, head, rgb);
        for (String para : body.split("\n")) {
            if (para.isBlank()) continue;
            out.addAll(font.split(Component.literal(para.strip()), textWidth));
        }
        out.add(FormattedCharSequence.EMPTY);
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.fill(left + 4, top + 4, left + pw + 4, top + ph + 4, 0x66000000);
        g.fill(left - 2, top - 2, left + pw + 2, top + ph + 2, PAPER_EDGE);
        g.fill(left, top, left + pw, top + ph, PAPER);
        g.fill(left, top, left + pw, top + 18, 0xFF000000 | subject.rgb);
        String head = lesson == null
                ? "一級建築士の教科書 ─ " + subject.examName + "「" + subject.label + "」"
                : subject.examName + "「" + subject.label + "」";
        g.drawCenteredString(font, head, left + pw / 2, top + 5, 0xFFFFFFFF);
        g.fill(left + 6, top + 40, left + pw - 6, top + 41, 0x33000000);

        if (lesson == null) renderIndex(g);
        else renderRead(g);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderIndex(GuiGraphics g) {
        int y = contentTop;
        for (FormattedCharSequence s : descLines) {
            g.drawString(font, s, textLeft, y, SUB_INK, false);
            y += LINE_H;
        }
        String info = subject.isGakka()
                ? "本番：" + subject.examQuestions + "問（四肢択一）／ 合格基準点の例：" + subject.passMark + "点（令和7年）"
                : "本番：1課題・6時間30分 ／ ランクⅠ〜Ⅳのうち、ランクⅠが合格";
        g.drawString(font, info, textLeft, y, 0xFF8D6E63, false);
        if (KnowledgeBase.lessonsOf(subject).isEmpty()) {
            g.drawString(font, "（レッスンデータを読み込めませんでした）", textLeft, y + 20, 0xFFC62828, false);
        }
    }

    private void renderRead(GuiGraphics g) {
        int visible = visibleLines();
        int y = contentTop;
        int end = Math.min(lines.size(), scroll + visible);
        for (int i = scroll; i < end; i++) {
            g.drawString(font, lines.get(i), textLeft, y, INK, false);
            y += LINE_H;
        }
        if (lines.size() > visible) {
            int trackTop = contentTop;
            int trackBottom = contentBottom - 2;
            int trackH = trackBottom - trackTop;
            int barH = Math.max(12, trackH * visible / lines.size());
            int maxScroll = lines.size() - visible;
            int barY = trackTop + (trackH - barH) * scroll / Math.max(1, maxScroll);
            int x = left + pw - 10;
            g.fill(x, trackTop, x + 4, trackBottom, 0x22000000);
            g.fill(x, barY, x + 4, barY + barH, 0xFF8D6E63);
        }
    }

    private int visibleLines() {
        return Math.max(1, (contentBottom - contentTop) / LINE_H);
    }

    private void clampScroll() {
        scroll = Mth.clamp(scroll, 0, Math.max(0, lines.size() - visibleLines()));
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (lesson != null) {
            scroll -= (int) Math.signum(delta) * 3;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ClientModEvents.OPEN_TEXTBOOK.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        if (lesson != null) {
            int page = Math.max(1, visibleLines() - 2);
            switch (keyCode) {
                case GLFW.GLFW_KEY_DOWN -> {
                    scroll++;
                    clampScroll();
                    return true;
                }
                case GLFW.GLFW_KEY_UP -> {
                    scroll--;
                    clampScroll();
                    return true;
                }
                case GLFW.GLFW_KEY_PAGE_DOWN -> {
                    scroll += page;
                    clampScroll();
                    return true;
                }
                case GLFW.GLFW_KEY_PAGE_UP -> {
                    scroll -= page;
                    clampScroll();
                    return true;
                }
                default -> {
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        saveState();
        super.onClose();
    }

    private void saveState() {
        savedSubject = subject;
        savedLessonId = lesson != null ? lesson.id() : null;
        savedScroll = scroll;
    }

    private void selectSubject(Subject s) {
        subject = s;
        lesson = null;
        indexPage = 0;
        scroll = 0;
        rebuildWidgets();
    }

    private void openLesson(Lesson l) {
        lesson = l;
        subject = l.subject();
        scroll = 0;
        rebuildWidgets();
    }

    private void runCommand(String command) {
        if (minecraft == null || minecraft.player == null) return;
        onClose();
        minecraft.player.connection.sendCommand(command);
    }

    private void openAsk(String prefix) {
        saveState();
        if (minecraft != null) minecraft.setScreen(new ChatScreen("/archi ask " + prefix));
    }
}
