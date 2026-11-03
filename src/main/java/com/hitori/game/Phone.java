package com.hitori.game;

import com.hitori.item.GameItem;
import com.hitori.registry.ModItems;
import com.hitori.registry.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Your phone (docs/DESIGN.md §3.6): the time, a weak flashlight (the held item becomes {@code phone_light}: the shader
 * lights it), the silent switch (unless silenced it vibrates audibly: noise 7), messages from a friend —
 * 「ほんとにやるの？」「やめとけって」… — and at 3:33 a call from your own number.
 */
public final class Phone {
    /** Messages: in-game clock (seconds) → lang key msg.hitori.n. */
    private static final double[] AT = {
            2 * 3600 + 24 * 60, 2 * 3600 + 38 * 60, 2 * 3600 + 51 * 60, 3 * 3600 + 8 * 60, 3 * 3600 + 49 * 60, 4 * 3600 + 11 * 60,
            4 * 3600 + 23 * 60, 4 * 3600 + 38 * 60};
    private static final int RING_TICKS = 20 * 22;

    private Phone() {}

    public static void reset() {
    }

    public static boolean hasPhone(ServerPlayer p) {
        return GameManager.has(p, ModItems.PHONE.get()) || GameManager.has(p, ModItems.PHONE_LIGHT.get());
    }

    public static void tick(ServerLevel level, GameData d) {
        if (d.phase == GameData.PHASE_ENDING || d.phase == GameData.PHASE_IDLE) return;
        for (int i = 0; i < AT.length; i++) {
            int bit = 1 << i;
            if ((d.messages & bit) == 0 && d.clock >= AT[i]) {
                d.messages |= bit;
                d.setDirty();
                // the friend's "I'm at your door" only makes sense while the ritual runs
                if (i >= 5 && !d.ritualRunning()) continue;
                for (ServerPlayer p : GameManager.players(level)) if (GameManager.inGame(p) && hasPhone(p)) message(p, d, "msg.hitori." + (i + 1));
            }
        }
        // 3:33: your own number
        if (d.ritualRunning() && d.clock >= Clock.CALL && d.flag("call_333")) {
            for (ServerPlayer p : GameManager.players(level)) {
                if (!GameManager.inGame(p) || !hasPhone(p)) continue;
                PlayerState ps = GameManager.state(p);
                ps.ringTicks = 0;
                ps.answered = false;
                GameManager.event(p, GameManager.EV_CALL, 1, "");
            }
        }
        for (ServerPlayer p : GameManager.players(level)) {
            if (!GameManager.inGame(p)) continue;
            PlayerState ps = GameManager.state(p);
            if (ps.ringTicks < 0) continue;
            ps.ringTicks++;
            if (!d.phoneSilent && ps.ringTicks % 40 == 1) {
                GameManager.privateSound(p, ModSounds.PHONE_RING_OWN.get(), p.getEyePosition(), 0.9f, 1.0f);
                p.level().playSound(null, p.getX(), p.getY() + 1.0, p.getZ(), ModSounds.PHONE_VIBRATE.get(), net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.0f);
                NoiseSystem.emit(level, p.getEyePosition(), 7.0f, "phone", p);
            }
            if (ps.ringTicks >= RING_TICKS) {
                ps.ringTicks = -1;
                GameManager.event(p, GameManager.EV_CALL, 0, "missed");
            }
        }
    }

    private static void message(ServerPlayer p, GameData d, String key) {
        GameManager.event(p, GameManager.EV_MESSAGE, d.phoneSilent ? 1 : 0, key);
        if (!d.phoneSilent) {
            p.level().playSound(null, p.getX(), p.getY() + 1.0, p.getZ(), ModSounds.PHONE_VIBRATE.get(), net.minecraft.sounds.SoundSource.PLAYERS, 0.7f, 1.0f);
            NoiseSystem.emit(p.serverLevel(), p.getEyePosition(), 7.0f, "phone", p);
        }
    }

    /** From the phone screen: 0 silent switch, 1 flashlight, 2 answer, 3 decline. */
    public static void action(ServerPlayer p, int what) {
        if (!GameManager.inGame(p) || !hasPhone(p)) return;
        GameData d = GameData.get(p.server);
        PlayerState ps = GameManager.state(p);
        switch (what) {
            case 0 -> {
                d.phoneSilent = !d.phoneSilent;
                d.setDirty();
                GameManager.privateSound(p, ModSounds.PHONE_TAP.get(), p.getEyePosition(), 0.5f, 1.0f);
            }
            case 1 -> toggleLight(p, ps);
            case 2 -> {
                if (ps.ringTicks < 0) return;
                ps.ringTicks = -1;
                ps.answered = true;
                GameManager.event(p, GameManager.EV_CALL, 2, "");
                // nothing on the line; breathing; then a woman's voice, very close
                GameManager.say(p, "phone", "say.hitori.call.1", 70);
                Director.laterPublic(70, () -> GameManager.privateSound(p, ModSounds.WOMAN_BREATH.get(), p.getEyePosition(), 0.9f, 1.0f));
                Director.laterPublic(130, () -> {
                    GameManager.privateSound(p, ModSounds.WOMAN_WHISPER.get(), p.getEyePosition(), 1.0f, 1.0f);
                    GameManager.say(p, "phone", "say.hitori.call.2", 70);
                    GameManager.addFear(p, 0.35f);
                });
                Director.laterPublic(210, () -> GameManager.event(p, GameManager.EV_CALL, 0, "ended"));
            }
            case 3 -> {
                if (ps.ringTicks < 0) return;
                ps.ringTicks = -1;
                GameManager.event(p, GameManager.EV_CALL, 0, "declined");
            }
            default -> {
            }
        }
    }

    /** The weak flashlight: the held phone becomes phone_light (and back). */
    public static void toggleLight(ServerPlayer p, PlayerState ps) {
        for (InteractionHand h : InteractionHand.values()) {
            ItemStack s = p.getItemInHand(h);
            GameItem.Kind k = GameItem.kindOf(s);
            if (k == GameItem.Kind.PHONE || k == GameItem.Kind.PHONE_LIGHT) {
                GameManager.transform(p, s, k == GameItem.Kind.PHONE ? ModItems.PHONE_LIGHT.get() : ModItems.PHONE.get());
                ps.phoneLight = k == GameItem.Kind.PHONE;
                GameManager.privateSound(p, ModSounds.PHONE_TAP.get(), p.getEyePosition(), 0.5f, 1.2f);
                return;
            }
        }
        GameManager.thought(p, "thought.hitori.hold_phone");
    }
}
