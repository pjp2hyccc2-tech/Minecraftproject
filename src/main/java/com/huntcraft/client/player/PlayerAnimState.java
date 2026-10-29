package com.huntcraft.client.player;

import com.huntcraft.gear.weapon.action.ActionDef;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Client side animation state of one player (local or remote). */
@OnlyIn(Dist.CLIENT)
public final class PlayerAnimState {
    private static final Map<UUID, PlayerAnimState> STATES = new HashMap<>();

    public static PlayerAnimState get(Player p) {
        PlayerAnimState s = STATES.computeIfAbsent(p.getUUID(), k -> new PlayerAnimState());
        s.lastSeen = p.level().getGameTime();
        return s;
    }

    public static void clear() {
        STATES.clear();
    }

    public static void tickAll(long gameTime) {
        STATES.entrySet().removeIf(e -> gameTime - e.getValue().lastSeen > 1200);
        for (PlayerAnimState s : STATES.values()) s.tick(gameTime);
    }

    // ---- current action
    @Nullable
    public ActionDef action;
    public long actionStart;
    public int actionOffset;
    /** Hit-stop: ticks still frozen, and the accumulated lag that is caught up afterwards. */
    public float freeze;
    public float lag;
    // ---- previous action, blended out for a few ticks
    @Nullable
    public ActionDef prevAction;
    public float prevTime;
    public long prevEnd = -100;
    // ---- remote stance mirror (local player reads WeaponState directly)
    public int useMode;
    public int flags;
    public ItemStack sheathed = ItemStack.EMPTY;
    // ---- misc
    public final WeaponTrail trail = new WeaponTrail();
    public long lastSeen;
    public long lastDodgeStart = -1;
    public int lastHurtTime;

    public void startAction(@Nullable ActionDef def, long gameTime, int offset) {
        if (action != null) {
            prevAction = action;
            prevTime = rawElapsed(gameTime, 0.0F);
            prevEnd = gameTime;
        }
        action = def;
        actionStart = gameTime;
        actionOffset = offset;
        freeze = 0;
        lag = 0;
        if (def == null) trail.stop();
    }

    public void endAction(long gameTime) {
        startAction(null, gameTime, 0);
    }

    /** Ticks since the action started (no hit-stop). */
    public float rawElapsed(long gameTime, float partialTick) {
        return gameTime - actionStart + actionOffset + partialTick;
    }

    /** Animation time in ticks including hit-stop (frozen while `freeze` > 0, then catches up). */
    public float actionTime(long gameTime, float partialTick) {
        if (action == null) return 0;
        float t = freeze > 0 ? gameTime - actionStart + actionOffset : rawElapsed(gameTime, partialTick);
        return Math.max(0, t - lag);
    }

    public boolean actionActive(long gameTime) {
        return action != null && rawElapsed(gameTime, 0) < action.ticks + 0.01F;
    }

    public void hitStop(int ticks) {
        freeze = Math.max(freeze, ticks);
    }

    private void tick(long gameTime) {
        if (action != null && rawElapsed(gameTime, 0) >= action.ticks) {
            // ended naturally: fade out from its last frame
            prevAction = action;
            prevTime = action.ticks;
            prevEnd = gameTime;
            action = null;
            freeze = 0;
            lag = 0;
        }
        if (freeze > 0) {
            freeze -= 1;
            lag += 1;
        } else if (lag > 0) {
            lag = Math.max(0, lag - 0.5F);
        }
        trail.tick(gameTime);
    }
}
