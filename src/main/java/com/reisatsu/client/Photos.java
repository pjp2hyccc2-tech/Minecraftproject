package com.reisatsu.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.reisatsu.Reisatsu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.LevelResource;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** The night's photos on disk ({@code <game dir>/reisatsu/photos/<world>/<id>.png}) and as textures. */
public final class Photos {
    private static final Map<String, ResourceLocation> TEX = new HashMap<>();
    private static final Set<String> MISSING = new HashSet<>();

    private Photos() {}

    /** The folder of this world's photos. */
    @Nullable
    public static Path dir() {
        Minecraft mc = Minecraft.getInstance();
        return mc.gameDirectory.toPath().resolve("reisatsu").resolve("photos").resolve(worldName(mc));
    }

    @Nullable
    public static Path file(String id) {
        Path d = dir();
        return d == null ? null : d.resolve(id + ".png");
    }

    static String worldName(Minecraft mc) {
        String name = "world";
        try {
            IntegratedServer sp = mc.getSingleplayerServer();
            if (sp != null) {
                Path root = sp.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
                if (root.getFileName() != null) name = root.getFileName().toString();
            } else if (mc.getCurrentServer() != null) {
                name = "server_" + mc.getCurrentServer().ip;
            }
        } catch (Exception ignored) {
        }
        StringBuilder b = new StringBuilder();
        for (char c : name.toCharArray()) b.append(Character.isLetterOrDigit(c) || c == '-' || c == '_' ? c : '_');
        return b.length() == 0 ? "world" : b.toString();
    }

    /** A freshly developed photo becomes a texture (the texture owns the image). */
    public static void register(String id, NativeImage img) {
        ResourceLocation rl = Reisatsu.id("photo/" + id);
        Minecraft.getInstance().getTextureManager().register(rl, new DynamicTexture(img));
        TEX.put(id, rl);
        MISSING.remove(id);
    }

    /** The texture of a photo (loaded from disk the first time), or null if it is not on this computer. */
    @Nullable
    public static ResourceLocation texture(String id) {
        if (id == null || id.isEmpty()) return null;
        ResourceLocation rl = TEX.get(id);
        if (rl != null) return rl;
        if (MISSING.contains(id)) return null;
        Path f = file(id);
        if (f != null && Files.exists(f)) {
            try (InputStream in = Files.newInputStream(f)) {
                NativeImage img = NativeImage.read(in);
                register(id, img);
                return TEX.get(id);
            } catch (Exception e) {
                Reisatsu.LOGGER.warn("[reisatsu] could not read photo {}: {}", f, e.toString());
            }
        }
        MISSING.add(id);
        return null;
    }

    public static void forgetMissing() {
        MISSING.clear();
    }
}
