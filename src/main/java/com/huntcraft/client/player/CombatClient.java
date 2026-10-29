package com.huntcraft.client.player;

import com.huntcraft.HuntCraft;
import com.huntcraft.client.ClientState;
import com.huntcraft.core.Element;
import com.huntcraft.gear.weapon.WeaponHits;
import com.huntcraft.gear.weapon.action.ActionDef;
import com.huntcraft.gear.weapon.action.ActionDefs;
import com.huntcraft.gear.weapon.action.CombatActions;
import com.huntcraft.network.HCNetwork;
import com.huntcraft.registry.HCSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/**
 * Client glue of the combat action system: packet handlers (actions, impacts, stances, sheathed weapons),
 * movement lock + root motion for the local player's actions, dodge-cancel, impact feedback (hit-stop, sparks).
 */
@Mod.EventBusSubscriber(modid = HuntCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class CombatClient {
    private static long lastVictory = -1000;

    private CombatClient() {
    }

    public static void registerClientActions() {
        CombatActions.clientBusy = p -> {
            PlayerAnimState st = PlayerAnimState.get(p);
            if (st.action == null || !st.action.lock) return false;
            float t = st.rawElapsed(p.level().getGameTime(), 0);
            return t < st.action.cancel;
        };
        HCNetwork.registerClientAction(CombatActions.ACTION, tag -> {
            Player p = player(tag);
            if (p == null) return;
            PlayerAnimState st = PlayerAnimState.get(p);
            String n = tag.getString("n");
            long gt = p.level().getGameTime();
            if (n.isEmpty()) st.endAction(gt);
            else st.startAction(ActionDefs.get(n), gt, tag.getInt("t"));
        });
        HCNetwork.registerClientAction(CombatActions.IMPACT, CombatClient::impact);
        HCNetwork.registerClientAction(CombatActions.STANCE, tag -> {
            Player p = player(tag);
            if (p == null) return;
            PlayerAnimState st = PlayerAnimState.get(p);
            st.useMode = tag.getInt("m");
            st.flags = tag.getInt("f");
        });
        HCNetwork.registerClientAction(CombatActions.SHEATH, tag -> {
            Player p = player(tag);
            if (p == null) return;
            PlayerAnimState st = PlayerAnimState.get(p);
            st.sheathed = tag.contains("item") ? ItemStack.of(tag.getCompound("item")) : ItemStack.EMPTY;
        });
    }

    private static Player player(CompoundTag tag) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        Entity e = mc.level.getEntity(tag.getInt("id"));
        return e instanceof Player p ? p : null;
    }

    // ================================================================== impact feedback
    private static void impact(CompoundTag tag) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Player attacker = player(tag);
        Vec3 at = new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"));
        int hs = tag.getInt("hs");
        boolean blunt = tag.getBoolean("b");
        Element el = Element.values()[Math.max(0, Math.min(Element.values().length - 1, tag.getInt("e")))];
        if (attacker != null) PlayerAnimState.get(attacker).hitStop(hs);
        var rnd = mc.level.random;
        int sparks = 6 + hs * 2;
        for (int i = 0; i < sparks; i++) {
            double vx = (rnd.nextDouble() - 0.5) * 0.5, vy = rnd.nextDouble() * 0.35, vz = (rnd.nextDouble() - 0.5) * 0.5;
            mc.level.addParticle(blunt ? ParticleTypes.CRIT : ParticleTypes.ENCHANTED_HIT, at.x, at.y, at.z, vx, vy, vz);
        }
        if (blunt) {
            for (int i = 0; i < 3; i++) {
                mc.level.addParticle(ParticleTypes.POOF, at.x, at.y, at.z, (rnd.nextDouble() - 0.5) * 0.15, 0.05, (rnd.nextDouble() - 0.5) * 0.15);
            }
        } else {
            mc.level.addParticle(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 0, 0, 0);
        }
        if (el != Element.NONE) {
            int c = el.color;
            DustParticleOptions dust = new DustParticleOptions(new Vector3f(((c >> 16) & 255) / 255F, ((c >> 8) & 255) / 255F, (c & 255) / 255F), 1.2F);
            for (int i = 0; i < 5 + hs; i++) {
                mc.level.addParticle(dust, at.x + (rnd.nextDouble() - 0.5) * 0.4, at.y + (rnd.nextDouble() - 0.5) * 0.4, at.z + (rnd.nextDouble() - 0.5) * 0.4,
                        (rnd.nextDouble() - 0.5) * 0.1, rnd.nextDouble() * 0.1, (rnd.nextDouble() - 0.5) * 0.1);
            }
        }
    }

    // ================================================================== local player: lock, root motion, dodge cancel
    /** Movement input is dropped while the local player is locked in an action. */
    public static boolean lockMovement(Player p) {
        Minecraft mc = Minecraft.getInstance();
        if (p != mc.player) return false;
        PlayerAnimState st = PlayerAnimState.get(p);
        if (st.action == null || !st.action.lock) return false;
        return st.rawElapsed(p.level().getGameTime(), 0) < st.action.ticks;
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        long gt = mc.level.getGameTime();
        PlayerAnimState.tickAll(gt);
        PlayerAnimState st = PlayerAnimState.get(p);
        // dodge started this tick (core handles the roll itself): cancel the running action
        ClientState.Dodge d = ClientState.DODGES.get(p.getId());
        if (d != null && d.start() != st.lastDodgeStart) {
            st.lastDodgeStart = d.start();
            if (st.action != null && !st.action.hasTag("nocancel")) {
                HCNetwork.sendToServer(CombatActions.CANCEL, new CompoundTag());
                st.endAction(gt);
            }
        }
        ActionDef a = st.action;
        if (a != null && st.actionActive(gt)) {
            int t = (int) st.rawElapsed(gt, 0);
            float sp = a.speedAt(t);
            float up = a.upAt(t);
            if (sp != 0 || up != 0) {
                Vec3 f = WeaponHits.flatLook(p);
                double dy = up > 0 && p.onGround() ? up : p.getDeltaMovement().y;
                p.setDeltaMovement(f.x * sp, dy, f.z * sp);
            }
            if (a.lock) p.setSprinting(false);
        }
    }

    /** The quest clear jingle triggers the victory pose. */
    @SubscribeEvent
    public static void onSound(PlaySoundEvent event) {
        if (event.getSound() == null || Minecraft.getInstance().level == null) return;
        if (!event.getSound().getLocation().equals(HCSounds.QUEST_CLEAR.get().getLocation())) return;
        long gt = Minecraft.getInstance().level.getGameTime();
        if (gt - lastVictory < 100) return;
        lastVictory = gt;
        CompoundTag t = new CompoundTag();
        t.putString("n", "victory");
        HCNetwork.sendToServer(CombatActions.EMOTE, t);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        PlayerAnimState.clear();
    }
}
