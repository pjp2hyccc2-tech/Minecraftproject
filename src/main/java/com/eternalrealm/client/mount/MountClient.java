package com.eternalrealm.client.mount;

import com.eternalrealm.client.ClientKeyMappings;
import com.eternalrealm.client.entity.model.RigTables;
import com.eternalrealm.client.entity.model.gen.MountRigs;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.mount.MountKind;
import com.eternalrealm.mount.forge.MountEntities;
import com.eternalrealm.mount.forge.MountEntity;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

/**
 * The mounts on the client (mod bus): the call and choose keys (U and I, in the game's key category), the models and
 * renderers of every mount kind (the generated {@link MountRigs}: the three first mounts and the ten breeds), and the
 * jump key the ridden mount reads.
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MountClient {
    /** Calls the chosen mount, or gets off it. */
    public static final KeyMapping CALL = key("mount", GLFW.GLFW_KEY_U);
    /** Opens the mount chooser. */
    public static final KeyMapping CHOOSE = key("mount_choose", GLFW.GLFW_KEY_I);

    private MountClient() {
    }

    private static KeyMapping key(String name, int defaultKey) {
        return new KeyMapping("key." + ModInfo.MOD_ID + "." + name, KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, defaultKey, ClientKeyMappings.CATEGORY);
    }

    public static RigTables tables(MountKind kind) {
        return MountRigs.tables(kind);
    }

    public static ModelLayerLocation layer(MountKind kind) {
        return new ModelLayerLocation(new ResourceLocation(ModInfo.MOD_ID, kind.entityPath()), "main");
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(CALL);
        event.register(CHOOSE);
    }

    @SubscribeEvent
    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        for (MountKind kind : MountKind.values()) {
            event.registerLayerDefinition(layer(kind), () -> MountRigs.layer(kind));
        }
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        for (MountKind kind : MountKind.values()) {
            event.registerEntityRenderer(MountEntities.type(kind), context -> new MountRenderer(context, kind));
        }
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        MountEntity.clientJumpHeld = () -> {
            Minecraft minecraft = Minecraft.getInstance();
            return minecraft.screen == null && minecraft.options.keyJump.isDown();
        };
        event.enqueueWork(() -> com.eternalrealm.client.ClientChannels.register(
                com.eternalrealm.mount.MountRules.STABLE_CHANNEL, com.eternalrealm.ui.mount.StableScreen::accept));
    }

    /** A deed's seal takes the colour of its mount's grade. */
    @SubscribeEvent
    public static void onItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, layer) -> layer == 1 ? com.eternalrealm.mount.forge.MountDeeds.read(stack)
                        .map(mount -> mount.grade().color()).orElse(0xFFFFFF) : -1,
                com.eternalrealm.mount.forge.MountItems.DEED.get());
    }
}
