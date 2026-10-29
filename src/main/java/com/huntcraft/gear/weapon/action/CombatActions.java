package com.huntcraft.gear.weapon.action;

import com.huntcraft.HuntCraft;
import com.huntcraft.core.CameraShake;
import com.huntcraft.core.CoreEvents;
import com.huntcraft.core.Element;
import com.huntcraft.core.HunterDamage;
import com.huntcraft.data.HunterData;
import com.huntcraft.entity.monster.MonsterPart;
import com.huntcraft.gear.weapon.HunterWeaponItem;
import com.huntcraft.gear.weapon.Sharpening;
import com.huntcraft.gear.weapon.WeaponFx;
import com.huntcraft.gear.weapon.WeaponHits;
import com.huntcraft.gear.weapon.WeaponSounds;
import com.huntcraft.gear.weapon.WeaponState;
import com.huntcraft.gear.weapon.WeaponTuning;
import com.huntcraft.network.HCNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Server side animation-driven combat: an input starts an {@link ActionDef}; every tick the timeline is advanced
 * (hit windows -> {@link HunterDamage} through {@link WeaponHits}, sounds, i-frames, cancel window / input buffer)
 * and the action is mirrored to every client tracking the player ("anim/action"), which plays the animation.
 */
public final class CombatActions {
    public static final String ACTION = "anim/action";
    public static final String IMPACT = "anim/impact";
    public static final String STANCE = "anim/stance";
    public static final String SHEATH = "anim/sheath";
    public static final String CANCEL = "anim/cancel";
    public static final String EMOTE = "anim/emote";
    public static final int FLAG_DEMON = 1, FLAG_ARCHDEMON = 2, FLAG_SHARPEN = 4, FLAG_DASH = 8, FLAG_FORESIGHT = 16;

    /** Client side "is the local player locked in an action" (installed by client/player/CombatClient). */
    public static Predicate<Player> clientBusy = p -> false;
    private static final Set<String> WARNED = new HashSet<>();

    private CombatActions() {
    }

    public static void registerServerActions() {
        HCNetwork.registerServerAction(CANCEL, (p, t) -> {
            ActionState a = WeaponState.get(p).act;
            if (a.current != null && !a.current.hasTag("nocancel")) cancel(p, true);
        });
        HCNetwork.registerServerAction(EMOTE, (p, t) -> {
            String n = t.getString("n");
            if ("victory".equals(n) && !isBusy(p)) start(p, "victory");
        });
    }

    // ================================================================== queries
    /** True while a locking action is before its cancel window (no new inputs accepted). */
    public static boolean isBusy(Player p) {
        if (p.level().isClientSide) return clientBusy.test(p);
        ActionState a = WeaponState.get(p).act;
        if (a.current == null) return false;
        int t = a.elapsed(p.level().getGameTime());
        return a.current.lock && t < a.current.cancel;
    }

    public static boolean canStart(Player p) {
        return !isBusy(p) && !CoreEvents.isLocked(p);
    }

    @Nullable
    public static ActionDef current(Player p) {
        return WeaponState.get(p).act.current;
    }

