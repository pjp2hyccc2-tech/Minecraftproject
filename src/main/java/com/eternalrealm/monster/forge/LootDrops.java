package com.eternalrealm.monster.forge;

import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.combat.WeaponType;
import com.eternalrealm.item.EquipmentBase;
import com.eternalrealm.item.EquipmentData;
import com.eternalrealm.item.EquipmentGenerator;
import com.eternalrealm.item.ItemRarity;
import com.eternalrealm.item.ItemTier;
import com.eternalrealm.item.forge.ERGearItems;
import com.eternalrealm.item.forge.EquipmentStacks;
import com.eternalrealm.job.JobDefinition;
import com.eternalrealm.job.JobType;
import com.eternalrealm.job.Jobs;
import com.eternalrealm.monster.DropTable;
import com.eternalrealm.monster.DropTables;
import com.eternalrealm.monster.LootDrop;
import com.eternalrealm.monster.LootRoller;
import com.eternalrealm.monster.MonsterTemplate;
import com.eternalrealm.server.ServerContext;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Personal loot of a monster: its drop table rolled with the killer's luck (LUK), materials and equipment of the
 * table's tier (rarity rolled by the monster module; the piece's base leans towards the killer's job weapon; 10 %, 18 %
 * from elites, are a trinket of the tier's grade, {@code com.eternalrealm.item.Trinkets#forDrop}). Drops
 * can only be picked up by the killer; rare and better pieces glow, legendary ones are announced to the server.
 */
public final class LootDrops {
    /** Share of equipment drops that are a weapon of the killer's job. */
    private static final double JOB_WEAPON_SHARE = 0.4;
    /** Share of the other drops that may be a weapon of another job (for trading); the rest is armor. */
    private static final double OTHER_WEAPON_SHARE = 0.15;
    private static final SplittableRandom RANDOM = new SplittableRandom();

    private LootDrops() {
    }

    public static void drop(ServerPlayer killer, LivingEntity victim, MonsterTemplate template, double luck) {
        boolean elite = template.role() == com.eternalrealm.monster.MonsterRole.ELITE;
        dropWielded(killer, victim, template, luck, elite);
        DropTable table = DropTables.byId(template.dropTableId()).orElse(null);
        if (table == null || table.isEmpty()) {
            return;
        }
        int field = template.areaIds().stream().mapToInt(Integer::intValue).min().orElse(0);
        int level = com.eternalrealm.monster.forge.MonsterTags.level(victim);
        for (LootDrop drop : LootRoller.roll(table, luck, RANDOM)) {
            if (drop instanceof LootDrop.ItemDrop item) {
                Item type = itemOf(item.itemId());
                if (type != null) {
                    spawn(killer, victim, new ItemStack(type, item.count()), ItemRarity.COMMON);
                }
            } else if (drop instanceof LootDrop.EquipmentDrop equipment) {
                ItemTier tier = ItemTier.byNumber(equipment.tier()).orElse(ItemTier.IRON);
                ItemRarity rarity = ItemRarity.byId(equipment.rarity().id()).orElse(ItemRarity.COMMON);
                // some drops are a trinket of the tier's grade (装身具, leaning towards the killer's job stats), some a
                // named design of the field (armory); both rolled at the same rarity
                java.util.Optional<com.eternalrealm.item.Design> design;
                if (RANDOM.nextDouble() < com.eternalrealm.item.Trinkets.dropShare(elite)) {
                    design = java.util.Optional.of(com.eternalrealm.item.Trinkets.forDrop(tier, level,
                            jobStats(killer), RANDOM));
                } else if (RANDOM.nextDouble() < com.eternalrealm.item.ArmoryDrops.fieldDesignShare(elite)) {
                    design = com.eternalrealm.item.ArmoryDrops.fieldDesign(field, level, RANDOM);
                } else {
                    design = java.util.Optional.empty();
                }
                EquipmentData data = design.isPresent() ? EquipmentGenerator.roll(design.get(), rarity, RANDOM)
                        : EquipmentGenerator.roll(baseFor(killer, tier), rarity, RANDOM);
                spawn(killer, victim, EquipmentStacks.create(data), rarity);
            }
        }
    }

