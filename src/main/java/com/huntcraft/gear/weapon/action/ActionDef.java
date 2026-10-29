package com.huntcraft.gear.weapon.action;

import com.huntcraft.core.DamageKind;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * One combat action (attack, reaction, emote...) = an animation plus its tick timeline, authored in
 * tools/models/hunter_anims.py and exported to assets/huntcraft/hcmodels/hunter_actions.json.
 * The server runs the timeline ({@link CombatActions}); clients play the animation of the same name
 * (client/player/HunterAnimator) stretched to {@link #ticks}.
 */
public final class ActionDef {
    /** A damage window: a cone (range/cone) or a sphere (area > 0, centred fwd/up in front of the player). */
    public record Hit(int tick, float mv, float range, float cone, int sharp, float koHead, float koBody, float elem, float status,
                      boolean bounce, boolean useSharp, int hitstop, float shake, String fx, float fwd, float up,
                      @Nullable DamageKind kind, float area, @Nullable String sound) {
    }

    /** Root motion: forward speed (blocks/tick) while from <= tick < to, optional upward velocity at `from`. */
    public record Move(int from, int to, float speed, float up) {
    }

    public record Sound(int tick, String name, float vol, float pitch) {
    }

    public final String name;
    public final String anim;
    public final int ticks;
    public final int cancel;
    public final boolean lock;
    public final int iframeStart;
    public final int iframeEnd;
    public final int trailStart;
    public final int trailEnd;
    public final float stamina;
    @Nullable
    public final String next;
    public final List<Hit> hits;
    public final List<Move> moves;
    public final List<Sound> sounds;
    public final Map<String, Float> tags;

    ActionDef(String name, String anim, int ticks, int cancel, boolean lock, int iframeStart, int iframeEnd, int trailStart, int trailEnd,
              float stamina, @Nullable String next, List<Hit> hits, List<Move> moves, List<Sound> sounds, Map<String, Float> tags) {
        this.name = name;
        this.anim = anim;
        this.ticks = ticks;
        this.cancel = cancel;
        this.lock = lock;
        this.iframeStart = iframeStart;
        this.iframeEnd = iframeEnd;
        this.trailStart = trailStart;
        this.trailEnd = trailEnd;
        this.stamina = stamina;
        this.next = next;
        this.hits = hits;
        this.moves = moves;
        this.sounds = sounds;
        this.tags = tags;
    }

    public float speedAt(int tick) {
        for (Move m : moves) if (tick >= m.from && tick < m.to) return m.speed;
        return 0;
    }

    public float upAt(int tick) {
        for (Move m : moves) if (tick == m.from) return m.up;
        return 0;
    }

    public boolean hasIframes(int tick) {
        return tick >= iframeStart && tick < iframeEnd;
    }

    public boolean trailActive(float tick) {
        return trailEnd > trailStart && tick >= trailStart && tick <= trailEnd;
    }

    public int tagInt(String key, int def) {
        Float f = tags.get(key);
        return f == null ? def : Math.round(f);
    }

    public boolean hasTag(String key) {
        return tags.containsKey(key);
    }

    /** Specials (everything but the left click chain) do not fill gauges. */
    public boolean isSpecial() {
        return tagInt("special", 1) != 0;
    }

    public int lastHitTick() {
        int t = -1;
        for (Hit h : hits) t = Math.max(t, h.tick);
        return t;
    }
}
