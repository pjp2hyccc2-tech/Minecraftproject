package com.frostbound.client;

import com.frostbound.core.resources.ResourceType;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/** Handles data sent by the server (main thread). */
public final class ClientPackets {
    private ClientPackets() {
    }

    public static void handle(String kind, CompoundTag data) {
        Minecraft mc = Minecraft.getInstance();
        switch (kind) {
            case "sync" -> ClientCity.applySync(data);
            case "notice" -> {
                String k = data.getString("kind");
                int color = switch (k) {
                    case "GOOD" -> Theme.GOOD;
                    case "WARNING" -> Theme.WARN;
                    case "MAJOR" -> Theme.BAD;
                    default -> Theme.ORANGE;
                };
                ClientCity.toast(text(data), color);
                String sound = data.getString("sound");
                if (!sound.isEmpty()) {
                    Sounds.play(sound, 0.7f, 1.0f);
                } else if (k.equals("WARNING")) {
                    Sounds.play("alert", 0.5f, 1.0f);
                } else if (k.equals("MAJOR")) {
                    Sounds.play("event_major", 0.7f, 1.0f);
                }
            }
            case "result" -> {
                if (!data.getBoolean("ok")) {
                    ClientCity.toast(text(data), Theme.BAD);
                    Sounds.play("alert", 0.35f, 1.3f);
                } else {
                    ClientCity.toast(text(data), Theme.GOOD);
                }
            }
            case "open" -> open(mc, data);
            case "heat_grid" -> ClientCity.applyHeatGrid(data);
            case "citizens" -> ClientCity.applyCitizens(data);
            default -> {
            }
        }
    }

    /** Message text with resource ids inside arguments replaced by their names. */
    static Component text(CompoundTag msg) {
        String key = msg.getString("key");
        List<String> args = ClientCity.strings(msg, "args");
        if (key.equals("frostbound.reason.resources") || key.equals("frostbound.msg.expedition_returned")) {
            for (int i = 0; i < args.size(); i++) {
                args.set(i, resourceNames(args.get(i)));
            }
        }
        return ClientCity.tr(key, args);
    }

    private static String resourceNames(String s) {
        String out = s;
        for (ResourceType r : ResourceType.values()) {
            out = out.replaceAll("\\b" + r.id() + "\\b", Component.translatable(r.key()).getString());
        }
        return out;
    }

    private static void open(Minecraft mc, CompoundTag data) {
        String screen = data.getString("screen");
        Screen cur = mc.screen;
        switch (screen) {
            case "founding" -> {
                if (cur == null) {
                    mc.setScreen(new FoundingScreen(false));
                }
            }
            case "close" -> {
                if (cur instanceof FoundingScreen) {
                    mc.setScreen(null);
                }
            }
            case "management" -> {
                if (cur instanceof ManagementScreen ms && data.getString("tab").equals("building")) {
                    ms.showBuilding(data.getInt("id"));
                } else if (cur == null || cur instanceof ManagementScreen) {
                    mc.setScreen(new ManagementScreen(data.getString("tab"), data.contains("id") ? data.getInt("id") : -1));
                }
            }
            case "event" -> {
                if (ClientCity.event() != null && (cur == null || cur instanceof ManagementScreen)) {
                    mc.setScreen(new EventScreen(cur));
                }
            }
            case "ending" -> {
                if (cur == null || cur instanceof ManagementScreen || cur instanceof EventScreen) {
                    mc.setScreen(new EndingScreen(cur instanceof ManagementScreen ? cur : null));
                }
            }
            default -> {
            }
        }
    }
}