    // ================================================================== start / cancel
    public static boolean start(ServerPlayer p, String name) {
        ActionDef def = ActionDefs.get(name);
        if (def == null) {
            if (WARNED.add(name)) HuntCraft.LOGGER.warn("Unknown combat action {}", name);
            return false;
        }
        WeaponState s = WeaponState.get(p);
        ActionState a = s.act;
        if (def.stamina > 0 && !HunterWeaponItem.useStamina(p, def.stamina)) {
            p.displayClientMessage(Component.translatable("message.huntcraft.weapon.no_stamina").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        ItemStack st = p.getMainHandItem();
        HunterWeaponItem w = st.getItem() instanceof HunterWeaponItem hw ? hw : null;
        if (a.current != null && w != null) w.onActionEnd(p, st, a.current, true);
        a.clear();
        a.current = def;
        a.start = p.level().getGameTime();
        broadcastAction(p, def.name, 0);
        if (w != null) w.onActionStart(p, st, def);
        return true;
    }

    public static void cancel(ServerPlayer p, boolean broadcast) {
        ActionState a = WeaponState.get(p).act;
        if (a.current == null) return;
        ItemStack st = p.getMainHandItem();
        if (st.getItem() instanceof HunterWeaponItem w) w.onActionEnd(p, st, a.current, true);
        a.lastName = a.current.name;
        a.lastEnd = p.level().getGameTime();
        a.clear();
        if (broadcast) broadcastAction(p, "", 0);
    }

    private static void broadcastAction(ServerPlayer p, String name, int elapsed) {
        CompoundTag t = new CompoundTag();
        t.putInt("id", p.getId());
        t.putString("n", name);
        if (elapsed > 0) t.putInt("t", elapsed);
        HCNetwork.sendToTracking(p, ACTION, t);
    }

    // ================================================================== inputs
    /** Left click (or attack key while guarding). */
    public static void attackInput(ServerPlayer p, boolean sneak) {
        ItemStack st = p.getMainHandItem();
        if (!(st.getItem() instanceof HunterWeaponItem w)) return;
        if (Sharpening.isSharpening(p) || CoreEvents.isLocked(p) || p.isSpectator() || HunterData.get(p).isDodging()) return;
        if (p.isUsingItem()) {
            if (w.isGuarding(p)) {
                String g = w.guardAction(p, st);
                if (g != null && !isBusy(p)) start(p, g);
            }
            return;
        }
        WeaponState s = WeaponState.get(p);
        ActionState a = s.act;
        long now = p.level().getGameTime();
        if (a.current != null) {
            int t = a.elapsed(now);
            if (a.current.lock && t < a.current.cancel) {
                a.queued = true;
                a.queuedSneak = sneak;
                return;
            }
            String next = a.current.next != null && !sneak && a.current.lock ? a.current.next : w.comboStart(p, st, sneak);
            start(p, next);
            return;
        }
        ActionDef last = ActionDefs.get(a.lastName);
        if (last != null && last.next != null && !sneak && now - a.lastEnd <= WeaponTuning.comboReset(w.weaponClass())) {
            start(p, last.next);
        } else {
            start(p, w.comboStart(p, st, sneak));
        }
    }

    /** Reaction to monster damage (flinch / knockdown). */
    public static void react(ServerPlayer p, float amount) {
        float max = Math.max(1, p.getMaxHealth());
        ActionState a = WeaponState.get(p).act;
        boolean down = a.current != null && "knockdown".equals(a.current.name);
        if (down) return;
        if (amount >= max * 0.3F) {
            start(p, "knockdown");
        } else if (amount >= max * 0.05F) {
            if (a.current != null && a.current.hasTag("nocancel")) return;
            start(p, "flinch");
        }
    }

    // ================================================================== tick
    public static void tick(ServerPlayer p, WeaponState s) {
        ActionState a = s.act;
        tickStance(p, s, a);
        if (a.current == null) return;
        ActionDef def = a.current;
        long now = p.level().getGameTime();
        int t = a.elapsed(now);
        ItemStack st = p.getMainHandItem();
        HunterWeaponItem w = st.getItem() instanceof HunterWeaponItem hw ? hw : null;
        if (p.isDeadOrDying() || (!def.hits.isEmpty() && w == null)) {
            cancel(p, true);
            return;
        }
        for (int i = 0; i < def.hits.size(); i++) {
            ActionDef.Hit h = def.hits.get(i);
            if (h.tick() == t && (a.hitsDone & (1 << i)) == 0) {
                a.hitsDone |= 1 << i;
                try {
                    applyHit(p, st, w, def, h);
                } catch (Exception e) {
                    HuntCraft.LOGGER.error("Action hit failed ({} #{})", def.name, i, e);
                }
            }
        }
        for (ActionDef.Sound snd : def.sounds) {
            if (snd.tick() == t) playSound(p, snd.name(), snd.vol(), snd.pitch());
        }
        if (def.hasIframes(t)) {
            HunterData d = HunterData.get(p);
            d.iframes = Math.max(d.iframes, 2);
        }
        if (w != null) w.onActionTick(p, st, def, t);
        if (a.current != def) return;   // the weapon hook started something else
        if (a.queued && t >= def.cancel) {
            a.queued = false;
            attackInput(p, a.queuedSneak);
            return;
        }
        if (t >= def.ticks) {
            if (w != null) w.onActionEnd(p, st, def, false);
            a.lastName = def.name;
            a.lastEnd = now;
            a.clear();
        }
    }

    /** Mirrors guard / charge / demon / sharpen state and the sheathed weapon to tracking clients. */
    private static void tickStance(ServerPlayer p, WeaponState s, ActionState a) {
        ItemStack main = p.getMainHandItem();
        boolean weapon = main.getItem() instanceof HunterWeaponItem;
        int mode = weapon && p.isUsingItem() ? s.useMode : 0;
        int flags = (s.demon ? FLAG_DEMON : 0) | (s.archdemon ? FLAG_ARCHDEMON : 0) | (s.sharpenLeft > 0 ? FLAG_SHARPEN : 0)
                | (s.dashTicks > 0 ? FLAG_DASH : 0) | (s.foresightTicks > 0 ? FLAG_FORESIGHT : 0);
        if (mode != a.sentMode || flags != a.sentFlags) {
            a.sentMode = mode;
            a.sentFlags = flags;
            HCNetwork.sendToTracking(p, STANCE, stanceTag(p, mode, flags));
        }
        if (weapon) {
            if (!ItemStack.isSameItem(a.lastWeapon, main)) a.lastWeapon = main.copy();
        } else if (!a.lastWeapon.isEmpty() && !hasItem(p, a.lastWeapon)) {
            a.lastWeapon = ItemStack.EMPTY;
        }
        ItemStack show = weapon ? ItemStack.EMPTY : a.lastWeapon;
        if (!a.sheathSent || !ItemStack.isSameItem(show, a.sentSheathed) || show.isEmpty() != a.sentSheathed.isEmpty()) {
            a.sentSheathed = show.copy();
            a.sheathSent = true;
            HCNetwork.sendToTracking(p, SHEATH, sheathTag(p, show));
        }
    }

    private static boolean hasItem(Player p, ItemStack like) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (p.getInventory().getItem(i).is(like.getItem())) return true;
        }
        return p.getOffhandItem().is(like.getItem());
    }

