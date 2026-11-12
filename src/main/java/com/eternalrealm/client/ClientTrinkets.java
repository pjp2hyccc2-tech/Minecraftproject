package com.eternalrealm.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;

/**
 * The owning player's trinkets (four accessories and two charms) as the server last sent them (UI data channel
 * {@code trinkets}, {@code TrinketSlots}): the equipment screen shows and compares them. Client thread only.
 */
public final class ClientTrinkets {
    /** The channel of {@code com.eternalrealm.item.forge.TrinketSlots}. */
    static final String CHANNEL = "trinkets";
    private static final ItemStack[] STACKS = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
            ItemStack.EMPTY, ItemStack.EMPTY};

    private ClientTrinkets() {
    }

    static void register() {
        ClientChannels.register(CHANNEL, ClientTrinkets::accept);
    }

    /** Storage order of {@code TrinketSlots.Slot}: 0, 1, 3, 4 the accessories, 2 and 5 the charms. */
    public static ItemStack get(int index) {
        return index >= 0 && index < STACKS.length ? STACKS[index] : ItemStack.EMPTY;
    }

    public static int size() {
        return STACKS.length;
    }

    public static void clear() {
        java.util.Arrays.fill(STACKS, ItemStack.EMPTY);
    }

    private static void accept(JsonObject data) {
        JsonArray stacks = data.has("stacks") && data.get("stacks").isJsonArray() ? data.getAsJsonArray("stacks")
                : new JsonArray();
        for (int i = 0; i < STACKS.length; i++) {
            STACKS[i] = i < stacks.size() ? parse(stacks.get(i)) : ItemStack.EMPTY;
        }
    }

    private static ItemStack parse(JsonElement element) {
        String text = element.isJsonPrimitive() ? element.getAsString() : "";
        if (text.isEmpty()) {
            return ItemStack.EMPTY;
        }
        try {
            return ItemStack.of(TagParser.parseTag(text));
        } catch (CommandSyntaxException e) {
            return ItemStack.EMPTY;
        }
    }
}
