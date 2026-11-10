package com.eternalrealm.npc;

import com.eternalrealm.story.StoryNpc;

/**
 * Which body (appearance id of {@link NpcBodies}) a named NPC wears. The appearance of a service NPC or story character
 * is its speaker id ({@code quest_board} wears "mina", {@code arcelia_knight_captain} wears "adel"), so the dialogue
 * portrait ({@code textures/gui/portrait/<speaker>.png}) and the body always show the same person. Pure.
 */
public final class NpcLooks {
    /** The gatekeepers at the dungeon entrances (Gant himself stands at the capital's gate). */
    public static final String WARDEN = "warden";

    private NpcLooks() {
    }

    /** Appearance of a service NPC of the capital, by role id ({@code NpcRole#id}). */
    public static String forRole(String roleId) {
        return speakerLook(roleId);
    }

    /** Appearance of a story character or quest giver, by {@link StoryNpc} id. */
    public static String forCharacter(String characterId) {
        return speakerLook(characterId);
    }

    private static String speakerLook(String id) {
        if (id == null) {
            return NpcBodies.DEFAULT;
        }
        return NpcBodies.orDefault(StoryNpc.byId(id).map(npc -> npc.speaker().id()).orElse(id));
    }
}
