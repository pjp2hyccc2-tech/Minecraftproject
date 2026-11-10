package com.eternalrealm.dungeon.forge;

import com.eternalrealm.boss.Contributions;
import com.eternalrealm.boss.forge.BossFight;
import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.combat.CombatStats;
import com.eternalrealm.combat.Combatant;
import com.eternalrealm.dungeon.ClearRank;
import com.eternalrealm.dungeon.DungeonRunEvent;
import com.eternalrealm.item.Consumable;
import com.eternalrealm.item.EquipmentGenerator;
import com.eternalrealm.item.ItemRarity;
import com.eternalrealm.item.ItemTier;
import com.eternalrealm.item.Materials;
import com.eternalrealm.item.forge.ERGearItems;
import com.eternalrealm.item.forge.EquipmentStacks;
import com.eternalrealm.item.forge.Inventories;
import com.eternalrealm.job.JobType;
import com.eternalrealm.monster.forge.LootDrops;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.server.UiActions;
import com.eternalrealm.server.UiArgs;
import com.eternalrealm.server.UiData;
import com.eternalrealm.social.forge.Names;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.UUID;

/**
 * The clear result (報酬画面): every member present gets a result screen with the grade
 * ({@link ClearRank}), the clear time, the party's contributions with the hero of the fight (殊勲) crowned, what they received and a bonus
 * chest of three face-down cards to pick one from — the grade decides how rich the cards are. The hero (殊勲) gets an
 * extra reward. A card not picked before leaving is picked at random on the way out.
 */
public final class DungeonResults {
    public static final String CHANNEL = "dungeon_result";
    public static final String BONUS_CHANNEL = "dungeon_bonus";
    public static final String BONUS_ACTION = "dungeon.bonus";
    public static final String EXIT_ACTION = "dungeon.exit";
    private static final int CARDS = 3;
    private static final SplittableRandom RANDOM = new SplittableRandom();
    /** Seconds until the instance sends everyone out after a clear (must match DungeonInstance). */
    static final int EXIT_SECONDS = 45;

    /** What one member received for the clear. */
    record Haul(long exp, long gold, List<ItemStack> items) {
    }

    /** One bonus card: an item stack or a pouch of gold. */
    private record Card(ItemStack stack, long gold) {
        JsonObject toJson() {
            JsonObject json = new JsonObject();
            if (stack != null) {
                json.addProperty("item", stack.save(new CompoundTag()).toString());
            } else {
                json.addProperty("gold", gold);
            }
            return json;
        }
    }

    private static final class Offer {
        final String instanceId;
        final List<Card> cards;
        boolean picked;

        Offer(String instanceId, List<Card> cards) {
            this.instanceId = instanceId;
            this.cards = cards;
        }
    }

    private static final Map<UUID, Offer> OFFERS = new HashMap<>();

    private DungeonResults() {
    }

    public static void register() {
        UiActions.register(BONUS_ACTION, (player, context, args) -> pick(player, context, (int) UiArgs.number(args, "card", 0,
                CARDS - 1)));
        UiActions.register(EXIT_ACTION, (player, context, args) -> context.game().dungeons().leaveCleared(player));
    }

    // ------------------------------------------------------------------ the result

