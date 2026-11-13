package com.eternalrealm.gametest;

import com.eternalrealm.combat.ActionDefinition;
import com.eternalrealm.combat.CombatRequest;
import com.eternalrealm.combat.CombatStats;
import com.eternalrealm.combat.Combatant;
import com.eternalrealm.combat.WeaponProfile;
import com.eternalrealm.combat.forge.WeaponItem;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.item.Armory;
import com.eternalrealm.item.Design;
import com.eternalrealm.item.Signatures;
import com.eternalrealm.item.WeaponDesign;
import com.eternalrealm.item.forge.EquipmentStacks;
import com.eternalrealm.monster.MonsterSpawn;
import com.eternalrealm.monster.MonsterTemplate;
import com.eternalrealm.monster.Monsters;
import com.eternalrealm.monster.forge.MonsterFactory;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.world.forge.FieldThreatService;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/**
 * The armory on a real server: named design stacks carry their design and model, a held boss weapon brings its own
 * weapon art to the weapon-skill key, the lords whose body has a weapon hand carry their signature weapon, and the
 * bestiary knights carry their named blades. Same arena and conventions as {@link CombatGameTests}.
 */
@GameTestHolder(ModInfo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArmoryGameTests {
    private static final String ARENA = "arena";
    private static final CombatStats PLAIN = new CombatStats(1.0, 0.0, 0.0, 1.0, 0.0, 1.5, 100.0, 1.0);

    private ArmoryGameTests() {
    }

    /**
     * Every design survives the trip into a stack: design tag, custom model data and the design's own profile; the
     * smithy's catalogue trinkets and the named trinkets of the lords and dungeons too.
     */
    @GameTest(template = ARENA)
    public static void designStacksCarryTheirDesign(GameTestHelper helper) {
        java.util.List<Design> all = new java.util.ArrayList<>(Armory.designs());
        all.addAll(com.eternalrealm.item.Trinkets.all());
        all.addAll(com.eternalrealm.item.NamedTrinkets.all());
        for (Design design : all) {
            ItemStack stack = EquipmentStacks.ofDesign(design);
            Optional<Design> read = EquipmentStacks.designOf(stack);
            check(read.isPresent() && read.get().id().equals(design.id()), design.id() + " read back as " + read);
            check(stack.getTag() != null && stack.getTag().getInt(EquipmentStacks.MODEL_TAG) == design.model(),
                    design.id() + " should carry custom model data " + design.model());
            if (design instanceof WeaponDesign weapon) {
                WeaponProfile profile = WeaponItem.profileOf(stack);
                check(profile.type() == weapon.type(), design.id() + " has the profile of " + profile.type());
                if (weapon.isBossWeapon()) {
                    check(profile.skill().id().equals(weapon.art()), design.id() + " brings the weapon skill "
                            + profile.skill().id() + " instead of " + weapon.art());
                }
            }
        }
        helper.succeed();
    }

    /** The weapon-skill key with a boss weapon in hand starts the weapon's art on the server. */
    @GameTest(template = ARENA)
    public static void bossWeaponStartsItsArt(GameTestHelper helper) {
        ServerContext context = requireContext();
        WeaponDesign design = Signatures.bossWeapon("final").orElseThrow();
        FakePlayer player = fighter(helper, "er_gt_boss_weapon", EquipmentStacks.ofDesign(design));
        UUID id = player.getUUID();
        Combatant combatant = context.combat().service().register(id, PLAIN, context.combat().now());
        try {
            context.combat().handleRequest(player, CombatRequest.SKILL, 1);
            Optional<ActionDefinition> action = combatant.currentAction();
            check(action.isPresent(), "the weapon skill of " + design.id() + " did not start");
            check(action.get().id().equals(design.art()), "started " + action.get().id() + " instead of "
                    + design.art());
        } finally {
            context.combat().onPlayerLeft(id);
        }
        helper.succeed();
    }

    /** A plain weapon (the iron starter) brings its tier's art, never the type's generic skill. */
    @GameTest(template = ARENA)
    public static void plainWeaponUsesItsTierArt(GameTestHelper helper) {
        ServerContext context = requireContext();
        WeaponDesign design = Signatures.bossWeapon("final").orElseThrow();
        String expected = com.eternalrealm.item.GeneratedArts.forTier(com.eternalrealm.item.ItemTier.IRON,
                design.type()).id();
        FakePlayer player = fighter(helper, "er_gt_plain_weapon",
                new ItemStack(com.eternalrealm.combat.forge.ERItems.weaponFor(design.type())));
        UUID id = player.getUUID();
        Combatant combatant = context.combat().service().register(id, PLAIN, context.combat().now());
        try {
            context.combat().handleRequest(player, CombatRequest.SKILL, 1);
            Optional<ActionDefinition> action = combatant.currentAction();
            check(action.isPresent() && action.get().id().equals(expected),
                    "a plain " + design.type() + " should use " + expected + ", not " + action);
        } finally {
            context.combat().onPlayerLeft(id);
        }
        helper.succeed();
    }

    /** Z with a named weapon runs its own art end to end: its hits damage, its element afflicts, its cooldown runs. */
    @GameTest(template = ARENA)
    public static void namedArtRunsEndToEnd(GameTestHelper helper) {
        // 収穫月の一閃: a precise thrust that blinds (holy) and heals
        WeaponDesign design = com.eternalrealm.item.Armory.weapon("harvest_moon_sabre").orElseThrow();
        runArt(helper, "er_gt_art_named", EquipmentStacks.ofDesign(design),
                com.eternalrealm.item.GeneratedArts.forDesign(design.id()).orElseThrow().id(),
                target -> target.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS), "blinded");
    }

    /** Z with a tier weapon runs the tier's art end to end (flame greatsword: a burning sweep). */
    @GameTest(template = ARENA)
    public static void tierArtRunsEndToEnd(GameTestHelper helper) {
        ItemStack flame = EquipmentStacks.create(com.eternalrealm.item.EquipmentData.of(
                com.eternalrealm.item.EquipmentBase.weapon(com.eternalrealm.combat.WeaponType.TWO_HANDED_SWORD,
                        com.eternalrealm.item.ItemTier.FLAME), com.eternalrealm.item.ItemRarity.COMMON,
                java.util.List.of()));
        runArt(helper, "er_gt_art_tier", flame, com.eternalrealm.item.GeneratedArts.forTier(
                        com.eternalrealm.item.ItemTier.FLAME, com.eternalrealm.combat.WeaponType.TWO_HANDED_SWORD).id(),
                net.minecraft.world.entity.LivingEntity::isOnFire, "set on fire");
    }

    /** A projectile art flies and bursts on its own (iron staff): the target in front loses health. */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void projectileArtHitsItsTarget(GameTestHelper helper) {
        ServerContext context = requireContext();
        net.minecraft.world.entity.animal.IronGolem target = helper.spawn(
                net.minecraft.world.entity.EntityType.IRON_GOLEM, new net.minecraft.core.BlockPos(4, 2, 9));
        target.setNoAi(true);
        FakePlayer player = fighter(helper, "er_gt_art_bolt", new ItemStack(
                com.eternalrealm.combat.forge.ERItems.weaponFor(com.eternalrealm.combat.WeaponType.STAFF)));
        UUID id = player.getUUID();
        Combatant combatant = context.combat().service().register(id, PLAIN, context.combat().now());
        float before = target.getHealth();
        long now = context.combat().now();
        context.combat().handleRequest(player, CombatRequest.SKILL, 1);
        ActionDefinition action = combatant.currentAction()
                .orElseThrow(() -> new GameTestAssertException("the staff's art did not start"));
        for (int t = 1; t <= action.durationTicks() + 1; t++) {
            for (com.eternalrealm.combat.CombatEvent event : combatant.tick(now + t)) {
                com.eternalrealm.combat.forge.CombatDriver.handleEvent(player, combatant.stats(), event);
            }
        }
        context.combat().onPlayerLeft(id);
        helper.succeedWhen(() -> check(target.getHealth() < before, "the bolt should hit the golem in front"));
    }

    private static void runArt(GameTestHelper helper, String name, ItemStack weapon, String expectedArt,
                               java.util.function.Predicate<net.minecraft.world.entity.LivingEntity> afflicted,
                               String what) {
        ServerContext context = requireContext();
        net.minecraft.world.entity.animal.IronGolem target = helper.spawn(
                net.minecraft.world.entity.EntityType.IRON_GOLEM, new net.minecraft.core.BlockPos(4, 2, 7));
        target.setNoAi(true);
        FakePlayer player = fighter(helper, name, weapon);
        UUID id = player.getUUID();
        Combatant combatant = context.combat().service().register(id, PLAIN, context.combat().now());
        try {
            float before = target.getHealth();
            long now = context.combat().now();
            context.combat().handleRequest(player, CombatRequest.SKILL, 1);
            ActionDefinition action = combatant.currentAction()
                    .orElseThrow(() -> new GameTestAssertException(expectedArt + " did not start"));
            check(action.id().equals(expectedArt), "started " + action.id() + " instead of " + expectedArt);
            check(combatant.skillCooldownRemaining(0, now + 1) > 0, expectedArt + " should be on cooldown");
            for (int t = 1; t <= action.durationTicks() + 1; t++) {
                for (com.eternalrealm.combat.CombatEvent event : combatant.tick(now + t)) {
                    com.eternalrealm.combat.forge.CombatDriver.handleEvent(player, combatant.stats(), event);
                }
            }
            check(target.getHealth() < before, expectedArt + " should damage the golem in front");
            check(afflicted.test(target), expectedArt + " should leave the golem " + what);
        } finally {
            context.combat().onPlayerLeft(id);
            target.discard();
        }
        helper.succeed();
    }

    /** Lords with a weapon hand carry their signature weapon; beasts do not. */
    @GameTest(template = ARENA)
    public static void lordsCarryTheirSignatureWeapons(GameTestHelper helper) {
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
        Mob strom = FieldThreatService.spawnLordForTest(helper.getLevel(), "strom", at)
                .orElseThrow(() -> new GameTestAssertException("strom did not spawn"));
        Mob durn = FieldThreatService.spawnLordForTest(helper.getLevel(), "durn", at.east(3))
                .orElseThrow(() -> new GameTestAssertException("durn did not spawn"));
        try {
            Optional<Design> held = EquipmentStacks.designOf(strom.getMainHandItem());
            check(held.isPresent() && held.get().id().equals(Signatures.lordWeapon("strom").orElseThrow().id()),
                    "strom holds " + strom.getMainHandItem() + " instead of his signature staff");
            check(EquipmentStacks.designOf(durn.getMainHandItem()).isEmpty(),
                    "the boar durn should not hold a weapon: " + durn.getMainHandItem());
        } finally {
            strom.discard();
            durn.discard();
        }
        helper.succeed();
    }

    /** Bestiary knights whose body holds a blade carry their named blade. */
    @GameTest(template = ARENA)
    public static void bestiaryKnightsCarryNamedBlades(GameTestHelper helper) {
        for (String templateId : new String[] {"f3_rust_guard", "f6_sky_knight"}) {
            MonsterTemplate template = Monsters.byId(templateId)
                    .orElseThrow(() -> new GameTestAssertException("no template " + templateId));
            WeaponDesign expected = Signatures.wielded(templateId).orElseThrow();
            Mob mob = MonsterFactory.create(helper.getLevel(), new MonsterSpawn(template, template.levels().max()),
                    false).orElseThrow(() -> new GameTestAssertException(templateId + " was not made"));
            try {
                Optional<Design> held = EquipmentStacks.designOf(mob.getMainHandItem());
                check(held.isPresent() && held.get().id().equals(expected.id()),
                        templateId + " holds " + mob.getMainHandItem() + " instead of " + expected.id());
            } finally {
                mob.discard();
            }
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
        player.setItemInHand(InteractionHand.MAIN_HAND, weapon);
        player.removeAllEffects();
        player.setHealth(player.getMaxHealth());
        return player;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
