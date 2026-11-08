package com.eternalrealm.story.forge;

import com.eternalrealm.server.UiActions;
import com.eternalrealm.server.UiArgs;

/** UI actions of the story: the client reports that the player closed a dialogue. */
public final class StoryActions {
    private StoryActions() {
    }

    /** Once during mod construction. */
    public static void register() {
        UiActions.register("dialogue.done", (player, context, args) ->
                context.game().story().onDialogueDone(player, (int) UiArgs.number(args, "id", 1, Integer.MAX_VALUE)));
    }
}
