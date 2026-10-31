package com.nihonisshu.tabi.client;

import com.nihonisshu.tabi.client.screen.DialogueScreen;
import com.nihonisshu.tabi.client.screen.JournalScreen;
import com.nihonisshu.tabi.client.screen.ShopScreen;
import com.nihonisshu.tabi.client.screen.TicketScreen;
import com.nihonisshu.tabi.entity.VehicleEntity;
import com.nihonisshu.tabi.registry.TabiSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;

/** Client-only handlers, reached through DistExecutor so dedicated servers never load this class. */
public final class ClientHooks {
    private ClientHooks() {}

    public static void handle(String kind, CompoundTag data) {
        Minecraft mc = Minecraft.getInstance();
        switch (kind) {
            case "sync" -> ClientState.data = data;
            case "dialogue" -> mc.setScreen(new DialogueScreen(data));
            case "shop" -> {
                if (mc.screen instanceof ShopScreen s && s.shopId().equals(data.getString("id"))) {
                    s.refresh(data);
                } else {
                    mc.setScreen(new ShopScreen(data));
                }
            }
            case "tickets" -> mc.setScreen(new TicketScreen(data));
            case "guide" -> Hud.showGuide(data.getString("text"));
            case "look" -> {
                if (mc.player != null) {
                    float yaw = data.getFloat("yaw");
                    mc.player.setYRot(yaw);
                    mc.player.setYHeadRot(yaw);
                    mc.player.yRotO = yaw;
                    mc.player.setXRot(6f);
                }
            }
            default -> {
            }
        }
    }

    public static void openJournal() {
        Minecraft.getInstance().setScreen(new JournalScreen());
    }

    public static void vehicleTick(VehicleEntity v) {
        VehicleSounds.tick(v);
    }

    public static void vehicleStateChanged(VehicleEntity v, byte from, byte to) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (to == VehicleEntity.STOPPED && v.route() != null && v.route().type.equals("bus") && v.hasPassenger(mc.player)) {
            mc.level.playLocalSound(v.getX(), v.getY(), v.getZ(), TabiSounds.BUS_BELL.get(), SoundSource.NEUTRAL, 1f, 1f, false);
        }
    }
}
