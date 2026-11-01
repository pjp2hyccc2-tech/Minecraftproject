package com.frostbound.selftest;

import com.frostbound.FrostboundMod;
import com.frostbound.core.CoreInfo;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.versions.forge.ForgeVersion;

/**
 * CI boot check, enabled with {@code -Dfrostbound.selftest=true} on a dedicated server.
 *
 * <p>Once the server has started it records what actually loaded, writes
 * {@code frostbound-selftest.json} into the server directory and stops the server.
 */
public final class BootSelfTest {
    public static final String FILE_NAME = "frostbound-selftest.json";

    private BootSelfTest() {
    }

    public static boolean isEnabled() {
        return Boolean.getBoolean("frostbound.selftest");
    }

    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        boolean modLoaded = ModList.get().isLoaded(FrostboundMod.MOD_ID);
        boolean coreLoaded = isClassPresent("com.frostbound.core.CoreInfo");
        int levels = 0;
        for (ServerLevel ignored : server.getAllLevels()) {
            levels++;
        }
        String minecraft = SharedConstants.getCurrentVersion().getName();
        String forge = ForgeVersion.getVersion();
        boolean pass = modLoaded && coreLoaded && CoreInfo.MOD_ID.equals(FrostboundMod.MOD_ID) && levels >= 3;

        String json = "{\n"
                + "  \"result\": \"" + (pass ? "pass" : "fail") + "\",\n"
                + "  \"modLoaded\": " + modLoaded + ",\n"
                + "  \"coreLoaded\": " + coreLoaded + ",\n"
                + "  \"levels\": " + levels + ",\n"
                + "  \"minecraft\": \"" + minecraft + "\",\n"
                + "  \"forge\": \"" + forge + "\"\n"
                + "}\n";
        Path out = server.getServerDirectory().toPath().resolve(FILE_NAME);
        try {
            Files.writeString(out, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            FrostboundMod.LOGGER.error("[FROSTBOUND] could not write self-test result", e);
        }
        FrostboundMod.LOGGER.info("[FROSTBOUND] boot self-test {}: {}", pass ? "PASS" : "FAIL", json.replace('\n', ' '));
        server.halt(false);
    }

    private static boolean isClassPresent(String name) {
        try {
            Class.forName(name, false, BootSelfTest.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
