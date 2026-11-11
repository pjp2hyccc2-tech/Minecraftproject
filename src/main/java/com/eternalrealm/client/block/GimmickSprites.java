package com.eternalrealm.client.block;

import com.eternalrealm.block.forge.ShopSignBlock;
import com.eternalrealm.core.ModInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

/**
 * Block-atlas sprites of the canal town gimmicks (mipmapped like every block texture, so pennants and sign pictures stay
 * clean at street distance). Looked up once and kept until the block atlas is stitched again.
 */
final class GimmickSprites {
    static final int CORD = 0;
    static final int PENNANT = 1;
    static final int FLAG = 2;
    static final int LANTERN = 3;
    static final int LANTERN_CAP = 4;
    static final int LEAVES = 5;
    static final int FLOWER = 6;
    static final int SHIRT = 7;
    static final int TOWEL = 8;
    static final int SOCKS = 9;
    static final int TROUSERS = 10;
    static final int DRESS = 11;
    static final int PEG = 12;
    static final int IRON = 13;
    static final int RING = 14;
    static final int SIGN_EDGE = 15;
    static final int SIGN_CHAIN = 16;
    static final int SIGN_RING = 17;
    /** First of the shop sign boards, one per {@link ShopSignBlock.Icon} in order. */
    static final int SIGN_BOARD = 18;

    private static final ResourceLocation[] IDS;
    private static TextureAtlasSprite[] sprites;

    static {
        String[] names = {"bunting/cord", "bunting/pennant", "bunting/flag", "bunting/lantern", "bunting/lantern_cap",
                "bunting/leaves", "bunting/flower", "bunting/shirt", "bunting/towel", "bunting/socks",
                "bunting/trousers", "bunting/dress", "bunting/peg", "bunting/iron", "bunting/ring", "shop_sign/edge",
                "shop_sign/chain", "shop_sign/ring"};
        ShopSignBlock.Icon[] icons = ShopSignBlock.Icon.values();
        IDS = new ResourceLocation[names.length + icons.length];
        for (int i = 0; i < names.length; i++) {
            IDS[i] = new ResourceLocation(ModInfo.MOD_ID, "block/" + names[i]);
        }
        for (ShopSignBlock.Icon icon : icons) {
            IDS[SIGN_BOARD + icon.ordinal()] = new ResourceLocation(ModInfo.MOD_ID,
                    "block/shop_sign/" + icon.getSerializedName());
        }
    }

    private GimmickSprites() {
    }

    static TextureAtlasSprite get(int id) {
        TextureAtlasSprite[] all = sprites;
        if (all == null) {
            TextureAtlas atlas = Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
            all = new TextureAtlasSprite[IDS.length];
            for (int i = 0; i < IDS.length; i++) {
                all[i] = atlas.getSprite(IDS[i]);
            }
            sprites = all;
        }
        return all[id];
    }

    /** The block atlas was stitched again (resource reload): look the sprites up afresh. */
    static void invalidate() {
        sprites = null;
    }
}
