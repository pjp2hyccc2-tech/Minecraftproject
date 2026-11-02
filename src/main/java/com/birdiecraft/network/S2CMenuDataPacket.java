package com.birdiecraft.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Data for the golf menu: courses, presets, open lobbies and the player's settings. */
public final class S2CMenuDataPacket {
    public record CourseEntry(String id, Component title, int holes, int par, String presetId) {
    }

    public record PresetEntry(String id, int stars, int theme, boolean built, int par) {
    }

    public record LobbyEntry(int matchId, String host, Component course, int players, int holes) {
    }

    public final List<CourseEntry> courses;
    public final List<PresetEntry> presets;
    public final List<LobbyEntry> lobbies;
    public final int style;
    public final boolean canBuild;
    public final boolean inMatch;
    public final boolean isLobbyHost;
    public final boolean building;

    public S2CMenuDataPacket(List<CourseEntry> courses, List<PresetEntry> presets, List<LobbyEntry> lobbies,
                             int style, boolean canBuild, boolean inMatch, boolean isLobbyHost, boolean building) {
        this.courses = courses;
        this.presets = presets;
        this.lobbies = lobbies;
        this.style = style;
        this.canBuild = canBuild;
        this.inMatch = inMatch;
        this.isLobbyHost = isLobbyHost;
        this.building = building;
    }

    public S2CMenuDataPacket(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        courses = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            courses.add(new CourseEntry(buf.readUtf(), buf.readComponent(), buf.readVarInt(), buf.readVarInt(), buf.readUtf()));
        }
        n = buf.readVarInt();
        presets = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            presets.add(new PresetEntry(buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt()));
        }
        n = buf.readVarInt();
        lobbies = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            lobbies.add(new LobbyEntry(buf.readVarInt(), buf.readUtf(), buf.readComponent(), buf.readVarInt(), buf.readVarInt()));
        }
        style = buf.readVarInt();
        canBuild = buf.readBoolean();
        inMatch = buf.readBoolean();
        isLobbyHost = buf.readBoolean();
        building = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(courses.size());
        for (CourseEntry c : courses) {
            buf.writeUtf(c.id());
            buf.writeComponent(c.title());
            buf.writeVarInt(c.holes());
            buf.writeVarInt(c.par());
            buf.writeUtf(c.presetId());
        }
        buf.writeVarInt(presets.size());
        for (PresetEntry p : presets) {
            buf.writeUtf(p.id());
            buf.writeVarInt(p.stars());
            buf.writeVarInt(p.theme());
            buf.writeBoolean(p.built());
            buf.writeVarInt(p.par());
        }
        buf.writeVarInt(lobbies.size());
        for (LobbyEntry l : lobbies) {
            buf.writeVarInt(l.matchId());
            buf.writeUtf(l.host());
            buf.writeComponent(l.course());
            buf.writeVarInt(l.players());
            buf.writeVarInt(l.holes());
        }
        buf.writeVarInt(style);
        buf.writeBoolean(canBuild);
        buf.writeBoolean(inMatch);
        buf.writeBoolean(isLobbyHost);
        buf.writeBoolean(building);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.birdiecraft.client.ClientPacketHandler.onMenuData(this));
    }
}
