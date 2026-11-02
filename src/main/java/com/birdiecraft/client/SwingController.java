package com.birdiecraft.client;

import com.birdiecraft.golf.engine.Club;
import com.birdiecraft.golf.engine.GaugeMath;
import com.birdiecraft.golf.engine.GolfUnits;
import com.birdiecraft.golf.engine.Lie;
import com.birdiecraft.golf.engine.PlayStyle;
import com.birdiecraft.golf.engine.ShotCalculator;
import com.birdiecraft.golf.engine.ShotInput;
import com.birdiecraft.golf.engine.ShotMode;
import com.birdiecraft.item.GolfClubItem;
import com.birdiecraft.match.MatchView;
import com.birdiecraft.network.C2SClubPacket;
import com.birdiecraft.network.C2SShotPacket;
import com.birdiecraft.network.GolfNetwork;
import com.birdiecraft.registry.ModSounds;
import com.birdiecraft.world.LevelTerrain;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The 3-click swing gauge.
 * Click 1 starts the marker, click 2 sets the power, click 3 (inside the impact zone near the
 * left end) sets the accuracy. Timing uses the real clock, not ticks, so it is frame accurate.
 */
public final class SwingController {
    public enum Phase { IDLE, RISING, RETURNING, SENT }

    public static Phase phase = Phase.IDLE;
    public static Club club = Club.DRIVER;
    public static ShotMode mode = ShotMode.NORMAL;
    public static int spinX;
    public static int spinY;
    public static int puttRange = 1;
    public static double power;
    public static double zoneHalf = 0.06;
    public static Lie lie = Lie.FAIRWAY;
    /** Result of the last swing for the HUD flash: deviation and time. */
    public static double lastDeviation;
    public static long lastShotMs;

    private static long riseStart;
    private static long returnStart;
    private static long sentAt;

    private SwingController() {
    }

    // ------------------------------------------------------------------ context

