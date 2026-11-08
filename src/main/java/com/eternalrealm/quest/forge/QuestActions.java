package com.eternalrealm.quest.forge;

import com.eternalrealm.quest.GameEvent;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.server.UiActions;
import com.eternalrealm.server.UiArgs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.registries.ForgeRegistries;

/** The quest page's actions and the pickup events that count for "collect" objectives. */
public final class QuestActions {
    private QuestActions() {
    }

    public static void register() {
        UiActions.register("quest.view", (player, context, args) -> context.game().quests().sendView(player));
        UiActions.register("quest.accept", (player, context, args) ->
                context.game().quests().accept(player, UiArgs.id(args, "id")));
        UiActions.register("quest.abandon", (player, context, args) ->
                context.game().quests().abandon(player, UiArgs.id(args, "id")));
        UiActions.register("quest.turn_in", (player, context, args) ->
                context.game().quests().turnIn(player, UiArgs.id(args, "id")));
    }

    /**
     * Items picked up from the ground count, except items someone dropped or threw (they could be counted again and
     * again); loot, ore and mob drops have no thrower.
     */
    public static void onItemPickup(PlayerEvent.ItemPickupEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) {
            return;
        }
        ItemEntity origin = event.getOriginalEntity();
        ItemStack stack = event.getStack();
        if (origin == null || origin.getOwner() != null || stack.isEmpty()) {
            return;
        }
        String id = String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem()));
        ServerContext.current().ifPresent(context ->
                context.game().quests().event(player, new GameEvent.ItemCollected(id, stack.getCount())));
    }
}