    static void show(ServerContext context, DungeonInstance instance, DungeonRunEvent.Cleared cleared,
                     Map<UUID, Haul> hauls) {
        ClearRank rank = ClearRank.of(cleared.durationTicks(), instance.run.timeLimitTicks(), instance.run.deaths());
        Contributions contributions = instance.fight().map(BossFight::contributions).orElse(new Contributions());
        Optional<Contributions.Line> hero = contributions.hero();

        // the party table, the same for everyone
        JsonArray party = new JsonArray();
        for (UUID member : instance.run.members()) {
            ServerPlayer online = context.game().dungeons().player(member);
            Optional<Contributions.Line> line = contributions.line(member);
            CharacterSnapshot snapshot = context.playerData().snapshot(member).orElse(null);
            JsonObject row = new JsonObject();
            row.addProperty("id", member.toString());
            row.addProperty("name", online != null ? online.getGameProfile().getName()
                    : line.map(Contributions.Line::name).orElse(Names.of(context.server(), member)));
            row.addProperty("job", snapshot == null ? line.map(Contributions.Line::job).orElse("none")
                    : JobType.fromStored(snapshot.jobId()).id());
            row.addProperty("level", snapshot == null ? 0 : snapshot.level());
            row.addProperty("damage", Math.round(line.map(Contributions.Line::damage).orElse(0.0)));
            row.addProperty("share", Math.round(line.map(Contributions.Line::share).orElse(0.0) * 1000.0) / 1000.0);
            row.addProperty("taken", Math.round(line.map(Contributions.Line::taken).orElse(0.0)));
            row.addProperty("falls", line.map(Contributions.Line::falls).orElse(0));
            row.addProperty("score", Math.round(line.map(Contributions.Line::score).orElse(0.0)));
            row.addProperty("hero", hero.map(best -> best.id().equals(member)).orElse(false));
            party.add(row);
        }

        hero.ifPresent(best -> {
            Component name = Component.literal(best.name()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
            instance.tellAll(Component.translatable("message.eternalrealm.dungeon.hero", name)
                    .withStyle(ChatFormatting.YELLOW), false);
        });

        for (Map.Entry<UUID, Haul> entry : hauls.entrySet()) {
            ServerPlayer player = context.game().dungeons().player(entry.getKey());
            if (player == null) {
                continue;
            }
            Haul haul = entry.getValue();
            List<ItemStack> items = new ArrayList<>(haul.items());
            boolean isHero = hero.map(best -> best.id().equals(player.getUUID())).orElse(false);
            if (isHero) {
                items.addAll(heroReward(player, instance));
            }
            List<Card> cards = rollCards(context, player, instance, rank);
            OFFERS.put(player.getUUID(), new Offer(instance.instanceId, cards));

            JsonObject data = new JsonObject();
            data.addProperty("dungeon", instance.nameKey);
            data.addProperty("difficulty", instance.difficulty.name().toLowerCase(java.util.Locale.ROOT));
            data.addProperty("difficultyKey", instance.difficulty.translationKey());
            data.addProperty("time", cleared.durationTicks() / 20.0);
            data.addProperty("limit", instance.run.timeLimitTicks() / 20.0);
            data.addProperty("falls", instance.run.deaths());
            data.addProperty("rank", rank.name());
            data.addProperty("me", player.getUUID().toString());
            data.addProperty("heroMe", isHero);
            data.add("party", party);
            JsonArray rewards = new JsonArray();
            for (ItemStack stack : items) {
                if (!stack.isEmpty()) {
                    rewards.add(stack.save(new CompoundTag()).toString());
                }
            }
            data.add("rewards", rewards);
            data.addProperty("exp", haul.exp());
            data.addProperty("gold", haul.gold());
            data.addProperty("cards", CARDS);
            data.addProperty("exit", EXIT_SECONDS);
            UiData.send(player, CHANNEL, data);
        }
    }

    /** The hero (殊勲)'s extra: two more enhancement stones and a boss material of the area. */
    private static List<ItemStack> heroReward(ServerPlayer player, DungeonInstance instance) {
        List<ItemStack> extra = new ArrayList<>();
        Item stone = ERGearItems.material(Materials.ENHANCEMENT_STONE);
        if (stone != null) {
            extra.add(new ItemStack(stone, 2));
        }
        if (instance.areaId >= 1 && instance.areaId <= 10) {
            Item soul = ERGearItems.material(com.eternalrealm.monster.Materials.bossMaterial(instance.areaId));
            if (soul != null) {
                extra.add(new ItemStack(soul, 1));
            }
        }
        for (ItemStack stack : extra) {
            Inventories.give(player, stack.copy());
        }
        player.displayClientMessage(Component.translatable("message.eternalrealm.dungeon.hero_reward")
                .withStyle(ChatFormatting.GOLD), false);
        return extra;
    }

    // ------------------------------------------------------------------ the bonus chest

    private static List<Card> rollCards(ServerContext context, ServerPlayer player, DungeonInstance instance,
                                        ClearRank rank) {
        List<Card> cards = new ArrayList<>(CARDS);
        for (int i = 0; i < CARDS; i++) {
            cards.add(rollCard(context, player, instance, rank.bonusTier()));
        }
        return cards;
    }

    private static Card rollCard(ServerContext context, ServerPlayer player, DungeonInstance instance, int tier) {
        int equipmentWeight = 5 + 5 * tier;
        int roll = RANDOM.nextInt(30 + 20 + 15 + 25 + equipmentWeight);
        if (roll < 30) {
            Item stone = ERGearItems.material(Materials.ENHANCEMENT_STONE);
            if (stone != null) {
                return new Card(new ItemStack(stone, 1 + tier + RANDOM.nextInt(2)), 0L);
            }
        } else if (roll < 50) {
            if (instance.areaId >= 1 && instance.areaId <= 10) {
                Item soul = ERGearItems.material(com.eternalrealm.monster.Materials.bossMaterial(instance.areaId));
                if (soul != null) {
                    return new Card(new ItemStack(soul, 1 + tier / 2), 0L);
                }
            }
        } else if (roll < 65) {
            return new Card(new ItemStack(ERGearItems.consumable(Consumable.EVERBLOOM_PETAL), tier >= 2 ? 2 : 1), 0L);
        } else if (roll >= 90) {
            double luck = context.combat().service().get(player.getUUID()).map(Combatant::stats)
                    .map(CombatStats::lootBonus).orElse(0.0);
            ItemRarity rarity = EquipmentGenerator.rollRarity(luck, RANDOM);
            ItemRarity floor = tier >= 3 ? ItemRarity.EPIC : tier >= 2 ? ItemRarity.RARE : ItemRarity.UNCOMMON;
            if (rarity.rank() < floor.rank()) {
                rarity = floor;
            }
            ItemTier itemTier = DungeonService.tierOfArea(instance.areaId);
            return new Card(EquipmentStacks.create(EquipmentGenerator.roll(LootDrops.baseFor(player, itemTier), rarity,
                    RANDOM)), 0L);
        }
        long base = Math.max(1, instance.recommendedLevel) * (20L + 15L * tier);
        long gold = Math.max(10L, Math.round(base * (0.75 + RANDOM.nextDouble() * 0.5)));
        return new Card(null, gold);
    }

    private static void pick(ServerPlayer player, ServerContext context, int index) {
        Offer offer = OFFERS.get(player.getUUID());
        if (offer == null || offer.picked || index < 0 || index >= offer.cards.size()) {
            return;
        }
        offer.picked = true;
        grant(player, context, offer.cards.get(index));
        JsonObject data = new JsonObject();
        data.addProperty("picked", index);
        JsonArray cards = new JsonArray();
        for (Card card : offer.cards) {
            cards.add(card.toJson());
        }
        data.add("cards", cards);
        UiData.send(player, BONUS_CHANNEL, data);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS, 0.8F, 1.4F);
    }

