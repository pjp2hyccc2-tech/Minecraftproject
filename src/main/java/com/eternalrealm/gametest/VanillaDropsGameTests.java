package com.eternalrealm.gametest;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.item.forge.VanillaDrops;
import com.eternalrealm.monster.Materials;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Vanilla items with nobody's name on them are told apart from a player's own (2026-11-14: バニラのアイテムは落ちない
 * ように). The refusal itself happens only in アストリア and the dungeons; these tests run in the test level.
 */
@GameTestHolder(ModInfo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VanillaDropsGameTests {
    private static final String ARENA = "arena";

    private VanillaDropsGameTests() {
    }

    @GameTest(template = ARENA)
    public static void strayVanillaItemsAreToldApart(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 at = helper.absoluteVec(new Vec3(4.5, 2.0, 4.5));
        check(VanillaDrops.stray(new ItemEntity(level, at.x, at.y, at.z, new ItemStack(Items.PINK_PETALS, 3))),
                "petals knocked loose are stray");

        ItemEntity owned = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(Items.WHEAT_SEEDS));
        owned.setTarget(UUID.randomUUID());
        check(!VanillaDrops.stray(owned), "loot that did not fit the bag is its owner's");

        ItemEntity thrown = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(Items.TORCH));
        CompoundTag saved = thrown.saveWithoutId(new CompoundTag());
        saved.putUUID("Thrower", UUID.randomUUID());
        thrown.load(saved);
        check(!VanillaDrops.stray(thrown), "a thrown item is its thrower's");

        ItemEntity kept = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(Items.IRON_INGOT));
        kept.getPersistentData().putBoolean(VanillaDrops.KEEP_TAG, true);
        check(!VanillaDrops.stray(kept), "a dying player's drop is kept");

        Item gel = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(Materials.SLIME_GEL));
        check(gel != null, "slime gel exists");
        check(!VanillaDrops.stray(new ItemEntity(level, at.x, at.y, at.z, new ItemStack(gel))),
                "the game's own items are never stray");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void itemsAPlayerLetsFallAreKept(GameTestHelper helper) {
        String name = "er_gt_toss";
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name));
        Vec3 at = helper.absoluteVec(new Vec3(4.5, 2.0, 4.5));
        player.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        ItemEntity dropped = player.drop(new ItemStack(Items.DIRT, 2), false);
        try {
            check(dropped != null, "the dirt fell");
            check(dropped.getPersistentData().getBoolean(VanillaDrops.KEEP_TAG), "marked as the player's");
            check(!VanillaDrops.stray(dropped), "so it is not stray");
        } finally {
            if (dropped != null) {
                dropped.discard();
            }
        }
        helper.succeed();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
