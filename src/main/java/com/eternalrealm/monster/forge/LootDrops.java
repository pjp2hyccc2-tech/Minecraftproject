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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Personal loot of a monster: its drop table rolled with the killer's luck (LUK), materials and equipment of the
 * table's tier (rarity rolled by the monster module; the piece's base leans towards the killer's job weapon). Drops
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
        DropTable table = DropTables.byId(template.dropTableId()).orElse(null);
        if (table == null || table.isEmpty()) {
            return;
        }
        for (LootDrop drop : LootRoller.roll(table, luck, RANDOM)) {
            if (drop instanceof LootDrop.ItemDrop item) {
                Item type = itemOf(item.itemId());
                if (type != null) {
                    spawn(killer, victim, new ItemStack(type, item.count()), ItemRarity.COMMON);
                }
            } else if (drop instanceof LootDrop.EquipmentDrop equipment) {
                ItemTier tier = ItemTier.byNumber(equipment.tier()).orElse(ItemTier.IRON);
                ItemRarity rarity = ItemRarity.byId(equipment.rarity().id()).orElse(ItemRarity.COMMON);
                EquipmentData data = EquipmentGenerator.roll(baseFor(killer, tier), rarity, RANDOM);
                spawn(killer, victim, EquipmentStacks.create(data), rarity);
            }
        }
    }

    private static Item itemOf(String id) {
        Item item = ERGearItems.material(id);
        if (item == null) {
            ResourceLocation location = ResourceLocation.tryParse(id);
            item = location == null ? null : ForgeRegistries.ITEMS.getValue(location);
        }
        return item == null || item == Items.AIR ? null : item;
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

    private static void spawn(ServerPlayer killer, LivingEntity victim, ItemStack stack, ItemRarity rarity) {
        ServerLevel level = (ServerLevel) victim.level();
        ItemEntity entity = new ItemEntity(level, victim.getX(), victim.getY() + 0.4, victim.getZ(), stack,
                (RANDOM.nextDouble() - 0.5) * 0.15, 0.25, (RANDOM.nextDouble() - 0.5) * 0.15);
        entity.setPickUpDelay(10);
        entity.setTarget(killer.getUUID());
        boolean rare = rarity.rank() >= ItemRarity.RARE.rank();
        if (rare) {
            entity.setGlowingTag(true);
        }
        level.addFreshEntity(entity);
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
