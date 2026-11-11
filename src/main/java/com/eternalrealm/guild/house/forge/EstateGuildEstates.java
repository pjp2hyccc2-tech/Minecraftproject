package com.eternalrealm.guild.house.forge;

import com.eternalrealm.estate.EstateKey;
import com.eternalrealm.estate.EstateResult;
import com.eternalrealm.estate.EstateStatus;
import com.eternalrealm.estate.Showcase;
import com.eternalrealm.estate.forge.EstateContents;
import com.eternalrealm.estate.forge.EstateService;
import com.eternalrealm.estate.forge.Showcases;
import com.eternalrealm.guild.house.GuildHouseCatalog;
import com.eternalrealm.guild.house.GuildHouseDesign;
import com.eternalrealm.server.ServerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Optional;
import java.util.UUID;

/**
 * The guild houses on the shared estates core (docs/modules/estates.md): a guild's house is the estate
 * {@code guild/<guild id>} whose design is the catalogue id. Members of the owning guild may enter and use it (the
 * access rule registered here); everyone else is refused at the gate and sent out if they get in some other way.
 */
public final class EstateGuildEstates implements GuildEstates {
    static final EstateGuildEstates INSTANCE = new EstateGuildEstates();

    private EstateGuildEstates() {
    }

    /**
     * Registers the member rule, the "house is ready" listener and the designs testers may tour with the estates and
     * installs the bridge.
     */
    static void install() {
        EstateService.registerAccess(EstateKey.GUILD, EstateGuildEstates::mayEnter);
        Showcases.register(EstateKey.GUILD, () -> GuildHouseCatalog.standard().designs().stream()
                .map(GuildHouseDesign::id).toList(), id -> GuildHouseCatalog.standard().design(id)
                .map(GuildHouseDesign::nameKey).orElse(id));
        EstateService.onReady((level, key) -> {
            if (EstateKey.GUILD.equals(key.kind())) {
                GuildHouses.get().ifPresent(houses -> houses.onReady(key.owner()));
            }
        });
        GuildHouses.setEstates(INSTANCE);
    }

    /** Members of the owning guild. Called every half second for each player inside: a map lookup. */
    static boolean mayEnter(ServerPlayer player, EstateKey estate) {
        return ServerContext.current()
                .map(context -> context.game().guilds())
                .filter(guilds -> guilds.available())
                .flatMap(guilds -> guilds.service().guildOf(player.getUUID()))
                .map(guild -> guild.id().equals(estate.owner()))
                .orElse(false);
    }

    private static Result of(EstateResult result) {
        return switch (result) {
            case OK -> Result.OK;
            case UNAVAILABLE -> Result.UNAVAILABLE;
            case BUILDING, BUSY -> Result.BUILDING;
            case NOT_CLAIMED -> Result.NOT_CLAIMED;
            default -> Result.FAILED;
        };
    }

    @Override
    public boolean available() {
        return EstateService.available();
    }

    @Override
    public Result claim(UUID guild, String design) {
        return of(EstateService.claim(new EstateKey(EstateKey.GUILD, guild, design)));
    }

    @Override
    public Result redesign(UUID guild, String design) {
        Optional<EstateKey> key = EstateService.estate(EstateKey.GUILD, guild);
        if (key.isEmpty()) {
            return Result.NOT_CLAIMED;
        }
        if (key.get().design().equals(design)) {
            return Result.OK;
        }
        return of(EstateService.redesign(key.get(), design));
    }

    @Override
    public Result release(UUID guild) {
        return EstateService.estate(EstateKey.GUILD, guild).map(key -> of(EstateService.release(key)))
                .orElse(Result.NOT_CLAIMED);
    }

    @Override
    public boolean ready(UUID guild) {
        return EstateService.estate(EstateKey.GUILD, guild).map(EstateService::ready).orElse(false);
    }

    @Override
    public int percent(UUID guild) {
        return EstateService.estate(EstateKey.GUILD, guild).map(EstateService::status)
                .map(EstateStatus::percent).orElse(0);
    }

    @Override
    public Result enter(ServerPlayer player, UUID guild) {
        return EstateService.estate(EstateKey.GUILD, guild).map(key -> of(EstateService.enter(player, key)))
                .orElse(Result.NOT_CLAIMED);
    }

    @Override
    public boolean leave(ServerPlayer player) {
        return EstateService.leave(player);
    }

    @Override
    public Optional<UUID> houseAt(Level level, BlockPos pos) {
        return EstateService.at(level, pos).filter(key -> EstateKey.GUILD.equals(key.kind())).map(EstateKey::owner);
    }

    @Override
    public boolean isShowcase(UUID owner) {
        return Showcases.showcaseDesign(EstateKey.GUILD, owner).isPresent();
    }

    @Override
    public boolean hasTourPass(ServerPlayer player) {
        return Showcases.hasPass(player);
    }

    @Override
    public boolean mayTour(ServerPlayer player) {
        return Showcases.mayTour(player);
    }

    @Override
    public int prepareShowcase(String design) {
        EstateStatus status = Showcases.ensure(EstateKey.GUILD, design);
        if (status.phase() == EstateStatus.Phase.NONE) {
            return -1;
        }
        return status.enterable() ? 100 : Math.min(99, status.percent());
    }

    @Override
    public Result enterShowcase(ServerPlayer player, String design) {
        return of(EstateService.enter(player, Showcase.key(EstateKey.GUILD, design)));
    }

    @Override
    public void welcomeShowcase(ServerPlayer player, String design) {
        Showcases.welcome(player, EstateKey.GUILD, design);
    }

    @Override
    public Optional<String> pieceName(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return Optional.empty();
        }
        String name = blockEntity.getPersistentData().getString(EstateContents.SLOT);
        return name.isEmpty() ? Optional.empty() : Optional.of(name);
    }
}
