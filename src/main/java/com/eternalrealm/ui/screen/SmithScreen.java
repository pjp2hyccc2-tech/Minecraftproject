package com.eternalrealm.ui.screen;

import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.client.ClientCharacterState;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.item.Affix;
import com.eternalrealm.item.Cost;
import com.eternalrealm.item.Crafting;
import com.eternalrealm.item.Enhancement;
import com.eternalrealm.item.EquipmentData;
import com.eternalrealm.item.EquipmentStats;
import com.eternalrealm.item.ItemTier;
import com.eternalrealm.item.Recipe;
import com.eternalrealm.item.Recipes;
import com.eternalrealm.item.Reroll;
import com.eternalrealm.item.Trinkets;
import com.eternalrealm.item.forge.ERGearItems;
import com.eternalrealm.item.forge.EquipmentStacks;
import com.eternalrealm.item.forge.Inventories;
import com.eternalrealm.item.forge.SmithService;
import com.eternalrealm.network.forge.ERNetwork;
import com.eternalrealm.network.forge.UiActionPacket;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The smithy (鍛冶屋), laid out like a hunting game's forge: on the left what can be worked on (the player's gear, worn
 * pieces and trinkets first, or the recipes by tier with the trinkets of each grade folded under their header), in the
 * middle the piece with its numbers now → after (in green, stat percentages included), the
 * enhancement pips and the success chance, on the right exactly what it costs with "have / need" for every material and
 * the gold, and one big button. Everything shown is computed here from the player's own inventory and the pure rules;
 * the server checks every request again ({@link SmithService}) and answers with the outcome, which plays as a banner
 * (gold sparks on success, a grey shudder on failure).
 */
public final class SmithScreen extends Screen {
    private static final int GOLD = 0xFFE8C66A;
    private static final int BORDER = 0xFF7A5A26;
    private static final int PANEL = 0xE8141218;
    private static final int PANEL_LIGHT = 0xF0201C24;
    private static final int ROW_HOVER = 0x30FFFFFF;
    private static final int ROW_SELECTED = 0x705A4618;
    private static final int GOOD = 0xFF6CE07A;
    private static final int BAD = 0xFFFF6A5A;
    private static final int ROW = 20;
    private static final long BANNER_MS = 1800L;

    private enum Tab {
        ENHANCE, CRAFT, REROLL
    }

    /** A piece of gear the player has: smithy slot number (see {@link SmithService#stackAt}), stack, data. */
    private record Piece(int slot, ItemStack stack, EquipmentData data, boolean worn) {
    }

    /**
     * A line of the left list: a tier header (tier set, recipe == null), a field header of the named designs (field
     * set, recipe == null), a trinket grade header (grade set, recipe == null; a click folds or unfolds it) or a
     * recipe.
     */
    private record Line(ItemTier tier, int field, Trinkets.Grade grade, Recipe recipe) {
        Line(ItemTier tier, int field, Recipe recipe) {
            this(tier, field, null, recipe);
        }
    }

    /** Trinket grades whose recipes are listed; the others are folded under their header (kept while the game runs). */
    private static final EnumSet<Trinkets.Grade> OPEN_GRADES = EnumSet.noneOf(Trinkets.Grade.class);
    private static boolean gradesChosen;

    /** Fields in the order their named designs open up (by level). */
    private static final int[] FIELD_ORDER = {1, 8, 2, 9, 3, 10, 4, 5, 6, 7};

    private record Banner(String kind, boolean success, int plus, Component name, long startedAt) {
    }

    private static Banner banner;
    private static Tab lastTab = Tab.ENHANCE;

    private final String npc;
    private Tab tab = lastTab;
    private int selectedSlot = -1;
    private UUID selectedId;
    private String selectedRecipe;
    private int scroll;
    private Button action;

    private SmithScreen(String npc) {
        super(Component.translatable("screen.eternalrealm.smith.title"));
        this.npc = npc;
    }

    /** The server opened the smithy ({@link SmithService#CHANNEL}). */
    public static void open(JsonObject data) {
        String npc = data.has("npc") && data.get("npc").isJsonPrimitive() ? data.get("npc").getAsString() : "goran";
        Minecraft.getInstance().setScreen(new SmithScreen(npc.matches("[a-z0-9_]{1,32}") ? npc : "goran"));
    }

    /** The outcome of a request ({@link SmithService#RESULT_CHANNEL}). */
    public static void onResult(JsonObject data) {
        String kind = data.has("kind") ? data.get("kind").getAsString() : "";
        boolean success = data.has("outcome") && "success".equals(data.get("outcome").getAsString());
        int plus = data.has("plus") ? data.get("plus").getAsInt() : 0;
        Component name = Component.empty();
        if (data.has("name")) {
            try {
                MutableComponent parsed = Component.Serializer.fromJson(data.get("name").getAsString());
                if (parsed != null) {
                    name = parsed;
                }
            } catch (JsonParseException | IllegalStateException e) {
                name = Component.empty();
            }
        }
        banner = new Banner(kind, success, plus, name, System.currentTimeMillis());
    }

    // ------------------------------------------------------------------ layout

    private int listLeft() {
        return 8;
    }

    private int listWidth() {
        return Mth.clamp(width * 28 / 100, 118, 176);
    }

    private int rightWidth() {
        return Mth.clamp(width * 27 / 100, 128, 172);
    }

    private int rightLeft() {
        return width - 8 - rightWidth();
    }

    private int centerLeft() {
        return listLeft() + listWidth() + 6;
    }

    private int centerRight() {
        return rightLeft() - 6;
    }

    private int top() {
        return 30;
    }

    private int bottom() {
        return height - 8;
    }

    private int listRows() {
        return Math.max(1, (bottom() - top() - 22) / ROW);
    }

