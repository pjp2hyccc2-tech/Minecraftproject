package com.eternalrealm.guild.house;

import com.eternalrealm.economy.Wallet;
import com.eternalrealm.guild.GuildRank;

/**
 * The rules of guild houses (docs/modules/guildhouse.md, "Decisions"):
 * <ul>
 *     <li>One house per guild. Only the guild master buys, switches the design or gives the house up.</li>
 *     <li>Prices are paid from the guild's <em>house fund</em> first (any member may pay gold into it), the rest from
 *     the master's wallet. The fund cannot be taken out again; it only pays for the house.</li>
 *     <li>Switching designs: the current house is taken back for {@value #REFUND_PERCENT}% of its price, which counts
 *     towards the new one; whatever is left of that trade-in (switching to a cheaper design) goes into the fund.</li>
 *     <li>Giving the house up: {@value #REFUND_PERCENT}% of its price goes into the fund.</li>
 *     <li>A guild disbanded: its house is released and the fund (with the {@value #REFUND_PERCENT}% for the house)
 *     goes to the last master.</li>
 *     <li>Inside: every member may use the storage chests; the vault chests only officers and the master. Every
 *     deposit and withdrawal is logged (the newest {@value #LOG_SIZE}). The message of the day is the master's and the
 *     officers'; every member may pin up to {@value #NOTES_PER_MEMBER} notes on the board ({@value #NOTES_MAX} in all,
 *     the oldest drop off).</li>
 * </ul>
 * Pure; every amount is in gold.
 */
public final class GuildHouseRules {
    public static final long MAX_PRICE = 100_000_000L;
    public static final int REFUND_PERCENT = 50;
    public static final long MAX_FUND = Wallet.MAX_BALANCE;
    public static final int MOTD_MAX = 120;
    public static final int NOTE_MAX = 80;
    public static final int NOTES_MAX = 16;
    public static final int NOTES_PER_MEMBER = 2;
    public static final int LOG_SIZE = 200;
    /** Storage piece names of a design: {@code chest.<n>} for everyone, {@code vault.<n>} for officers and the master. */
    public static final String CHEST_PREFIX = "chest.";
    public static final String VAULT_PREFIX = "vault.";

    private GuildHouseRules() {
    }

    /** Buy, switch the design, give the house up. */
    public static boolean mayManage(GuildRank rank) {
        return rank == GuildRank.MASTER;
    }

    /** Write the message of the day, take down anyone's note. */
    public static boolean mayModerate(GuildRank rank) {
        return rank == GuildRank.MASTER || rank == GuildRank.OFFICER;
    }

    /** Open a vault chest ({@value #VAULT_PREFIX}…). */
    public static boolean mayOpenVault(GuildRank rank) {
        return rank == GuildRank.MASTER || rank == GuildRank.OFFICER;
    }

    /** Whether a storage piece name is a vault. */
    public static boolean isVault(String pieceName) {
        return pieceName != null && pieceName.startsWith(VAULT_PREFIX);
    }

    /** Shared storage chests (27 slots each) of a house of {@code tier}: 2, 3, 4, 5, 6 plus vaults. */
    public static int storageChests(int tier) {
        return tier + 1;
    }

    /** Vault chests of a house of {@code tier}: 1 up to tier 3, then 2. */
    public static int vaultChests(int tier) {
        return tier >= 4 ? 2 : 1;
    }

    /** What the house is worth when it is given up or traded in. */
    public static long refund(GuildHouseDesign design) {
        return design.price() * REFUND_PERCENT / 100;
    }

    /**
     * Gold to pay for switching from {@code current} to {@code target}: positive = to pay, negative = to put into the
     * fund (the trade-in is worth more than the new design).
     */
    public static long switchBalance(GuildHouseDesign current, GuildHouseDesign target) {
        return target.price() - refund(current);
    }

    /**
     * How a cost is paid: from the fund first, the rest from the master's wallet.
     *
     * @param fromFund   taken out of the fund
     * @param fromWallet taken from the master's wallet
     * @param affordable whether fund and wallet together cover the cost
     */
    public record Payment(long fromFund, long fromWallet, boolean affordable) {
        public static final Payment FREE = new Payment(0, 0, true);
    }

    public static Payment pay(long cost, long fund, long wallet) {
        if (cost < 0 || fund < 0 || wallet < 0) {
            throw new IllegalArgumentException("negative amount");
        }
        long fromFund = Math.min(cost, fund);
        long fromWallet = cost - fromFund;
        return new Payment(fromFund, fromWallet, fromWallet <= wallet);
    }

    /** Text a player may write on the board: trimmed, control characters removed, at most {@code max} chars. */
    public static String clean(String text, int max) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        text.strip().codePoints().filter(c -> !Character.isISOControl(c) && c != '§').forEach(out::appendCodePoint);
        String result = out.toString().strip();
        return result.codePointCount(0, result.length()) > max
                ? result.substring(0, result.offsetByCodePoints(0, max)) : result;
    }
}
