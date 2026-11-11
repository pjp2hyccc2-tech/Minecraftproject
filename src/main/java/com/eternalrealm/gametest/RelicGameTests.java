package com.eternalrealm.gametest;

import com.eternalrealm.character.SetLevelResult;
import com.eternalrealm.character.StatType;
import com.eternalrealm.combat.ActionDefinition;
import com.eternalrealm.combat.CombatEvent;
import com.eternalrealm.combat.CombatRequest;
import com.eternalrealm.combat.CombatStats;
import com.eternalrealm.combat.Combatant;
import com.eternalrealm.combat.WeaponProfile;
import com.eternalrealm.combat.forge.CombatDriver;
import com.eternalrealm.combat.forge.WeaponItem;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.item.Armory;
import com.eternalrealm.item.Design;
import com.eternalrealm.item.EquipmentData;
import com.eternalrealm.item.EquipmentGenerator;
import com.eternalrealm.item.EquipmentStats;
import com.eternalrealm.item.GeneratedArts;
import com.eternalrealm.item.ItemRarity;
import com.eternalrealm.item.ItemTier;
import com.eternalrealm.item.Relics;
import com.eternalrealm.item.WeaponArt;
import com.eternalrealm.item.WeaponDesign;
import com.eternalrealm.item.forge.BagService;
import com.eternalrealm.item.forge.EquipmentStacks;
import com.eternalrealm.item.forge.RelicDrops;
import com.eternalrealm.item.forge.TrinketSlots;
import com.eternalrealm.job.ComposedStats;
import com.eternalrealm.job.forge.PlayerStats;
import com.eternalrealm.player.PlayerDataService;
import com.eternalrealm.server.ServerContext;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Relics (金枠の遺宝), rarity and levels on a real server: a piece above the character's level can be neither
 * equipped, wielded, guarded with nor used for its art, and starts working at its level; accessories and the charm go
 * into their own slots and add their stats (one copy of each); a rarer piece raises its art; a forced relic drop hands
 * out a legendary, gold-framed relic of the right source. Same arena and conventions as {@link CombatGameTests}.
 */
