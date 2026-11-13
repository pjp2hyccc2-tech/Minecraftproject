package com.eternalrealm.client.chat;

import com.eternalrealm.core.ModInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * The latest chat messages for the small framed chat ({@code ui.chat.FantasyChat}): every message the vanilla chat
 * receives from the server (players, the game, commands; not the action bar), as it will be shown, and the game's own
 * client-side notices ({@link #local}). The conversation log the dialogue writes into the chat for re-reading goes only
 * to the vanilla chat (it was just read; the small chat stays quiet). Players' plain chat lines are shown as
 * "名前: 本文" with the name in gold. Client thread only.
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ChatLog {
    private static final int MAX = 100;
    private static final Deque<Entry> ENTRIES = new ArrayDeque<>();

    private ChatLog() {
    }

    /** One message: what is shown, when it came (GUI ticks), and its lines wrapped at the last width asked for. */
    public static final class Entry {
        private final Component shown;
        private final int tick;
        private int wrappedAt = -1;
        private List<FormattedCharSequence> lines = List.of();
        private int countedAt = -1;
        private int count;

        Entry(Component shown, int tick) {
            this.shown = shown;
            this.tick = tick;
        }

        public Component shown() {
            return shown;
        }

        public int tick() {
            return tick;
        }

        /** The message's lines at {@code width} (text pixels). */
        public List<FormattedCharSequence> lines(Font font, int width) {
            if (width != wrappedAt) {
                lines = font.split(shown, width);
                wrappedAt = width;
            }
            return lines;
        }

        /** How many lines the message takes at {@code width} (another width than {@link #lines}, cached apart). */
        public int count(Font font, int width) {
            if (width == wrappedAt) {
                return lines.size();
            }
            if (width != countedAt) {
                count = Math.max(1, font.split(shown, width).size());
                countedAt = width;
            }
            return count;
        }
    }

    /** What the vanilla chat is about to show (after every other mod has had its say). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onChat(ClientChatReceivedEvent event) {
        if (event instanceof ClientChatReceivedEvent.System system && system.isOverlay()) {
            return; // the action bar
        }
        Component message = event.getMessage();
        if (message != null) {
            add(restyle(message));
        }
    }

    /** A notice of the game's own on this client: into the vanilla chat and the small one. */
    public static void local(Component message) {
        Minecraft.getInstance().gui.getChat().addMessage(message);
        add(message);
    }

    private static void add(Component message) {
        ENTRIES.addFirst(new Entry(message, Minecraft.getInstance().gui.getGuiTicks()));
        while (ENTRIES.size() > MAX) {
            ENTRIES.removeLast();
        }
    }

    /** "<名前> 本文" (a player's plain chat) as "名前: 本文", the name gold unless it has a colour of its own. */
    static Component restyle(Component message) {
        if (!(message.getContents() instanceof TranslatableContents contents)
                || !"chat.type.text".equals(contents.getKey()) || contents.getArgs().length != 2
                || !message.getSiblings().isEmpty()) {
            return message;
        }
        Object name = contents.getArgs()[0];
        Object text = contents.getArgs()[1];
        MutableComponent who = name instanceof Component component ? component.copy()
                : Component.literal(String.valueOf(name));
        if (who.getStyle().getColor() == null) {
            who.withStyle(ChatFormatting.GOLD);
        }
        MutableComponent out = Component.empty().withStyle(message.getStyle()).append(who)
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY));
        out.append(text instanceof Component component ? component : Component.literal(String.valueOf(text)));
        return out;
    }

    /** The messages, newest first (a read-only view). */
    public static java.util.Collection<Entry> newestFirst() {
        return java.util.Collections.unmodifiableCollection(ENTRIES);
    }

    /** How many messages there are (for the open chat's frame). */
    public static int size() {
        return ENTRIES.size();
    }

    /** Leaving the world: no messages of the last one. */
    public static void clear() {
        ENTRIES.clear();
    }
}
