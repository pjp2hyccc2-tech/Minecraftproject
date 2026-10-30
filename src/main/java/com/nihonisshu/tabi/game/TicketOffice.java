package com.nihonisshu.tabi.game;

import com.nihonisshu.tabi.data.Route;
import com.nihonisshu.tabi.entity.VehicleEntity;
import com.nihonisshu.tabi.network.TabiNet;
import com.nihonisshu.tabi.registry.TabiItems;
import com.nihonisshu.tabi.registry.TabiSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Ticket machines, boarding checks (train tickets / bus fares) and ticket collection on arrival. */
public final class TicketOffice {
    private TicketOffice() {}

    public static void open(ServerPlayer p, BlockPos pos) {
        Vec3 at = Vec3.atCenterOf(pos);
        Route best = null;
        Route.Station from = null;
        double bd = 64 * 64;
        for (Route r : Route.all()) {
            if (!r.type.equals("train")) continue;
            for (Route.Station st : r.stations) {
                double d = r.pos(st.s).distanceToSqr(at);
                if (d < bd) {
                    bd = d;
                    best = r;
                    from = st;
                }
            }
        }
        if (best == null) {
            p.displayClientMessage(Component.literal("（この券売機は使えません）"), true);
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("route", best.id);
        tag.putString("routeName", best.name);
        tag.putInt("from", from.index);
        tag.putString("fromName", from.name);
        ListTag dests = new ListTag();
        for (Route.Station st : best.stations) {
            if (st.index == from.index) continue;
            CompoundTag d = new CompoundTag();
            d.putInt("idx", st.index);
            d.putString("name", st.name);
            d.putString("kana", st.kana);
            d.putInt("fare", best.fare(from, st));
            dests.add(d);
        }
        tag.put("dests", dests);
        tag.putInt("yen", PlayerData.yen(p));
        TabiNet.send(p, "tickets", tag);
    }

    public static void buy(ServerPlayer p, String routeId, int from, int to) {
        Route r = Route.get(routeId);
        if (r == null || from < 0 || to < 0 || from >= r.stations.size() || to >= r.stations.size() || from == to) return;
        Route.Station a = r.stations.get(from), b = r.stations.get(to);
        int fare = r.fare(a, b);
        if (!PlayerData.spend(p, fare)) return;
        ItemStack ticket = new ItemStack(TabiItems.TICKET.get());
        CompoundTag t = ticket.getOrCreateTag();
        t.putString("route", r.id);
        t.putInt("from", from);
        t.putInt("to", to);
        t.putString("fromName", a.name);
        t.putString("toName", b.name);
        t.putInt("fare", fare);
        if (!p.getInventory().add(ticket)) p.drop(ticket, false);
        p.displayClientMessage(Component.literal("切符を買いました：" + a.name + " → " + b.name + "（" + fare + "円）").withStyle(ChatFormatting.YELLOW), false);
        p.level().playSound(null, p.blockPosition(), TabiSounds.TICKET_GATE.get(), SoundSource.PLAYERS, 0.8f, 1.0f);
    }

    private static int findTicket(ServerPlayer p, String route, int from) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack st = p.getInventory().getItem(i);
            if (!st.is(TabiItems.TICKET.get()) || !st.hasTag()) continue;
            CompoundTag t = st.getTag();
            if (t != null && t.getString("route").equals(route) && (from < 0 || t.getInt("from") == from)) return i;
        }
        return -1;
    }

    public static boolean canBoard(ServerPlayer p, VehicleEntity v) {
        Route r = v.route();
        if (r == null) return false;
        if (r.type.equals("bus")) {
            if (!PlayerData.flag(p, "permit_town")) {
                p.displayClientMessage(Component.literal("お母さんに出かけることを伝えてから乗ろう"), true);
                return false;
            }
            int fare = (int) r.fareBase;
            if (!PlayerData.spend(p, fare)) return false;
            p.displayClientMessage(Component.literal("運賃 " + fare + "円 をお支払いしました。発車まで少々お待ちください。"), true);
            return true;
        }
        Route.Station here = v.currentStation();
        if (findTicket(p, r.id, here == null ? -1 : here.index) < 0) {
            p.displayClientMessage(Component.literal("切符を買ってから乗ろう（券売機は駅の中にあります）").withStyle(ChatFormatting.RED), true);
            return false;
        }
        PlayerData.unlock(p, "ride." + r.id);
        return true;
    }

    public static void onAlight(ServerPlayer p, VehicleEntity v) {
        Route r = v.route();
        Route.Station st = v.currentStation();
        if (r == null || st == null || v.getState() != VehicleEntity.STOPPED) return;
        PlayerData.unlock(p, "station." + st.id);
        if (!r.type.equals("train")) return;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack stack = p.getInventory().getItem(i);
            if (!stack.is(TabiItems.TICKET.get()) || !stack.hasTag()) continue;
            CompoundTag t = stack.getTag();
            if (t != null && t.getString("route").equals(r.id) && t.getInt("to") == st.index) {
                stack.shrink(1);
                p.displayClientMessage(Component.literal(st.name + "に着きました。切符を回収しました。").withStyle(ChatFormatting.AQUA), true);
                return;
            }
        }
    }
}
