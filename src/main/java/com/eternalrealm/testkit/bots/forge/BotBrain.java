package com.eternalrealm.testkit.bots.forge;

import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.character.StatType;
import com.eternalrealm.estate.EstateKey;
import com.eternalrealm.estate.RoomDesign;
import com.eternalrealm.estate.forge.EstateLevels;
import com.eternalrealm.estate.forge.EstateService;
import com.eternalrealm.estate.forge.InnService;
import com.eternalrealm.item.Consumable;
import com.eternalrealm.item.ItemPrices;
import com.eternalrealm.item.forge.ConsumableItem;
import com.eternalrealm.item.forge.MainHand;
import com.eternalrealm.item.forge.QuickUse;
import com.eternalrealm.item.forge.ShopMenu;
import com.eternalrealm.job.JobType;
import com.eternalrealm.job.Jobs;
import com.eternalrealm.job.forge.JobActions;
import com.eternalrealm.menu.ServerMenu;
import com.eternalrealm.monster.MonsterTemplate;
import com.eternalrealm.monster.Monsters;
import com.eternalrealm.party.Party;
import com.eternalrealm.revival.forge.RevivalService;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.server.UiActions;
import com.eternalrealm.testkit.bots.BotPlans;
import com.eternalrealm.world.field.FieldLayout;
import com.eternalrealm.world.field.Habitats;
import com.eternalrealm.world.field.Waystones;
import com.eternalrealm.world.forge.AstoriaTravel;
import com.eternalrealm.world.forge.HubLayout;
import com.eternalrealm.world.forge.Realm;
import com.eternalrealm.world.forge.WaystoneService;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The mind of a test bot (docs/modules/testkit.md §4): what a player does in a session, in turn. It takes its job at
 * the job master, then lives in the capital and the fields: shops (sells its loot, buys potions and a return scroll),
 * rests at the inn (rents the cheapest room once it can pay, or sits in the lobby), strolls about town, and goes
 * hunting at a habitat that suits its level, alone or with a party it gathers from the other bots; its party follows
 * it out, fights beside it and comes back with it. It spends its stat and skill points as it levels, drinks when hurt,
 * revives a fallen party member with a petal when it has one, returns from a fall like a player, and now and then says
 * something in the chat. Everything goes through the services a client's requests use.
 *
 * <p>Held ({@link #hold()}), it makes no plans of its own and only carries out orders ({@link #walkTo},
 * {@link #fight}): for tests.</p>
 */
public final class BotBrain {
    private static final Logger LOG = LogUtils.getLogger();
    /** At most one chat line per bot in this many ticks (6 minutes). */
    private static final int CHAT_GAP = 20 * 60 * 6;
    /** How long a hunt lasts at least (ticks); up to half again as long. */
    private static final int HUNT_TICKS = 20 * 60 * 4;
    /** A walk to a shop or the inn taking longer than this (ticks) ends with a quick trip. */
    private static final int ERRAND_WALK_TICKS = 20 * 90;
    /** Party members farther than this from their leader catch up at once (as by the same waystone). */
    private static final double CATCH_UP = 48.0;

    /** What the bot is doing. */
    public enum Activity {
        /** Just logged in: waits for its character, then takes its job. */
        SETUP,
        /** In the capital, deciding or strolling. */
        TOWN,
        SHOP,
        INN,
        HUNT,
        /** On its way back to the capital. */
        RETURN,
        /** Fallen, waiting for help or to return. */
        DOWN
    }

    private final TestBot bot;
    private final RandomSource random;
    private final BotMover mover;
    private final BotCombat combat;
    private Activity activity = Activity.SETUP;
    private int step;
    private int since;
    private int wait;
    private int ticks;
    private boolean held;
    private boolean townOnly;
    private int failures;
    private Vec3 spot;
    private Vec3 roamCenter;
    private double roamRadius;
    private int groundField;
    private int huntUntil;
    private int huntsSinceRest;
    private UUID leader;
    private UUID reviving;
    private int lastLevel;
    private int lastChat = -CHAT_GAP;
    private int downSince = -1;
    private long lastFailure;

    BotBrain(TestBot bot) {
        this.bot = bot;
        this.random = bot.getRandom();
        this.mover = new BotMover(bot);
        this.combat = new BotCombat(bot, mover, random);
    }

    // ------------------------------------------------------------------ orders (tests, commands)

    /** No plans of its own any more: only orders. */
    public void hold() {
        held = true;
        mover.stop();
        combat.disengage();
    }

    /** Back to its own plans. */
    public void release() {
        held = false;
        begin(activity == Activity.SETUP ? Activity.SETUP : Activity.TOWN);
    }

    public boolean held() {
        return held;
    }

    /** Errands in town only, no hunting (tests that must not send a bot out into the fields). */
    public void keepInTown(boolean only) {
        townOnly = only;
    }

    /** How often its plans went wrong ({@link #failed}). */
    public int failures() {
        return failures;
    }

    /** Walks to {@code target} (done within {@code within} blocks). */
    public void walkTo(Vec3 target, double within) {
        mover.goTo(target, within, false);
    }

    public boolean arrived() {
        return mover.arrived();
    }

    /** Fights {@code enemy}. */
    public void fight(LivingEntity enemy) {
        combat.engage(enemy);
    }

    public LivingEntity enemy() {
        return combat.target();
    }

    public Activity activity() {
        return activity;
    }

    /** Starts {@code next} at once (a command): a hunt is started as the bot would start it. */
    public void send(Activity next) {
        held = false;
        if (next == Activity.HUNT) {
            ServerContext.current().ifPresent(context -> context.playerData().snapshot(bot.getUUID())
                    .ifPresent(me -> startHunt(context, me, false)));
        } else {
            begin(next);
        }
    }

    /** It would have died where nothing catches a fall ({@link TestBots#onLivingDeath}): home it goes. */
    void knockedOut() {
        combat.disengage();
        begin(Activity.RETURN);
    }

    /** Called by its party leader: out to the leader's hunt. */
    void joinHunt(TestBot leaderBot) {
        if (held || activity == Activity.DOWN || activity == Activity.SETUP) {
            return;
        }
        leader = leaderBot.getUUID();
        begin(Activity.HUNT);
        step = 10;
    }

    /** Whether the bot is in town with nothing to do but errands: free to join a party. */
    boolean available() {
        return !held && activity == Activity.TOWN && inTown();
    }

    // ------------------------------------------------------------------ tick

    void tick() {
        ticks++;
        ServerContext context = ServerContext.current().orElse(null);
        if (context == null || !context.playerData().isActive(bot.getUUID())) {
            standStill();
            return;
        }
        if (RevivalService.isDown(bot.getUUID())) {
            down(context);
            return;
        }
        if (activity == Activity.DOWN) {
            downSince = -1;
            begin(Activity.RETURN); // back on its feet (helped up or returned): home to restock
        }
        CharacterSnapshot me = context.playerData().snapshot(bot.getUUID()).orElse(null);
        if (me == null) {
            standStill();
            return;
        }
        if (!held) {
            if (ticks % 20 == 0) {
                upkeep(context, me);
            }
            switch (activity) {
                case SETUP -> setup(context, me);
                case TOWN -> town(context, me);
                case SHOP -> shop(me);
                case INN -> inn(context, me);
                case HUNT -> hunt(context, me);
                case RETURN -> goHome();
                case DOWN -> {
                    // handled above
                }
            }
        }
        combat.beforeMove();
        mover.tick();
        combat.afterMove(me.level());
    }

    /** Something went wrong in its plans: logged (rarely), and it starts over from the capital. */
    void failed(RuntimeException e) {
        failures++;
        long now = System.currentTimeMillis();
        if (now - lastFailure > 30_000L) {
            lastFailure = now;
            LOG.warn("Test bot {} stumbled while {} (step {}); starting over", bot.getGameProfile().getName(), activity,
                    step, e);
        }
        mover.stop();
        combat.disengage();
        if (bot.containerMenu != bot.inventoryMenu) {
            bot.closeContainer();
        }
        begin(activity == Activity.SETUP ? Activity.SETUP : Activity.RETURN);
    }

    private void standStill() {
        mover.stop();
        mover.tick();
    }

    private void begin(Activity next) {
        activity = next;
        step = 0;
        since = ticks;
        wait = 0;
        mover.stop();
        if (next != Activity.HUNT) {
            combat.disengage();
            leader = null;
            reviving = null;
        }
        if (bot.containerMenu != bot.inventoryMenu) {
            bot.closeContainer();
        }
    }

    private void stepTo(int next) {
        step = next;
        since = ticks;
    }

    private int elapsed() {
        return ticks - since;
    }

    // ------------------------------------------------------------------ upkeep

    /** Spends stat and skill points as a player of its job would, and keeps a weapon in hand. */
    private void upkeep(ServerContext context, CharacterSnapshot me) {
        JobType job = JobType.fromStored(me.jobId());
        if (job.isChosen() && me.unspentStatPoints() > 0) {
            Map<StatType, Integer> plan = BotPlans.allocation(Jobs.get(job).growthPerLevel(), me.allocatedStats(),
                    me.unspentStatPoints());
            plan.forEach((stat, points) -> context.playerData().allocateStat(bot.getUUID(), stat, points));
        }
        if (job.isChosen() && me.unspentSkillPoints() > 0) {
            BotPlans.nextSkill(job, me.level(), me.skillInvestments(), me.unspentSkillPoints())
                    .ifPresent(skill -> JobActions.invest(bot, context, skill));
        }
        if (me.level() > lastLevel) {
            if (lastLevel > 0) {
                say("level", 0.5, me.level());
            }
            lastLevel = me.level();
        }
        if (job.isChosen() && ticks % 200 == 0) {
            MainHand.ensureWeapon(bot);
        }
    }

    // ------------------------------------------------------------------ setup

    private void setup(ServerContext context, CharacterSnapshot me) {
        if (me.hasJob()) {
            lastLevel = me.level();
            begin(Activity.TOWN);
            return;
        }
        if (step == 1) {
            if (elapsed() > 100) {
                stepTo(0); // the job was not taken (still loading?): try again
            }
            return;
        }
        Optional<HubLayout> hub = TestBots.hub(bot.server);
        if (hub.isPresent() && inTown() && elapsed() < 20 * 45) {
            Vec3 master = Vec3.atBottomCenterOf(hub.get().jobMaster());
            if (!near(master, 4.0)) {
                walk(master, 3.0);
                return;
            }
        }
        mover.stop();
        if (JobActions.chooseJob(bot, context, BotPlans.jobOf(bot.number()))) {
            say("greet", 0.6);
        }
        stepTo(1);
    }

    // ------------------------------------------------------------------ town

    private void town(ServerContext context, CharacterSnapshot me) {
        if (TestBots.hub(bot.server).isEmpty() || !AstoriaTravel.isInAstoria(bot)) {
            if (!townOnly) {
                startHunt(context, me, true); // a world without the capital: nothing to do but hunt
            }
            return;
        }
        if (!inTown()) {
            begin(Activity.RETURN);
            return;
        }
        switch (step) {
            case 0 -> {
                long price = ItemPrices.consumableShopPrice(BotPlans.bestPotion(me.level()));
                BotPlans.Errand errand = BotPlans.nextErrand(BotBag.potions(bot), BotBag.freeSlots(bot), me.gold(),
                        price, huntsSinceRest, random.nextDouble());
                switch (errand) {
                    case SHOP -> {
                        say("shop", 0.2);
                        begin(Activity.SHOP);
                    }
                    case INN -> {
                        say("inn", 0.3);
                        begin(Activity.INN);
                    }
                    case WANDER -> stroll();
                    case HUNT -> {
                        if (townOnly) {
                            stroll();
                        } else {
                            startHunt(context, me, true);
                        }
                    }
                }
            }
            case 1 -> {
                if (near(spot, 3.0) || elapsed() > 20 * 45) {
                    wait = 60 + random.nextInt(160);
                    stepTo(2);
                } else {
                    walk(spot, 2.5);
                }
            }
            case 2 -> {
                if (elapsed() % 30 == 0) {
                    lookAround();
                }
                if (elapsed() >= wait) {
                    stepTo(0);
                }
            }
            default -> stepTo(0);
        }
    }

    /** A stroll to one of the capital's places (a shop, a board, a master). */
    private void stroll() {
        Optional<HubLayout> hub = TestBots.hub(bot.server);
        if (hub.isEmpty()) {
            return;
        }
        List<BlockPos> places = new ArrayList<>(hub.get().namedWithPrefix("npc.").values());
        places.add(hub.get().spawnPoint());
        spot = Vec3.atBottomCenterOf(places.get(random.nextInt(places.size())));
        stepTo(1);
    }

    // ------------------------------------------------------------------ shop

    private void shop(CharacterSnapshot me) {
        Optional<HubLayout> hub = TestBots.hub(bot.server);
        if (hub.isEmpty()) {
            begin(Activity.TOWN);
            return;
        }
        switch (step) {
            case 0 -> {
                Vec3 counter = Vec3.atBottomCenterOf(hub.get().itemShop());
                if (!near(counter, 3.5)) {
                    reach(counter, 2.5);
                    return;
                }
                mover.stop();
                ShopMenu.open(bot);
                wait = 15 + random.nextInt(25); // a look at the shelves
                stepTo(1);
            }
            case 1 -> {
                if (!(bot.containerMenu instanceof ServerMenu)) {
                    begin(Activity.TOWN);
                } else if (elapsed() >= wait) {
                    stepTo(2);
                }
            }
            case 2 -> {
                if (elapsed() % 4 != 0) {
                    return;
                }
                if (elapsed() > 400) {
                    stepTo(3); // something would not sell: on to buying
                    return;
                }
                List<Integer> loot = BotBag.loot(bot);
                if (!loot.isEmpty()) {
                    clickInventory(loot.get(0), false);
                    return;
                }
                List<Integer> spare = BotBag.spareEquipment(bot);
                if (!spare.isEmpty() && BotBag.freeSlots(bot) < BotPlans.FEW_FREE_SLOTS * 2) {
                    clickInventory(spare.get(0), true); // equipment is sold with a shift-click
                    return;
                }
                stepTo(3);
            }
            case 3 -> {
                if (elapsed() % 3 != 0) {
                    return;
                }
                if (elapsed() > 200) {
                    wait = 10;
                    stepTo(4); // a purchase that does not go through: done shopping
                    return;
                }
                long gold = gold();
                Consumable potion = BotPlans.bestPotion(me.level());
                if (BotPlans.potionsToBuy(BotBag.potions(bot), gold, ItemPrices.consumableShopPrice(potion)) > 0) {
                    clickShelf(potion);
                    return;
                }
                if (BotBag.slotOf(bot, Consumable.RETURN_SCROLL) < 0
                        && gold >= ItemPrices.consumableShopPrice(Consumable.RETURN_SCROLL) * 5) {
                    clickShelf(Consumable.RETURN_SCROLL);
                    return;
                }
                if (BotBag.slotOf(bot, Consumable.EVERBLOOM_PETAL) < 0
                        && gold >= ItemPrices.consumableShopPrice(Consumable.EVERBLOOM_PETAL) * 10) {
                    clickShelf(Consumable.EVERBLOOM_PETAL);
                    return;
                }
                wait = 10 + random.nextInt(30);
                stepTo(4);
            }
            case 4 -> {
                if (elapsed() >= wait) {
                    begin(Activity.TOWN);
                }
            }
            default -> begin(Activity.TOWN);
        }
    }

    /** A click on the shop's shelf (one of {@code consumable}), as the player's click on its icon. */
    private void clickShelf(Consumable consumable) {
        if (bot.containerMenu instanceof ServerMenu menu) {
            menu.clicked(ShopMenu.shelfSlot(consumable), 0, ClickType.PICKUP, bot);
        }
    }

    /** A click on the bot's own inventory slot in the open shop (selling it). */
    private void clickInventory(int inventorySlot, boolean shift) {
        if (!(bot.containerMenu instanceof ServerMenu menu)) {
            return;
        }
        for (Slot slot : menu.slots) {
            if (slot.container == bot.getInventory() && slot.getContainerSlot() == inventorySlot) {
                menu.clicked(slot.index, 0, shift ? ClickType.QUICK_MOVE : ClickType.PICKUP, bot);
                return;
            }
        }
    }

    // ------------------------------------------------------------------ inn

    private void inn(ServerContext context, CharacterSnapshot me) {
        Optional<HubLayout> hub = TestBots.hub(bot.server);
        Optional<BlockPos> desk = hub.flatMap(layout -> layout.named("npc.innkeeper"));
        if (desk.isEmpty()) {
            begin(Activity.TOWN);
            return;
        }
        Optional<EstateKey> room = EstateService.estate(EstateKey.ROOM, bot.getUUID());
        switch (step) {
            case 0 -> {
                Vec3 at = Vec3.atBottomCenterOf(desk.get());
                if (!near(at, 3.5)) {
                    reach(at, 2.5);
                    return;
                }
                mover.stop();
                stepTo(1);
            }
            case 1 -> {
                if (room.isPresent() && EstateService.available()) {
                    UiActions.dispatch(bot, context, InnService.VISIT, new JsonObject());
                    stepTo(3);
                } else if (EstateService.available() && me.gold() >= RoomDesign.GARRET.price() + 500L) {
                    JsonObject args = new JsonObject();
                    args.addProperty("design", RoomDesign.GARRET.id());
                    UiActions.dispatch(bot, context, InnService.RENT, args);
                    say("room", 0.5);
                    stepTo(2);
                } else {
                    wait = 400 + random.nextInt(400); // a rest in the lobby
                    stepTo(5);
                }
            }
            case 2 -> {
                if (room.isPresent() && EstateService.ready(room.get())) {
                    stepTo(1);
                } else if (elapsed() > 20 * 60) {
                    wait = 200;
                    stepTo(5);
                }
            }
            case 3 -> {
                if (EstateLevels.isEstateLevel(bot.level())) {
                    spot = bot.position();
                    wait = 20 * (30 + random.nextInt(60));
                    stepTo(4);
                } else if (elapsed() > 200) {
                    wait = 200; // the room was not ready: a rest in the lobby instead
                    stepTo(5);
                }
            }
            case 4 -> {
                if (!EstateLevels.isEstateLevel(bot.level())) {
                    huntsSinceRest = 0;
                    begin(Activity.TOWN);
                    return;
                }
                if (elapsed() % 100 == 50) {
                    walk(spot.add(random.nextInt(5) - 2, 0.0, random.nextInt(5) - 2), 0.8);
                }
                if (elapsed() >= wait) {
                    EstateService.leave(bot);
                    huntsSinceRest = 0;
                    begin(Activity.TOWN);
                }
            }
            case 5 -> {
                if (elapsed() % 40 == 0) {
                    lookAround();
                }
                if (elapsed() >= wait) {
                    huntsSinceRest = 0;
                    begin(Activity.TOWN);
                }
            }
            default -> begin(Activity.TOWN);
        }
    }

    // ------------------------------------------------------------------ hunting

    /**
     * Out to hunt: a party leader (or a bot gathering one) picks the habitat and calls its members; a member waits in
     * town for its leader's call.
     */
    private void startHunt(ServerContext context, CharacterSnapshot me, boolean mayRecruit) {
        Optional<Party> party = context.game().parties().service().partyOf(bot.getUUID());
        if (party.isPresent() && !party.get().isLeader(bot.getUUID()) && inTown()) {
            if (TestBots.byId(party.get().leader()).isPresent()) {
                stroll(); // the leader calls when it goes
                return;
            }
            UiActions.dispatch(bot, context, "party.leave", new JsonObject()); // its leader is gone
            party = Optional.empty();
        }
        if (party.isEmpty() && mayRecruit && inTown() && random.nextFloat() < 0.5F) {
            party = recruit(context, me);
        }
        leader = null;
        begin(Activity.HUNT);
        chooseGround(me.level());
        say(party.isPresent() ? "party" : "hunt", 0.25);
        party.ifPresent(found -> {
            for (UUID member : found.memberIds()) {
                if (!member.equals(bot.getUUID())) {
                    TestBots.byId(member).ifPresent(other -> other.mind().joinHunt(bot));
                }
            }
        });
    }

    /** Gathers one to three free bots of a similar level near it into a party. */
    private Optional<Party> recruit(ServerContext context, CharacterSnapshot me) {
        List<TestBot> free = new ArrayList<>();
        for (TestBot other : TestBots.online()) {
            if (other == bot || !other.mind().available() || other.level() != bot.level()
                    || other.distanceTo(bot) > 64.0
                    || context.game().parties().service().partyOf(other.getUUID()).isPresent()) {
                continue;
            }
            int level = context.playerData().snapshot(other.getUUID()).map(CharacterSnapshot::level).orElse(-100);
            if (Math.abs(level - me.level()) <= 6) {
                free.add(other);
            }
        }
        if (free.isEmpty()) {
            return Optional.empty();
        }
        Collections.shuffle(free, new java.util.Random(random.nextLong()));
        int wanted = 1 + random.nextInt(3);
        for (TestBot other : free.subList(0, Math.min(wanted, free.size()))) {
            JsonObject invite = new JsonObject();
            invite.addProperty("name", other.getGameProfile().getName());
            UiActions.dispatch(bot, context, "party.invite", invite);
            UiActions.dispatch(other, context, "party.accept", new JsonObject());
        }
        return context.game().parties().service().partyOf(bot.getUUID());
    }

    /** Picks the habitat to hunt at (a place away from the capital in a world without habitats). */
    private void chooseGround(int level) {
        ServerLevel realm = Realm.level(bot.server);
        if (Realm.hasDimension(bot.server)) {
            List<Habitats.Habitat> grounds = BotPlans.huntingGrounds(level, Habitats.all(),
                    monster -> Monsters.byId(monster).map(MonsterTemplate::levels));
            if (!grounds.isEmpty()) {
                Habitats.Habitat ground = grounds.get(random.nextInt(grounds.size()));
                roamCenter = new Vec3(ground.x() + 0.5, 0.0, ground.z() + 0.5);
                roamRadius = Math.max(6.0, ground.radius());
                groundField = ground.field();
                return;
            }
        }
        BlockPos center = Realm.center(realm);
        double angle = random.nextDouble() * Math.PI * 2.0;
        double distance = 130.0 + random.nextDouble() * 90.0;
        roamCenter = new Vec3(center.getX() + Math.cos(angle) * distance, 0.0,
                center.getZ() + Math.sin(angle) * distance);
        roamRadius = 24.0;
        groundField = FieldLayout.areaAt(roamCenter.x, roamCenter.z);
    }

    private void hunt(ServerContext context, CharacterSnapshot me) {
        if (step >= 10) {
            follow(context);
            return;
        }
        if (roamCenter == null) {
            chooseGround(me.level());
        }
        ServerLevel realm = Realm.level(bot.server);
        reviveAllies(context);
        switch (step) {
            case 0 -> {
                travelTo(realm);
                huntUntil = ticks + HUNT_TICKS + random.nextInt(HUNT_TICKS / 2);
                stepTo(1);
            }
            case 1 -> {
                defend();
                Vec3 center = ground(realm, roamCenter);
                if (bot.level() == realm && horizontal(bot.position(), center) <= Math.max(4.0, roamRadius * 0.6)) {
                    stepTo(2);
                } else if (!combat.fighting() && reviving == null) {
                    walk(center, 3.0);
                }
            }
            case 2 -> {
                if (!combat.fighting() && reviving == null && ticks % 5 == 0) {
                    Optional<Mob> prey = BotCombat.nearestMonster(bot, bot.position(), 20.0,
                            mob -> horizontal(mob.position(), roamCenter) <= roamRadius + 16.0);
                    if (prey.isPresent()) {
                        combat.engage(prey.get());
                    } else if (!mover.moving()) {
                        double angle = random.nextDouble() * Math.PI * 2.0;
                        double distance = random.nextDouble() * roamRadius;
                        walk(ground(realm, roamCenter.add(Math.cos(angle) * distance, 0.0, Math.sin(angle) * distance)),
                                1.5);
                    }
                }
                boolean spent = BotBag.potions(bot) == 0 && bot.getHealth() < bot.getMaxHealth() * 0.35F;
                if (ticks >= huntUntil || spent || BotBag.freeSlots(bot) < 4) {
                    huntsSinceRest++;
                    begin(Activity.RETURN);
                }
            }
            default -> stepTo(0);
        }
    }

    /** From the capital (or from far away) to the hunting ground: by the nearest waystone, as a player travels. */
    private void travelTo(ServerLevel realm) {
        if (bot.level() == realm && horizontal(bot.position(), roamCenter) < 160.0 && !inTown()) {
            return;
        }
        if (Realm.hasDimension(bot.server)) {
            Optional<FieldLayout.Waystone> stone = BotPlans.waystoneFor(groundField, roamCenter.x, roamCenter.z,
                    Waystones.all());
            if (stone.isPresent()) {
                WaystoneService.Arrival arrival = WaystoneService.arrival(realm, stone.get());
                bot.teleportTo(realm, arrival.position().x, arrival.position().y, arrival.position().z, arrival.yaw(),
                        0.0F);
                bot.fallDistance = 0.0F;
                return;
            }
        }
        Vec3 at = ground(realm, roamCenter);
        bot.teleportTo(realm, at.x, at.y, at.z, bot.getYRot(), 0.0F);
        bot.fallDistance = 0.0F;
    }

    /** A party member on its leader's hunt: keeps up with it and fights what it fights. */
    private void follow(ServerContext context) {
        ServerPlayer lead = leader == null ? null : bot.server.getPlayerList().getPlayer(leader);
        boolean together = lead != null && context.game().parties().service().partyOf(bot.getUUID())
                .filter(party -> party.contains(leader)).isPresent();
        boolean leaderHunting = lead instanceof TestBot leadBot
                ? leadBot.mind().activity() == Activity.HUNT || leadBot.mind().activity() == Activity.DOWN
                : lead != null;
        if (!together || !leaderHunting) {
            begin(Activity.RETURN);
            return;
        }
        reviveAllies(context);
        if (lead.level() != bot.level() || bot.distanceTo(lead) > CATCH_UP) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            bot.teleportTo(lead.serverLevel(), lead.getX() + Math.cos(angle) * 2.0, lead.getY(),
                    lead.getZ() + Math.sin(angle) * 2.0, lead.getYRot(), 0.0F);
            bot.fallDistance = 0.0F;
            return;
        }
        if (combat.fighting() || reviving != null || ticks % 5 != 0) {
            return;
        }
        LivingEntity theirs = lead instanceof TestBot leadBot ? leadBot.mind().enemy() : null;
        if (theirs != null && theirs.distanceTo(bot) < 20.0) {
            combat.engage(theirs);
            return;
        }
        Optional<Mob> near = BotCombat.nearestMonster(bot, lead.position(), 14.0, mob -> true);
        if (near.isPresent()) {
            combat.engage(near.get());
        } else if (bot.distanceTo(lead) > 5.0) {
            walk(lead.position(), 3.0);
        }
    }

    /** On the way: a monster that goes for the bot is fought. */
    private void defend() {
        if (combat.fighting() || ticks % 5 != 0) {
            return;
        }
        BotCombat.nearestMonster(bot, bot.position(), 10.0, mob -> mob.getTarget() == bot).ifPresent(combat::engage);
    }

    /** A fallen party member near it is revived with a petal, if the bot has one. */
    private void reviveAllies(ServerContext context) {
        if (ticks % 10 != 0 && reviving == null) {
            return;
        }
        int petal = BotBag.slotOf(bot, Consumable.EVERBLOOM_PETAL);
        if (petal < 0) {
            reviving = null;
            return;
        }
        ServerPlayer fallen = reviving == null ? null : bot.server.getPlayerList().getPlayer(reviving);
        if (fallen == null || !RevivalService.isDown(fallen.getUUID()) || fallen.level() != bot.level()) {
            reviving = null;
            fallen = null;
            Optional<Party> party = context.game().parties().service().partyOf(bot.getUUID());
            if (party.isEmpty()) {
                return;
            }
            for (UUID member : party.get().memberIds()) {
                ServerPlayer other = bot.server.getPlayerList().getPlayer(member);
                if (other != null && other != bot && other.level() == bot.level() && other.distanceTo(bot) < 24.0
                        && RevivalService.isDown(other.getUUID())) {
                    reviving = member;
                    fallen = other;
                    combat.disengage();
                    say("revive", 0.6, other.getDisplayName());
                    break;
                }
            }
            if (fallen == null) {
                return;
            }
        }
        if (bot.distanceTo(fallen) > 3.0) {
            walk(fallen.position(), 2.0);
            return;
        }
        mover.stop();
        combat.face(fallen);
        ItemStack stack = bot.getInventory().items.get(petal);
        if (stack.getItem() instanceof ConsumableItem item) {
            item.interactLivingEntity(stack, bot, fallen, InteractionHand.MAIN_HAND);
        }
        reviving = null;
    }

    // ------------------------------------------------------------------ home, falls

    private void goHome() {
        switch (step) {
            case 0 -> {
                if (inTown() || (!Realm.hasDimension(bot.server) && nearSpawn())) {
                    begin(Activity.TOWN);
                    return;
                }
                if (EstateLevels.isEstateLevel(bot.level())) {
                    EstateService.leave(bot);
                    return;
                }
                int scroll = BotBag.slotOf(bot, Consumable.RETURN_SCROLL);
                if (Realm.hasDimension(bot.server) && scroll >= 0 && QuickUse.useSlot(bot, scroll)) {
                    mover.stop();
                    wait = 20 * 12;
                    stepTo(1);
                    return;
                }
                teleportHome();
            }
            case 1 -> {
                mover.stop();
                if (inTown()) {
                    begin(Activity.TOWN);
                } else if (elapsed() > wait) {
                    teleportHome(); // the channel was broken off: the quick way
                }
            }
            default -> stepTo(0);
        }
    }

    private void teleportHome() {
        if (!AstoriaTravel.sendToHub(bot)) {
            ServerLevel overworld = bot.server.overworld();
            BlockPos spawn = overworld.getSharedSpawnPos();
            int y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
            bot.teleportTo(overworld, spawn.getX() + 0.5, y, spawn.getZ() + 0.5, bot.getYRot(), 0.0F);
        }
        bot.fallDistance = 0.0F;
        begin(Activity.TOWN);
    }

    /** Fallen: waits a little for a party member's petal, then returns as a player would. */
    private void down(ServerContext context) {
        if (activity != Activity.DOWN) {
            begin(Activity.DOWN);
            downSince = ticks;
            say("down", 0.3);
        }
        standStill();
        boolean partied = context.game().parties().service().partyOf(bot.getUUID()).isPresent();
        int patience = partied ? 200 : 60;
        if (ticks - downSince >= patience && (ticks - downSince) % 40 == 0) {
            UiActions.dispatch(bot, context, RevivalService.RETURN_ACTION, new JsonObject());
        }
    }

    // ------------------------------------------------------------------ helpers

    private long gold() {
        return ServerContext.current().flatMap(context -> context.playerData().snapshot(bot.getUUID()))
                .map(CharacterSnapshot::gold).orElse(0L);
    }

    private boolean inTown() {
        return AstoriaTravel.isInAstoria(bot) && FieldLayout.inCapital(bot.getX(), bot.getZ());
    }

    private boolean nearSpawn() {
        BlockPos spawn = bot.server.overworld().getSharedSpawnPos();
        return bot.level() == bot.server.overworld() && horizontal(bot.position(), Vec3.atCenterOf(spawn)) < 24.0;
    }

    private boolean near(Vec3 target, double within) {
        return target != null && horizontal(bot.position(), target) <= within && Math.abs(bot.getY() - target.y) < 4.0;
    }

    private void walk(Vec3 target, double within) {
        mover.goTo(target, within, horizontal(bot.position(), target) > 24.0);
    }

    /**
     * Walks to a place in town; after {@value #ERRAND_WALK_TICKS} ticks on the way (lost in the streets) it is set
     * down there, as by a quick trip.
     */
    private void reach(Vec3 target, double within) {
        if (elapsed() > ERRAND_WALK_TICKS) {
            Vec3 at = BotMover.standable(bot.serverLevel(), target);
            bot.teleportTo(bot.serverLevel(), at.x, at.y, at.z, bot.getYRot(), 0.0F);
            bot.fallDistance = 0.0F;
            mover.stop();
            return;
        }
        walk(target, within);
    }

    private void lookAround() {
        float yaw = bot.getYRot() + (random.nextFloat() - 0.5F) * 120.0F;
        bot.setYRot(yaw);
        bot.setYHeadRot(yaw);
        bot.setXRot((random.nextFloat() - 0.5F) * 30.0F);
    }

    /** {@code at} set down on the ground (at the bot's own height while that ground is not loaded yet). */
    private Vec3 ground(ServerLevel level, Vec3 at) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(at.x), Mth.floor(at.z));
        return new Vec3(at.x, y <= level.getMinBuildHeight() ? bot.getY() : y, at.z);
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** A line in the chat now and then (never more than one per bot in {@link #CHAT_GAP} ticks). */
    private void say(String key, double chance, Object... args) {
        if (!TestBots.chatEnabled() || ticks - lastChat < CHAT_GAP || random.nextDouble() >= chance) {
            return;
        }
        lastChat = ticks;
        Component line = Component.translatable("chat.eternalrealm.bot." + key, args);
        bot.server.getPlayerList().broadcastSystemMessage(
                Component.translatable("chat.type.text", bot.getDisplayName(), line), false);
    }
}
