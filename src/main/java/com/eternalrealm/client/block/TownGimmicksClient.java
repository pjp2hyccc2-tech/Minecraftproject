package com.eternalrealm.client.block;

import com.eternalrealm.block.forge.TownGimmickBlocks;
import com.eternalrealm.core.ModInfo;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client wiring of the canal town gimmicks ({@link TownGimmickBlocks}): their renderers and sprite cache. */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TownGimmicksClient {
    private TownGimmicksClient() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(TownGimmickBlocks.BUNTING_ENTITY.get(), BuntingRenderer::new);
        event.registerBlockEntityRenderer(TownGimmickBlocks.SECRET_DOOR_ENTITY.get(), SecretDoorRenderer::new);
        event.registerBlockEntityRenderer(TownGimmickBlocks.SHOP_SIGN_ENTITY.get(), ShopSignRenderer::new);
        event.registerBlockEntityRenderer(TownGimmickBlocks.FLOATING_LANTERN_ENTITY.get(),
                FloatingLanternRenderer::new);
    }

    @SubscribeEvent
    public static void onTextureStitch(TextureStitchEvent.Post event) {
        if (TextureAtlas.LOCATION_BLOCKS.equals(event.getAtlas().location())) {
            GimmickSprites.invalidate();
        }
    }
}
