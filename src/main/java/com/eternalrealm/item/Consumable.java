package com.eternalrealm.item;

import com.eternalrealm.core.ModInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Shop consumables. {@link #id()} is the stable item path and persistence key: never rename it.
 *
 * <p>The three health potions share one cooldown ({@link #cooldownGroup()}), so carrying several sizes never allows
 * drinking faster; the cooldown is long enough that potions bridge a fight instead of replacing the healer. Prices
 * are NPC shop prices; selling back yields a quarter ({@link ItemPrices#consumableSellPrice}).</p>
 */
public enum Consumable {
    HEALTH_POTION_SMALL("health_potion_small", 1, 25, 16, "health_potion", 300,
            new ConsumableEffect.Heal(0.15, 4.0)),
    HEALTH_POTION_MEDIUM("health_potion_medium", 15, 80, 16, "health_potion", 300,
            new ConsumableEffect.Heal(0.25, 10.0)),
    HEALTH_POTION_LARGE("health_potion_large", 30, 200, 16, "health_potion", 300,
            new ConsumableEffect.Heal(0.35, 20.0)),
    /** Refills the stamina pool at once: one more dodge or guard when it matters. */
    STAMINA_TONIC("stamina_tonic", 5, 40, 16, "stamina_tonic", 600,
            new ConsumableEffect.RestoreStamina(1.0, 0.0)),
    /** Teleports home after a 5 second channel. */
    RETURN_SCROLL("return_scroll", 1, 60, 16, "return_scroll", 0,
            new ConsumableEffect.ReturnHome(100)),
    /** Resets stat and skill points (忘却の秘薬). From level 10: below it a wrong choice costs little to level past. */
    OBLIVION_ELIXIR("oblivion_elixir", 10, 3000, 1, "oblivion_elixir", 0,
            new ConsumableEffect.ResetPoints(true, true));

    public enum UseCheck {
        OK,
        LEVEL_TOO_LOW,
        ON_COOLDOWN;

        public String translationKey() {
            return "message." + ModInfo.MOD_ID + ".consumable." + name().toLowerCase(Locale.ROOT);
        }
    }

    private final String id;
    private final int requiredLevel;
    private final long shopPrice;
    private final int maxStack;
    private final String cooldownGroup;
    private final int cooldownTicks;
    private final ConsumableEffect effect;

    Consumable(String id, int requiredLevel, long shopPrice, int maxStack, String cooldownGroup, int cooldownTicks,
               ConsumableEffect effect) {
        this.id = id;
        this.requiredLevel = requiredLevel;
        this.shopPrice = shopPrice;
        this.maxStack = maxStack;
        this.cooldownGroup = cooldownGroup;
        this.cooldownTicks = cooldownTicks;
        this.effect = effect;
    }

    public String id() {
        return id;
    }

    /** Namespaced item id, e.g. {@code eternalrealm:health_potion_small}. */
    public String itemId() {
        return ModInfo.MOD_ID + ":" + id;
    }

    /** Item name ({@code item.eternalrealm.<id>}), so the item can be registered under {@link #id()} as it is. */
    public String translationKey() {
        return "item." + ModInfo.MOD_ID + "." + id;
    }

    /** Tooltip description; its arguments are {@link #descriptionArgs()}. */
    public String descriptionKey() {
        return translationKey() + ".desc";
    }

    /**
     * Formatted numbers for {@link #descriptionKey()}: heal → percent, flat, cooldown seconds; stamina → percent,
     * cooldown seconds; return → channel seconds; reset → none.
     */
    public List<String> descriptionArgs() {
        if (effect instanceof ConsumableEffect.Heal heal) {
            return List.of(EquipmentTooltip.number(heal.fraction() * 100.0), EquipmentTooltip.number(heal.flat()),
                    EquipmentTooltip.number(cooldownTicks / 20.0));
        }
        if (effect instanceof ConsumableEffect.RestoreStamina stamina) {
            return List.of(EquipmentTooltip.number(stamina.fraction() * 100.0),
                    EquipmentTooltip.number(cooldownTicks / 20.0));
        }
        if (effect instanceof ConsumableEffect.ReturnHome home) {
            return List.of(EquipmentTooltip.number(home.channelTicks() / 20.0));
        }
        return List.of();
    }

    public int requiredLevel() {
        return requiredLevel;
    }

    /** NPC shop price in gold. */
    public long shopPrice() {
        return shopPrice;
    }

    /** Suggested item stack size. */
    public int maxStack() {
        return maxStack;
    }

    /** Consumables of the same group share one cooldown. */
    public String cooldownGroup() {
        return cooldownGroup;
    }

    /** Cooldown started for the whole group by a use; 0 = none. */
    public int cooldownTicks() {
        return cooldownTicks;
    }

    public ConsumableEffect effect() {
        return effect;
    }

    public boolean sharesCooldownWith(Consumable other) {
        return cooldownGroup.equals(Objects.requireNonNull(other, "other").cooldownGroup);
    }

    /** Every consumable whose cooldown a use of this one starts (itself included). */
    public List<Consumable> cooldownGroupMembers() {
        List<Consumable> members = new ArrayList<>();
        for (Consumable consumable : values()) {
            if (sharesCooldownWith(consumable)) {
                members.add(consumable);
            }
        }
        return Collections.unmodifiableList(members);
    }

    /**
     * Whether a character may use it now.
     *
     * @param cooldownRemainingTicks remaining cooldown of this consumable's group (0 = ready)
     */
    public UseCheck check(int level, int cooldownRemainingTicks) {
        if (cooldownRemainingTicks < 0) {
            throw new IllegalArgumentException("cooldownRemainingTicks must be >= 0: " + cooldownRemainingTicks);
        }
        if (level < requiredLevel) {
            return UseCheck.LEVEL_TOO_LOW;
        }
        return cooldownRemainingTicks > 0 ? UseCheck.ON_COOLDOWN : UseCheck.OK;
    }

    public static Optional<Consumable> byId(String id) {
        for (Consumable consumable : values()) {
            if (consumable.id.equals(id)) {
                return Optional.of(consumable);
            }
        }
        return Optional.empty();
    }
}