    @Override
    protected void init() {
        int x = width / 2 - 120;
        for (Tab t : Tab.values()) {
            Tab which = t;
            Component label = Component.translatable("screen.eternalrealm.smith.tab." + t.name().toLowerCase(Locale.ROOT));
            Button button = Button.builder(which == tab ? Component.literal("» ").append(label.copy()
                            .withStyle(ChatFormatting.GOLD)).append(" «") : label, b -> switchTab(which))
                    .bounds(x, 6, 78, 18).build();
            addRenderableWidget(button);
            x += 82;
        }
        action = addRenderableWidget(Button.builder(Component.empty(), b -> act())
                .bounds(rightLeft() + 8, bottom() - 30, rightWidth() - 16, 22).build());
        updateAction();
    }

    private void switchTab(Tab which) {
        tab = which;
        lastTab = which;
        scroll = 0;
        rebuildWidgets();
    }

    // ------------------------------------------------------------------ data from the client's own state

    private static LocalPlayer player() {
        return Minecraft.getInstance().player;
    }

    private static long gold() {
        return ClientCharacterState.snapshot().map(CharacterSnapshot::gold).orElse(0L);
    }

    private static int level() {
        return ClientCharacterState.snapshot().map(CharacterSnapshot::level).orElse(1);
    }

    private static Map<String, Integer> counts() {
        LocalPlayer player = player();
        return player == null ? Map.of() : Inventories.counts(player);
    }

