package com.huntcraft.village.story;

import com.huntcraft.data.HunterData;
import com.huntcraft.entity.monster.MonsterRegistry;
import com.huntcraft.registry.HCItems;
import com.huntcraft.registry.HCSounds;
import com.huntcraft.village.companion.MofuData;
import com.huntcraft.village.menu.ItemBoxContainer;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The village chief's chronicle: story chapters unlocked by progress, rank guidance and one-time milestone rewards
 * (claimed at the chief, stored in {@code village.rewards}).
 */
public final class ElderStory {
    public record Chapter(String id, Predicate<HunterData> unlocked, String hintKey) {
        public Component title() {
            return Component.translatable("story.huntcraft." + id + ".title");
        }

        public Component text() {
            return Component.translatable("story.huntcraft." + id + ".text");
        }

        public Component hint() {
            return Component.translatable(hintKey);
        }
    }

    public record Reward(String id, Predicate<HunterData> reached, int zenny, List<Gift> gifts) {
        public Component title() {
            return Component.translatable("reward.huntcraft." + id);
        }
    }

    public record Gift(Supplier<? extends Item> item, int count) {
    }

    public static final List<Chapter> CHAPTERS = List.of(
            new Chapter("arrival", d -> true, "story.huntcraft.hint.always"),
            new Chapter("first_hunt", d -> largeHunts(d) >= 1, "story.huntcraft.hint.first_hunt"),
            new Chapter("rumors", d -> d.hunterRank >= 3, "story.huntcraft.hint.hr3"),
            new Chapter("ember", d -> d.hunterRank >= 5, "story.huntcraft.hint.hr5"),
            new Chapter("high_rank", d -> d.hunterRank >= 8, "story.huntcraft.hint.hr8"),
            new Chapter("dark_flame", d -> d.totalHunted("azragos") >= 1, "story.huntcraft.hint.azragos"));

    public static final List<Reward> REWARDS = List.of(
            new Reward("first_large", d -> largeHunts(d) >= 1, 800, List.of(new Gift(HCItems.MOFU_COIN, 1), new Gift(HCItems.MEGA_POTION, 3))),
            new Reward("hr2", d -> d.hunterRank >= 2, 1000, List.of(new Gift(HCItems.POTION, 5), new Gift(HCItems.FARCASTER, 1))),
            new Reward("hr3", d -> d.hunterRank >= 3, 1500, List.of(new Gift(HCItems.ARMOR_SPHERE, 3))),
            new Reward("hr5", d -> d.hunterRank >= 5, 3000, List.of(new Gift(HCItems.MEGA_POTION, 5), new Gift(HCItems.ARMOR_SPHERE_PLUS, 1))),
            new Reward("hr8", d -> d.hunterRank >= 8, 6000, List.of(new Gift(HCItems.ARMOR_SPHERE_PLUS, 3), new Gift(HCItems.MOFU_COIN, 2))),
            new Reward("all_large", d -> huntedAllLarge(d), 10000, List.of(new Gift(HCItems.HARD_ARMOR_SPHERE, 2), new Gift(HCItems.MOFU_COIN, 3))));

    private ElderStory() {
    }

    public static int largeHunts(HunterData d) {
        int n = 0;
        for (MonsterRegistry.Entry e : MonsterRegistry.large()) n += d.totalHunted(e.id());
        return n;
    }

    public static boolean huntedAllLarge(HunterData d) {
        List<MonsterRegistry.Entry> large = MonsterRegistry.large();
        if (large.isEmpty()) return false;
        for (MonsterRegistry.Entry e : large) if (d.totalHunted(e.id()) <= 0) return false;
        return true;
    }

    public static boolean claimed(Player p, Reward r) {
        return MofuData.village(p).getCompound("rewards").getBoolean(r.id());
    }

    public static int unclaimed(Player p) {
        HunterData d = HunterData.get(p);
        int n = 0;
        for (Reward r : REWARDS) if (r.reached().test(d) && !claimed(p, r)) n++;
        return n;
    }

    /** Server action "village/elder" {op:"claim", id}. */
    public static void handle(ServerPlayer p, CompoundTag t) {
        if (!"claim".equals(t.getString("op"))) return;
        String id = t.getString("id");
        HunterData d = HunterData.get(p);
        for (Reward r : REWARDS) {
            if (!r.id().equals(id)) continue;
            if (!r.reached().test(d) || claimed(p, r)) return;
            CompoundTag v = MofuData.village(p);
            CompoundTag rw = v.getCompound("rewards");
            rw.putBoolean(id, true);
            v.put("rewards", rw);
            d.addZenny(r.zenny());
            for (Gift g : r.gifts()) {
                ItemStack st = new ItemStack(g.item().get(), g.count());
                ItemStack rest = ItemBoxContainer.insert(d.itemBox, st);
                if (!rest.isEmpty() && !p.getInventory().add(rest)) p.drop(rest, false);
            }
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), HCSounds.LEVEL_UP.get(), SoundSource.PLAYERS, 0.9F, 1.0F);
            p.displayClientMessage(Component.translatable("message.huntcraft.reward_claimed", r.title(), r.zenny()).withStyle(ChatFormatting.GOLD), false);
            d.markDirty();
            return;
        }
    }
}
