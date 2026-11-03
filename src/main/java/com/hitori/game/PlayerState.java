package com.hitori.game;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * One player's night: nerves, body (breath, the salt water in the mouth), hiding and peeking, the camera app, the
 * timed ritual actions, cinematic locks. The mouth, the breath and the hiding spot persist in the player's data
 * (save and quit inside the closet with salt water in your mouth: still there).
 */
public final class PlayerState {
    // ---- nerves (0..1)
    public float fear = 0.15f;
    public float danger;
    public int syncTimer;
    public int saveTimer;

    // ---- body
    /** Salt water in the mouth (docs §3, "Mouth"). */
    public float mouth;
    public boolean breathHolding;
    public int breathTicks;
    public float breathGauge = 1.0f;
    public int heavyBreathIn = 40;
    public double stepAccum;
    public double lastX, lastZ;
    public int airTicks;
    public double airTopY;

    // ---- where
    public String room = "";

    // ---- hiding ("oshiire" = the 押し入れ, "western" = the western room's closet)
    @Nullable public String hideSpot;
    @Nullable public BlockPos hidePos;
    @Nullable public Vec3 hideAt;
    public boolean peeking;
    public int peekTicks;
    public int hiddenTicks;
    /** The salt water was lost (gasp, speaking) during this hide: the rule is broken once. */
    public boolean lostWaterThisHide;

    // ---- the camera app: the camera number viewed (0 = closed), the device (0 laptop, 1 phone)
    public int appCamera;
    public int appDevice;
    public int appTicks;
    /** The camera the player looked at before (the woman comes closer when you come back to her feed). */
    public int lastCamera;

    // ---- timed actions (cutting, sewing, counting...)
    @Nullable public String action;
    public int actionTicks;
    public int actionLength;
    /** Eyes closed while counting. */
    public boolean eyesClosed;

    // ---- cinematic
    public boolean locked;
    @Nullable public Vec3 lockPos;
    public int caughtTicks = -1;
    public int endingTicks = -1;
    public int sotoTicks = -1;

    // ---- the phone
    public boolean phoneLight;
    public int ringTicks = -1;
    public boolean answered;

    // ---- the doll
    /** Play tick the player last saw the doll (eyes or camera). */
    public long sawDollAt;

    // ---- misc
    public boolean shaders;
    public int hintIn = 100;

    // ---- debug
    public float forceFear = -1.0f;
    public float forceDanger = -1.0f;
    public int forceTicks;

    public boolean hiding() {
        return hideSpot != null;
    }

    public boolean inCloset() {
        return "oshiire".equals(hideSpot);
    }

    public boolean inApp() {
        return appCamera > 0;
    }

    public boolean caught() {
        return caughtTicks >= 0;
    }

    public boolean busy() {
        return action != null;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putFloat("Mouth", mouth);
        t.putFloat("Fear", fear);
        if (hideSpot != null) t.putString("Hide", hideSpot);
        if (hidePos != null) t.putLong("HidePos", hidePos.asLong());
        t.putBoolean("PhoneLight", phoneLight);
        return t;
    }

    public void load(CompoundTag t) {
        mouth = t.getFloat("Mouth");
        if (t.contains("Fear")) fear = t.getFloat("Fear");
        if (t.contains("Hide")) hideSpot = t.getString("Hide");
        if (t.contains("HidePos")) hidePos = BlockPos.of(t.getLong("HidePos"));
        phoneLight = t.getBoolean("PhoneLight");
    }
}
