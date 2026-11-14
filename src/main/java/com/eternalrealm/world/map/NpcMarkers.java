package com.eternalrealm.world.map;

import com.eternalrealm.core.ModInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * What the minimap shows for the town's NPCs inside the capital (the owner, 2026-11-11: 「王国内に入るとミニマップに
 * NPCのいる場所に何のNPCかわかるように表示を出して」): an icon per service (the atlas
 * {@code textures/gui/map/npc_icons.png}, drawn by {@code tools/art/map_npc_icons.py}, one {@value #ICON_SIZE}-pixel
 * icon per role in this order) and a short label, the service's name ("鍛冶屋") or a story character's own name.
 * Labels that would overlap a nearer one or leave the map are left out ({@link #place}). Pure.
 */
public final class NpcMarkers {
    public static final int ICON_SIZE = 12;
    public static final String ATLAS = "textures/gui/map/npc_icons.png";
    /** The story characters' role id (their label is their own name). */
    public static final String STORY = "story";
    /** At most this many labels at once (the nearest first). */
    public static final int MAX_LABELS = 8;
    private static final List<String> ROLES = List.of("quest_board", "guild_registrar", "item_shop", "blacksmith",
            "job_master", "auction", "mailbox", "gatekeeper", "arena_master", "hall_of_fame", "stable_master", "innkeeper",
            "guild_estates", STORY);

    private NpcMarkers() {
    }

    /** The roles with an icon, in atlas order (the last is the story characters'). */
    public static List<String> roles() {
        return ROLES;
    }

    /** Index of the icon of {@code role} in the atlas; unknown roles get the story icon. */
    public static int icon(String role) {
        int index = ROLES.indexOf(role);
        return index < 0 ? ROLES.size() - 1 : index;
    }

    /** The label of a service ("map.eternalrealm.npc.blacksmith"); empty for story characters (their own name). */
    public static String labelKey(String role) {
        if (role == null || STORY.equals(role) || !ROLES.contains(role)) {
            return "";
        }
        return "map." + ModInfo.MOD_ID + ".npc." + role;
    }

    /**
     * Which labels to draw: {@code boxes} are {x0, y0, x1, y1} in drawing order (nearest NPC first); a label is
     * drawn if it lies inside the bounds and does not overlap a label drawn before it, at most {@link #MAX_LABELS}.
     *
     * @return the indices of the labels to draw, in order
     */
    public static List<Integer> place(List<float[]> boxes, float minX, float minY, float maxX, float maxY) {
        List<Integer> drawn = new ArrayList<>();
        List<float[]> taken = new ArrayList<>();
        for (int i = 0; i < boxes.size() && drawn.size() < MAX_LABELS; i++) {
            float[] box = boxes.get(i);
            if (box[0] < minX || box[1] < minY || box[2] > maxX || box[3] > maxY) {
                continue;
            }
            boolean free = true;
            for (float[] other : taken) {
                if (box[0] < other[2] && other[0] < box[2] && box[1] < other[3] && other[1] < box[3]) {
                    free = false;
                    break;
                }
            }
            if (free) {
                drawn.add(i);
                taken.add(box);
            }
        }
        return drawn;
    }
}
