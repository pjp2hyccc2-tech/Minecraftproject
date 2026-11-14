package com.eternalrealm.gametest;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.item.forge.Bags;
import com.eternalrealm.monster.Materials;
import com.eternalrealm.player.PlayerDataService;
import com.eternalrealm.quest.ActiveQuest;
import com.eternalrealm.quest.QuestLog;
import com.eternalrealm.quest.QuestLogCodec;
import com.eternalrealm.quest.forge.QuestService;
import com.eternalrealm.server.ServerContext;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * "Collect" quest objectives count monster loot: loot goes straight into the bag (no item on the ground is picked
 * up), so the loot itself is reported (2026-11-14: スライムの素材を集めるクエストが一生クリアできない).
 */
@GameTestHolder(ModInfo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CollectQuestGameTests {
    private static final String ARENA = "arena";
    private static final String QUEST = "s1_slime_gel";

    private CollectQuestGameTests() {
    }

    @GameTest(template = ARENA)
    public static void lootIntoTheBagCountsForCollectObjectives(GameTestHelper helper) {
        ServerContext context = ServerContext.current()
                .orElseThrow(() -> new GameTestAssertException("no server context"));
        PlayerDataService data = context.playerData();
        String name = "er_gt_collect";
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name));
        Vec3 at = helper.absoluteVec(new Vec3(4.5, 2.0, 4.5));
        player.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        UUID id = player.getUUID();
        data.onPlayerJoin(id, name);
        try {
            QuestLog log = new QuestLog(List.of(new ActiveQuest(QUEST, List.of(0))), Map.of(), QuestLog.NEVER_RESET,
                    Set.of());
            check(data.setExtension(id, QuestService.EXTENSION, QuestLogCodec.toJson(log).toString()),
                    "the quest log was set");
            QuestService quests = context.game().quests();
            check(quests.progressOf(player, QUEST).equals(List.of(0)), "the quest is active: "
                    + quests.progressOf(player, QUEST));
            Item gel = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(Materials.SLIME_GEL));
            check(gel != null, "slime gel exists");
            int added = Bags.loot(player, new ItemStack(gel, 3));
            check(added == 3, "three went into the bag: " + added);
            check(quests.progressOf(player, QUEST).equals(List.of(3)), "the loot counted: "
                    + quests.progressOf(player, QUEST));
            Bags.give(player, new ItemStack(gel, 2));
            check(quests.progressOf(player, QUEST).equals(List.of(3)), "a plain give (a purchase, a trade) does not");
            Bags.loot(player, new ItemStack(gel, 10));
            check(quests.progressOf(player, QUEST).equals(List.of(8)), "capped at what the objective needs: "
                    + quests.progressOf(player, QUEST));
        } finally {
            player.getInventory().clearContent();
            data.onPlayerLeave(id);
        }
        helper.succeed();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