    private static CompoundTag stanceTag(ServerPlayer p, int mode, int flags) {
        CompoundTag t = new CompoundTag();
        t.putInt("id", p.getId());
        t.putInt("m", mode);
        t.putInt("f", flags);
        return t;
    }

    private static CompoundTag sheathTag(ServerPlayer p, ItemStack show) {
        CompoundTag t = new CompoundTag();
        t.putInt("id", p.getId());
        if (!show.isEmpty()) t.put("item", show.save(new CompoundTag()));
        return t;
    }

    /** A player started tracking another one: resend the full animation state. */
    public static void onStartTracking(ServerPlayer target, ServerPlayer viewer) {
        WeaponState s = WeaponState.get(target);
        ActionState a = s.act;
        if (a.current != null) {
            CompoundTag t = new CompoundTag();
            t.putInt("id", target.getId());
            t.putString("n", a.current.name);
            t.putInt("t", a.elapsed(target.level().getGameTime()));
            HCNetwork.sendToPlayer(viewer, ACTION, t);
        }
        HCNetwork.sendToPlayer(viewer, STANCE, stanceTag(target, Math.max(0, a.sentMode), Math.max(0, a.sentFlags)));
        HCNetwork.sendToPlayer(viewer, SHEATH, sheathTag(target, a.sentSheathed));
    }

