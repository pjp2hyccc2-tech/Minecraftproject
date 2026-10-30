package com.nihonisshu.tabi.client;

import com.nihonisshu.tabi.entity.VehicleEntity;
import com.nihonisshu.tabi.registry.TabiSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/** Rumble loop that follows a moving train/bus and the "gatan-goton" of rail joints. */
public final class VehicleSounds {
    private VehicleSounds() {}

    private static final double RAIL_LENGTH = 25.0;

    public static void tick(VehicleEntity v) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        boolean bus = v.route() != null && v.route().type.equals("bus");
        float speed = Math.abs(v.curS - v.prevS);
        double dist = mc.player.distanceTo(v);
        if (speed > 0.02f && dist < 96 && !(v.clientSound instanceof RunSound rs && !rs.isStopped())) {
            RunSound s = new RunSound(v, bus);
            v.clientSound = s;
            mc.getSoundManager().play(s);
        }
        if (!bus && speed > 0.05f && dist < 48) {
            double a = Math.floor(v.prevS / RAIL_LENGTH), b = Math.floor(v.curS / RAIL_LENGTH);
            if (a != b) {
                boolean riding = v.hasPassenger(mc.player);
                float vol = riding ? 0.55f : 0.35f;
                mc.level.playLocalSound(v.getX(), v.getY(), v.getZ(), TabiSounds.TRAIN_JOINT.get(), SoundSource.NEUTRAL,
                        vol, 0.85f + Mth.clamp(speed, 0f, 1f) * 0.3f, false);
            }
        }
    }

    public static final class RunSound extends AbstractTickableSoundInstance {
        private final VehicleEntity v;

        RunSound(VehicleEntity v, boolean bus) {
            super(bus ? TabiSounds.BUS_RUN.get() : TabiSounds.TRAIN_RUN.get(), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.v = v;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01f;
            this.x = v.getX();
            this.y = v.getY();
            this.z = v.getZ();
        }

        @Override
        public void tick() {
            if (v.isRemoved()) {
                stop();
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            float speed = Math.abs(v.curS - v.prevS);
            boolean riding = mc.player != null && v.hasPassenger(mc.player);
            this.x = riding && mc.player != null ? mc.player.getX() : v.getX();
            this.y = riding && mc.player != null ? mc.player.getY() : v.getY();
            this.z = riding && mc.player != null ? mc.player.getZ() : v.getZ();
            if (speed < 0.01f) {
                this.volume = Math.max(0f, this.volume - 0.05f);
                if (this.volume <= 0.001f) stop();
                return;
            }
            this.volume = Mth.clamp(speed * (riding ? 0.9f : 0.7f), 0.05f, 0.8f);
            this.pitch = 0.75f + Mth.clamp(speed, 0f, 1f) * 0.45f;
        }
    }
}
