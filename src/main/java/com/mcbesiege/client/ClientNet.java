package com.mcbesiege.client;

import com.mcbesiege.entity.MachineEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

/** Client handlers for server messages. */
public final class ClientNet {
    private ClientNet() {}

    public static void handle(String kind, CompoundTag d) {
        Minecraft mc = Minecraft.getInstance();
        switch (kind) {
            case "mstruct" -> {
                if (mc.level == null) return;
                Entity e = mc.level.getEntity(d.getInt("id"));
                if (e instanceof MachineEntity me) me.view.applyStructure(d);
            }
            case "control" -> {
                ClientState.pendingControl = d.getInt("id");
                ClientState.pendingControlTicks = 0;
            }
            case "missions" -> mc.setScreen(new MissionScreen(d));
            case "mission_status" -> ClientState.mission = d;
            default -> {}
        }
    }

    public static void state(int id, int version, double[] poses, byte[] flags) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(id);
        if (e instanceof MachineEntity me) me.view.applyState(version, poses, flags);
    }
}
