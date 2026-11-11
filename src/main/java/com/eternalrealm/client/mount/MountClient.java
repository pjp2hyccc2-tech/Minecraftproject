package com.eternalrealm.client.mount;

import com.eternalrealm.client.ClientKeyMappings;
import com.eternalrealm.client.entity.model.RigTables;
import com.eternalrealm.client.entity.model.gen.BikeMountAnimations;
import com.eternalrealm.client.entity.model.gen.BikeMountGeometry;
import com.eternalrealm.client.entity.model.gen.GriffinMountAnimations;
import com.eternalrealm.client.entity.model.gen.GriffinMountGeometry;
import com.eternalrealm.client.entity.model.gen.HorseMountAnimations;
import com.eternalrealm.client.entity.model.gen.HorseMountGeometry;
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
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

/**
 * The mounts on the client (mod bus): the call and choose keys (U and I, in the game's key category), the models and
 * renderers of the three mounts, and the jump key the ridden mount reads.
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MountClient {
    /** Calls the chosen mount, or gets off it. */
    public static final KeyMapping CALL = key("mount", GLFW.GLFW_KEY_U);
    /** Opens the mount chooser. */
    public static final KeyMapping CHOOSE = key("mount_choose", GLFW.GLFW_KEY_I);

    public static final RigTables HORSE = new RigTables("mount_horse", HorseMountGeometry.VARIANTS,
            HorseMountGeometry.TOGGLED_PARTS, HorseMountGeometry.VISIBLE, HorseMountGeometry.POSED_PARTS,
            HorseMountGeometry.POSES, HorseMountGeometry.SCALES, HorseMountGeometry.GLOWS,
            HorseMountAnimations::forVariant);
    public static final RigTables GRIFFIN = new RigTables("mount_griffin", GriffinMountGeometry.VARIANTS,
            GriffinMountGeometry.TOGGLED_PARTS, GriffinMountGeometry.VISIBLE, GriffinMountGeometry.POSED_PARTS,
            GriffinMountGeometry.POSES, GriffinMountGeometry.SCALES, GriffinMountGeometry.GLOWS,
            GriffinMountAnimations::forVariant);
    public static final RigTables BIKE = new RigTables("mount_bike", BikeMountGeometry.VARIANTS,
            BikeMountGeometry.TOGGLED_PARTS, BikeMountGeometry.VISIBLE, BikeMountGeometry.POSED_PARTS,
            BikeMountGeometry.POSES, BikeMountGeometry.SCALES, BikeMountGeometry.GLOWS,
            BikeMountAnimations::forVariant);

    private MountClient() {
    }

    private static KeyMapping key(String name, int defaultKey) {
        return new KeyMapping("key." + ModInfo.MOD_ID + "." + name, KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, defaultKey, ClientKeyMappings.CATEGORY);
    }

    public static RigTables tables(MountKind kind) {
        return switch (kind) {
            case HORSE -> HORSE;
            case GRIFFIN -> GRIFFIN;
            case BIKE -> BIKE;
        };
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
        event.registerLayerDefinition(layer(MountKind.HORSE), () -> HorseMountGeometry.create(0.0F));
        event.registerLayerDefinition(layer(MountKind.GRIFFIN), () -> GriffinMountGeometry.create(0.0F));
        event.registerLayerDefinition(layer(MountKind.BIKE), () -> BikeMountGeometry.create(0.0F));
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
    }
}
