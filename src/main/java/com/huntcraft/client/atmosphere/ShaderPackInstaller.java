package com.huntcraft.client.atmosphere;

import com.huntcraft.HuntCraft;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Ships the bundled "HuntCraft Shaders" pack ({@code /HuntCraftShaders.zip} in the mod jar) into the game's
 * {@code shaderpacks/} directory and, when Oculus (or Iris) is present and no pack is selected yet, selects it.
 * OptiFine users get {@code optionsshaders.txt} pre-filled so the pack is active on the next launch.
 * Everything is best-effort and never throws into mod loading.
 */
@Mod.EventBusSubscriber(modid = HuntCraft.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ShaderPackInstaller {
    public static final String PACK_FILE = "HuntCraftShaders.zip";
    private static final String RESOURCE = "/" + PACK_FILE;
    private static volatile String status = "not run";
    private static volatile boolean installed;
    /** Translation key of a one-time chat message for the player (consumed by AtmosphereEvents), or null. */
    private static volatile String pendingMessage;

    private ShaderPackInstaller() {
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(ShaderPackInstaller::install);
    }

    /** Human readable summary for logs / the autotest. */
    public static String status() {
        return status;
    }

    public static boolean isInstalled() {
        return installed;
    }

    /** Returns and clears the pending one-time chat message key. */
    public static String takePendingMessage() {
        String m = pendingMessage;
        pendingMessage = null;
        return m;
    }

    public static boolean hasShaderLoader() {
        return ModList.get().isLoaded("oculus") || ModList.get().isLoaded("iris") || isOptiFine();
    }

    static boolean isOptiFine() {
        try {
            Class.forName("net.optifine.Config");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static synchronized void install() {
        try {
            Path gameDir = FMLPaths.GAMEDIR.get();
            Path packsDir = gameDir.resolve("shaderpacks");
            boolean oculus = ModList.get().isLoaded("oculus") || ModList.get().isLoaded("iris");
            boolean optifine = isOptiFine();
            if (!oculus && !optifine && !Files.isDirectory(packsDir)) {
                status = "no shader loader (Oculus/Iris/OptiFine) and no shaderpacks/ directory - pack not installed";
                HuntCraft.LOGGER.info("[HuntCraft Shaders] {}", status);
                return;
            }
            byte[] data = readResource();
            if (data == null) {
                status = "bundled " + PACK_FILE + " missing from the mod jar";
                HuntCraft.LOGGER.warn("[HuntCraft Shaders] {}", status);
                return;
            }
            Files.createDirectories(packsDir);
            Path target = packsDir.resolve(PACK_FILE);
            boolean copied = false;
            if (!Files.exists(target) || !Arrays.equals(sha1(data), sha1(Files.readAllBytes(target)))) {
                Files.write(target, data);
                copied = true;
            }
            installed = true;
            List<String> notes = new ArrayList<>();
            notes.add(copied ? "copied " + PACK_FILE + " to " + target : PACK_FILE + " up to date in " + packsDir);
            if (oculus) notes.add(selectInOculus(copied));
            if (optifine) notes.add(selectInOptiFine(gameDir));
            status = String.join("; ", notes);
            HuntCraft.LOGGER.info("[HuntCraft Shaders] {}", status);
        } catch (Throwable t) {
            status = "install failed: " + t;
            HuntCraft.LOGGER.warn("[HuntCraft Shaders] could not install the shader pack", t);
        }
    }

    private static byte[] readResource() throws IOException {
        try (InputStream in = ShaderPackInstaller.class.getResourceAsStream(RESOURCE)) {
            if (in == null) return null;
            return in.readAllBytes();
        }
    }

    private static byte[] sha1(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-1").digest(data);
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private static Class<?> irisClass() {
        for (String name : new String[]{"net.irisshaders.iris.Iris", "net.coderbot.iris.Iris"}) {
            try {
                return Class.forName(name);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    /**
     * Oculus keeps its selection in config/oculus.properties (read at Options.load, long before mods run), so the
     * selection is changed through its public API: IrisConfig#setShaderPackName + Iris#loadShaderpackWhenPossible.
     */
    @SuppressWarnings("unchecked")
    private static String selectInOculus(boolean packUpdated) {
        Class<?> iris = irisClass();
        if (iris == null) return "Oculus loaded but its Iris class was not found";
        try {
            Object config = iris.getMethod("getIrisConfig").invoke(null);
            if (config == null) return "Oculus config not initialised yet";
            Optional<String> current = (Optional<String>) config.getClass().getMethod("getShaderPackName").invoke(config);
            boolean enabled = (Boolean) config.getClass().getMethod("areShadersEnabled").invoke(config);
            boolean select = current.isEmpty();
            boolean reload = select || (packUpdated && current.get().equals(PACK_FILE) && enabled);
            if (select) {
                config.getClass().getMethod("setShaderPackName", String.class).invoke(config, PACK_FILE);
                config.getClass().getMethod("setShadersEnabled", boolean.class).invoke(config, true);
                config.getClass().getMethod("save").invoke(config);
            }
            if (reload) {
                try {
                    iris.getMethod("loadShaderpackWhenPossible").invoke(null);
                } catch (NoSuchMethodException e) {
                    Method reloadMethod = iris.getMethod("reload");
                    Minecraft.getInstance().execute(() -> {
                        try {
                            reloadMethod.invoke(null);
                        } catch (Exception ex) {
                            HuntCraft.LOGGER.warn("[HuntCraft Shaders] Oculus reload failed", ex);
                        }
                    });
                }
            }
            if (select) {
                pendingMessage = "huntcraft.shaders.installed";
                return "selected " + PACK_FILE + " in Oculus (no pack was selected)";
            }
            if (reload) pendingMessage = "huntcraft.shaders.updated";
            return "Oculus pack already selected: " + current.get() + (reload ? " (reloading updated pack)" : "");
        } catch (Throwable t) {
            return "Oculus selection failed: " + t;
        }
    }

    /** OptiFine reads optionsshaders.txt at startup; write the selection for the next launch and try to apply it now. */
    private static String selectInOptiFine(Path gameDir) {
        try {
            Path opts = gameDir.resolve("optionsshaders.txt");
            List<String> lines = Files.exists(opts) ? new ArrayList<>(Files.readAllLines(opts, StandardCharsets.UTF_8)) : new ArrayList<>();
            String current = null;
            int idx = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).startsWith("shaderPack=")) {
                    idx = i;
                    current = lines.get(i).substring("shaderPack=".length()).trim();
                }
            }
            boolean none = current == null || current.isEmpty() || current.equals("OFF") || current.equals("(internal)");
            if (!none) return "OptiFine pack already selected: " + current;
            if (idx >= 0) lines.set(idx, "shaderPack=" + PACK_FILE);
            else lines.add("shaderPack=" + PACK_FILE);
            Files.write(opts, lines, StandardCharsets.UTF_8);
            pendingMessage = "huntcraft.shaders.optifine";
            try {
                Class<?> shaders = Class.forName("net.optifine.shaders.Shaders");
                shaders.getMethod("setShaderPack", String.class).invoke(null, PACK_FILE);
                shaders.getMethod("uninit").invoke(null);
                return "selected " + PACK_FILE + " in OptiFine";
            } catch (Throwable t) {
                return "wrote optionsshaders.txt; OptiFine will use " + PACK_FILE + " from the next launch";
            }
        } catch (Throwable t) {
            return "OptiFine selection failed: " + t;
        }
    }

    /** What the shader loader is doing right now (for the autotest log). */
    public static String activePackInfo() {
        Class<?> iris = irisClass();
        if (iris == null) return isOptiFine() ? "optifine present" : "no shader loader";
        try {
            Object name = iris.getMethod("getCurrentPackName").invoke(null);
            String inUse;
            try {
                inUse = String.valueOf(iris.getMethod("isPackInUseQuick").invoke(null));
            } catch (NoSuchMethodException e) {
                Optional<?> pack = (Optional<?>) iris.getMethod("getCurrentPack").invoke(null);
                inUse = String.valueOf(pack.isPresent());
            }
            Object config = iris.getMethod("getIrisConfig").invoke(null);
            Object selected = config == null ? "?" : config.getClass().getMethod("getShaderPackName").invoke(config);
            return "oculus pack=" + name + " selected=" + selected + " inUse=" + inUse;
        } catch (Throwable t) {
            return "oculus query failed: " + t;
        }
    }
}
