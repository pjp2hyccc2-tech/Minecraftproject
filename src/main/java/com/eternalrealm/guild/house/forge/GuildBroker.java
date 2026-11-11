package com.eternalrealm.guild.house.forge;

import com.eternalrealm.npc.forge.NpcEntity;
import com.eternalrealm.npc.forge.NpcRole;
import com.eternalrealm.npc.forge.Npcs;
import com.eternalrealm.world.forge.HubLayout;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

import java.util.Optional;

/**
 * Célestin, the guild house broker (ギルドハウス仲介人): stands at the capital's {@value #SPOT} spot in the pavilion of
 * the Guild Gate Terrace, wears a noble's coat and opens the catalogue of the twenty guild houses.
 */
public final class GuildBroker {
    public static final String SPOT = "npc.guild_estates";
    static final String LOOK = "noble_gent";

    private GuildBroker() {
    }

    static void register() {
        Npcs.onInteract(NpcRole.GUILD_ESTATES, (player, npc) -> GuildHouseUi.openCatalogue(player, null));
    }

    /** Places the broker at the capital's spot (called with the capital's other NPCs). */
    public static void place(ServerLevel astoria, HubLayout layout) {
        Optional<BlockPos> pos = layout.named(SPOT);
        if (pos.isEmpty()) {
            return;
        }
        Npcs.ensure(astoria, pos.get(), NpcRole.GUILD_ESTATES, layout.namedYaw(SPOT, 0.0F)).ifPresent(npc -> {
            if (npc instanceof NpcEntity body) {
                body.setAppearance(LOOK);
            }
            npc.setCustomName(Component.translatable(NpcRole.GUILD_ESTATES.translationKey())
                    .withStyle(ChatFormatting.GOLD));
        });
    }

    /** Whether a broker stands within {@link GuildHouseUi#REACH} blocks of the player. */
    static boolean near(ServerPlayer player) {
        return !player.serverLevel().getEntitiesOfClass(Mob.class,
                player.getBoundingBox().inflate(GuildHouseUi.REACH),
                mob -> Npcs.roleOf(mob).filter(role -> role == NpcRole.GUILD_ESTATES).isPresent()).isEmpty();
    }
}
