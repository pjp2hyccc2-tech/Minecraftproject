package com.birdiecraft.client;

import com.birdiecraft.BirdieCraft;
import com.birdiecraft.course.HoleMap;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/** GPU texture of the current hole overview map. */
public final class HoleMapTexture implements AutoCloseable {
    private static int counter;

    public final HoleMap map;
    public final ResourceLocation location;
    private final DynamicTexture texture;

    public HoleMapTexture(HoleMap map) {
        this.map = map;
        NativeImage img = new NativeImage(map.cols, map.rows, false);
        for (int r = 0; r < map.rows; r++) {
            for (int c = 0; c < map.cols; c++) {
                int i = r * map.cols + c;
                int rgb = color(map.codes[i], c, r);
                int h = map.heights[i];
                rgb = shade(rgb, 1.0 + h * 0.035);
                // slight relief: compare with the cell further from the tee
                if (r + 1 < map.rows) {
                    int hn = map.heights[i + map.cols];
                    if (hn > h) rgb = shade(rgb, 0.86);
                    else if (hn < h) rgb = shade(rgb, 1.1);
                }
                img.setPixelRGBA(c, map.rows - 1 - r, abgr(rgb));
            }
        }
        this.texture = new DynamicTexture(img);
        this.location = BirdieCraft.id("dynamic/hole_map_" + (counter++));
        Minecraft.getInstance().getTextureManager().register(location, texture);
    }

    private static int color(byte code, int c, int r) {
        boolean checker = ((c + r) & 1) == 0;
        return switch (code) {
            case HoleMap.FAIRWAY -> 0x6CC84A;
            case HoleMap.FAIRWAY_DARK -> 0x5DB53E;
            case HoleMap.GREEN, HoleMap.CUP -> checker ? 0x8BE36A : 0x83DB62;
            case HoleMap.FRINGE -> 0x72CF51;
            case HoleMap.ROUGH -> checker ? 0x3F8E2C : 0x3B872A;
            case HoleMap.DEEP -> 0x2F6B22;
            case HoleMap.TEE -> 0x7ED65A;
            case HoleMap.BUNKER -> checker ? 0xEBDDB0 : 0xE3D3A2;
            case HoleMap.WATER -> checker ? 0x3C8CDB : 0x3683D1;
            case HoleMap.LAVA -> checker ? 0xF06A1F : 0xE65A14;
            case HoleMap.ICE -> 0xA9D7F2;
            case HoleMap.TREE -> checker ? 0x1E4E1A : 0x224F1C;
            case HoleMap.PATH -> 0xBDBDB5;
            case HoleMap.STAKE -> 0xF4F4F4;
            case HoleMap.OB_SAND -> 0xC9B27A;
            case HoleMap.SNOW -> 0xE8EEF2;
            case HoleMap.ROCK -> 0x6E6A66;
            case HoleMap.OB -> 0x557A3C;
            default -> 0x2A2A2A;
        };
    }

    private static int shade(int rgb, double f) {
        int r = (int) Math.max(0, Math.min(255, ((rgb >> 16) & 0xFF) * f));
        int g = (int) Math.max(0, Math.min(255, ((rgb >> 8) & 0xFF) * f));
        int b = (int) Math.max(0, Math.min(255, (rgb & 0xFF) * f));
        return (r << 16) | (g << 8) | b;
    }

    private static int abgr(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return 0xFF000000 | (b << 16) | (g << 8) | r;
    }

    @Override
    public void close() {
        Minecraft.getInstance().getTextureManager().release(location);
    }
}