    /** The gear the player carries: held and worn pieces first, then the inventory in slot order. */
    private static List<Piece> pieces() {
        LocalPlayer player = player();
        List<Piece> out = new ArrayList<>();
        if (player == null) {
            return out;
        }
        Inventory inventory = player.getInventory();
        List<Integer> order = new ArrayList<>();
        order.add(inventory.selected);
        order.add(40);
        for (int armor = 39; armor >= 36; armor--) {
            order.add(armor);
        }
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            if (slot != inventory.selected) {
                order.add(slot);
            }
        }
        for (int slot : order) {
            ItemStack stack = SmithService.stackAt(inventory, slot);
            Optional<EquipmentData> data = EquipmentStacks.read(stack);
            if (data.isPresent()) {
                out.add(new Piece(slot, stack, data.get(), slot >= 36 || slot == inventory.selected));
            }
            if (slot == 36) {
                // the worn trinkets, right after the armor (smithy slots 41..46, the server's storage order)
                for (int i = 0; i < com.eternalrealm.client.ClientTrinkets.size(); i++) {
                    ItemStack trinket = com.eternalrealm.client.ClientTrinkets.get(i);
                    Optional<EquipmentData> worn = EquipmentStacks.read(trinket);
                    if (worn.isPresent() && worn.get().uniqueId() != null) {
                        out.add(new Piece(SmithService.FIRST_TRINKET_SLOT + i, trinket, worn.get(), true));
                    }
                }
            }
        }
        return out;
    }

    private Optional<Piece> selectedPiece() {
        for (Piece piece : pieces()) {
            if (piece.slot() == selectedSlot && piece.data().uniqueId().equals(selectedId)) {
                return Optional.of(piece);
            }
        }
        return Optional.empty();
    }

    private static List<Line> recipeLines() {
        if (!gradesChosen) {
            // at first only the best trinket grade the character may craft is unfolded
            gradesChosen = true;
            Trinkets.Grade best = Trinkets.Grade.NOVICE;
            for (Trinkets.Grade grade : Trinkets.Grade.values()) {
                if (level() >= grade.level()) {
                    best = grade;
                }
            }
            OPEN_GRADES.add(best);
        }
        List<Line> lines = new ArrayList<>();
        for (ItemTier tier : ItemTier.values()) {
            List<Recipe> recipes = Recipes.forTier(tier);
            if (recipes.isEmpty()) {
                continue;
            }
            lines.add(new Line(tier, 0, null));
            for (Recipe recipe : recipes) {
                lines.add(new Line(tier, 0, recipe));
            }
            trinketSection(lines, Trinkets.Grade.ofTier(tier));
        }
        for (Trinkets.Grade grade : Trinkets.Grade.values()) {
            if (grade.ordinal() >= ItemTier.COUNT) {
                trinketSection(lines, grade);
            }
        }
        for (int field : FIELD_ORDER) {
            List<Recipe> recipes = Recipes.forField(field);
            if (recipes.isEmpty()) {
                continue;
            }
            lines.add(new Line(null, field, null));
            for (Recipe recipe : recipes) {
                lines.add(new Line(null, field, recipe));
            }
        }
        return lines;
    }

    /** A trinket grade's header and, when unfolded, its twelve recipes. */
    private static void trinketSection(List<Line> lines, Trinkets.Grade grade) {
        lines.add(new Line(null, 0, grade, null));
        if (OPEN_GRADES.contains(grade)) {
            for (Recipe recipe : Recipes.trinketRecipes(grade)) {
                lines.add(new Line(null, 0, grade, recipe));
            }
        }
    }

    private Optional<Recipe> selectedRecipe() {
        return selectedRecipe == null ? Optional.empty() : Recipes.byId(selectedRecipe);
    }

    private static ItemStack preview(Recipe recipe) {
        if (recipe.design() != null) {
            return EquipmentStacks.ofDesign(recipe.design());
        }
        ItemStack stack = new ItemStack(ERGearItems.equipment(recipe.output()));
        EquipmentStacks.write(stack, EquipmentStacks.plain(recipe.output()));
        return stack;
    }

    // ------------------------------------------------------------------ the button

    /** Label, whether it can be pressed, and the reason if not. */
    private record ActionState(Component label, boolean ok, Component reason) {
    }

    private ActionState actionState() {
        Map<String, Integer> counts = counts();
        long gold = gold();
        switch (tab) {
            case ENHANCE -> {
                Optional<Piece> piece = selectedPiece();
                if (piece.isEmpty()) {
                    return new ActionState(Component.translatable("screen.eternalrealm.smith.do_enhance"), false,
                            Component.translatable("screen.eternalrealm.smith.pick_gear"));
                }
                Enhancement.Check check = Enhancement.check(piece.get().data(), counts, gold);
                return new ActionState(Component.translatable("screen.eternalrealm.smith.do_enhance"),
                        check == Enhancement.Check.OK, Component.translatable(check.translationKey()));
            }
            case REROLL -> {
                Optional<Piece> piece = selectedPiece();
                if (piece.isEmpty()) {
                    return new ActionState(Component.translatable("screen.eternalrealm.smith.do_reroll"), false,
                            Component.translatable("screen.eternalrealm.smith.pick_gear"));
                }
                Reroll.Check check = Reroll.check(piece.get().data(), counts, gold);
                return new ActionState(Component.translatable("screen.eternalrealm.smith.do_reroll"),
                        check == Reroll.Check.OK, Component.translatable(check.translationKey()));
            }
            default -> {
                Optional<Recipe> recipe = selectedRecipe();
                if (recipe.isEmpty()) {
                    return new ActionState(Component.translatable("screen.eternalrealm.smith.do_craft"), false,
                            Component.translatable("screen.eternalrealm.smith.pick_recipe"));
                }
                Crafting.Check check = Crafting.check(recipe.get(), counts, gold, level());
                return new ActionState(Component.translatable("screen.eternalrealm.smith.do_craft"), check.isOk(),
                        Component.translatable(check.result().translationKey()));
            }
        }
    }

    private void updateAction() {
        if (action == null) {
            return;
        }
        ActionState state = actionState();
        action.setMessage(state.ok() ? state.label().copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                : state.label());
        action.active = state.ok();
    }

    private void act() {
        ActionState state = actionState();
        if (!state.ok()) {
            return;
        }
        JsonObject args = new JsonObject();
        switch (tab) {
            case ENHANCE, REROLL -> {
                Piece piece = selectedPiece().orElse(null);
                if (piece == null) {
                    return;
                }
                args.addProperty("slot", piece.slot());
                args.addProperty("id", piece.data().uniqueId().toString());
                ERNetwork.sendToServer(new UiActionPacket(tab == Tab.ENHANCE ? SmithService.ENHANCE : SmithService.REROLL,
                        args));
            }
            case CRAFT -> {
                args.addProperty("recipe", selectedRecipe);
                ERNetwork.sendToServer(new UiActionPacket(SmithService.CRAFT, args));
            }
        }
        action.active = false; // until the inventory has changed
    }

    @Override
    public void tick() {
        super.tick();
        if (player() == null) {
            onClose();
            return;
        }
        updateAction();
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        int x0 = listLeft();
        int y0 = top() + 18;
        if (button == 0 && mouseX >= x0 && mouseX < x0 + listWidth() && mouseY >= y0) {
            int index = scroll + (int) ((mouseY - y0) / ROW);
            if (tab == Tab.CRAFT) {
                List<Line> lines = recipeLines();
                if (index >= 0 && index < lines.size() && (int) ((mouseY - y0) / ROW) < listRows()) {
                    Line line = lines.get(index);
                    if (line.recipe() != null) {
                        selectedRecipe = line.recipe().id();
                        click();
                        return true;
                    }
                    if (line.grade() != null) {
                        // fold or unfold the grade's trinkets
                        if (!OPEN_GRADES.remove(line.grade())) {
                            OPEN_GRADES.add(line.grade());
                        }
                        scroll = Mth.clamp(scroll, 0, Math.max(0, recipeLines().size() - listRows()));
                        click();
                        return true;
                    }
                }
            } else {
                List<Piece> pieces = pieces();
                if (index >= 0 && index < pieces.size() && (int) ((mouseY - y0) / ROW) < listRows()) {
                    selectedSlot = pieces.get(index).slot();
                    selectedId = pieces.get(index).data().uniqueId();
                    click();
                    return true;
                }
            }
        }
        return false;
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance
                .forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int total = tab == Tab.CRAFT ? recipeLines().size() : pieces().size();
        scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, Math.max(0, total - listRows()));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        // header: smith's portrait and name, gold
        ResourceLocation portrait = new ResourceLocation(ModInfo.MOD_ID, "textures/gui/portrait/" + npc + ".png");
        graphics.fill(7, 3, 33, 29, BORDER);
        graphics.blit(portrait, 8, 4, 24, 24, 0.0F, 0.0F, 48, 48, 48, 48);
        graphics.drawString(font, title.copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), 38, 6, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("story.eternalrealm.speaker." + npc)
                .withStyle(ChatFormatting.GRAY), 38, 17, 0xFFFFFF);
        Component goldText = Component.translatable("screen.eternalrealm.smith.gold", String.format(Locale.ROOT, "%,d",
                gold())).withStyle(ChatFormatting.GOLD);
        graphics.drawString(font, goldText, width - 8 - font.width(goldText), 11, 0xFFFFFF);

        panel(graphics, listLeft(), top(), listLeft() + listWidth(), bottom());
        panel(graphics, centerLeft(), top(), centerRight(), bottom());
        panel(graphics, rightLeft(), top(), rightLeft() + rightWidth(), bottom());
        if (tab == Tab.CRAFT) {
            drawRecipeList(graphics, mouseX, mouseY);
            drawRecipe(graphics);
        } else {
            drawGearList(graphics, mouseX, mouseY);
            drawPiece(graphics);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        drawBanner(graphics);
        listTooltip(graphics, mouseX, mouseY);
    }

    private static void panel(GuiGraphics graphics, int x0, int y0, int x1, int y1) {
        graphics.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, BORDER);
        graphics.fill(x0, y0, x1, y1, PANEL);
    }

    private void header(GuiGraphics graphics, int x0, int x1, Component text) {
        graphics.fill(x0, top(), x1, top() + 14, PANEL_LIGHT);
        graphics.drawString(font, text.copy().withStyle(ChatFormatting.GOLD), x0 + 5, top() + 3, 0xFFFFFF);
    }

    private void drawGearList(GuiGraphics graphics, int mouseX, int mouseY) {
        int x0 = listLeft();
        int x1 = x0 + listWidth();
        header(graphics, x0, x1, Component.translatable(tab == Tab.REROLL ? "screen.eternalrealm.smith.list.reroll"
                : "screen.eternalrealm.smith.list.gear"));
        List<Piece> pieces = pieces();
        int y = top() + 18;
        if (pieces.isEmpty()) {
            for (var line : font.split(Component.translatable("screen.eternalrealm.smith.no_gear")
                    .withStyle(ChatFormatting.GRAY), listWidth() - 10)) {
                graphics.drawString(font, line, x0 + 5, y, 0xFFFFFF, false);
                y += 10;
            }
            return;
        }
        for (int i = scroll; i < pieces.size() && i - scroll < listRows(); i++) {
            Piece piece = pieces.get(i);
            int rowTop = y + (i - scroll) * ROW;
            boolean selected = piece.slot() == selectedSlot && piece.data().uniqueId().equals(selectedId);
            boolean hovered = mouseX >= x0 && mouseX < x1 && mouseY >= rowTop && mouseY < rowTop + ROW;
            if (selected) {
                graphics.fill(x0 + 1, rowTop, x1 - 1, rowTop + ROW - 1, ROW_SELECTED);
            } else if (hovered) {
                graphics.fill(x0 + 1, rowTop, x1 - 1, rowTop + ROW - 1, ROW_HOVER);
            }
            graphics.renderItem(piece.stack(), x0 + 3, rowTop + 2);
            boolean usable = tab != Tab.REROLL || Reroll.canReroll(piece.data());
            Component name = piece.stack().getHoverName();
            String text = font.plainSubstrByWidth(name.getString(), listWidth() - 46);
            graphics.drawString(font, Component.literal(text).withStyle(name.getStyle()), x0 + 22, rowTop + 2,
                    usable ? 0xFFFFFF : 0x808080, false);
            Component sub = Component.translatable(piece.data().isWeapon() ? piece.data().weaponType().translationKey()
                    : piece.data().slot().translationKey()).withStyle(ChatFormatting.DARK_GRAY);
            graphics.drawString(font, sub, x0 + 22, rowTop + 11, 0xFFFFFF, false);
            if (piece.worn()) {
                graphics.drawString(font, Component.literal("E").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                        x1 - 10, rowTop + 6, 0xFFFFFF, false);
            }
        }
        scrollbar(graphics, x1 - 3, y, pieces.size());
    }

    private void drawRecipeList(GuiGraphics graphics, int mouseX, int mouseY) {
        int x0 = listLeft();
        int x1 = x0 + listWidth();
        header(graphics, x0, x1, Component.translatable("screen.eternalrealm.smith.list.recipes"));
        List<Line> lines = recipeLines();
        Map<String, Integer> counts = counts();
        long gold = gold();
        int level = level();
        int y = top() + 18;
        for (int i = scroll; i < lines.size() && i - scroll < listRows(); i++) {
            Line line = lines.get(i);
            int rowTop = y + (i - scroll) * ROW;
            if (line.recipe() == null && line.grade() != null) {
                // the trinkets of a grade (装身具), folded or not
                Trinkets.Grade grade = line.grade();
                boolean open = OPEN_GRADES.contains(grade);
                boolean hovered = mouseX >= x0 && mouseX < x1 && mouseY >= rowTop && mouseY < rowTop + ROW;
                graphics.fill(x0 + 1, rowTop + 2, x1 - 1, rowTop + ROW - 2, hovered ? 0x60307A8A : 0x40306A7A);
                graphics.drawString(font, font.plainSubstrByWidth(Component.translatable(
                                "screen.eternalrealm.smith.trinket_header", open ? "▼" : "▶",
                                Component.translatable("smith.eternalrealm.trinket_grade." + grade.id()), grade.level())
                        .getString(), listWidth() - 10), x0 + 5, rowTop + 6,
                        level >= grade.level() ? 0xFF8AE0F0 : 0xFF5A6A70, false);
                continue;
            }
            if (line.recipe() == null && line.tier() == null) {
                // the named designs of a field (armory)
                graphics.fill(x0 + 1, rowTop + 2, x1 - 1, rowTop + ROW - 2, 0x40A05A20);
                int from = com.eternalrealm.item.Armory.levelOf(line.field(), 0);
                String area = com.eternalrealm.world.Areas.byId(line.field())
                        .map(definition -> definition.translationKey()).orElse("");
                graphics.drawString(font, font.plainSubstrByWidth(Component.translatable(
                                "screen.eternalrealm.smith.design_header", Component.translatable(area), from)
                        .getString(), listWidth() - 10), x0 + 5, rowTop + 6,
                        level >= from ? 0xFFFFC870 : 0xFF6A6A6A, false);
                continue;
            }
            if (line.recipe() == null) {
                graphics.fill(x0 + 1, rowTop + 2, x1 - 1, rowTop + ROW - 2, 0x40806020);
                boolean reachable = level >= line.tier().requiredLevel();
                graphics.drawString(font, Component.translatable("screen.eternalrealm.smith.tier_header",
                                Component.translatable(line.tier().translationKey()), line.tier().requiredLevel())
                        .withStyle(reachable ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY), x0 + 5, rowTop + 6,
                        0xFFFFFF, false);
                continue;
            }
            Recipe recipe = line.recipe();
            boolean selected = recipe.id().equals(selectedRecipe);
            boolean hovered = mouseX >= x0 && mouseX < x1 && mouseY >= rowTop && mouseY < rowTop + ROW;
            if (selected) {
                graphics.fill(x0 + 1, rowTop, x1 - 1, rowTop + ROW - 1, ROW_SELECTED);
            } else if (hovered) {
                graphics.fill(x0 + 1, rowTop, x1 - 1, rowTop + ROW - 1, ROW_HOVER);
            }
            ItemStack stack = preview(recipe);
            graphics.renderItem(stack, x0 + 3, rowTop + 2);
            boolean ok = Crafting.check(recipe, counts, gold, level).isOk();
            // weapons the player's job cannot use stay craftable (trade), but read as "not for you"
            boolean otherJob = recipe.output().isWeapon() && !com.eternalrealm.job.Jobs.canUse(
                    com.eternalrealm.item.forge.EquipmentStacks.viewerJob(), recipe.output().weaponType());
            String text = font.plainSubstrByWidth(stack.getHoverName().getString(), listWidth() - (otherJob ? 44 : 34));
            graphics.drawString(font, text, x0 + 22, rowTop + 6, otherJob ? 0x9A6A6A : ok ? 0xFFFFFF : 0x8A8A8A,
                    false);
            if (otherJob) {
                graphics.drawString(font, "✘", x1 - 18, rowTop + 6, 0xFFB04040, false);
            }
            if (ok) {
                graphics.fill(x1 - 9, rowTop + 8, x1 - 5, rowTop + 12, GOOD);
            }
        }
        scrollbar(graphics, x1 - 3, y, lines.size());
    }

    private void scrollbar(GuiGraphics graphics, int x, int y, int total) {
        int rows = listRows();
        if (total <= rows) {
            return;
        }
        int h = rows * ROW;
        int barH = Math.max(12, h * rows / total);
        int barY = y + (h - barH) * scroll / Math.max(1, total - rows);
        graphics.fill(x, y, x + 2, y + h, 0x40FFFFFF);
        graphics.fill(x, barY, x + 2, barY + barH, GOLD);
    }

    /** Enhance / reroll: the chosen piece in the middle, the cost on the right. */
    private void drawPiece(GuiGraphics graphics) {
        int cx0 = centerLeft();
        int cx1 = centerRight();
        header(graphics, cx0, cx1, Component.translatable(tab == Tab.ENHANCE ? "screen.eternalrealm.smith.enhance_title"
                : "screen.eternalrealm.smith.reroll_title"));
        header(graphics, rightLeft(), rightLeft() + rightWidth(), Component.translatable("screen.eternalrealm.smith.cost"));
        Optional<Piece> chosen = selectedPiece();
        if (chosen.isEmpty()) {
            centeredHint(graphics, Component.translatable("screen.eternalrealm.smith.pick_gear"));
            drawReason(graphics);
            return;
        }
        Piece piece = chosen.get();
        EquipmentData data = piece.data();
        int y = itemHeader(graphics, piece.stack(), data.requiredLevel());
        if (tab == Tab.ENHANCE) {
            if (Enhancement.isMaxed(data)) {
                graphics.drawString(font, Component.translatable("message.eternalrealm.enhancement.max_level")
                        .withStyle(ChatFormatting.GOLD), cx0 + 8, y, 0xFFFFFF, false);
                pips(graphics, cx0 + 8, y + 14, data.enhancement(), false);
                drawReason(graphics);
                return;
            }
            EquipmentData next = data.withEnhancement(data.enhancement() + 1);
            if (data.isWeapon()) {
                y = statRow(graphics, y, "screen.eternalrealm.smith.stat.attack", EquipmentStats.baseAttack(data),
                        EquipmentStats.baseAttack(next));
            } else if (!data.isTrinket()) {
                y = statRow(graphics, y, "screen.eternalrealm.smith.stat.defense", EquipmentStats.baseDefense(data),
                        EquipmentStats.baseDefense(next));
                y = statRow(graphics, y, "screen.eternalrealm.smith.stat.health", EquipmentStats.baseMaxHealth(data),
                        EquipmentStats.baseMaxHealth(next));
            }
            // the stat percentages grow faster (+10 % of themselves per level)
            com.eternalrealm.character.StatPercents nowPercents = EquipmentStats.of(data).percents();
            com.eternalrealm.character.StatPercents nextPercents = EquipmentStats.of(next).percents();
            for (com.eternalrealm.character.StatType type : com.eternalrealm.character.StatType.values()) {
                if (nextPercents.get(type) > 0.0) {
                    y = statPercentRow(graphics, y, type, nowPercents.get(type) * 100.0,
                            nextPercents.get(type) * 100.0);
                }
            }
            y = percentRow(graphics, y, (EquipmentStats.enhancementFactor(data.enhancement()) - 1.0) * 100.0,
                    (EquipmentStats.enhancementFactor(next.enhancement()) - 1.0) * 100.0);
            y += 4;
            graphics.drawString(font, Component.translatable("screen.eternalrealm.smith.level", data.enhancement(),
                    next.enhancement()).withStyle(ChatFormatting.YELLOW), cx0 + 8, y, 0xFFFFFF, false);
            y += 11;
            pips(graphics, cx0 + 8, y, data.enhancement(), true);
            y += 14;
            double chance = Enhancement.successChance(next.enhancement());
            chanceBar(graphics, cx0 + 8, y, Math.min(cx1 - cx0 - 16, 150), chance);
            y += 16;
            graphics.drawString(font, Component.translatable("screen.eternalrealm.smith.expected",
                    String.format(Locale.ROOT, "%.1f", Enhancement.expectedAttempts(data.enhancement(),
                            next.enhancement()))).withStyle(ChatFormatting.GRAY), cx0 + 8, y, 0xFFFFFF, false);
            y += 11;
            for (var line : font.split(Component.translatable("screen.eternalrealm.smith.failure_note")
                    .withStyle(ChatFormatting.DARK_GRAY), cx1 - cx0 - 16)) {
                graphics.drawString(font, line, cx0 + 8, y, 0xFFFFFF, false);
                y += 10;
            }
            affixes(graphics, y + 4, data);
            costPanel(graphics, Enhancement.cost(data));
        } else {
            if (!Reroll.canReroll(data)) {
                graphics.drawString(font, Component.translatable("message.eternalrealm.reroll.no_affixes")
                        .withStyle(ChatFormatting.GRAY), cx0 + 8, y, 0xFFFFFF, false);
                drawReason(graphics);
                return;
            }
            for (var line : font.split(Component.translatable("screen.eternalrealm.smith.reroll_note")
                    .withStyle(ChatFormatting.GRAY), cx1 - cx0 - 16)) {
                graphics.drawString(font, line, cx0 + 8, y, 0xFFFFFF, false);
                y += 10;
            }
            affixes(graphics, y + 6, data);
            costPanel(graphics, Reroll.cost(data));
        }
        drawReason(graphics);
    }

    /** Craft: the recipe's piece as it comes out, its cost on the right. */
    private void drawRecipe(GuiGraphics graphics) {
        int cx0 = centerLeft();
        int cx1 = centerRight();
        header(graphics, cx0, cx1, Component.translatable("screen.eternalrealm.smith.craft_title"));
        header(graphics, rightLeft(), rightLeft() + rightWidth(), Component.translatable("screen.eternalrealm.smith.cost"));
        Optional<Recipe> chosen = selectedRecipe();
        if (chosen.isEmpty()) {
            centeredHint(graphics, Component.translatable("screen.eternalrealm.smith.pick_recipe"));
            drawReason(graphics);
            return;
        }
        Recipe recipe = chosen.get();
        ItemStack stack = preview(recipe);
        EquipmentData data = recipe.design() != null
                ? EquipmentData.of(recipe.design(), com.eternalrealm.item.ItemRarity.COMMON, List.of())
                : EquipmentStacks.plain(recipe.output());
        int y = itemHeader(graphics, stack, recipe.requiredLevel());
        if (data.isWeapon()) {
            y = valueRow(graphics, y, "screen.eternalrealm.smith.stat.attack", EquipmentStats.baseAttack(data));
        } else if (!data.isTrinket()) {
            y = valueRow(graphics, y, "screen.eternalrealm.smith.stat.defense", EquipmentStats.baseDefense(data));
            y = valueRow(graphics, y, "screen.eternalrealm.smith.stat.health", EquipmentStats.baseMaxHealth(data));
        }
        if (recipe.design() != null) {
            // what makes the design special: its stat percentages, traits, perks, art or set bonuses
            int shown = 0;
            for (com.eternalrealm.item.TooltipLine line : com.eternalrealm.item.EquipmentTooltip.lines(data)) {
                boolean trinketStats = line.kind() == com.eternalrealm.item.TooltipLine.Kind.BASE_STAT
                        && data.isTrinket();
                switch (line.kind()) {
                    case TRAIT, PERK, ART, SET, BASE_STAT -> {
                        if (line.kind() == com.eternalrealm.item.TooltipLine.Kind.BASE_STAT && !trinketStats) {
                            continue;
                        }
                        for (var part : font.split(EquipmentStacks.component(line), cx1 - cx0 - 16)) {
                            if (shown++ < 9) {
                                graphics.drawString(font, part, cx0 + 8, y, 0xFFFFFF, false);
                                y += 10;
                            }
                        }
                    }
                    default -> {
                    }
                }
            }
        }
        y += 6;
        for (var line : font.split(Component.translatable("screen.eternalrealm.smith.craft_note")
                .withStyle(ChatFormatting.GRAY), cx1 - cx0 - 16)) {
            graphics.drawString(font, line, cx0 + 8, y, 0xFFFFFF, false);
            y += 10;
        }
        costPanel(graphics, recipe.cost());
        drawReason(graphics);
    }

    private void centeredHint(GuiGraphics graphics, Component text) {
        int cx = (centerLeft() + centerRight()) / 2;
        graphics.drawCenteredString(font, text.copy().withStyle(ChatFormatting.GRAY), cx, (top() + bottom()) / 2 - 4,
                0xFFFFFF);
    }

    /** Big icon, name, type and requirement; returns the y below. */
    private int itemHeader(GuiGraphics graphics, ItemStack stack, int requiredLevel) {
        int x = centerLeft() + 8;
        int y = top() + 20;
        graphics.fill(x - 1, y - 1, x + 49, y + 49, BORDER);
        graphics.fill(x, y, x + 48, y + 48, 0xFF0C0A10);
        graphics.pose().pushPose();
        graphics.pose().translate(x + 4, y + 4, 0);
        graphics.pose().scale(2.5F, 2.5F, 1.0F);
        graphics.renderItem(stack, 0, 0);
        graphics.pose().popPose();
        int tx = x + 56;
        int maxWidth = centerRight() - tx - 6;
        List<Component> tooltip = new ArrayList<>();
        EquipmentStacks.appendTooltip(stack, tooltip);
        graphics.drawString(font, font.plainSubstrByWidth(stack.getHoverName().getString(), maxWidth), tx, y + 4,
                stack.getHoverName().getStyle().getColor() == null ? 0xFFFFFF
                        : stack.getHoverName().getStyle().getColor().getValue(), true);
        if (!tooltip.isEmpty()) {
            graphics.drawString(font, tooltip.get(0), tx, y + 16, 0xFFFFFF, false);
        }
        boolean enough = level() >= requiredLevel;
        graphics.drawString(font, Component.translatable("screen.eternalrealm.smith.required_level", requiredLevel)
                .withStyle(enough ? ChatFormatting.GRAY : ChatFormatting.RED), tx, y + 28, 0xFFFFFF, false);
        return y + 56;
    }

    private int statRow(GuiGraphics graphics, int y, String key, double now, double next) {
        int x = centerLeft() + 8;
        graphics.drawString(font, Component.translatable(key), x, y, 0xFFC8C0B0, false);
        String a = number(now);
        String b = number(next);
        int col = x + 70;
        graphics.drawString(font, a, col, y, 0xFFFFFF, false);
        graphics.drawString(font, "→", col + 34, y, 0xFFB0B0B0, false);
        graphics.drawString(font, b, col + 46, y, next > now ? GOOD : 0xFFFFFF, false);
        if (next > now) {
            graphics.drawString(font, "(+" + number(next - now) + ")", col + 50 + font.width(b), y, 0xFF4FA05A, false);
        }
        return y + 12;
    }

    /** "STR%  +33 → +36.3" for a stat percentage of the piece. */
    private int statPercentRow(GuiGraphics graphics, int y, com.eternalrealm.character.StatType type, double now,
                               double next) {
        int x = centerLeft() + 8;
        graphics.drawString(font, Component.translatable("screen.eternalrealm.smith.stat.percent", type.name()), x, y,
                0xFFFFD27A, false);
        int col = x + 70;
        graphics.drawString(font, "+" + number(now) + "%", col, y, 0xFFFFFF, false);
        graphics.drawString(font, "→", col + 34, y, 0xFFB0B0B0, false);
        graphics.drawString(font, "+" + number(next) + "%", col + 46, y, next > now ? GOOD : 0xFFFFFF, false);
        return y + 12;
    }

    private int percentRow(GuiGraphics graphics, int y, double now, double next) {
        int x = centerLeft() + 8;
        graphics.drawString(font, Component.translatable("screen.eternalrealm.smith.stat.bonus"), x, y, 0xFFC8C0B0,
                false);
        int col = x + 70;
        graphics.drawString(font, "+" + number(now) + "%", col, y, 0xFFFFFF, false);
        graphics.drawString(font, "→", col + 34, y, 0xFFB0B0B0, false);
        graphics.drawString(font, "+" + number(next) + "%", col + 46, y, GOOD, false);
        return y + 12;
    }

    private int valueRow(GuiGraphics graphics, int y, String key, double value) {
        int x = centerLeft() + 8;
        graphics.drawString(font, Component.translatable(key), x, y, 0xFFC8C0B0, false);
        graphics.drawString(font, number(value), x + 70, y, 0xFFFFFF, false);
        return y + 12;
    }

    private static String number(double value) {
        double rounded = Math.round(value * 10.0) / 10.0;
        return rounded == Math.rint(rounded) ? Long.toString((long) rounded) : String.format(Locale.ROOT, "%.1f", rounded);
    }

    /** Ten pips: filled up to the current level; the next one blinks if {@code showNext}. */
    private void pips(GuiGraphics graphics, int x, int y, int level, boolean showNext) {
        long time = System.currentTimeMillis();
        for (int i = 1; i <= EquipmentData.MAX_ENHANCEMENT; i++) {
            int px = x + (i - 1) * 11;
            graphics.fill(px, y, px + 9, y + 9, 0xFF000000);
            int color;
            if (i <= level) {
                color = GOLD;
            } else if (showNext && i == level + 1) {
                color = (time / 300) % 2 == 0 ? 0xFF6CE07A : 0xFF2A5A30;
            } else {
                color = 0xFF3A3440;
            }
            graphics.fill(px + 1, y + 1, px + 8, y + 8, color);
        }
    }

    private void chanceBar(GuiGraphics graphics, int x, int y, int w, double chance) {
        graphics.drawString(font, Component.translatable("screen.eternalrealm.smith.chance",
                String.format(Locale.ROOT, "%.0f", chance * 100.0)).withStyle(ChatFormatting.AQUA), x, y, 0xFFFFFF, false);
        int barX = x + 78;
        int barW = Math.max(30, w - 78);
        graphics.fill(barX, y + 1, barX + barW, y + 8, 0xFF000000);
        int fill = (int) Math.round((barW - 2) * chance);
        int color = chance >= 0.8 ? GOOD : chance >= 0.5 ? 0xFFE8C040 : BAD;
        graphics.fill(barX + 1, y + 2, barX + 1 + fill, y + 7, color);
    }

    private void affixes(GuiGraphics graphics, int y, EquipmentData data) {
        int x = centerLeft() + 8;
        if (data.affixes().isEmpty()) {
            return;
        }
        graphics.drawString(font, Component.translatable("screen.eternalrealm.smith.affixes")
                .withStyle(ChatFormatting.DARK_AQUA), x, y, 0xFFFFFF, false);
        y += 11;
        for (Affix affix : data.affixes()) {
            if (y > bottom() - 10) {
                break;
            }
            // as it counts (rarity and, for stat percentages, enhancement included), like the tooltip
            graphics.drawString(font, Component.translatable(affix.kind().translationKey(),
                    affix.kind().format(EquipmentStats.affixValue(affix, data.rarity(), data.enhancement())))
                    .withStyle(ChatFormatting.AQUA), x + 4, y, 0xFFFFFF, false);
            y += 10;
        }
    }

    /** Right panel: every material with have / need, then the gold. */
    private void costPanel(GuiGraphics graphics, Cost cost) {
        Map<String, Integer> counts = counts();
        int x = rightLeft() + 6;
        int y = top() + 20;
        for (Map.Entry<String, Integer> entry : cost.items().entrySet()) {
            Item item = Inventories.item(entry.getKey());
            ItemStack icon = new ItemStack(item == null ? Items.BARRIER : item);
            int have = counts.getOrDefault(entry.getKey(), 0);
            boolean enough = have >= entry.getValue();
            graphics.fill(x - 2, y - 2, rightLeft() + rightWidth() - 4, y + 18, enough ? 0x2040C060 : 0x30C04040);
            graphics.renderItem(icon, x, y);
            String name = font.plainSubstrByWidth(icon.getHoverName().getString(), rightWidth() - 34);
            graphics.drawString(font, name, x + 20, y, 0xFFFFFF, false);
            Component amount = Component.literal(have + " / " + entry.getValue())
                    .withStyle(enough ? ChatFormatting.GREEN : ChatFormatting.RED);
            graphics.drawString(font, amount, x + 20, y + 9, 0xFFFFFF, false);
            graphics.drawString(font, enough ? "✔" : "✘", rightLeft() + rightWidth() - 14, y + 4,
                    enough ? GOOD : BAD, false);
            y += 22;
        }
        long gold = gold();
        boolean enough = gold >= cost.gold();
        y += 4;
        graphics.drawString(font, Component.translatable("screen.eternalrealm.smith.cost_gold",
                String.format(Locale.ROOT, "%,d", cost.gold())).withStyle(ChatFormatting.GOLD), x, y, 0xFFFFFF, false);
        graphics.drawString(font, Component.translatable("screen.eternalrealm.smith.have_gold",
                        String.format(Locale.ROOT, "%,d", gold)).withStyle(enough ? ChatFormatting.GREEN : ChatFormatting.RED),
                x, y + 11, 0xFFFFFF, false);
    }

    /** Above the button: why it cannot be pressed. */
    private void drawReason(GuiGraphics graphics) {
        ActionState state = actionState();
        if (state.ok()) {
            return;
        }
        int y = bottom() - 44;
        for (var line : font.split(state.reason().copy().withStyle(ChatFormatting.RED), rightWidth() - 12)) {
            graphics.drawString(font, line, rightLeft() + 6, y, 0xFFFFFF, false);
            y += 10;
        }
    }

    private void listTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int x0 = listLeft();
        int y0 = top() + 18;
        if (mouseX < x0 || mouseX >= x0 + listWidth() || mouseY < y0) {
            return;
        }
        int row = (int) ((mouseY - y0) / ROW);
        if (row >= listRows()) {
            return;
        }
        int index = scroll + row;
        if (tab == Tab.CRAFT) {
            List<Line> lines = recipeLines();
            if (index < lines.size() && lines.get(index).recipe() != null) {
                graphics.renderTooltip(font, preview(lines.get(index).recipe()), mouseX, mouseY);
            }
        } else {
            List<Piece> pieces = pieces();
            if (index < pieces.size()) {
                graphics.renderTooltip(font, pieces.get(index).stack(), mouseX, mouseY);
            }
        }
    }

    /** The outcome banner: gold with sparks on success, dark red and shaking on failure. */
    private void drawBanner(GuiGraphics graphics) {
        Banner shown = banner;
        if (shown == null) {
            return;
        }
        long elapsed = System.currentTimeMillis() - shown.startedAt();
        if (elapsed > BANNER_MS) {
            banner = null;
            return;
        }
        float f = elapsed / (float) BANNER_MS;
        float alpha = f < 0.1F ? f / 0.1F : f > 0.75F ? (1.0F - f) / 0.25F : 1.0F;
        int a = Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 220.0F);
        int cy = height / 2;
        int shake = shown.success() ? 0 : Math.round((float) Math.sin(elapsed * 0.09) * 6.0F * (1.0F - f));
        int band = shown.success() ? 0x5A4210 : 0x3A1414;
        graphics.fill(0, cy - 22, width, cy + 22, (a << 24) | band);
        graphics.fill(0, cy - 23, width, cy - 22, (a << 24) | (shown.success() ? 0xE8C66A : 0x8A3030));
        graphics.fill(0, cy + 22, width, cy + 23, (a << 24) | (shown.success() ? 0xE8C66A : 0x8A3030));
        if (shown.success()) {
            // sparks flying out from the centre
            for (int i = 0; i < 28; i++) {
                double angle = i * 2.399963;
                double speed = 40.0 + (i * 37 % 60);
                double r = speed * Math.min(1.0, f * 2.2);
                int sx = width / 2 + (int) Math.round(Math.cos(angle) * r * 2.2);
                int sy = cy + (int) Math.round(Math.sin(angle) * r * 0.55);
                graphics.fill(sx, sy, sx + 2, sy + 2, (a << 24) | (i % 3 == 0 ? 0xFFFFFF : 0xFFD860));
            }
        }
        String key = switch (shown.kind()) {
            case "enhance" -> shown.success() ? "screen.eternalrealm.smith.banner.enhanced" : "screen.eternalrealm.smith.banner.failed";
            case "reroll" -> "screen.eternalrealm.smith.banner.rerolled";
            default -> "screen.eternalrealm.smith.banner.crafted";
        };
        Component big = Component.translatable(key, shown.plus()).withStyle(shown.success() ? ChatFormatting.GOLD
                : ChatFormatting.GRAY, ChatFormatting.BOLD);
        graphics.pose().pushPose();
        graphics.pose().translate(width / 2.0F + shake, cy - 14, 0);
        graphics.pose().scale(2.0F, 2.0F, 1.0F);
        graphics.drawCenteredString(font, big, 0, 0, 0xFFFFFF);
        graphics.pose().popPose();
        graphics.drawCenteredString(font, shown.name(), width / 2 + shake, cy + 9, 0xFFFFFF);
    }
}
