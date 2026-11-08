package com.eternalrealm.party;

import com.eternalrealm.economy.AuctionOutcome;
import com.eternalrealm.economy.EconomyMessages;
import com.eternalrealm.economy.MailReason;
import com.eternalrealm.economy.ShopOutcome;
import com.eternalrealm.economy.TradeEndReason;
import com.eternalrealm.economy.TradeOutcome;
import com.eternalrealm.economy.WalletResult;
import com.eternalrealm.guild.GuildMessages;
import com.eternalrealm.guild.GuildOutcome;
import com.eternalrealm.guild.GuildRank;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The party, guild and economy texts live in docs/i18n/party.json until the integrator merges them into the lang
 * files. Keeps that file and the keys referenced by the code in sync, so no message is ever shown as a raw key.
 */
public class SocialTranslationsTest {
    private static final Path FILE = Paths.get("docs", "i18n", "party.json");
    private static final Pattern KEY_FORMAT = Pattern.compile("[a-z_]+\\.eternalrealm\\.[a-z0-9_.]+");
    private static final Pattern PLACEHOLDER = Pattern.compile("%(?:(\\d+)\\$)?s");
    private static final Pattern RAW_KEY = Pattern.compile("(?m)^\\s*\"([^\"]+)\"\\s*:\\s*\\{");

    @Test
    public void everyKeyUsedByTheCodeIsTranslatedAndNoOtherKey() throws Exception {
        assertEquals(codeKeys(), new TreeSet<>(load().keySet()));
    }

    @Test
    public void everyEntryHasJapaneseAndEnglishWithTheSameArguments() throws IOException {
        for (Map.Entry<String, JsonElement> entry : load().entrySet()) {
            String key = entry.getKey();
            assertTrue("key format: " + key, KEY_FORMAT.matcher(key).matches());
            JsonObject texts = entry.getValue().getAsJsonObject();
            assertEquals(key, Set.of("ja", "en"), texts.keySet());
            String japanese = texts.get("ja").getAsString();
            String english = texts.get("en").getAsString();
            assertFalse(key, japanese.isBlank() || english.isBlank());
            assertEquals("placeholders of " + key, placeholders(english), placeholders(japanese));
        }
    }

    /** The keys are unique and merged into the lang files with exactly these texts. */
    @Test
    public void keysAreUniqueAndMergedIntoTheLangFiles() throws IOException {
        Matcher matcher = RAW_KEY.matcher(Files.readString(FILE, StandardCharsets.UTF_8));
        Set<String> seen = new HashSet<>();
        while (matcher.find()) {
            assertTrue("duplicate key " + matcher.group(1), seen.add(matcher.group(1)));
        }
        Path lang = Paths.get("src", "main", "resources", "assets", "eternalrealm", "lang");
        JsonObject english = JsonParser.parseString(Files.readString(lang.resolve("en_us.json"), StandardCharsets.UTF_8))
                .getAsJsonObject();
        JsonObject japanese = JsonParser.parseString(Files.readString(lang.resolve("ja_jp.json"), StandardCharsets.UTF_8))
                .getAsJsonObject();
        JsonObject source = load();
        for (String key : seen) {
            JsonObject texts = source.getAsJsonObject(key);
            assertTrue("not merged: " + key, english.has(key) && japanese.has(key));
            assertEquals(key, texts.get("en").getAsString(), english.get(key).getAsString());
            assertEquals(key, texts.get("ja").getAsString(), japanese.get(key).getAsString());
        }
    }

    private static JsonObject load() throws IOException {
        return JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    /** Argument indices used by a text ({@code %s} counts up, {@code %2$s} is explicit), sorted. */
    private static List<Integer> placeholders(String text) {
        Matcher matcher = PLACEHOLDER.matcher(text.replace("%%", ""));
        List<Integer> arguments = new ArrayList<>();
        int next = 1;
        while (matcher.find()) {
            arguments.add(matcher.group(1) != null ? Integer.parseInt(matcher.group(1)) : next++);
        }
        Collections.sort(arguments);
        return arguments;
    }

    private static Set<String> codeKeys() throws IllegalAccessException {
        Set<String> keys = new TreeSet<>();
        for (Class<?> holder : List.of(PartyMessages.class, GuildMessages.class, EconomyMessages.class)) {
            for (Field field : holder.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && field.getType() == String.class) {
                    keys.add((String) field.get(null));
                }
            }
        }
        for (PartyType type : PartyType.values()) {
            keys.add(type.translationKey());
        }
        for (PartyOutcome outcome : PartyOutcome.values()) {
            outcome.messageKey().ifPresent(keys::add);
        }
        for (GuildRank rank : GuildRank.values()) {
            keys.add(rank.translationKey());
        }
        for (GuildOutcome outcome : GuildOutcome.values()) {
            outcome.messageKey().ifPresent(keys::add);
        }
        for (WalletResult.Status status : WalletResult.Status.values()) {
            status.messageKey().ifPresent(keys::add);
        }
        for (TradeOutcome outcome : TradeOutcome.values()) {
            outcome.messageKey().ifPresent(keys::add);
        }
        for (TradeEndReason reason : TradeEndReason.values()) {
            keys.add(reason.messageKey());
        }
        for (AuctionOutcome outcome : AuctionOutcome.values()) {
            outcome.messageKey().ifPresent(keys::add);
        }
        for (ShopOutcome outcome : ShopOutcome.values()) {
            outcome.messageKey().ifPresent(keys::add);
        }
        for (MailReason reason : MailReason.values()) {
            keys.add(reason.translationKey());
        }
        return keys;
    }
}
