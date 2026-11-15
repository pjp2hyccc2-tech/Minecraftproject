package com.eternalrealm.client;

import com.eternalrealm.config.ERClientConfig;
import com.eternalrealm.job.Jobs;
import com.eternalrealm.job.SkillDefinition;
import com.eternalrealm.job.fx.SkillShow;
import com.eternalrealm.job.fx.SkillShows;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.ViewportEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The finales (秘奥義) on this client beyond their light ({@link SkillFx}):
 * <ul>
 *     <li>the screen a finale directs for the one performing it ({@link SkillShow.Screen}: cinema bars, a zoom of the
 *     field of view, a tinted vignette and flashes, drawn by {@link FinaleCinema}), and a faint flash for whoever
 *     stands close ({@code finaleCinema} in the settings switches all of it off);</li>
 *     <li>the stretches where the caster's weapon, or the caster, is not drawn ({@link SkillShow.Window}) while a
 *     prop or after-images stand in for it: only while the skill light draws those (never with a shader pack or with
 *     the skill light switched off).</li>
 * </ul>
 * A finale is followed from its action's start on every player in sight, on the action's own timeline (held by the
 * local hit stop, faster with attack speed) like its light. Client thread only.
 */
public final class FinaleClient {
    /** Finales farther than this are not followed (the skill light's range). */
    private static final double RANGE = 96.0;
    /** Onlookers closer than this see a finale's flashes, fainter with distance. */
    private static final double FLASH_RANGE = 24.0;
    /** How strong an onlooker's flash is at most (the performer's own is whole). */
    private static final float ONLOOKER_FLASH = 0.3F;
    private static final Map<Integer, Tracked> TRACKED = new HashMap<>();
    /** Main-hand stacks taken out for the drawing of a player this frame (put back right after, at the latest the next tick). */
    private static final Map<Integer, Swap> SWAPPED = new HashMap<>();
    private static final Map<String, Optional<SkillShow>> SHOWS = new HashMap<>();

    private FinaleClient() {
    }

    /** A finale being performed. */
    private static final class Tracked {
        final SkillShow show;
        final String actionId;
        final long start;
        final double pace;
        double held;

        Tracked(SkillShow show, String actionId, long start, double pace) {
            this.show = show;
            this.actionId = actionId;
            this.start = start;
            this.pace = pace;
        }
    }

    private record Swap(Player player, int slot, ItemStack stack) {
    }

    /** The show of {@code actionId} if it is a finale's. */
    private static Optional<SkillShow> finale(String actionId) {
        return SHOWS.computeIfAbsent(actionId, id -> Jobs.skill(id).filter(SkillDefinition::isFinale)
                .flatMap(skill -> SkillShows.forAction(id)));
    }

