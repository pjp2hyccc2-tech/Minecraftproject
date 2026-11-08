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
            List.of("core", "season", "character", "player", "persistence", "network", "combat", "job", "skill", "item", "economy", "party", "guild", "ranking", "quest", "world", "monster", "boss", "dungeon", "pvp");
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
        String code = stripComments(Files.readString(file, StandardCharsets.UTF_8));
        String[] lines = code.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            for (String reference : forbidden) {
                if (line.contains(reference)) {
                    violations.add(file + ":" + (i + 1) + " -> " + reference + "   [" + line.trim() + "]");
                }
            }
        }
    }

    /**
     * Removes {@code //} and {@code /* *\/} comments (documentation may mention any package) while keeping string
     * literals and line breaks, so reported line numbers stay correct.
     */
    static String stripComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        while (i < source.length()) {
            char c = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
            if (c == '/' && next == '/') {
                while (i < source.length() && source.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && next == '*') {
                i += 2;
                while (i < source.length() && !(source.charAt(i) == '*' && i + 1 < source.length() && source.charAt(i + 1) == '/')) {
                    if (source.charAt(i) == '\n') {
                        out.append('\n');
                    }
                    i++;
                }
                i += 2;
            } else if (c == '"' || c == '\'') {
                out.append(c);
                i++;
                while (i < source.length() && source.charAt(i) != c && source.charAt(i) != '\n') {
                    if (source.charAt(i) == '\\' && i + 1 < source.length()) {
                        out.append(source.charAt(i++));
                    }
                    out.append(source.charAt(i++));
                }
                if (i < source.length()) {
                    out.append(source.charAt(i++));
                }
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    @Test
    public void commentStrippingKeepsCodeAndLineNumbers() {
        String source = "import a.b; // net.minecraft.client.X\n/* com.eternalrealm.ui\n */ String s = \"// not a comment\";\n";
        String stripped = stripComments(source);
        assertTrue(stripped.contains("import a.b;"));
        assertTrue(stripped.contains("\"// not a comment\""));
        assertTrue(!stripped.contains("net.minecraft.client") && !stripped.contains("com.eternalrealm.ui"));
        assertTrue(stripped.split("\n", -1).length == source.split("\n", -1).length);
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
