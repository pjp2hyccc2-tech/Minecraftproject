package jp.satsujinki.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import jp.satsujinki.Satsujinki;
import jp.satsujinki.client.ClientState;
import jp.satsujinki.game.KillStyle;
import jp.satsujinki.game.ShopCatalog;
import jp.satsujinki.network.BuyPacket;
import jp.satsujinki.network.ModNetwork;
import jp.satsujinki.network.TerminalActionPacket;
import jp.satsujinki.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** 主催者端末: start a death game, buy machines with joy points, read your records. */
public class HostTerminalScreen extends Screen {
    private static final ResourceLocation BG = Satsujinki.id("textures/gui/terminal.png");
    private static final int W = 256, H = 200;
    private static int lastTab;
    private static int lastCount = 8;

    private final BlockPos pos;
    private int tab;
    private int left, top;
    private int age;
    private final List<Button> buyButtons = new ArrayList<>();
    private final List<ItemStack> shopStacks = new ArrayList<>();
    private Button startButton;
    private Button stopButton;

    public HostTerminalScreen(BlockPos pos) {
        super(Component.translatable("satsujinki.terminal.title"));
        this.pos = pos;
        this.tab = lastTab;
    }

    public static void selectTab(int tab) {
        lastTab = Mth.clamp(tab, 0, 2);
    }

