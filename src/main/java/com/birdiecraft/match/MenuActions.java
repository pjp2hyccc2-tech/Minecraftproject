package com.birdiecraft.match;

import com.birdiecraft.config.GolfConfig;
import com.birdiecraft.course.Course;
import com.birdiecraft.course.CourseBuilder;
import com.birdiecraft.course.CoursePreset;
import com.birdiecraft.course.GolfCourseData;
import com.birdiecraft.golf.engine.Difficulty;
import com.birdiecraft.golf.engine.PlayStyle;
import com.birdiecraft.network.C2SMenuActionPacket;
import com.birdiecraft.network.GolfNetwork;
import com.birdiecraft.network.S2CMenuDataPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/** Server side of the golf menu. */
public final class MenuActions {
    private MenuActions() {
    }

    public static boolean canBuild(ServerPlayer player) {
        return player.hasPermissions(2) || GolfConfig.ALLOW_BUILD_WITHOUT_OP.get()
                || player.getServer() != null && player.getServer().isSingleplayerOwner(player.getGameProfile());
    }

    public static void handle(ServerPlayer player, C2SMenuActionPacket msg) {
        GolfCourseData data = GolfCourseData.get(player.getServer());
        switch (msg.action) {
            case C2SMenuActionPacket.REQUEST_MENU -> {
            }
            case C2SMenuActionPacket.START_SINGLE, C2SMenuActionPacket.START_VS, C2SMenuActionPacket.CREATE_LOBBY -> {
                Course course = data.get(msg.arg);
                if (course == null) {
                    player.sendSystemMessage(Component.translatable("msg.birdiecraft.no_course", msg.arg).withStyle(ChatFormatting.RED));
                    break;
                }
                MatchMode mode = msg.action == C2SMenuActionPacket.START_SINGLE ? MatchMode.SINGLE
                        : msg.action == C2SMenuActionPacket.START_VS ? MatchMode.VS_CPU : MatchMode.MULTI;
                int start = Math.max(0, msg.intArg(0, 0));
                int count = Math.max(1, msg.intArg(1, course.holes.size()));
                int cpus = msg.intArg(2, mode == MatchMode.VS_CPU ? 1 : 0);
                Difficulty diff = Difficulty.byId(msg.intArg(3, 1));
                MatchManager.create(player, mode, course, start, count, cpus, diff);
            }
            case C2SMenuActionPacket.JOIN -> MatchManager.join(player, msg.intArg(0, -1));
            case C2SMenuActionPacket.LEAVE -> MatchManager.leave(player);
            case C2SMenuActionPacket.BEGIN -> MatchManager.begin(player);
            case C2SMenuActionPacket.ADD_CPU -> MatchManager.addCpu(player, Difficulty.byId(msg.intArg(0, 1)));
            case C2SMenuActionPacket.SET_STYLE -> {
                PlayStyle style = PlayStyle.byId(msg.intArg(0, 0));
                PlayerProfiles.setStyle(player, style);
                GolfMatch m = MatchManager.matchOf(player);
                if (m != null && m.state == MatchState.LOBBY) {
                    Participant p = m.participant(player.getUUID());
                    if (p != null) p.style = style;
                    m.sync();
                }
            }
            case C2SMenuActionPacket.BUILD_PRESET -> {
                CoursePreset preset = CoursePreset.byId(msg.arg);
                if (preset == null) break;
                if (!canBuild(player)) {
                    player.sendSystemMessage(Component.translatable("msg.birdiecraft.no_permission").withStyle(ChatFormatting.RED));
                    break;
                }
                if (CourseBuilder.isBusy()) {
                    player.sendSystemMessage(Component.translatable("msg.birdiecraft.builder_busy").withStyle(ChatFormatting.RED));
                    break;
                }
                player.sendSystemMessage(Component.translatable("msg.birdiecraft.build_start", Component.translatable(preset.translationKey()))
                        .withStyle(ChatFormatting.YELLOW));
                CourseBuilder.buildPreset(player, preset);
            }
            default -> {
            }
        }
        sendMenu(player);
    }

    public static void sendMenu(ServerPlayer player) {
        GolfCourseData data = GolfCourseData.get(player.getServer());
        List<S2CMenuDataPacket.CourseEntry> courses = new ArrayList<>();
        for (Course c : data.all()) {
            courses.add(new S2CMenuDataPacket.CourseEntry(c.id, c.title(), c.holes.size(), c.totalPar(), c.presetId));
        }
        List<S2CMenuDataPacket.PresetEntry> presets = new ArrayList<>();
        for (CoursePreset p : CoursePreset.VALUES) {
            presets.add(new S2CMenuDataPacket.PresetEntry(p.id, p.stars, p.theme.ordinal(), data.byPreset(p.id) != null, p.totalPar()));
        }
        List<S2CMenuDataPacket.LobbyEntry> lobbies = new ArrayList<>();
        for (GolfMatch m : MatchManager.openLobbies()) {
            ServerPlayer host = m.host != null ? player.getServer().getPlayerList().getPlayer(m.host) : null;
            lobbies.add(new S2CMenuDataPacket.LobbyEntry(m.id, host != null ? host.getGameProfile().getName() : "?",
                    m.course.title(), m.participants.size(), m.holeCount));
        }
        GolfMatch mine = MatchManager.matchOf(player);
        boolean lobbyHost = mine != null && mine.state == MatchState.LOBBY && player.getUUID().equals(mine.host);
        GolfNetwork.sendTo(player, new S2CMenuDataPacket(courses, presets, lobbies, PlayerProfiles.style(player).ordinal(),
                canBuild(player), mine != null, lobbyHost, CourseBuilder.isBusy()));
    }
}
