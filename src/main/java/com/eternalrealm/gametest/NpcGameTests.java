package com.eternalrealm.gametest;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.npc.NpcLooks;
import com.eternalrealm.npc.Townsfolk;
import com.eternalrealm.npc.forge.Crowds;
import com.eternalrealm.npc.forge.NpcEntities;
import com.eternalrealm.npc.forge.NpcEntity;
import com.eternalrealm.npc.forge.NpcRole;
import com.eternalrealm.npc.forge.Npcs;
import com.eternalrealm.npc.forge.StoryNpcs;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests of the town NPC bodies: service NPCs and characters wear their own appearance and replace the villagers
 * of older versions (keeping their data), cannot be hurt or pushed; townsfolk and guards take their spots once,
 * survive saving, and duplicate bodies of one person resolve to the newest.
 */
@GameTestHolder(ModInfo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpcGameTests {
    private static final String ARENA = "arena";

    private NpcGameTests() {
    }

    @GameTest(template = ARENA)
    public static void serviceNpcsWearTheirBodiesAndReplaceVillagers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos post = helper.absolutePos(new BlockPos(2, 2, 2));
        Villager old = EntityType.VILLAGER.create(level);
        check(old != null, "could not create a villager");
        old.getPersistentData().putString(Npcs.TAG, NpcRole.QUEST_BOARD.id());
        old.getPersistentData().putString("eternalrealm_gametest", "kept");
        old.moveTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, 0.0F, 0.0F);
        level.addFreshEntity(old);

        Mob npc = Npcs.ensure(level, post, NpcRole.QUEST_BOARD, 90.0F).orElse(null);
        check(npc instanceof NpcEntity, "ensure must create an NpcEntity, got " + npc);
        NpcEntity body = (NpcEntity) npc;
        check(old.isRemoved(), "the villager body is replaced");
        check("kept".equals(body.getPersistentData().getString("eternalrealm_gametest")), "persistent data is kept");
        check("mina".equals(body.getAppearance()), "the quest board wears Mina, got " + body.getAppearance());
        check(body.mode() == NpcEntity.Mode.POST && post.equals(body.home()), "service NPCs hold their post");
        check(Npcs.ensure(level, post, NpcRole.QUEST_BOARD, 90.0F).orElse(null) == body, "ensure finds the NPC again");
        check(level.getEntitiesOfClass(Mob.class, new AABB(post).inflate(4.0), mob -> Npcs.roleOf(mob).isPresent())
                .size() == 1, "no duplicates");
        check(!body.hurt(level.damageSources().generic(), 10.0F) && body.getHealth() == body.getMaxHealth(),
                "NPCs cannot be hurt");
        check(!body.isPushable() && !body.isAttackable(), "NPCs are neither pushed nor attacked");

        Mob lilia = StoryNpcs.ensure(level, helper.absolutePos(new BlockPos(6, 2, 2)), "lilia",
                Component.literal("Lilia"), 0.0F).orElse(null);
        check(lilia instanceof NpcEntity princess && "lilia".equals(princess.getAppearance()), "Lilia wears her body");
        Mob warden = Npcs.ensure(level, helper.absolutePos(new BlockPos(6, 2, 6)), NpcRole.GATEKEEPER, 0.0F,
                Npcs.ENTRANCE, "d3").orElse(null);
        check(warden instanceof NpcEntity gate && NpcLooks.WARDEN.equals(gate.getAppearance()),
                "the gatekeepers of the dungeon entrances are wardens");
        body.discard();
        lilia.discard();
        warden.discard();
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void townsfolkAndGuardsTakeTheirSpots(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos quay = helper.absolutePos(new BlockPos(2, 2, 6));
        NpcEntity sailor = Crowds.ensure(level, new Crowds.Spot("crowd.harbour.1", quay, 90.0F)).orElse(null);
        check(sailor != null, "no townsperson at a crowd spot");
        check(Townsfolk.pool("harbour").contains(sailor.getAppearance()), "harbour folk, got " + sailor.getAppearance());
        check(sailor.mode() == NpcEntity.Mode.STROLL && quay.equals(sailor.home()), "townsfolk stroll near their spot");
        check(Npcs.roleOf(sailor).isEmpty(), "townsfolk are not service NPCs");
        check(Crowds.ensure(level, new Crowds.Spot("crowd.harbour.1", quay, 90.0F)).orElse(null) == sailor,
                "placing again finds the same townsperson");
        NpcEntity archer = Crowds.ensure(level, new Crowds.Spot("guard.wall_gametest.1",
                helper.absolutePos(new BlockPos(6, 2, 4)), 180.0F)).orElse(null);
        check(archer != null && "archer".equals(archer.getAppearance()) && archer.mode() == NpcEntity.Mode.WATCH,
                "wall guards are archers standing watch");
        check(Crowds.ensure(level, new Crowds.Spot("npc.quest_board", quay, 0.0F)).isEmpty(),
                "other spots get nobody");
        CompoundTag tag = archer.saveWithoutId(new CompoundTag());
        NpcEntity copy = NpcEntities.NPC.get().create(level);
        check(copy != null, "could not create an NPC");
        copy.load(tag);
        check("archer".equals(copy.getAppearance()) && copy.mode() == NpcEntity.Mode.WATCH
                && archer.home().equals(copy.home()), "appearance, mode and post survive saving");
        check(!sailor.hurt(level.damageSources().generic(), 10.0F), "townsfolk cannot be hurt");
        sailor.discard();
        archer.discard();
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void duplicateBodiesResolveToTheNewest(GameTestHelper helper) {
        NpcEntity older = spawnFolk(helper, "crowd.gametest_duplicates.1", new BlockPos(2, 2, 4), 10L);
        NpcEntity newer = spawnFolk(helper, "crowd.gametest_duplicates.1", new BlockPos(4, 2, 4), 20L);
        helper.succeedWhen(() -> {
            check(older.isRemoved(), "the older body should be removed");
            check(!newer.isRemoved(), "the newest body stays");
            newer.discard();
        });
    }

    private static NpcEntity spawnFolk(GameTestHelper helper, String spot, BlockPos relative, long stamp) {
        NpcEntity npc = NpcEntities.NPC.get().create(helper.getLevel());
        check(npc != null, "could not create an NPC");
        BlockPos pos = helper.absolutePos(relative);
        npc.getPersistentData().putString(Crowds.SPOT, spot);
        npc.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
        npc.markEnsured(stamp);
        helper.getLevel().addFreshEntity(npc);
        return npc;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
