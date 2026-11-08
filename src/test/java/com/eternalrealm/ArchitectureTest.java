package com.eternalrealm;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Enforces the module rules of docs/ARCHITECTURE.md on the source tree:
 * <ol>
 *     <li>Pure modules (game rules, persistence, protocol logic) never depend on Minecraft/Forge or the platform layer,
 *         so they stay unit-testable and portable to a future multi-server backend.</li>
 *     <li>Only {@code client} and {@code ui} may touch client-only classes, so a dedicated server never loads them.</li>
 * </ol>
 */
public class ArchitectureTest {
    private static final Path SOURCE_ROOT = Paths.get("src", "main", "java", "com", "eternalrealm");
    private static final List<String> PURE_MODULES =
            List.of("core", "season", "character", "player", "persistence", "network");
    private static final List<String> PLATFORM_REFERENCES = List.of(
            "net.minecraft.", "net.minecraftforge.", "com.mojang.", "org.lwjgl.", "io.netty.",
            "com.eternalrealm.bootstrap", "com.eternalrealm.server.", "com.eternalrealm.config",
            "com.eternalrealm.client", "com.eternalrealm.ui", "com.eternalrealm.gametest",
            ".forge.", "com.eternalrealm.EternalRealm");
    private static final List<String> CLIENT_ONLY_REFERENCES = List.of(
            "net.minecraft.client.", "com.mojang.blaze3d.", "org.lwjgl.",
            "com.eternalrealm.client", "com.eternalrealm.ui");

    @Test
    public void pureModulesDoNotDependOnThePlatform() throws IOException {
        List<String> violations = new ArrayList<>();
        int checked = 0;
        for (String module : PURE_MODULES) {
            for (Path file : javaFiles(SOURCE_ROOT.resolve(module))) {
                if (isUnder(file, "forge")) {
                    continue;
                }
                checked++;
                collect(file, PLATFORM_REFERENCES, violations);
            }
        }
        assertTrue("no pure sources found - is the working directory the project root?", checked > 20);
        assertNoViolations("Pure module depends on the platform layer", violations);
    }

    @Test
    public void onlyClientPackagesReferenceClientOnlyClasses() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : javaFiles(SOURCE_ROOT)) {
            Path relative = SOURCE_ROOT.relativize(file);
            String top = relative.getNameCount() > 1 ? relative.getName(0).toString() : "";
            if (top.equals("client") || top.equals("ui")) {
                continue;
            }
            collect(file, CLIENT_ONLY_REFERENCES, violations);
        }
        assertNoViolations("Common/server code references client-only classes", violations);
    }

    private static void collect(Path file, List<String> forbidden, List<String> violations) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            for (String reference : forbidden) {
                if (line.contains(reference)) {
                    violations.add(file + ":" + (i + 1) + " -> " + reference + "   [" + line.trim() + "]");
                }
            }
        }
    }

    private static boolean isUnder(Path file, String directoryName) {
        for (Path part : file) {
            if (part.toString().equals(directoryName)) {
                return true;
            }
        }
        return false;
    }

    private static List<Path> javaFiles(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
    }

    private static void assertNoViolations(String message, List<String> violations) {
        if (!violations.isEmpty()) {
            fail(message + ":\n  " + String.join("\n  ", violations));
        }
    }
}