    /** Every client tick: follows the finales that began in sight, forgets those that ended. */
    public static void tick() {
        restoreWeapons();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            TRACKED.clear();
            return;
        }
        long tick = ClientCombatState.tick();
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        for (AbstractClientPlayer player : minecraft.level.players()) {
            if (player.distanceToSqr(camera) > RANGE * RANGE) {
                continue;
            }
            Optional<CombatAnimations.Anim> anim = CombatAnimations.of(player);
            if (anim.isEmpty() || anim.get().actionId() == null) {
                continue;
            }
            Tracked known = TRACKED.get(player.getId());
            if (known != null && known.start == anim.get().start() && known.actionId.equals(anim.get().actionId())) {
                continue;
            }
            Optional<SkillShow> show = finale(anim.get().actionId());
            if (show.isPresent()) {
                double pace = Mth.clamp(show.get().duration() / (double) Math.max(1, anim.get().duration()), 0.5, 2.0);
                TRACKED.put(player.getId(), new Tracked(show.get(), anim.get().actionId(), anim.get().start(), pace));
            }
        }
        TRACKED.entrySet().removeIf(entry -> minecraft.level.getEntity(entry.getKey()) == null
                || time(minecraft, entry.getKey(), entry.getValue(), tick, 0.0F) > entry.getValue().show.length() + 2.0);
    }

    /** The finale's own time now: the action's progress while it lasts (held by a hit stop), then on at its pace. */
    private static double time(Minecraft minecraft, int entityId, Tracked tracked, long tick, float partial) {
        double raw = tick - tracked.start + partial;
        if (minecraft.level != null && minecraft.level.getEntity(entityId) instanceof LivingEntity living) {
            Optional<CombatAnimations.Anim> anim = CombatAnimations.of(living);
            if (anim.isPresent() && anim.get().start() == tracked.start
                    && tracked.actionId.equals(anim.get().actionId())) {
                tracked.held = Math.max(0.0, raw - anim.get().elapsed(tick, partial));
            }
        }
        return (raw - tracked.held) * tracked.pace;
    }

    // ------------------------------------------------------------------------------------------------ the screen

    /**
     * What the screen shows now: the local player's own finale whole (bars, zoom, vignette, flashes), the flashes of
     * the finales close by faintly. Nothing with {@code finaleCinema} off.
     */
    public static SkillShow.ScreenState screen(float partial) {
        Minecraft minecraft = Minecraft.getInstance();
        if (TRACKED.isEmpty() || minecraft.level == null || minecraft.player == null
                || !ERClientConfig.FINALE_CINEMA.get()) {
            return SkillShow.ScreenState.NONE;
        }
        long tick = ClientCombatState.tick();
        SkillShow.ScreenState own = SkillShow.ScreenState.NONE;
        float flash = 0.0F;
        int flashRgb = 0;
        for (Map.Entry<Integer, Tracked> entry : TRACKED.entrySet()) {
            SkillShow.ScreenState s = entry.getValue().show.screenAt(time(minecraft, entry.getKey(), entry.getValue(),
                    tick, partial));
            if (s.none()) {
                continue;
            }
            if (entry.getKey() == minecraft.player.getId()) {
                own = s;
                continue;
            }
            Entity caster = minecraft.level.getEntity(entry.getKey());
            double distance = caster == null ? FLASH_RANGE : caster.distanceTo(minecraft.player);
            float k = ONLOOKER_FLASH * (float) Mth.clamp(1.0 - distance / FLASH_RANGE, 0.0, 1.0);
            if (s.flash() * k > flash) {
                flash = s.flash() * k;
                flashRgb = s.flashRgb();
            }
        }
        if (flash > own.flash()) {
            return new SkillShow.ScreenState(flash, flashRgb, own.bars(), own.zoom(), own.vignette(), own.vignetteRgb());
        }
        return own;
    }

    /** Closes the view in (or opens it) while the local player's finale zooms. */
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (!event.usedConfiguredFov() || TRACKED.isEmpty()) {
            return; // never the hand's own view
        }
        float zoom = screen((float) event.getPartialTick()).zoom();
        if (Math.abs(zoom - 1.0F) > 0.001F) {
            event.setFOV(event.getFOV() * zoom);
        }
    }

    // ------------------------------------------------------------------------------------------------ hiding

    /** The finale of {@code player} if it hides anything and the props and after-images are drawn. */
    private static Tracked hiding(Player player) {
        if (TRACKED.isEmpty() || !SkillFx.drawing()) {
            return null;
        }
        Tracked tracked = TRACKED.get(player.getId());
        return tracked == null || tracked.show.hidden().isEmpty() ? null : tracked;
    }

    /**
     * Before a player is drawn: not at all while its finale hides it (a flash step: only its after-images show),
     * without the weapon in its hand while a prop stands in for it (put back after it is drawn).
     */
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        Tracked tracked = hiding(player);
        if (tracked == null) {
            return;
        }
        double t = time(Minecraft.getInstance(), player.getId(), tracked, ClientCombatState.tick(),
                event.getPartialTick());
        if (tracked.show.hidesBody(t)) {
            event.setCanceled(true);
            return;
        }
        if (tracked.show.hidesWeapon(t) && !SWAPPED.containsKey(player.getId())) {
            Inventory inventory = player.getInventory();
            int slot = inventory.selected;
            if (slot >= 0 && slot < inventory.items.size() && !inventory.items.get(slot).isEmpty()) {
                SWAPPED.put(player.getId(), new Swap(player, slot, inventory.items.get(slot)));
                inventory.items.set(slot, ItemStack.EMPTY);
            }
        }
    }

    /** After a player is drawn: its weapon back in its hand. */
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        Swap swap = SWAPPED.remove(event.getEntity().getId());
        if (swap != null) {
            restore(swap);
        }
    }

    /** The local player's hands (and what they hold) are not drawn while its finale hides its weapon. */
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        Tracked tracked = player == null ? null : hiding(player);
        if (tracked != null && tracked.show.hidesWeapon(time(minecraft, player.getId(), tracked,
                ClientCombatState.tick(), event.getPartialTick()))) {
            event.setCanceled(true);
        }
    }

    private static void restore(Swap swap) {
        Inventory inventory = swap.player().getInventory();
        if (swap.slot() < inventory.items.size() && inventory.items.get(swap.slot()).isEmpty()) {
            inventory.items.set(swap.slot(), swap.stack());
        }
    }

    /** A safety net: any weapon still out (a drawing that never finished) goes back. */
    private static void restoreWeapons() {
        if (!SWAPPED.isEmpty()) {
            SWAPPED.values().forEach(FinaleClient::restore);
            SWAPPED.clear();
        }
    }

    /** Forgets everything (leaving a world). */
    public static void clear() {
        restoreWeapons();
        TRACKED.clear();
    }
}