@GameTestHolder(ModInfo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RelicGameTests {
    private static final String ARENA = "arena";
    private static final CombatStats PLAIN = new CombatStats(1.0, 0.0, 0.0, 1.0, 0.0, 1.5, 100.0, 1.0);

    private RelicGameTests() {
    }

    /** The level gates of a relic (Lv55) for a level-10 character, then at level 55; the trinket slots. */
    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void levelGatesEquipWieldAndTrinkets(GameTestHelper helper) {
        ServerContext context = requireContext();
        PlayerDataService data = context.playerData();
        FakePlayer player = fighter(helper, "er_gt_relic_gate", ItemStack.EMPTY);
        UUID id = player.getUUID();
        data.onPlayerJoin(id, "gametest_relics");
        AtomicBoolean done = new AtomicBoolean();
        AtomicReference<GameTestAssertException> failure = new AtomicReference<>();
        helper.succeedWhen(() -> {
            if (failure.get() != null) {
                throw failure.get();
            }
            if (done.get()) {
                return;
            }
            if (!data.isActive(id)) {
                throw new GameTestAssertException("waiting for the character to load");
            }
            try {
                gates(context, data, player);
                done.set(true);
            } catch (RuntimeException e) {
                GameTestAssertException wrapped = e instanceof GameTestAssertException assertion ? assertion
                        : new GameTestAssertException("relic gates failed: " + e);
                failure.set(wrapped);
                throw wrapped;
            } finally {
                for (TrinketSlots.Slot slot : TrinketSlots.Slot.values()) {
                    TrinketSlots.set(player, slot, ItemStack.EMPTY);
                }
                context.combat().onPlayerLeft(id);
                data.onPlayerLeave(id);
            }
        });
    }

    private static void gates(ServerContext context, PlayerDataService data, FakePlayer player) {
        UUID id = player.getUUID();
        Relics.Source alvin = Relics.source("l_alvin").orElseThrow();
        check(alvin.relicLevel() == 55, "Alvin's relics ask for level 55, not " + alvin.relicLevel());
        SplittableRandom random = new SplittableRandom(11);
        ItemStack weapon = RelicDrops.create(alvin.weapon(), random);
        Inventory inventory = player.getInventory();
        check(data.setLevel(id, 10) == SetLevelResult.SUCCESS, "setting level 10");
        // equipping from the bag
        inventory.items.set(3, weapon.copy());
        expect(BagService.equip(player, 3), BagService.EquipResult.LEVEL, "the Lv55 relic at level 10");
        // wielding it anyway (put straight into the hand): no art, no guard
        inventory.items.set(0, weapon.copy());
        inventory.items.set(3, ItemStack.EMPTY);
        check(CombatDriver.refusedLevel(player) == 55, "the held relic should need level 55, not "
                + CombatDriver.refusedLevel(player));
        Combatant combatant = context.combat().service().register(id, PLAIN, context.combat().now());
        context.combat().handleRequest(player, CombatRequest.SKILL, 1);
        check(combatant.currentAction().isEmpty(), "a weapon above the level must not start its art");
        context.combat().handleRequest(player, CombatRequest.LIGHT, 2);
        check(combatant.currentAction().isEmpty(), "a weapon above the level must not attack");
        WeaponProfile profile = WeaponItem.profileOf(player.getMainHandItem());
        check(!context.combat().startGuard(player, profile), "a weapon above the level must not guard");
        // at its level everything works
        check(data.setLevel(id, 55) == SetLevelResult.SUCCESS, "setting level 55");
        check(CombatDriver.refusedLevel(player) == 0, "at level 55 the relic may be used");
        context.combat().handleRequest(player, CombatRequest.SKILL, 3);
        Optional<ActionDefinition> action = combatant.currentAction();
        String art = GeneratedArts.forDesign(alvin.weapon().id()).orElseThrow().id();
        check(action.isPresent() && action.get().id().equals(art), "at level 55 the relic's art " + art
                + " should start, got " + action);
        // the trinkets: their own slots, their stats, one copy of each
        ComposedStats before = PlayerStats.compose(player, data.snapshot(id).orElseThrow());
        ItemStack ring = RelicDrops.create(alvin.accessory(), random);
        ItemStack bell = RelicDrops.create(alvin.charm(), random);
        inventory.items.set(4, ring.copy());
        inventory.items.set(5, bell.copy());
        check(data.setLevel(id, 10) == SetLevelResult.SUCCESS, "back to level 10");
        expect(BagService.equip(player, 4), BagService.EquipResult.LEVEL, "the Lv55 accessory at level 10");
        check(data.setLevel(id, 55) == SetLevelResult.SUCCESS, "level 55 again");
        expect(BagService.equip(player, 4), BagService.EquipResult.OK, "the accessory");
        expect(BagService.equip(player, 5), BagService.EquipResult.OK, "the charm");
        check(inventory.items.get(4).isEmpty() && inventory.items.get(5).isEmpty(), "both left the bag");
        check(EquipmentStacks.isGoldFrame(TrinketSlots.get(player, TrinketSlots.Slot.ACCESSORY_1)),
                "the ring should be worn in the first accessory slot");
        check(EquipmentStacks.isGoldFrame(TrinketSlots.get(player, TrinketSlots.Slot.CHARM)),
                "the bell should be worn in the charm slot");
        ComposedStats after = PlayerStats.compose(player, data.snapshot(id).orElseThrow());
        EquipmentData ringData = EquipmentStacks.read(ring).orElseThrow();
        EquipmentData bellData = EquipmentStacks.read(bell).orElseThrow();
        for (StatType type : StatType.values()) {
            int added = EquipmentStats.of(ringData).stats().get(type) + EquipmentStats.of(bellData).stats().get(type);
            check(after.total().get(type) - before.total().get(type) == added, type + " should rise by " + added
                    + ": " + before.total().get(type) + " -> " + after.total().get(type));
        }
        check(after.modifiers().maxHealthFlat() > before.modifiers().maxHealthFlat(), "the charm adds max health");
        // a second copy of the same accessory is refused
        inventory.items.set(6, RelicDrops.create(alvin.accessory(), random));
        expect(BagService.equip(player, 6, TrinketSlots.Slot.ACCESSORY_2), BagService.EquipResult.DUPLICATE,
                "the same accessory twice");
        // taking it off puts it back into the bag
        check(BagService.unequip(player, TrinketSlots.Slot.ACCESSORY_1), "taking the ring off");
        check(TrinketSlots.get(player, TrinketSlots.Slot.ACCESSORY_1).isEmpty(), "the slot should be empty");
        inventory.items.set(0, ItemStack.EMPTY);
    }

    /**
     * The same art from a common and from a legendary piece: the legendary one hits harder (×1.30 art power). Two
     * fighters, because a fighter's art cooldown outlasts leaving and coming back.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void rarityRaisesTheArt(GameTestHelper helper) {
        WeaponDesign design = Armory.weapon("harvest_moon_sabre").orElseThrow();
        WeaponArt art = GeneratedArts.forDesign(design.id()).orElseThrow();
        ItemStack common = EquipmentStacks.ofDesign(design);
        ItemStack legendary = EquipmentStacks.create(EquipmentGenerator.roll(design, ItemRarity.LEGENDARY,
                new SplittableRandom(3)));
        FakePlayer plain = fighter(helper, "er_gt_relic_rarity_common", common);
        FakePlayer rare = fighter(helper, "er_gt_relic_rarity_legendary", legendary);
        check(CombatDriver.artPower(plain, art) == 1.0, "a common piece adds nothing to its art");
        check(Math.abs(CombatDriver.artPower(rare, art) - ItemRarity.LEGENDARY.power()) < 1.0e-9,
                "a legendary piece raises its art ×" + ItemRarity.LEGENDARY.power() + ", got "
                        + CombatDriver.artPower(rare, art));
        check(CombatDriver.artPower(rare, GeneratedArts.forTier(ItemTier.IRON, design.type())) == 1.0,
                "another weapon's art gets nothing from this piece");
        float low = artDamage(helper, plain);
        float high = artDamage(helper, rare);
        check(low > 0.0F, "the common art should hurt the golem");
        check(high > low * 1.2F, "the legendary art should hit harder: " + low + " vs " + high);
        helper.succeed();
    }

    /** Runs the held weapon's art at a fresh golem in front; the health it took. */
    private static float artDamage(GameTestHelper helper, FakePlayer player) {
        ServerContext context = requireContext();
        IronGolem target = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(4, 2, 7));
        target.setNoAi(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
        target.setHealth(1000.0F);
        float before = target.getHealth();
        Vec3 position = helper.absoluteVec(new Vec3(4.5, 2.0, 4.5));
        player.moveTo(position.x, position.y, position.z, 0.0F, 0.0F);
        player.getRandom().setSeed(42L);
        UUID id = player.getUUID();
        Combatant combatant = context.combat().service().register(id, PLAIN, context.combat().now());
        try {
            long now = context.combat().now();
            context.combat().handleRequest(player, CombatRequest.SKILL, 1);
            ActionDefinition action = combatant.currentAction().orElseThrow(() -> new GameTestAssertException(
                    "the art did not start for " + player.getGameProfile().getName()));
            for (int t = 1; t <= action.durationTicks() + 1; t++) {
                for (CombatEvent event : combatant.tick(now + t)) {
                    CombatDriver.handleEvent(player, combatant.stats(), event);
                }
            }
            return before - target.getHealth();
        } finally {
            context.combat().onPlayerLeft(id);
            target.discard();
        }
    }

    /** With the odds forced to 1, every source drops one of its relics: legendary, gold-framed, its own level. */
    @GameTest(template = ARENA)
    public static void forcedRelicDropsHandOutGoldFramedRelics(GameTestHelper helper) {
        FakePlayer player = fighter(helper, "er_gt_relic_drop", ItemStack.EMPTY);
        SplittableRandom random = new SplittableRandom(5);
        RelicDrops.force(1.0);
        try {
            ItemStack stack = RelicDrops.roll(player, Relics.SourceKind.LORD, "garm", random)
                    .orElseThrow(() -> new GameTestAssertException("no relic with the odds forced to 1"));
            check(EquipmentStacks.isGoldFrame(stack), "a relic shows the gold frame");
            EquipmentData data = EquipmentStacks.read(stack).orElseThrow();
            check(data.rarity() == ItemRarity.LEGENDARY && data.isRelic(), "a relic is legendary: " + data.rarity());
            check(data.design().origin().equals("l_garm"), "Garm's relic came from " + data.design().origin());
            check(EquipmentStacks.requiredLevel(stack).orElse(0) == 20, "Garm's relics ask for level 20");
            check(data.uniqueId() != null, "a dropped relic has a unique id");
            for (Relics.Source source : Relics.sources()) {
                ItemStack drop = RelicDrops.roll(player, source.kind(), source.target(), random)
                        .orElseThrow(() -> new GameTestAssertException(source.key() + " dropped nothing"));
                Design design = EquipmentStacks.designOf(drop)
                        .orElseThrow(() -> new GameTestAssertException(source.key() + ": no design on the stack"));
                check(design.isRelic() && design.origin().equals(source.key()),
                        source.key() + " dropped " + design.id());
            }
            // every relic as a stack: its design, its model, and a relic weapon brings its own art
            for (Design design : Relics.designs()) {
                ItemStack made = EquipmentStacks.ofDesign(design);
                check(EquipmentStacks.designOf(made).map(Design::id).orElse("").equals(design.id()),
                        design.id() + " did not survive the stack");
                check(made.getTag() != null && made.getTag().getInt(EquipmentStacks.MODEL_TAG) == design.model(),
                        design.id() + " should carry custom model data " + design.model());
                if (design instanceof WeaponDesign weapon) {
                    WeaponProfile profile = WeaponItem.profileOf(made);
                    check(profile != null && profile.skill().id().equals(GeneratedArts.forDesign(weapon.id())
                            .orElseThrow().id()), design.id() + " should bring its relic art");
                }
            }
            RelicDrops.force(0.0);
            check(RelicDrops.roll(player, Relics.SourceKind.LORD, "garm", random).isEmpty(), "odds 0: nothing");
            RelicDrops.force(1.0);
            check(RelicDrops.roll(player, Relics.SourceKind.LORD, "nobody", random).isEmpty(),
                    "an unknown source has no relics");
        } finally {
            RelicDrops.force(-1.0);
        }
        helper.succeed();
    }

    private static ServerContext requireContext() {
        return ServerContext.current().orElseThrow(() -> new GameTestAssertException("ServerContext is not installed"));
    }

    private static FakePlayer fighter(GameTestHelper helper, String name, ItemStack weapon) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name));
        Vec3 position = helper.absoluteVec(new Vec3(4.5, 2.0, 4.5));
        player.moveTo(position.x, position.y, position.z, 0.0F, 0.0F);
        player.getInventory().items.set(0, weapon);
        player.getInventory().selected = 0;
        player.removeAllEffects();
        player.setHealth(player.getMaxHealth());
        return player;
    }

    private static void expect(BagService.EquipResult actual, BagService.EquipResult wanted, String what) {
        check(actual == wanted, what + ": expected " + wanted + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
