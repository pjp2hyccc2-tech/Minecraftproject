package com.eternalrealm.estate.forge;

import com.eternalrealm.estate.EstateKey;
import net.minecraft.server.level.ServerPlayer;

/**
 * Who besides the owner may enter and use estates of a kind ({@link EstateService#registerAccess}); e.g. the guild
 * houses let every member of the owning guild in. The owner of a player-owned estate (a player whose id is the
 * estate's owner) and operators in creative mode are always let in. Called on the server thread, often (every half
 * second for each player inside an estate): keep it cheap.
 */
@FunctionalInterface
public interface EstateAccess {
    boolean mayEnter(ServerPlayer player, EstateKey estate);
}
