package com.huntcraft.gear.armor;

import com.huntcraft.core.Rank;
import com.huntcraft.core.SkillType;
import com.huntcraft.item.MaterialItem;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Charm (護石): random skills + decoration slots stored in NBT ("HCCharm": Tier, Skills[{Id,Lv}], Slots[]).
 * Active while held in the offhand or anywhere in the hotbar; only one charm counts (offhand first, then the
 * left-most hotbar slot).
 */
public class CharmItem extends Item {
    public static final String TAG = "HCCharm";
    public static final int MAX_TIER = 3;
    private static final int[] TIER_RARITY = {1, 2, 4, 6};

    /** Skill pool weights for random charms. */
    private static final Map<SkillType, Integer> POOL = new LinkedHashMap<>();

    static {
        w(SkillType.ATTACK_BOOST, 6);
        w(SkillType.CRITICAL_EYE, 6);
        w(SkillType.WEAKNESS_EXPLOIT, 3);
        w(SkillType.CRITICAL_BOOST, 2);
        w(SkillType.ELEMENT_ATTACK, 4);
        w(SkillType.HEALTH_BOOST, 5);
        w(SkillType.DEFENSE_BOOST, 5);
        w(SkillType.CONSTITUTION, 5);
        w(SkillType.STAMINA_SURGE, 5);
        w(SkillType.EVADE_WINDOW, 5);
        w(SkillType.GUARD, 4);
        w(SkillType.EARPLUGS, 3);
        w(SkillType.FIRE_RES, 3);
        w(SkillType.WATER_RES, 3);
        w(SkillType.THUNDER_RES, 3);
        w(SkillType.ICE_RES, 3);
        w(SkillType.DRAGON_RES, 3);
        w(SkillType.HANDICRAFT, 2);
        w(SkillType.RAZOR_SHARP, 3);
        w(SkillType.SPEED_SHARPENING, 3);
        w(SkillType.FOCUS, 4);
        w(SkillType.HEROICS, 3);
        w(SkillType.AGITATOR, 3);
        w(SkillType.RECOVERY_UP, 4);
        w(SkillType.GATHERING, 3);
        w(SkillType.CARVING, 3);
        w(SkillType.STUN_RES, 3);
        w(SkillType.POISON_RES, 3);
        w(SkillType.TREMOR_RES, 3);
        w(SkillType.WINDPROOF, 3);
        w(SkillType.HEAT_COLD_RES, 2);
    }

    private static void w(SkillType t, int weight) {
        POOL.put(t, weight);
    }

    public CharmItem(Properties props) {
        super(props);
    }

    // ------------------------------------------------------------------ NBT helpers
    public static int getTier(ItemStack stack) {
        CompoundTag t = stack.getTagElement(TAG);
        if (t == null) return 1;
        return Math.max(1, Math.min(MAX_TIER, t.getInt("Tier")));
    }

    public static boolean isRolled(ItemStack stack) {
        CompoundTag t = stack.getTagElement(TAG);
        return t != null && t.contains("Skills", Tag.TAG_LIST);
    }

    public static Map<SkillType, Integer> getSkills(ItemStack stack) {
        Map<SkillType, Integer> out = new LinkedHashMap<>();
        CompoundTag t = stack.getTagElement(TAG);
        if (t == null) return out;
        ListTag l = t.getList("Skills", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag s = l.getCompound(i);
            SkillType type = SkillType.byName(s.getString("Id"));
            int lv = s.getInt("Lv");
            if (type != null && lv != 0) out.merge(type, lv, Integer::sum);
        }
        return out;
    }

    public static int[] getSlots(ItemStack stack) {
        CompoundTag t = stack.getTagElement(TAG);
        return t == null ? new int[0] : t.getIntArray("Slots");
    }

    /** Creates a charm with fixed contents. */
    public static ItemStack create(Item item, int tier, Map<SkillType, Integer> skills, int[] slots) {
        ItemStack st = new ItemStack(item);
        CompoundTag t = st.getOrCreateTagElement(TAG);
        t.putInt("Tier", Math.max(1, Math.min(MAX_TIER, tier)));
        ListTag l = new ListTag();
        skills.forEach((k, v) -> {
            CompoundTag s = new CompoundTag();
            s.putString("Id", k.name());
            s.putInt("Lv", v);
            l.add(s);
        });
        t.put("Skills", l);
        t.putIntArray("Slots", slots);
        return st;
    }

    /** Unrolled charm of a tier (rolls its skills the first time it ticks in an inventory). */
    public static ItemStack unrolled(Item item, int tier) {
        ItemStack st = new ItemStack(item);
        st.getOrCreateTagElement(TAG).putInt("Tier", Math.max(1, Math.min(MAX_TIER, tier)));
        return st;
    }

    // ------------------------------------------------------------------ random generation
    /** Tier distribution: high rank / high HR hunters find better charms. */
    public static int rollTier(RandomSource r, @Nullable Rank rank, int hunterRank) {
        float f = r.nextFloat();
        if (rank == Rank.HIGH || hunterRank >= 8) {
            if (f < 0.20F) return 3;
            if (f < 0.70F) return 2;
            return 1;
        }
        if (f < 0.03F) return 3;
        if (f < 0.28F) return 2;
        return 1;
    }

