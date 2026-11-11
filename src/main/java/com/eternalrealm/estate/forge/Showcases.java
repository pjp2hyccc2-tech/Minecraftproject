package com.eternalrealm.estate.forge;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.estate.EstateKey;
import com.eternalrealm.estate.EstateResult;
import com.eternalrealm.estate.EstateStatus;
import com.eternalrealm.estate.Showcase;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Showcase estates for testing (docs/modules/testkit.md): every design a kind registers ({@link #register}: the inn's
 * five rooms, the twenty guild houses) can stand once more as a {@link Showcase} that no player or guild owns, and
 * players holding the test pass tour them — through the innkeeper's screen, the guild gates or
 * {@code /er admin test tour}. An operator grants the pass ({@code /er admin test unlock}); it lives in the player's
 * own persistent data (saved with the player, kept on respawn). Showcases are built when a pass is granted (one after
 * another, like any estate) or on the first tour, and are never torn down: what testers store in them stays there.
 * Server thread (the registration at mod construction).
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Showcases {
    private static final Logger LOG = LogUtils.getLogger();
    /** Key of the test pass in the player's persistent data. */
    static final String PASS = "eternalrealm_showcase_pass";

    /** What a kind shows: its designs (cheapest first) and the translation key of a design's name. */
    private record Kind(Supplier<List<String>> designs, Function<String, String> nameKey) {
    }

    private static volatile Map<String, Kind> kinds = Map.of();

    private Showcases() {
    }

    // ------------------------------------------------------------------ registration (mod construction)

    /** The designs of {@code kind} that testers may tour, and how a design is called. */
    public static synchronized void register(String kind, Supplier<List<String>> designs,
                                             Function<String, String> nameKey) {
        if (!EstateKey.isName(kind)) {
            throw new IllegalArgumentException("bad estate kind '" + kind + "'");
        }
        Map<String, Kind> next = new LinkedHashMap<>(kinds);
        next.put(kind, new Kind(designs, nameKey));
        kinds = Collections.unmodifiableMap(next);
    }

    /** The kinds with showcases, in the order they were registered. */
    public static List<String> kinds() {
        return List.copyOf(kinds.keySet());
    }

    /** The designs of {@code kind} (empty for a kind without showcases). */
    public static List<String> designs(String kind) {
        Kind found = kinds.get(kind);
        if (found == null) {
            return List.of();
        }
        try {
            return found.designs().get().stream().filter(EstateKey::isName).toList();
        } catch (RuntimeException e) {
            LOG.error("The designs of the estate kind {} could not be listed", kind, e);
            return List.of();
        }
    }

    public static boolean known(String kind, String design) {
        return designs(kind).contains(design);
    }

    /** A design's name as players read it. */
    public static Component name(String kind, String design) {
        Kind found = kinds.get(kind);
        return found == null ? Component.literal(design) : Component.translatable(found.nameKey().apply(design));
    }

    /** The design whose showcase {@code owner} is, if {@code owner} owns a showcase of {@code kind}. */
    public static Optional<String> showcaseDesign(String kind, UUID owner) {
        return Showcase.designOf(kind, owner, designs(kind));
    }

    // ------------------------------------------------------------------ the test pass

    /** Whether the player holds the test pass (the gates and the innkeeper then offer the showcases). */
    public static boolean hasPass(ServerPlayer player) {
        return player.getPersistentData().getBoolean(PASS);
    }

    /** Whether the player may be in showcases: the pass, or a creative operator (who may be in any estate). */
    public static boolean mayTour(ServerPlayer player) {
        return hasPass(player) || player.isCreative() && player.hasPermissions(2);
    }

    /** Hands the player the test pass. @return false if they had it already */
    public static boolean grant(ServerPlayer player) {
        if (hasPass(player)) {
            return false;
        }
        player.getPersistentData().putBoolean(PASS, true);
        LOG.info("Test kit: {} may tour the showcases", player.getGameProfile().getName());
        return true;
    }

    /** Takes the pass back; a player inside a showcase is taken out. @return false if they had none */
    public static boolean revoke(ServerPlayer player) {
        if (!hasPass(player)) {
            return false;
        }
        player.getPersistentData().remove(PASS);
        if (EstateService.at(player).filter(Showcase::is).isPresent() && !mayTour(player)) {
            EstateService.leave(player);
        }
        LOG.info("Test kit: {} no longer tours the showcases", player.getGameProfile().getName());
        return true;
    }

    /** The pass stays with the player (respawn, the way back from the End). */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (event.getOriginal().getPersistentData().getBoolean(PASS)) {
            event.getEntity().getPersistentData().putBoolean(PASS, true);
        }
    }

    // ------------------------------------------------------------------ the showcases

    public static EstateStatus status(String kind, String design) {
        return EstateService.status(Showcase.key(kind, design));
    }

    /**
     * Makes sure the showcase of {@code design} stands or is being built (claims it if it is not there yet).
     *
     * @return its status afterwards ({@link EstateStatus#NONE} if it cannot be built: unknown design, estates not
     * running)
     */
    public static EstateStatus ensure(String kind, String design) {
        if (!known(kind, design)) {
            return EstateStatus.NONE;
        }
        EstateKey key = Showcase.key(kind, design);
        EstateStatus status = EstateService.status(key);
        if (status.phase() == EstateStatus.Phase.NONE) {
            EstateResult claimed = EstateService.claim(key);
            if (claimed.ok()) {
                LOG.info("Building the showcase {}/{}", kind, design);
            } else {
                LOG.warn("The showcase {}/{} cannot be built: {}", kind, design, claimed);
            }
            status = EstateService.status(key);
        }
        return status;
    }

    /** Starts the build of every showcase not built yet. @return how many builds were started */
    public static int prepareAll() {
        int started = 0;
        for (String kind : kinds()) {
            for (String design : designs(kind)) {
                EstateKey key = Showcase.key(kind, design);
                if (EstateService.status(key).phase() == EstateStatus.Phase.NONE && EstateService.claim(key).ok()) {
                    started++;
                }
            }
        }
        if (started > 0) {
            LOG.info("Building {} showcase(s)", started);
        }
        return started;
    }

    /**
     * Takes {@code player} into the showcase of {@code design}: in when it stands, otherwise its build is started (if
     * needed) and the progress shown. Tells the player when it cannot be done.
     */
    public static EstateResult tour(ServerPlayer player, String kind, String design) {
        if (!known(kind, design)) {
            return tell(player, EstateResult.UNKNOWN_DESIGN);
        }
        if (!mayTour(player)) {
            return tell(player, EstateResult.NOT_ALLOWED);
        }
        if (!EstateService.available()) {
            return tell(player, EstateResult.UNAVAILABLE);
        }
        EstateStatus status = ensure(kind, design);
        if (!status.enterable()) {
            preparing(player, kind, design, status);
            return EstateResult.BUILDING;
        }
        EstateResult entered = EstateService.enter(player, Showcase.key(kind, design));
        if (entered.ok()) {
            welcome(player, kind, design);
        }
        return entered;
    }

    /** "The showcase is being prepared… n %" on the action bar. */
    public static void preparing(ServerPlayer player, String kind, String design, EstateStatus status) {
        player.displayClientMessage(Component.translatable("message.eternalrealm.showcase.preparing",
                name(kind, design), status.percent()).withStyle(ChatFormatting.YELLOW), true);
    }

    /** Title on arrival in a showcase: "Showcase: <design>", and below it that it is for testing. */
    public static void welcome(ServerPlayer player, String kind, String design) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable(
                "message.eternalrealm.showcase.welcome", name(kind, design)).withStyle(ChatFormatting.GOLD)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable(
                "message.eternalrealm.showcase.welcome_sub").withStyle(ChatFormatting.WHITE)));
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.2F);
    }

    private static EstateResult tell(ServerPlayer player, EstateResult result) {
        player.displayClientMessage(Component.translatable(result.messageKey(), 0).withStyle(ChatFormatting.RED),
                true);
        return result;
    }
}
