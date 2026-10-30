package com.mcbesiege.client;

import com.mcbesiege.MCBesiege;
import com.mcbesiege.block.MachinePartBlock;
import com.mcbesiege.block.MachinePartBlockEntity;
import com.mcbesiege.entity.MachineEntity;
import com.mcbesiege.item.WrenchItem;
import com.mcbesiege.machine.Assembler;
import com.mcbesiege.machine.Blueprint;
import com.mcbesiege.net.Net;
import com.mcbesiege.part.Keys;
import com.mcbesiege.part.PartConfig;
import com.mcbesiege.part.PartType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;

/** Client game events: hotkeys, riding controls, orbit camera, zone / center-of-mass rendering. */
@Mod.EventBusSubscriber(modid = MCBesiege.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    private static Vec3 com;
    private static int comTicks;

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ShotTour.tick(mc);
        if (mc.player == null || mc.level == null) return;
        while (ClientSetup.KEY_SIMULATE.consumeClick()) {
            CompoundTag t = new CompoundTag();
            if (mc.hitResult instanceof BlockHitResult bh && mc.hitResult.getType() == HitResult.Type.BLOCK) t.putLong("pos", bh.getBlockPos().asLong());
            Net.toServer("toggle", t);
        }
        while (ClientSetup.KEY_BUILDER.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new BuilderScreen());
        }
        while (ClientSetup.KEY_MIRROR.consumeClick()) Net.toServer("mirror", new CompoundTag());
        while (ClientSetup.KEY_CONTROL.consumeClick()) {
            MachineEntity own = ownMachine(mc);
            if (own != null && mc.screen == null) mc.setScreen(new ControlScreen(own.getId()));
        }
        if (ClientState.pendingControl >= 0) {
            Entity en = mc.level.getEntity(ClientState.pendingControl);
            if (en instanceof MachineEntity && (mc.screen == null || mc.screen instanceof BuilderScreen)) {
                mc.setScreen(new ControlScreen(en.getId()));
                ClientState.pendingControl = -1;
            } else if (++ClientState.pendingControlTicks > 100) ClientState.pendingControl = -1;
        }
        ridingInput(mc);
        if (++comTicks % 10 == 0) updateCom(mc);
    }

    static MachineEntity ownMachine(Minecraft mc) {
        MachineEntity best = null;
        double bd = Double.MAX_VALUE;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof MachineEntity me) || me.isRemoved()) continue;
            if (me.owner() == null || !me.owner().equals(mc.player.getUUID())) continue;
            double d = me.distanceToSqr(mc.player);
            if (d < bd) {
                bd = d;
                best = me;
            }
        }
        return best;
    }

    /** While sitting in a seat, WASD / space drive the machine as arrow keys / space. */
    private static void ridingInput(Minecraft mc) {
        int[] keys = new int[0];
        int id = -1;
        if (mc.player.getVehicle() instanceof MachineEntity me && !(mc.screen instanceof ControlScreen)) {
            id = me.getId();
            var in = mc.player.input;
            int n = 0;
            int[] buf = new int[5];
            if (in.up) buf[n++] = Keys.UP;
            if (in.down) buf[n++] = Keys.DOWN;
            if (in.left) buf[n++] = Keys.LEFT;
            if (in.right) buf[n++] = Keys.RIGHT;
            if (in.jumping) buf[n++] = Keys.SPACE;
            keys = Arrays.copyOf(buf, n);
        }
        if (!Arrays.equals(keys, ClientState.ridingKeys)) {
            if (id >= 0) Net.sendKeys(id, keys);
            else if (mc.player.getVehicle() == null && ClientState.ridingKeys.length > 0) {
                MachineEntity own = ownMachine(mc);
                if (own != null) Net.sendKeys(own.getId(), new int[0]);
            }
            ClientState.ridingKeys = keys;
        }
    }

    /** Besiege shows the machine's center of mass while building; we do it while holding the wrench. */
    private static void updateCom(Minecraft mc) {
        com = null;
        if (!(mc.player.getMainHandItem().getItem() instanceof WrenchItem)) return;
        BlockPos base = mc.player.blockPosition();
        BlockPos start = null;
        double bd = Double.MAX_VALUE;
        for (BlockPos q : BlockPos.betweenClosed(base.offset(-12, -6, -12), base.offset(12, 6, 12))) {
            BlockState s = mc.level.getBlockState(q);
            if (s.getBlock() instanceof MachinePartBlock b && b.type == PartType.STARTING_BLOCK) {
                double d = q.distSqr(base);
                if (d < bd) {
                    bd = d;
                    start = q.immutable();
                }
            }
        }
        if (start == null) return;
        Assembler.Result r = Assembler.capture(mc.level, start, 1000);
        if (r.blueprint == null) return;
        double m = 0, x = 0, y = 0, z = 0;
        for (Blueprint.Entry en : r.blueprint.entries) {
            PartType t = en.type();
            if (t == null) continue;
            double mass = t.mass;
            if (t == PartType.BALLAST) {
                BlockEntity be = mc.level.getBlockEntity(start.offset(en.rel));
                if (be instanceof MachinePartBlockEntity pbe) mass = PartConfig.of(t, pbe.getConfig()).slider("mass");
            }
            BlockPos p = start.offset(en.rel);
            m += mass;
            x += mass * (p.getX() + 0.5);
            y += mass * (p.getY() + 0.5);
            z += mass * (p.getZ() + 0.5);
        }
        if (m > 0) com = new Vec3(x / m, y / m, z / m);
    }

    @SubscribeEvent
    public static void renderTick(TickEvent.RenderTickEvent e) {
        if (e.phase == TickEvent.Phase.START) OrbitCamera.update(e.renderTickTime);
    }

    @SubscribeEvent
    public static void renderHand(RenderHandEvent e) {
        if (OrbitCamera.isActive()) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void screenOpening(ScreenEvent.Opening e) {
        ShotTour.onScreen(e.getScreen());
    }

    @SubscribeEvent
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut e) {
        OrbitCamera.end();
        ClientState.reset();
    }

    @SubscribeEvent
    public static void renderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        CompoundTag m = ClientState.mission;
        boolean zone = m.contains("zone");
        if (!zone && com == null) return;
        PoseStack ps = e.getPoseStack();
        Vec3 cam = e.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        if (zone) {
            int[] z = m.getIntArray("zone");
            if (z.length == 6) {
                float pulse = 0.6f + 0.4f * (float) Math.sin((mc.level.getGameTime() + e.getPartialTick()) * 0.15);
                LevelRenderer.renderLineBox(ps, lines, new AABB(z[0], z[1], z[2], z[3], z[1] + 12, z[5]), 1f, 0.85f * pulse, 0.1f, 1f);
            }
            int[] g = m.getIntArray("goal");
            if (g.length == 6) LevelRenderer.renderLineBox(ps, lines, new AABB(g[0], g[1], g[2], g[3], g[1] + 8, g[5]), 1f, 0.84f, 0f, 1f);
        }
        if (com != null) {
            double r = 0.18;
            LevelRenderer.renderLineBox(ps, lines, new AABB(com.x - r, com.y - r, com.z - r, com.x + r, com.y + r, com.z + r), 0.2f, 0.5f, 1f, 1f);
            LevelRenderer.renderLineBox(ps, lines, new AABB(com.x - 0.02, com.y - 3, com.z - 0.02, com.x + 0.02, com.y, com.z + 0.02), 0.2f, 0.5f, 1f, 0.6f);
        }
        ps.popPose();
        buffers.endBatch(RenderType.lines());
    }
}
