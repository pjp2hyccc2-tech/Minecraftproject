package com.eternalrealm.client.revival;

import com.eternalrealm.client.ClientChannels;
import com.eternalrealm.client.ClientUiState;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.item.Consumable;
import com.eternalrealm.item.forge.ERGearItems;
import com.eternalrealm.ui.screen.RevivalScreen;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The client half of the revival system: keeps the revival screen open while the local player is fallen (it comes
 * back after the chat or the pause menu), switches to the third-person view so the player sees themselves on the
 * ground, freezes movement input, lays every fallen player flat, and writes "戦闘不能" over their bodies (with a hint
 * for allies holding a 世界樹の葉).
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientRevival {
    private static final Logger LOG = LogUtils.getLogger();
    public static final String CHANNEL = "revival";
    public static final String BODIES_CHANNEL = "revival_bodies";

    private static JsonObject screen;
    private static long receivedAt;
    private static final Set<Integer> BODIES = new HashSet<>();
    private static final Set<UUID> DOWN_IDS = new HashSet<>();
    private static final Set<Integer> LAID = new HashSet<>();
    private static CameraType cameraBefore;
    private static boolean markersDisabled;

    private ClientRevival() {
    }

    public static void init() {
        ClientChannels.register(CHANNEL, ClientRevival::onScreenData);
        ClientChannels.register(BODIES_CHANNEL, ClientRevival::onBodies);
    }

    // ------------------------------------------------------------------ state

    private static void onScreenData(JsonObject data) {
        String state = ClientUiState.stringOr(data, "state", "up");
        Minecraft minecraft = Minecraft.getInstance();
        if ("down".equals(state)) {
            boolean fresh = screen == null;
            screen = data;
            receivedAt = System.nanoTime();
            if (fresh) {
                cameraBefore = minecraft.options.getCameraType();
                minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                minecraft.setScreen(new RevivalScreen());
            } else if (minecraft.screen instanceof RevivalScreen open) {
                open.refresh();
            }
        } else {
            boolean wasDown = screen != null;
            screen = null;
            if (minecraft.screen instanceof RevivalScreen) {
                minecraft.setScreen(null);
            }
            if (wasDown && cameraBefore != null) {
                minecraft.options.setCameraType(cameraBefore);
            }
            cameraBefore = null;
        }
    }

    private static void onBodies(JsonObject data) {
        BODIES.clear();
        DOWN_IDS.clear();
        if (data.has("ids") && data.get("ids").isJsonArray()) {
            for (JsonElement id : data.getAsJsonArray("ids")) {
                BODIES.add(id.getAsInt());
            }
        }
        if (data.has("uuids") && data.get("uuids").isJsonArray()) {
            JsonArray uuids = data.getAsJsonArray("uuids");
            for (JsonElement id : uuids) {
                try {
                    DOWN_IDS.add(UUID.fromString(id.getAsString()));
                } catch (IllegalArgumentException ignored) {
                    // a broken id is simply not shown
                }
            }
        }
    }

    /** Whether the local player is fallen. */
    public static boolean isDown() {
        return screen != null;
    }

    /** The latest revival screen document (while fallen). */
    public static Optional<JsonObject> screenData() {
        return Optional.ofNullable(screen);
    }

    /** Seconds since the screen document arrived (its countdowns are relative to that moment). */
    public static double secondsSinceUpdate() {
        return (System.nanoTime() - receivedAt) / 1.0E9;
    }

    /** Whether the player (by id) lies fallen in this level (party frames). */
    public static boolean isDown(UUID playerId) {
        return DOWN_IDS.contains(playerId);
    }

    public static boolean isBody(Entity entity) {
        return entity instanceof Player && BODIES.contains(entity.getId());
    }

    // ------------------------------------------------------------------ events

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (screen == null || minecraft.player == null) {
            return;
        }
        // the screen comes back once the chat or the pause menu is closed
        if (minecraft.screen == null) {
            minecraft.setScreen(new RevivalScreen());
        }
        if (minecraft.options.getCameraType() == CameraType.FIRST_PERSON && minecraft.screen instanceof RevivalScreen) {
            minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (screen == null) {
            return;
        }
        var input = event.getInput();
        input.forwardImpulse = 0.0F;
        input.leftImpulse = 0.0F;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        if (!BODIES.contains(player.getId())) {
            return;
        }
        // lie on the back, head away from where the body faces, a little above the ground
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        float bodyYaw = Mth.rotLerp(event.getPartialTick(), player.yBodyRotO, player.yBodyRot);
        pose.translate(0.0, 0.18, 0.0);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        pose.mulPose(Axis.XP.rotationDegrees(90.0F));
        pose.mulPose(Axis.YP.rotationDegrees(-(180.0F - bodyYaw)));
        LAID.add(player.getId());
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (LAID.remove(event.getEntity().getId())) {
            event.getPoseStack().popPose();
        }
    }

    @SubscribeEvent
    public static void onNameTag(RenderNameTagEvent event) {
        if (isBody(event.getEntity())) {
            event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (markersDisabled || BODIES.isEmpty() || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        try {
            drawMarkers(event);
        } catch (RuntimeException e) {
            markersDisabled = true;
            LOG.error("Fallen markers disabled after a rendering error", e);
        }
    }

    private static void drawMarkers(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 cameraPosition = camera.getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        Font font = minecraft.font;
        Item leaf = ERGearItems.consumable(Consumable.YGGDRASIL_LEAF);
        boolean holdingLeaf = minecraft.player.getMainHandItem().is(leaf) || minecraft.player.getOffhandItem().is(leaf);
        float time = (minecraft.level.getGameTime() % 40 + event.getPartialTick()) / 40.0F;
        for (int id : BODIES) {
            Entity body = minecraft.level.getEntity(id);
            if (body == null || body == minecraft.player && minecraft.options.getCameraType().isFirstPerson()) {
                continue;
            }
            Vec3 at = body.getPosition(event.getPartialTick());
            double distance = at.distanceTo(cameraPosition);
            if (distance > 48.0) {
                continue;
            }
            pose.pushPose();
            pose.translate(at.x - cameraPosition.x, at.y + 1.15 + Math.sin(time * Math.PI * 2.0) * 0.05 - cameraPosition.y,
                    at.z - cameraPosition.z);
            pose.mulPose(camera.rotation());
            float scale = 0.025F;
            pose.scale(-scale, -scale, scale);
            Matrix4f matrix = pose.last().pose();
            Component title = Component.translatable("revival.eternalrealm.marker.down");
            Component name = body.getDisplayName();
            font.drawInBatch(title, -font.width(title) / 2.0F, -10.0F, 0xFFFF5A4A, true, matrix, buffers,
                    Font.DisplayMode.SEE_THROUGH, 0x60000000, LightTexture.FULL_BRIGHT);
            font.drawInBatch(name, -font.width(name) / 2.0F, 0.0F, 0xFFE0E0E0, true, matrix, buffers,
                    Font.DisplayMode.NORMAL, 0x40000000, LightTexture.FULL_BRIGHT);
            if (holdingLeaf && body != minecraft.player && distance < 6.0) {
                Component hint = Component.translatable("revival.eternalrealm.marker.leaf_hint");
                font.drawInBatch(hint, -font.width(hint) / 2.0F, 11.0F, 0xFF8CF27A, true, matrix, buffers,
                        Font.DisplayMode.SEE_THROUGH, 0x60000000, LightTexture.FULL_BRIGHT);
            }
            pose.popPose();
        }
        buffers.endBatch();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        screen = null;
        BODIES.clear();
        DOWN_IDS.clear();
        LAID.clear();
        if (cameraBefore != null) {
            Minecraft.getInstance().options.setCameraType(cameraBefore);
            cameraBefore = null;
        }
    }
}
