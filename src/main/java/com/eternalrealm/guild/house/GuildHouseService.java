package com.eternalrealm.guild.house;

import com.eternalrealm.guild.GuildRank;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The guild houses of one logical server: which design each guild owns, its house fund, its board and storage log
 * ({@link GuildHouseRules} explains the rules). Server thread only; pure. Membership and ranks come from the guild
 * module (the caller passes the acting player's {@link GuildRank}); gold is charged by the caller after a successful
 * operation ({@link GuildHouseResult#fromWallet()}), within the same tick as the check that preceded it.
 *
 * <p><b>Persistence:</b> every change increments {@link #revision()}; save {@link #houses()} and report back with
 * {@link #markSaved(long)} like the guild directory.</p>
 */
public final class GuildHouseService {
    private final GuildHouseCatalog catalog;
    private final Map<UUID, GuildHouse> houses = new LinkedHashMap<>();
    private long revision;
    private long savedRevision;

    /** @throws IllegalArgumentException if a stored house names an unknown design or a guild twice */
    public GuildHouseService(GuildHouseCatalog catalog, Collection<GuildHouse> stored) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        for (GuildHouse house : stored) {
            if (house.design() != null && !catalog.contains(house.design())) {
                throw new IllegalArgumentException("guild " + house.guildId() + " owns unknown design " + house.design());
            }
            if (houses.put(house.guildId(), house) != null) {
                throw new IllegalArgumentException("guild " + house.guildId() + " stored twice");
            }
        }
    }

    public GuildHouseCatalog catalog() {
        return catalog;
    }

    /** The guild's data (an empty record if it never had any). */
    public GuildHouse house(UUID guildId) {
        GuildHouse house = houses.get(Objects.requireNonNull(guildId, "guildId"));
        return house == null ? GuildHouse.empty(guildId) : house;
    }

    /** The design the guild owns. */
    public Optional<GuildHouseDesign> design(UUID guildId) {
        return house(guildId).designId().flatMap(catalog::design);
    }

    /** Snapshots of every guild's data (the form to persist). */
    public List<GuildHouse> houses() {
        return List.copyOf(houses.values());
    }

    /** Guilds owning a house, with their designs. */
    public Map<UUID, String> owners() {
        Map<UUID, String> owners = new LinkedHashMap<>();
        houses.values().stream().filter(GuildHouse::hasHouse).forEach(h -> owners.put(h.guildId(), h.design()));
        return owners;
    }

    // ------------------------------------------------------------------------------------------------ gates

    /**
     * What the gate of {@code gateDesign} does with a player whose guild is {@code guildId} ({@code null}: in no
     * guild): into the guild's own house when the guild owns exactly that design, else a refusal.
     */
    public GateAccess gate(String gateDesign, UUID guildId) {
        if (guildId == null) {
            return GateAccess.NOT_IN_GUILD;
        }
        GuildHouse house = house(guildId);
        if (!house.hasHouse()) {
            return GateAccess.NO_HOUSE;
        }
        return house.design().equals(gateDesign) ? GateAccess.ENTER : GateAccess.OTHER_DESIGN;
    }

    // ------------------------------------------------------------------------------------------------ buying

    /** Whether {@link #buy} would succeed (same arguments), without changing anything. */
    public GuildHouseResult checkBuy(UUID guildId, GuildRank rank, String designId, long wallet) {
        GuildHouse house = house(guildId);
        Optional<GuildHouseDesign> design = catalog.design(designId);
        GuildHouseOutcome denied = rank == null ? GuildHouseOutcome.NOT_IN_GUILD
                : !GuildHouseRules.mayManage(rank) ? GuildHouseOutcome.NO_PERMISSION
                : design.isEmpty() ? GuildHouseOutcome.UNKNOWN_DESIGN
                : house.hasHouse() ? GuildHouseOutcome.ALREADY_OWNED : null;
        if (denied != null) {
            return GuildHouseResult.failure(denied, house);
        }
        long price = design.get().price();
        GuildHouseRules.Payment payment = GuildHouseRules.pay(price, house.fund(), Math.max(0, wallet));
        if (!payment.affordable()) {
            return GuildHouseResult.failure(GuildHouseOutcome.NOT_ENOUGH_GOLD, house);
        }
        return new GuildHouseResult(GuildHouseOutcome.OK, house, price, payment.fromFund(), payment.fromWallet(), 0);
    }

    /** The master buys {@code designId}: paid from the fund, the rest from their wallet (charged by the caller). */
    public GuildHouseResult buy(UUID guildId, GuildRank rank, String designId, long wallet, Instant now) {
        Objects.requireNonNull(now, "now");
        GuildHouseResult check = checkBuy(guildId, rank, designId, wallet);
        if (!check.isSuccess()) {
            return check;
        }
        GuildHouse updated = check.house().withFund(check.house().fund() - check.fromFund()).withDesign(designId, now);
        store(updated);
        return new GuildHouseResult(GuildHouseOutcome.OK, updated, check.cost(), check.fromFund(), check.fromWallet(), 0);
    }

    // ------------------------------------------------------------------------------------------------ switching

    /** Whether {@link #switchTo} would succeed, without changing anything. */
    public GuildHouseResult checkSwitch(UUID guildId, GuildRank rank, String designId, long wallet) {
        GuildHouse house = house(guildId);
        Optional<GuildHouseDesign> target = catalog.design(designId);
        GuildHouseOutcome denied = rank == null ? GuildHouseOutcome.NOT_IN_GUILD
                : !GuildHouseRules.mayManage(rank) ? GuildHouseOutcome.NO_PERMISSION
                : target.isEmpty() ? GuildHouseOutcome.UNKNOWN_DESIGN
                : !house.hasHouse() ? GuildHouseOutcome.NO_HOUSE
                : house.design().equals(designId) ? GuildHouseOutcome.SAME_DESIGN : null;
        if (denied != null) {
            return GuildHouseResult.failure(denied, house);
        }
        GuildHouseDesign current = catalog.design(house.design()).orElseThrow();
        long balance = GuildHouseRules.switchBalance(current, target.get());
        if (balance <= 0) {
            long toFund = Math.min(-balance, GuildHouseRules.MAX_FUND - house.fund());
            return new GuildHouseResult(GuildHouseOutcome.OK, house, 0, 0, 0, toFund);
        }
        GuildHouseRules.Payment payment = GuildHouseRules.pay(balance, house.fund(), Math.max(0, wallet));
        if (!payment.affordable()) {
            return GuildHouseResult.failure(GuildHouseOutcome.NOT_ENOUGH_GOLD, house);
        }
        return new GuildHouseResult(GuildHouseOutcome.OK, house, balance, payment.fromFund(), payment.fromWallet(), 0);
    }

    /**
     * The master switches the house to {@code designId}: the current house is traded in for
     * {@value GuildHouseRules#REFUND_PERCENT}% of its price; a positive balance is paid like a purchase, a negative one
     * goes into the fund.
     */
    public GuildHouseResult switchTo(UUID guildId, GuildRank rank, String designId, long wallet, Instant now) {
        Objects.requireNonNull(now, "now");
        GuildHouseResult check = checkSwitch(guildId, rank, designId, wallet);
        if (!check.isSuccess()) {
            return check;
        }
        GuildHouse house = check.house();
        GuildHouse updated = house.withFund(house.fund() - check.fromFund() + check.toFund()).withDesign(designId, now);
        store(updated);
        return new GuildHouseResult(GuildHouseOutcome.OK, updated, check.cost(), check.fromFund(), check.fromWallet(),
                check.toFund());
    }

    // ------------------------------------------------------------------------------------------------ giving up

    public GuildHouseResult checkSell(UUID guildId, GuildRank rank) {
        GuildHouse house = house(guildId);
        GuildHouseOutcome denied = rank == null ? GuildHouseOutcome.NOT_IN_GUILD
                : !GuildHouseRules.mayManage(rank) ? GuildHouseOutcome.NO_PERMISSION
                : !house.hasHouse() ? GuildHouseOutcome.NO_HOUSE : null;
        if (denied != null) {
            return GuildHouseResult.failure(denied, house);
        }
        long refund = GuildHouseRules.refund(catalog.design(house.design()).orElseThrow());
        return new GuildHouseResult(GuildHouseOutcome.OK, house, 0, 0, 0,
                Math.min(refund, GuildHouseRules.MAX_FUND - house.fund()));
    }

    /** The master gives the house up: {@value GuildHouseRules#REFUND_PERCENT}% of its price goes into the fund. */
    public GuildHouseResult sell(UUID guildId, GuildRank rank) {
        GuildHouseResult check = checkSell(guildId, rank);
        if (!check.isSuccess()) {
            return check;
        }
        GuildHouse updated = check.house().withFund(check.house().fund() + check.toFund()).withDesign(null, null);
        store(updated);
        return new GuildHouseResult(GuildHouseOutcome.OK, updated, 0, 0, 0, check.toFund());
    }

    /**
     * What a disbanded guild leaves behind.
     *
     * @param design the design its house was (to release the estate), or {@code null}
     * @param gold   the fund plus {@value GuildHouseRules#REFUND_PERCENT}% for the house: for the last master
     */
    public record Release(String design, long gold) {
    }

    /** The guild is gone: its data is removed; returns the house to release and the gold for the last master. */
    public Release disband(UUID guildId) {
        GuildHouse house = houses.remove(Objects.requireNonNull(guildId, "guildId"));
        if (house == null) {
            return new Release(null, 0);
        }
        revision++;
        long refund = house.design() == null ? 0 : catalog.design(house.design()).map(GuildHouseRules::refund).orElse(0L);
        long gold = house.fund() + refund;
        return new Release(house.design(), gold);
    }

    // ------------------------------------------------------------------------------------------------ the fund

    /** A member pays {@code amount} from their wallet ({@code wallet} = its balance) into the house fund. */
    public GuildHouseResult donate(UUID guildId, GuildRank rank, long amount, long wallet) {
        GuildHouse house = house(guildId);
        if (rank == null) {
            return GuildHouseResult.failure(GuildHouseOutcome.NOT_IN_GUILD, house);
        }
        if (amount <= 0 || amount > wallet || amount > GuildHouseRules.MAX_FUND - house.fund()) {
            return GuildHouseResult.failure(GuildHouseOutcome.INVALID_AMOUNT, house);
        }
        GuildHouse updated = house.withFund(house.fund() + amount);
        store(updated);
        return new GuildHouseResult(GuildHouseOutcome.OK, updated, amount, 0, amount, amount);
    }

    // ------------------------------------------------------------------------------------------------ the board

    /** The master or an officer sets the message of the day ({@code ""} clears it). */
    public GuildHouseResult setMotd(UUID guildId, GuildRank rank, String text) {
        GuildHouse house = house(guildId);
        if (rank == null) {
            return GuildHouseResult.failure(GuildHouseOutcome.NOT_IN_GUILD, house);
        }
        if (!GuildHouseRules.mayModerate(rank)) {
            return GuildHouseResult.failure(GuildHouseOutcome.NO_PERMISSION, house);
        }
        GuildHouse updated = house.withMotd(GuildHouseRules.clean(text, GuildHouseRules.MOTD_MAX));
        store(updated);
        return GuildHouseResult.ok(updated);
    }

    /**
     * A member pins a note. Each member may have {@value GuildHouseRules#NOTES_PER_MEMBER} notes up; when the board is
     * full ({@value GuildHouseRules#NOTES_MAX}) the oldest note drops off.
     */
    public GuildHouseResult addNote(UUID guildId, GuildRank rank, UUID author, String authorName, String text,
                                    Instant now) {
        GuildHouse house = house(guildId);
        if (rank == null) {
            return GuildHouseResult.failure(GuildHouseOutcome.NOT_IN_GUILD, house);
        }
        String clean = GuildHouseRules.clean(text, GuildHouseRules.NOTE_MAX);
        if (clean.isEmpty()) {
            return GuildHouseResult.failure(GuildHouseOutcome.EMPTY_TEXT, house);
        }
        long mine = house.notes().stream().filter(note -> note.author().equals(author)).count();
        if (mine >= GuildHouseRules.NOTES_PER_MEMBER) {
            return GuildHouseResult.failure(GuildHouseOutcome.TOO_MANY_NOTES, house);
        }
        List<BoardNote> notes = new ArrayList<>(house.notes());
        notes.add(new BoardNote(house.nextId(), author, authorName, clean, Objects.requireNonNull(now, "now")));
        while (notes.size() > GuildHouseRules.NOTES_MAX) {
            notes.remove(0);
        }
        GuildHouse updated = house.withNotes(notes, house.nextId() + 1);
        store(updated);
        return GuildHouseResult.ok(updated);
    }

    /** Takes a note down: its author, the master or an officer. */
    public GuildHouseResult removeNote(UUID guildId, UUID actor, GuildRank rank, long noteId) {
        GuildHouse house = house(guildId);
        if (rank == null) {
            return GuildHouseResult.failure(GuildHouseOutcome.NOT_IN_GUILD, house);
        }
        Optional<BoardNote> note = house.notes().stream().filter(n -> n.id() == noteId).findFirst();
        if (note.isEmpty()) {
            return GuildHouseResult.failure(GuildHouseOutcome.NOTE_NOT_FOUND, house);
        }
        if (!note.get().author().equals(actor) && !GuildHouseRules.mayModerate(rank)) {
            return GuildHouseResult.failure(GuildHouseOutcome.NO_PERMISSION, house);
        }
        List<BoardNote> notes = new ArrayList<>(house.notes());
        notes.remove(note.get());
        GuildHouse updated = house.withNotes(notes, house.nextId());
        store(updated);
        return GuildHouseResult.ok(updated);
    }

    // ------------------------------------------------------------------------------------------------ the log

    /** Appends to the guild's storage log (the oldest lines drop off beyond {@value GuildHouseRules#LOG_SIZE}). */
    public void log(UUID guildId, StorageLogEntry entry) {
        store(house(guildId).withLog(Objects.requireNonNull(entry, "entry")));
    }

    // ------------------------------------------------------------------------------------------------ persistence

    public long revision() {
        return revision;
    }

    public boolean isDirty() {
        return revision > savedRevision;
    }

    public void markSaved(long persistedRevision) {
        if (persistedRevision > savedRevision) {
            savedRevision = persistedRevision;
        }
    }

    private void store(GuildHouse house) {
        if (house.isBlank()) {
            houses.remove(house.guildId());
        } else {
            houses.put(house.guildId(), house);
        }
        revision++;
    }
}