    @Override
    protected void init() {
        this.left = (this.width - W) / 2;
        this.top = (this.height - H) / 2;
        this.buyButtons.clear();
        this.shopStacks.clear();
        this.startButton = null;
        this.stopButton = null;
        String[] tabs = {"game", "shop", "records"};
        for (int i = 0; i < tabs.length; i++) {
            final int t = i;
            Button b = Button.builder(Component.translatable("satsujinki.terminal.tab." + tabs[i]), x -> {
                this.tab = t;
                lastTab = t;
                ClientState.playUi(ModSounds.HOST_REMOTE.get(), 1.2F, 0.5F);
                this.init(this.minecraft, this.width, this.height);
            }).bounds(this.left + 8 + i * 82, this.top + 22, 78, 18).build();
            b.active = this.tab != i;
            this.addRenderableWidget(b);
        }
        if (this.tab == 0) initGame();
        else if (this.tab == 1) initShop();
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), x -> this.onClose())
                .bounds(this.left + W - 58, this.top + H - 24, 50, 18).build());
    }

    private void initGame() {
        int y = this.top + 74;
        this.addRenderableWidget(Button.builder(Component.literal("-5"), b -> lastCount = Math.max(1, lastCount - 5)).bounds(this.left + 22, y, 26, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("-1"), b -> lastCount = Math.max(1, lastCount - 1)).bounds(this.left + 50, y, 26, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("+1"), b -> lastCount = Math.min(40, lastCount + 1)).bounds(this.left + 180, y, 26, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("+5"), b -> lastCount = Math.min(40, lastCount + 5)).bounds(this.left + 208, y, 26, 18).build());
        this.startButton = Button.builder(Component.translatable("satsujinki.terminal.start").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), b -> {
            ModNetwork.sendToServer(new TerminalActionPacket(TerminalActionPacket.START, lastCount, this.pos));
            this.onClose();
        }).bounds(this.left + 48, this.top + 104, 160, 24).build();
        this.addRenderableWidget(this.startButton);
        this.stopButton = Button.builder(Component.translatable("satsujinki.terminal.stop"),
                b -> ModNetwork.sendToServer(new TerminalActionPacket(TerminalActionPacket.STOP, 0, this.pos)))
                .bounds(this.left + 78, this.top + 134, 100, 18).build();
        this.addRenderableWidget(this.stopButton);
        refresh();
    }

    private void initShop() {
        List<ShopCatalog.Entry> entries = ShopCatalog.ENTRIES;
        for (int i = 0; i < entries.size(); i++) {
            int col = i % 2, row = i / 2;
            int x = this.left + 10 + col * 120;
            int y = this.top + 46 + row * 20;
            final int idx = i;
            Button b = Button.builder(Component.literal(String.format("%,d", entries.get(i).price())), btn -> ModNetwork.sendToServer(new BuyPacket(idx)))
                    .bounds(x + 70, y, 46, 18).build();
            this.buyButtons.add(b);
            this.shopStacks.add(entries.get(i).stack().get());
            this.addRenderableWidget(b);
        }
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < this.buyButtons.size(); i++) {
            this.buyButtons.get(i).active = ClientState.points >= ShopCatalog.ENTRIES.get(i).price();
        }
        if (this.startButton != null) this.startButton.active = !ClientState.roundActive;
        if (this.stopButton != null) this.stopButton.active = ClientState.roundActive;
    }

    @Override
    public void tick() {
        this.age++;
        refresh();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        this.renderBackground(g);
        RenderSystem.enableBlend();
        g.blit(BG, this.left, this.top, 0.0F, 0.0F, W, H, 256, 256);
        RenderSystem.disableBlend();
        float t = this.age + pt;
        int titleCol = 0xFF000000 | Mth.hsvToRgb(0.0F, 0.9F, 0.75F + 0.25F * Mth.sin(t * 0.2F));
        g.drawString(this.font, this.title, this.left + 10, this.top + 8, titleCol, true);
        Component pc = Component.translatable("satsujinki.hud.points", String.format("%,d", ClientState.points));
        g.drawString(this.font, pc, this.left + W - 10 - this.font.width(pc), this.top + 8, 0xFFFFC84A, true);

        switch (this.tab) {
            case 0 -> renderGame(g);
            case 1 -> renderShop(g, mouseX, mouseY);
            default -> renderRecords(g);
        }
        super.render(g, mouseX, mouseY, pt);
        if (this.tab == 1) shopTooltip(g, mouseX, mouseY);
    }

    private void renderGame(GuiGraphics g) {
        int cx = this.left + W / 2;
        g.drawCenteredString(this.font, Component.translatable("satsujinki.terminal.game_title").withStyle(ChatFormatting.BOLD), cx, this.top + 50, 0xFFE0E0E0);
        g.drawCenteredString(this.font, Component.translatable("satsujinki.terminal.count", lastCount), cx, this.top + 79, 0xFFFF5050);
        if (ClientState.roundActive) {
            int secs = ClientState.roundTicksLeft / 20;
            g.drawCenteredString(this.font, Component.translatable("satsujinki.hud.round", ClientState.roundAlive, ClientState.roundTotal,
                    String.format("%02d:%02d", secs / 60, secs % 60)), cx, this.top + 158, 0xFFFF8080);
        } else {
            g.drawCenteredString(this.font, Component.translatable("satsujinki.terminal.hint1"), cx, this.top + 158, 0xFFA09090);
            g.drawCenteredString(this.font, Component.translatable("satsujinki.terminal.hint2"), cx, this.top + 169, 0xFFA09090);
        }
    }

    private void renderShop(GuiGraphics g, int mouseX, int mouseY) {
        for (int i = 0; i < this.shopStacks.size(); i++) {
            int col = i % 2, row = i / 2;
            int x = this.left + 10 + col * 120;
            int y = this.top + 46 + row * 20;
            ItemStack st = this.shopStacks.get(i);
            g.renderItem(st, x, y + 1);
            g.renderItemDecorations(this.font, st, x, y + 1);
            String name = st.getHoverName().getString();
            if (this.font.width(name) > 50) name = this.font.plainSubstrByWidth(name, 46) + "…";
            g.drawString(this.font, name, x + 18, y + 5, 0xFFE0D0D0, false);
        }
    }

    private void shopTooltip(GuiGraphics g, int mouseX, int mouseY) {
        for (int i = 0; i < this.shopStacks.size(); i++) {
            int col = i % 2, row = i / 2;
            int x = this.left + 10 + col * 120;
            int y = this.top + 46 + row * 20;
            if (mouseX >= x && mouseX < x + 68 && mouseY >= y && mouseY < y + 18) {
                g.renderTooltip(this.font, Component.translatable("satsujinki.shop.desc." + ShopCatalog.ENTRIES.get(i).id()), mouseX, mouseY);
            }
        }
    }

    private void renderRecords(GuiGraphics g) {
        int x = this.left + 16;
        int y = this.top + 50;
        g.drawString(this.font, Component.translatable("satsujinki.records.kills", ClientState.totalKills), x, y, 0xFFFFFFFF, true);
        g.drawString(this.font, Component.translatable("satsujinki.records.combo", ClientState.maxCombo), x, y + 12, 0xFFFFFFFF, true);
        g.drawString(this.font, Component.translatable("satsujinki.records.rank", Component.translatable("satsujinki.rank." + Mth.clamp(ClientState.bestRank, 0, 6))), x, y + 24, 0xFFFFFFFF, true);
        g.drawString(this.font, Component.translatable("satsujinki.records.rounds", ClientState.rounds, ClientState.perfect), x, y + 36, 0xFFFFFFFF, true);
        int yy = y + 54;
        for (KillStyle k : KillStyle.values()) {
            int n = k.ordinal() < ClientState.styleKills.length ? ClientState.styleKills[k.ordinal()] : 0;
            g.drawString(this.font, Component.translatable(k.langKey()), x + 8, yy, k.color, true);
            g.drawString(this.font, String.valueOf(n), x + 130, yy, 0xFFFFFFFF, true);
            yy += 11;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