    /**
     * A monster carrying a named weapon may drop it ({@code com.eternalrealm.item.Signatures#wielded}); only the weapon
     * it really holds (a lord holds its own signature weapon, which the lord's rewards hand out instead).
     */
    private static void dropWielded(ServerPlayer killer, LivingEntity victim, MonsterTemplate template, double luck,
                                    boolean elite) {
        com.eternalrealm.item.Signatures.wielded(template.id())
                .filter(design -> EquipmentStacks.designOf(victim.getMainHandItem()).map(design::equals).orElse(false))
                .filter(design -> RANDOM.nextDouble() < com.eternalrealm.item.ArmoryDrops.wieldedDropChance(elite))
                .ifPresent(design -> {
                    ItemRarity rarity = EquipmentGenerator.rollRarity(luck, RANDOM);
                    spawn(killer, victim, EquipmentStacks.create(EquipmentGenerator.roll(design, rarity, RANDOM)),
                            rarity.rank() < ItemRarity.RARE.rank() ? ItemRarity.RARE : rarity);
                });
    }

    private static Item itemOf(String id) {
        Item item = ERGearItems.material(id);
        if (item == null) {
            ResourceLocation location = ResourceLocation.tryParse(id);
            item = location == null ? null : ForgeRegistries.ITEMS.getValue(location);
        }
        return item == null || item == Items.AIR ? null : item;
    }

    /** The stats the killer's job grows (a dropped trinket's focus leans towards them); none without a job. */
    public static java.util.Set<com.eternalrealm.character.StatType> jobStats(ServerPlayer killer) {
        JobType job = ServerContext.current().flatMap(context -> context.playerData().snapshot(killer.getUUID()))
                .map(CharacterSnapshot::jobId).map(JobType::fromStored).orElse(JobType.NONE);
        java.util.Set<com.eternalrealm.character.StatType> stats =
                java.util.EnumSet.noneOf(com.eternalrealm.character.StatType.class);
        if (job.isChosen()) {
            com.eternalrealm.character.StatBlock growth = Jobs.get(job).growthPerLevel();
            for (com.eternalrealm.character.StatType type : com.eternalrealm.character.StatType.values()) {
                if (growth.get(type) > 0) {
                    stats.add(type);
                }
            }
        }
        return stats;
    }

    /**
     * A base of the tier for the killer: with a job, often one of the job's weapons, otherwise armor and only rarely a
     * weapon the job cannot use; without a job any weapon or armor piece of the tier.
     */
    public static EquipmentBase baseFor(ServerPlayer killer, ItemTier tier) {
        JobType job = ServerContext.current().flatMap(context -> context.playerData().snapshot(killer.getUUID()))
                .map(CharacterSnapshot::jobId).map(JobType::fromStored).orElse(JobType.NONE);
        if (job.isChosen() && RANDOM.nextDouble() < JOB_WEAPON_SHARE) {
            JobDefinition definition = Jobs.get(job);
            List<WeaponType> weapons = new ArrayList<>(definition.weapons());
            if (weapons.isEmpty()) {
                weapons.add(definition.starterWeapon());
            }
            weapons.sort(null);
            return EquipmentBase.weapon(weapons.get(RANDOM.nextInt(weapons.size())), tier);
        }
        boolean anyWeapon = !job.isChosen() || RANDOM.nextDouble() < OTHER_WEAPON_SHARE;
        List<EquipmentBase> bases = new ArrayList<>();
        for (EquipmentBase base : EquipmentBase.all()) {
            if (base.tier() == tier && (anyWeapon || !base.isWeapon())) {
                bases.add(base);
            }
        }
        return bases.get(RANDOM.nextInt(bases.size()));
    }

    /** The killer's loot goes straight into their bag (what does not fit drops at their feet, for them alone). */
    private static void spawn(ServerPlayer killer, LivingEntity victim, ItemStack stack, ItemRarity rarity) {
        ServerLevel level = (ServerLevel) victim.level();
        boolean rare = rarity.rank() >= ItemRarity.RARE.rank();
        com.eternalrealm.item.forge.Bags.loot(killer, stack); // counts for "collect" objectives
        if (!rare) {
            return;
        }
        killer.sendSystemMessage(Component.translatable("message.eternalrealm.loot.rare", stack.getDisplayName())
                .withStyle(ChatFormatting.GRAY));
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                rarity == ItemRarity.LEGENDARY ? com.eternalrealm.sound.ERSounds.LOOT_LEGENDARY_DROP.get()
                        : com.eternalrealm.sound.ERSounds.LOOT_RARE_DROP.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        if (rarity == ItemRarity.LEGENDARY) {
            killer.server.getPlayerList().broadcastSystemMessage(Component.translatable(
                    "message.eternalrealm.loot.legendary_broadcast", killer.getDisplayName(), stack.getDisplayName())
                    .withStyle(ChatFormatting.GOLD), false);
        }
    }
}
