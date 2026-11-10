package com.eternalrealm.npc.forge;

import com.eternalrealm.npc.Townsfolk;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.story.DialogueLine;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Optional;

/**
 * The townsfolk, guards and residents of the capital: one {@link NpcEntity} per {@code crowd.*} / {@code guard.*} spot
 * of the capital build and per resident's spot such as {@code castle.king} (who stands where: {@link Townsfolk}). Placing is idempotent like {@link Npcs#ensure}: the NPC of a
 * spot is found again by the spot name it carries, duplicates are removed. Townsfolk are not service NPCs (no role
 * tag, not on the map); talking to one plays a short greeting in the ambient dialogue box. Server thread only.
 */
public final class Crowds {
    /** Persistent data key holding the spot name of a townsperson or guard. */
    public static final String SPOT = "eternalrealm_spot";

    /** A named spot of the capital: feet position and facing. */
    public record Spot(String name, BlockPos pos, float yaw) {
    }

    private Crowds() {
    }

    /** Places (or puts back) the townsperson or guard of every spot. @return how many stand now */
    public static int populate(ServerLevel level, List<Spot> spots) {
        int placed = 0;
        for (Spot spot : spots) {
            placed += ensure(level, spot).isPresent() ? 1 : 0;
        }
        return placed;
    }

    /** The NPC of one spot (created if missing; the chunk is loaded); empty for names that are not crowd or guard spots. */
    public static Optional<NpcEntity> ensure(ServerLevel level, Spot spot) {
        Optional<Townsfolk.Spot> parsed = Townsfolk.parse(spot.name());
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        BlockPos pos = spot.pos();
        level.getChunk(pos.getX() >> 4, pos.getZ() >> 4); // the spot's chunk must be loaded
        List<NpcEntity> existing = level.getEntitiesOfClass(NpcEntity.class,
                new AABB(pos).inflate(Townsfolk.STROLL_RADIUS + 10.0),
                npc -> spot.name().equals(npc.getPersistentData().getString(SPOT)));
        NpcEntity npc = existing.isEmpty() ? null : existing.get(0);
        for (int i = 1; i < existing.size(); i++) {
            existing.get(i).discard();
        }
        if (npc == null) {
            npc = NpcEntities.NPC.get().create(level);
            if (npc == null) {
                return Optional.empty();
            }
            npc.getPersistentData().putString(SPOT, spot.name());
            npc.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, spot.yaw(), 0.0F);
            level.addFreshEntity(npc);
        }
        String appearance = Townsfolk.appearanceFor(spot.name());
        npc.setAppearance(appearance);
        Townsfolk.Kind kind = parsed.get().kind();
        switch (kind) {
            case GUARD -> npc.setWatch(pos, spot.yaw());
            case RESIDENT -> npc.setPost(pos, spot.yaw());
            default -> npc.setStroll(pos, spot.yaw());
        }
        npc.setInvulnerable(true);
        npc.setSilent(true);
        npc.setPersistenceRequired();
        if (kind == Townsfolk.Kind.RESIDENT) {
            // named people show their name like the story characters
            npc.setCustomName(Component.translatable(Townsfolk.nameKey(appearance)).withStyle(ChatFormatting.AQUA));
            npc.setCustomNameVisible(true);
        } else {
            // the name shows when a player looks at them
            npc.setCustomName(Component.translatable(Townsfolk.nameKey(appearance)));
            npc.setCustomNameVisible(false);
        }
        npc.markEnsured(level.getGameTime());
        return Optional.of(npc);
    }

    /** Removes every townsperson and guard in {@code area} (before the capital is populated anew). */
    public static int removeAll(ServerLevel level, AABB area) {
        List<NpcEntity> found = level.getEntitiesOfClass(NpcEntity.class, area,
                npc -> npc.getPersistentData().contains(SPOT));
        found.forEach(NpcEntity::discard);
        return found.size();
    }

    /** A player talks to a townsperson: one random line of their kind, in the ambient dialogue box. */
    static void greet(ServerPlayer player, NpcEntity npc) {
        if (npc.isTalking()) {
            return; // still answering the last one
        }
        npc.startTalking(player);
        player.playNotifySound(com.eternalrealm.sound.ERSounds.UI_NPC_TALK.get(), SoundSource.NEUTRAL, 0.7F,
                0.95F + npc.getRandom().nextFloat() * 0.1F);
        String appearance = npc.getAppearance();
        String speaker = Townsfolk.nameKey(appearance);
        String line = Townsfolk.greetingKey(appearance, npc.getRandom().nextInt(Townsfolk.greetingCount(appearance)));
        Optional<ServerContext> context = ServerContext.current();
        if (context.isPresent()) {
            context.get().game().story().play(player, List.of(new DialogueLine(speaker, line)), false, null);
        } else {
            player.sendSystemMessage(Component.literal("[").append(Component.translatable(speaker)
                    .withStyle(ChatFormatting.AQUA)).append("] ").append(Component.translatable(line)));
        }
    }
}