    // ================================================================== hits
    private static void applyHit(ServerPlayer p, ItemStack st, @Nullable HunterWeaponItem w, ActionDef def, ActionDef.Hit h) {
        List<WeaponHits.Target> targets = h.area() > 0
                ? WeaponHits.areaTargets(p, WeaponHits.front(p, h.fwd(), h.up()), h.area())
                : WeaponHits.coneTargets(p, h.range(), h.cone());
        List<Vec3> impacts = new ArrayList<>();
        Runnable apply = () -> {
            for (WeaponHits.Target tg : targets) {
                if (!tg.owner().isAlive()) continue;
                boolean ok = HunterDamage.hit(p, tg.struck(), h.mv(), ctx -> {
                    if (h.kind() != null) ctx.kind = h.kind();
                    ctx.sharpnessCost = h.sharp();
                    ctx.elementMult = h.elem();
                    ctx.statusMult = h.status();
                    ctx.useSharpness = h.useSharp();
                    ctx.canBounce = h.bounce();
                    ctx.extraKO = tg.isKoZone() ? h.koHead() : h.koBody();
                    if (w != null) w.configureActionHit(p, st, def, h, tg, ctx);
                });
                if (ok) impacts.add(impactPoint(p, tg.struck()));
            }
        };
        if (def.isSpecial()) HunterWeaponItem.special(p, apply);
        else apply.run();
        swingFx(p, w, st, h);
        int n = impacts.size();
        if (n > 0) {
            Vec3 at = impacts.get(0);
            Element el = w != null ? w.getElement(st) : Element.NONE;
            CompoundTag t = new CompoundTag();
            t.putInt("id", p.getId());
            t.putDouble("x", at.x);
            t.putDouble("y", at.y);
            t.putDouble("z", at.z);
            t.putInt("hs", h.hitstop());
            t.putInt("e", el.ordinal());
            t.putBoolean("b", h.kind() == com.huntcraft.core.DamageKind.BLUNT || (w != null && w.weaponClass().defaultKind == com.huntcraft.core.DamageKind.BLUNT && h.kind() == null));
            t.putInt("n", n);
            HCNetwork.sendToTracking(p, IMPACT, t);
            if (h.sound() != null) playSound(p, h.sound(), 1.0F, 1.0F);
            boolean blunt = t.getBoolean("b");
            p.level().playSound(null, at.x, at.y, at.z, blunt ? WeaponSounds.HIT_BLUNT.get() : WeaponSounds.HIT_SLASH.get(), SoundSource.PLAYERS,
                    0.5F + 0.1F * h.hitstop(), 0.95F + p.getRandom().nextFloat() * 0.1F);
            if (h.shake() > 0) CameraShake.shake(p, h.shake(), 5 + h.hitstop());
        }
        if (w != null) w.onActionHitDone(p, st, def, h, n);
    }

    private static Vec3 impactPoint(ServerPlayer p, Entity struck) {
        AABB box = struck instanceof MonsterPart mp ? mp.getBoundingBox() : struck.getBoundingBox();
        Vec3 c = box.getCenter();
        Vec3 from = p.getEyePosition().subtract(0, 0.35, 0);
        Vec3 d = c.subtract(from);
        double len = d.length();
        if (len < 1.0E-3) return c;
        double half = Math.min(box.getXsize(), box.getZsize()) * 0.5;
        double k = Math.max(0.4, len - half * 0.8) / len;
        return from.add(d.scale(Math.min(1.0, k)));
    }

    private static void swingFx(ServerPlayer p, @Nullable HunterWeaponItem w, ItemStack st, ActionDef.Hit h) {
        switch (h.fx()) {
            case "slam" -> {
                Vec3 at = WeaponHits.front(p, h.fwd(), 0.1);
                WeaponFx.slam(p, at, 0.6F + h.shake());
                if (w != null) WeaponFx.element(p, w.getElement(st), at.add(0, 0.8, 0), 10);
            }
            case "bash" -> WeaponFx.send(p, ParticleTypes.CRIT, WeaponHits.front(p, h.fwd(), h.up()), 6, 0.3, 0.3, 0.3, 0.15);
            case "spirit" -> WeaponFx.send(p, WeaponFx.dust(0xFF3A2A, 1.0F), WeaponHits.front(p, 1.6, 1.1), 14, 0.8, 0.4, 0.8, 0.0);
            case "counter" -> WeaponFx.send(p, ParticleTypes.ENCHANTED_HIT, WeaponHits.front(p, 2.4, 1.2), 12, 0.3, 0.3, 0.3, 0.25);
            case "note" -> WeaponFx.send(p, ParticleTypes.NOTE, p.position().add(0, 1.6, 0), 12, 1.2, 0.6, 1.2, 1.0);
            default -> {
                // plain slashes: the client side weapon trail is the swing effect, impacts add their own sparks
            }
        }
    }

    public static void playSound(Player p, String name, float vol, float pitch) {
        SoundEvent se = ForgeRegistries.SOUND_EVENTS.getValue(name.contains(":") ? new ResourceLocation(name) : HuntCraft.id(name));
        if (se == null) {
            if (WARNED.add("snd:" + name)) HuntCraft.LOGGER.warn("Unknown action sound {}", name);
            return;
        }
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), se, SoundSource.PLAYERS, vol, pitch);
    }
}
