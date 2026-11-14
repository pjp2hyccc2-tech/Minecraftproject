package com.eternalrealm.client;

import com.eternalrealm.item.bag.Bag;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;

/**
 * The client's copy of the bag's back (カバンの奥, {@code com.eternalrealm.item.forge.BagBack}): the server sends the
 * {@value Bag#BACK_SIZE} stacks whenever they change. Read by the bag screen, the quick-use keys and the smithy.
 */
public final class ClientBagBack {
    /** The channel of {@code BagBack}. */
    static final String CHANNEL = "bag.back";
    private static final ItemStack[] STACKS = new ItemStack[Bag.BACK_SIZE];

    static {
        Arrays.fill(STACKS, ItemStack.EMPTY);
    }

    private ClientBagBack() {
    }

    static void register() {
        ClientChannels.register(CHANNEL, ClientBagBack::accept);
    }

    /** The stack of a back slot ({@value Bag#BACK_FIRST}..{@value Bag#BACK_LAST}), empty for anything else. */
    public static ItemStack at(int slot) {
        return Bag.isBackSlot(slot) ? STACKS[Bag.backIndex(slot)] : ItemStack.EMPTY;
    }

    /** How many of {@code item} wait in the back. */
    public static int count(Item item) {
        int count = 0;
        for (ItemStack stack : STACKS) {
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** The first back slot holding {@code item}, or -1. */
    public static int find(Item item) {
        for (int i = 0; i < STACKS.length; i++) {
            if (STACKS[i].is(item)) {
                return Bag.BACK_FIRST + i;
            }
        }
        return -1;
    }

    /** Occupied back slots. */
    public static int used() {
        int used = 0;
        for (ItemStack stack : STACKS) {
            if (!stack.isEmpty()) {
                used++;
            }
        }
        return used;
    }

    public static void clear() {
        Arrays.fill(STACKS, ItemStack.EMPTY);
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
            ItemStack stack = ItemStack.of(TagParser.parseTag(text));
            return stack.isEmpty() ? ItemStack.EMPTY : stack;
        } catch (CommandSyntaxException e) {
            return ItemStack.EMPTY;
        }
    }
}