    public static boolean holdingClub() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p != null && p.getMainHandItem().getItem() instanceof GolfClubItem;
    }

    /** Whether the gauge may be used right now (my turn in a match, or practice). */
    public static boolean canSwing() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || !holdingClub()) return false;
        MatchView m = ClientGolfState.match;
        if (m != null) return m.isMyTurn() && !ClientGolfState.isFlying(m.myId);
        return !ClientGolfState.practiceFlightActive();
    }

    public static boolean isActive() {
        return phase == Phase.RISING || phase == Phase.RETURNING;
    }

    /** Ball position the shot starts from. */
    public static Vec3 origin() {
        Minecraft mc = Minecraft.getInstance();
        MatchView m = ClientGolfState.match;
        if (m != null) {
            MatchView.PView me = m.me();
            if (me != null) return new Vec3(me.bx, me.by, me.bz);
        }
        if (mc.player == null) return Vec3.ZERO;
        return mc.player.position();
    }

    public static PlayStyle style() {
        MatchView m = ClientGolfState.match;
        if (m != null && m.me() != null) return PlayStyle.byId(m.me().style);
        if (ClientGolfState.menu != null) return PlayStyle.byId(ClientGolfState.menu.style);
        return PlayStyle.BALANCED;
    }

    public static int powerShotsLeft() {
        MatchView m = ClientGolfState.match;
        if (m != null && m.me() != null) return m.me().powerShots;
        return 99;
    }

    /** Distance to the pin in blocks, or -1 outside a match. */
    public static double pinDistance() {
        MatchView m = ClientGolfState.match;
        if (m == null) return -1;
        Vec3 o = origin();
        return GolfUnits.horizontalDistance(o.x, o.z, m.cup.getX() + 0.5, m.cup.getZ() + 0.5);
    }

    // ------------------------------------------------------------------ gauge

    public static double gaugePosition() {
        long now = System.nanoTime();
        return switch (phase) {
            case RISING -> Math.min(1.0, (now - riseStart) / 1.0e9 * GaugeMath.riseSpeed(club, mode));
            case RETURNING -> power - (now - returnStart) / 1.0e9 * GaugeMath.returnSpeed(club, mode);
            default -> 0.0;
        };
    }

    public static void click() {
        switch (phase) {
            case IDLE -> {
                if (!canSwing()) return;
                if (mode == ShotMode.POWER && powerShotsLeft() <= 0) mode = ShotMode.NORMAL;
                updateLie();
                phase = Phase.RISING;
                riseStart = System.nanoTime();
                tickSound(0.8f);
            }
            case RISING -> {
                power = Math.max(0.01, gaugePosition());
                phase = Phase.RETURNING;
                returnStart = System.nanoTime();
                tickSound(1.0f);
            }
            case RETURNING -> fire(gaugePosition());
            default -> {
            }
        }
    }

    public static void cancel() {
        if (phase != Phase.SENT) phase = Phase.IDLE;
    }

    private static void fire(double pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            phase = Phase.IDLE;
            return;
        }
        double dev = GaugeMath.deviationAt(pos, zoneHalf);
        ShotInput in = new ShotInput(club, mode, mc.player.getYRot(), power, dev, spinX, spinY, puttRange);
        GolfNetwork.sendToServer(new C2SShotPacket(in));
        lastDeviation = dev;
        lastShotMs = System.currentTimeMillis();
        phase = Phase.SENT;
        sentAt = System.currentTimeMillis();
        tickSound(1.25f);
        if (mode == ShotMode.POWER) mode = ShotMode.NORMAL;
    }

    public static void tick() {
        if (phase == Phase.RISING) {
            if (!canSwing()) {
                phase = Phase.IDLE;
                return;
            }
            double rise = GaugeMath.riseSpeed(club, mode);
            if ((System.nanoTime() - riseStart) / 1.0e9 * rise >= 1.0) {
                // overflow: the power locks at 100% and the marker comes back
                power = 1.0;
                phase = Phase.RETURNING;
                returnStart = riseStart + (long) (1.0e9 / rise);
            }
        } else if (phase == Phase.RETURNING) {
            if (!canSwing()) {
                phase = Phase.IDLE;
                return;
            }
            if (gaugePosition() <= GaugeMath.MISS_POSITION) fire(GaugeMath.MISS_POSITION);
        } else if (phase == Phase.SENT) {
            if (System.currentTimeMillis() - sentAt > 1500) phase = Phase.IDLE;
        } else if (holdingClub() && Minecraft.getInstance().level != null && Minecraft.getInstance().player.tickCount % 5 == 0) {
            updateLie();
        }
        if (club.isPutter() && phase == Phase.IDLE) autoPuttRange();
    }

    private static boolean manualPuttRange;

    private static void autoPuttRange() {
        if (manualPuttRange) return;
        double d = pinDistance();
        if (d >= 0) puttRange = ShotCalculator.autoPuttRange(d);
    }

    public static void updateLie() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 o = origin();
        lie = new LevelTerrain(mc.level).lieUnder(o.x, o.y, o.z);
        zoneHalf = GaugeMath.zoneHalfWidth(club, mode, lie, style());
    }

    // ------------------------------------------------------------------ settings

    public static void setClub(Club c) {
        if (isActive()) return;
        club = c;
        if (c.isPutter()) {
            mode = ShotMode.NORMAL;
            spinX = 0;
            spinY = 0;
            manualPuttRange = false;
        }
        updateLie();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            ItemStack stack = mc.player.getMainHandItem();
            if (stack.getItem() instanceof GolfClubItem) GolfClubItem.setClub(stack, c);
            GolfNetwork.sendToServer(new C2SClubPacket(c));
        }
        tickSound(1.4f);
    }

    public static void cycleClub(int dir) {
        setClub(dir > 0 ? club.next() : club.prev());
    }

    public static void cycleMode() {
        if (isActive() || club.isPutter()) return;
        ShotMode next = ShotMode.VALUES[(mode.ordinal() + 1) % ShotMode.VALUES.length];
        if (next == ShotMode.POWER && powerShotsLeft() <= 0) next = ShotMode.APPROACH;
        mode = next;
        updateLie();
        tickSound(1.2f);
    }

    public static void spin(int dx, int dy) {
        if (isActive()) return;
        if (club.isPutter()) {
            if (dy != 0) {
                manualPuttRange = true;
                puttRange = Math.max(0, Math.min(ShotCalculator.PUTT_RANGES_YARDS.length - 1, puttRange + dy));
                tickSound(1.3f);
            }
            return;
        }
        spinX = Math.max(-2, Math.min(2, spinX + dx));
        spinY = Math.max(-2, Math.min(2, spinY + dy));
        tickSound(1.3f);
    }

    /** Picks a sensible club when a turn starts. */
    public static void onTurnStart() {
        phase = Phase.IDLE;
        mode = ShotMode.NORMAL;
        spinX = 0;
        spinY = 0;
        manualPuttRange = false;
        updateLie();
        double d = pinDistance();
        if (d < 0) return;
        Club pick;
        if (lie == Lie.GREEN || (lie == Lie.FRINGE && d < 9)) {
            pick = Club.PUTTER;
        } else {
            pick = Club.SW;
            for (Club c : Club.VALUES) {
                if (c.isPutter()) continue;
                double carry = ShotCalculator.maxCarryBlocks(c, ShotMode.NORMAL, style()) * lie.midPower(c);
                if (carry <= d * 1.04) {
                    pick = c;
                    break;
                }
            }
            if (lie == Lie.TEE && d > ShotCalculator.maxCarryBlocks(Club.DRIVER, ShotMode.NORMAL, style())) pick = Club.DRIVER;
            double swCarry = ShotCalculator.maxCarryBlocks(Club.SW, ShotMode.NORMAL, style()) * lie.midPower(Club.SW);
            if (pick == Club.SW && d < swCarry * 0.5) mode = ShotMode.APPROACH;
        }
        setClub(pick);
        aimAtPin();
    }

    public static void aimAtPin() {
        Minecraft mc = Minecraft.getInstance();
        MatchView m = ClientGolfState.match;
        if (mc.player == null || m == null) return;
        Vec3 o = origin();
        float yaw = (float) GolfUnits.yawTo(o.x, o.z, m.cup.getX() + 0.5, m.cup.getZ() + 0.5);
        mc.player.setYRot(yaw);
        mc.player.yRotO = yaw;
        mc.player.setYHeadRot(yaw);
    }

    public static Component clubName() {
        return Component.translatable(club.translationKey());
    }

    private static void tickSound(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.GAUGE_TICK.get(), pitch, 0.6f));
    }
}