    public static ItemStack roll(Item item, RandomSource r, @Nullable Rank rank, int hunterRank) {
        return rollContents(item, rollTier(r, rank, hunterRank), r);
    }

    public static ItemStack rollContents(Item item, int tier, RandomSource r) {
        Map<SkillType, Integer> skills = new LinkedHashMap<>();
        SkillType first = pick(r, null);
        int lv1 = switch (tier) {
            case 3 -> 2 + r.nextInt(2);
            case 2 -> 1 + r.nextInt(2);
            default -> 1;
        };
        skills.put(first, Math.min(first.maxLevel, lv1));
        float secondChance = tier == 3 ? 0.85F : tier == 2 ? 0.6F : 0.3F;
        if (r.nextFloat() < secondChance) {
            SkillType second = pick(r, first);
            int lv2 = tier == 3 ? 1 + r.nextInt(2) : 1;
            skills.put(second, Math.min(second.maxLevel, lv2));
        }
        int slotCount = switch (tier) {
            case 3 -> 1 + r.nextInt(3);
            case 2 -> r.nextInt(3);
            default -> r.nextInt(2);
        };
        int[] slots = new int[slotCount];
        for (int i = 0; i < slotCount; i++) slots[i] = 1 + r.nextInt(Math.min(3, tier + (i == 0 ? 1 : 0)));
        // biggest slot first
        java.util.Arrays.sort(slots);
        for (int i = 0; i < slots.length / 2; i++) {
            int tmp = slots[i];
            slots[i] = slots[slots.length - 1 - i];
            slots[slots.length - 1 - i] = tmp;
        }
        return create(item, tier, skills, slots);
    }

    private static SkillType pick(RandomSource r, @Nullable SkillType exclude) {
        int total = 0;
        for (Map.Entry<SkillType, Integer> e : POOL.entrySet()) if (e.getKey() != exclude) total += e.getValue();
        int n = r.nextInt(Math.max(1, total));
        for (Map.Entry<SkillType, Integer> e : POOL.entrySet()) {
            if (e.getKey() == exclude) continue;
            n -= e.getValue();
            if (n < 0) return e.getKey();
        }
        return SkillType.ATTACK_BOOST;
    }

    // ------------------------------------------------------------------ activation
    /** The single active charm of a player: offhand first, then the hotbar from the left. */
    public static ItemStack findActive(Player p) {
        ItemStack off = p.getOffhandItem();
        if (off.getItem() instanceof CharmItem && isRolled(off)) return off;
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.getItem() instanceof CharmItem && isRolled(s)) return s;
        }
        return ItemStack.EMPTY;
    }

    public static void addSkills(ItemStack charm, Map<SkillType, Integer> out) {
        getSkills(charm).forEach((k, v) -> out.merge(k, v, Integer::sum));
    }

    // ------------------------------------------------------------------ item behaviour
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && !isRolled(stack)) {
            ItemStack rolled = rollContents(this, getTier(stack), level.getRandom());
            stack.setTag(rolled.getTag());
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return getTier(stack) >= MAX_TIER;
    }

    @Override
    public Component getName(ItemStack stack) {
        int tier = getTier(stack);
        return Component.translatable("item.huntcraft.charm.tier" + tier)
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(MaterialItem.rarityColor(TIER_RARITY[tier]))));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int tier = getTier(stack);
        tooltip.add(Component.translatable("tooltip.huntcraft.rarity", TIER_RARITY[tier]).withStyle(ChatFormatting.GRAY));
        if (!isRolled(stack)) {
            tooltip.add(Component.translatable("tooltip.huntcraft.charm.unknown").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            return;
        }
        tooltip.add(Component.translatable("tooltip.huntcraft.armor.skills").withStyle(ChatFormatting.GRAY));
        getSkills(stack).forEach((k, v) -> tooltip.add(Component.literal("  ")
                .append(Component.translatable(k.key()).withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" +" + v).withStyle(ChatFormatting.WHITE))));
        int[] slots = getSlots(stack);
        MutableComponent sl = Component.translatable("tooltip.huntcraft.charm.slots").withStyle(ChatFormatting.GRAY);
        StringBuilder sb = new StringBuilder(" ");
        for (int i = 0; i < 3; i++) {
            if (i < slots.length) sb.append(slots[i] >= 3 ? "③" : slots[i] == 2 ? "②" : "①");
            else sb.append("—");
        }
        sl.append(Component.literal(sb.toString()).withStyle(ChatFormatting.AQUA));
        tooltip.add(sl);
        tooltip.add(Component.translatable("tooltip.huntcraft.charm.hint").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    /** Debug/test helper. */
    public static Map<SkillType, Integer> emptySkills() {
        return new EnumMap<>(SkillType.class);
    }

    static List<SkillType> pool() {
        return new ArrayList<>(POOL.keySet());
    }
}
