package com.eternalrealm.gametest;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.world.forge.capital.CapitalBlueprint;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The blueprint capital shipped with the mod (if any) is consistent: every palette state parses on this version,
 * every tile decodes, and the spots the game needs exist.
 */
@GameTestHolder(ModInfo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CapitalGameTests {
    private static final String EMPTY = "empty";
    private static final List<String> REQUIRED_SPOTS = List.of("spawn", "npc.quest_board", "npc.item_shop",
            "npc.blacksmith", "npc.job_master", "npc.auction", "npc.mailbox", "npc.guild_registrar", "npc.gatekeeper",
            "npc.arena_master", "npc.hall_of_fame");

    private CapitalGameTests() {
    }

    @GameTest(template = EMPTY, timeoutTicks = 1200)
    public static void capitalBlueprintIsValid(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Optional<CapitalBlueprint> loaded = CapitalBlueprint.load(level.getServer().getResourceManager());
        if (loaded.isEmpty()) {
            helper.succeed(); // the classic town is used
            return;
        }
        CapitalBlueprint blueprint = loaded.get();
        List<String> problems = new ArrayList<>();
        blueprint.resolvePalette(level.holderLookup(Registries.BLOCK), problems);
        if (!problems.isEmpty()) {
            helper.fail("invalid palette states: " + problems);
            return;
        }
        for (CapitalBlueprint.Tile tile : blueprint.tiles()) {
            try {
                blueprint.decode(level.getServer().getResourceManager(), tile);
            } catch (Exception e) {
                helper.fail("tile " + tile.file() + ": " + e.getMessage());
                return;
            }
        }
        for (String spot : REQUIRED_SPOTS) {
            if (!blueprint.spots().containsKey(spot)) {
                helper.fail("missing spot " + spot);
                return;
            }
        }
        helper.succeed();
    }
}