    private static void grant(ServerPlayer player, ServerContext context, Card card) {
        if (card.stack() != null) {
            Inventories.give(player, card.stack().copy());
            player.displayClientMessage(Component.translatable("message.eternalrealm.dungeon.bonus_item",
                    card.stack().getHoverName(), card.stack().getCount()).withStyle(ChatFormatting.GOLD), false);
        } else {
            context.playerData().grantGold(player.getUUID(), card.gold());
            player.displayClientMessage(Component.translatable("message.eternalrealm.dungeon.bonus_gold", card.gold())
                    .withStyle(ChatFormatting.GOLD), false);
        }
    }

    /** On the way out of an instance: a bonus card not picked yet is picked at random. */
    static void onExit(ServerPlayer player, DungeonInstance instance) {
        Offer offer = OFFERS.get(player.getUUID());
        if (offer == null || !offer.instanceId.equals(instance.instanceId)) {
            return;
        }
        OFFERS.remove(player.getUUID());
        if (!offer.picked) {
            offer.picked = true;
            ServerContext.current().ifPresent(context -> grant(player, context,
                    offer.cards.get(RANDOM.nextInt(offer.cards.size()))));
        }
    }

    /** Server stopping or restarting: forget every offer. */
    static void clear() {
        OFFERS.clear();
    }
}
