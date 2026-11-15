package com.eternalrealm.gametest;

import com.eternalrealm.combat.WeaponType;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.item.EquipmentBase;
import com.eternalrealm.item.EquipmentData;
import com.eternalrealm.item.ItemTier;
import com.eternalrealm.item.forge.ERGearItems;
import com.eternalrealm.item.forge.EquipmentStacks;
import com.eternalrealm.item.forge.SmithService;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.server.UiActions;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * The smithy and pieces without a unique id (the creative menu, a command, an old world: 2026-11-16 「鍛冶屋で
 * しょりおちした」, such a piece crashed the smithy screen): opening the smithy gives every piece the player carries an
 * id, an action on a piece that still has none gives it one, and unreadable stored data is never overwritten.
 */
@GameTestHolder(ModInfo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SmithGameTests {
    private static final String ARENA = "arena";

    private SmithGameTests() {
    }

    @GameTest(template = ARENA)
    public static void piecesWithoutAnIdGetOneAtTheSmithy(GameTestHelper helper) {
        ServerContext context = ServerContext.current()
                .orElseThrow(() -> new GameTestAssertException("no server context"));
        FakePlayer player = player(helper, "er_gt_smith_ids");
        Inventory inventory = player.getInventory();
        EquipmentBase sword = EquipmentBase.weapon(WeaponType.ONE_HANDED_SWORD, ItemTier.IRON);
        inventory.items.set(0, new ItemStack(ERGearItems.equipment(sword)));
        ItemStack damaged = new ItemStack(ERGearItems.equipment(sword));
        damaged.getOrCreateTag().putString(EquipmentStacks.TAG, "not equipment data");
        inventory.items.set(1, damaged);
        check(idOf(inventory.items.get(0)) == null, "a plain stack has no id");

        SmithService.open(player, "goran");
        UUID given = idOf(inventory.items.get(0));
        check(given != null, "opening the smithy should give the piece an id");
        check("not equipment data".equals(inventory.items.get(1).getTag().getString(EquipmentStacks.TAG)),
                "unreadable stored data must be left alone");
        SmithService.open(player, "goran");
        check(given.equals(idOf(inventory.items.get(0))), "an id, once given, stays");

        // a piece that came in after the smithy opened: the screen sends no id, the smithy gives it one first
        inventory.items.set(2, new ItemStack(ERGearItems.equipment(sword)));
        JsonObject args = new JsonObject();
        args.addProperty("slot", 2);
        UiActions.dispatch(player, context, SmithService.ENHANCE, args);
        check(idOf(inventory.items.get(2)) != null, "an action on a piece without an id gives it one");
        // the id the screen shows is required as soon as there is one
        args.addProperty("id", UUID.randomUUID().toString());
        ItemStack before = inventory.items.get(2).copy();
        UiActions.dispatch(player, context, SmithService.ENHANCE, args);
        check(ItemStack.matches(before, inventory.items.get(2)), "another piece's id must not touch this one");
        inventory.clearContent();
        helper.succeed();
    }

    private static UUID idOf(ItemStack stack) {
        return EquipmentStacks.read(stack).map(EquipmentData::uniqueId).orElse(null);
    }

    private static FakePlayer player(GameTestHelper helper, String name) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name));
        Vec3 at = helper.absoluteVec(new Vec3(4.5, 2.0, 4.5));
        player.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        player.getInventory().clearContent();
        return player;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
