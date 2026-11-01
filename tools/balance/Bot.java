import com.frostbound.core.Difficulty;
import com.frostbound.core.city.ActionResult;
import com.frostbound.core.city.Building;
import com.frostbound.core.city.BuildingCategory;
import com.frostbound.core.city.BuildingType;
import com.frostbound.core.city.BuildingTypes;
import com.frostbound.core.city.City;
import com.frostbound.core.city.WorldAccess;
import com.frostbound.core.expedition.ExpeditionSystem;
import com.frostbound.core.expedition.Site;
import com.frostbound.core.research.Tech;
import com.frostbound.core.research.Techs;
import com.frostbound.core.resources.ResourceType;
import com.frostbound.core.sim.SimClock;
import com.frostbound.core.society.Law;
import com.frostbound.core.society.Laws;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Balance harness: a heuristic "reasonable player" that plays a city for 100 days through the public City API
 * (staffing, housing, food, coal, heat, research, laws, medicine, expeditions) and prints how it went.
 *
 * <p>javac -cp core-classes -d out tools/balance/Bot.java && java -cp core-classes:out Bot NORMAL [seed] [quiet]
 */
public final class Bot {
    private static final String[] RESEARCH = {"insulation", "wooden_houses", "hunting_gear", "coal_efficiency", "gen_efficiency",
            "clinic", "heated_houses", "research_methods", "gen_range", "deep_mining", "scouting_gear", "apartments", "greenhouse",
            "gen_overdrive", "faster_building", "core_extraction", "gen_level4", "heat_boost", "cold_suits", "hospital",
            "food_processing", "machine_factory", "long_range", "advanced_medicine", "steam_engine_factory"};
    private static final String[] LAWS = {"medical_priority", "free_medicine", "extended_shift", "entertainment", "self_governance"};

    private static int built;
    private static final List<String> log = new ArrayList<>();

    public static void main(String[] a) {
        Difficulty d = Difficulty.valueOf(a.length > 0 ? a[0] : "NORMAL");
        long seed = a.length > 1 ? Long.parseLong(a[1]) : 42L;
        boolean quiet = a.length > 2;
        City c = City.found(seed, d, 20, "Bot", false, 0, 64, 0, WorldAccess.NONE);
        int minAlive = 999;
        for (int day = 1; day <= 101 && c.running(); day++) {
            for (int s = 0; s < SimClock.STEPS_PER_DAY && c.running(); s++) {
                c.step();
                if (c.events().pending() != null) {
                    answer(c);
                }
                if (s % SimClock.STEPS_PER_HOUR == 0) {
                    manage(c);
                }
            }
            var p = c.population();
            minAlive = Math.min(minAlive, p.alive);
            if (!quiet && (day % 10 == 0 || day <= 3 || day >= 96)) {
                System.out.printf(Locale.ROOT,
                        "day %3d | pop %3d sick %2d homeless %2d deaths %3d | hope %4.1f disc %4.1f body %4.1f | coal %4d wood %4d iron %4d food %4d med %3d core %d | gen L%d%s f=%.2f T=%.1f cold=%.1f | b=%d r=%d%n",
                        c.clock().day(), p.alive, p.sick, p.homeless, p.deathsTotal, p.avgHope, p.avgDiscontent, p.avgBodyTemp,
                        c.ledger().amount(ResourceType.COAL), c.ledger().amount(ResourceType.WOOD), c.ledger().amount(ResourceType.IRON),
                        c.ledger().amount(ResourceType.FOOD), c.ledger().amount(ResourceType.MEDICINE), c.ledger().amount(ResourceType.STEAM_CORE),
                        c.generator().level(), c.generator().overload() ? "+" : "", c.heat().supplyRatio(), c.outsideTemperature(),
                        coldestHome(c), c.buildings().size(), c.research().completed().size());
            }
        }
        java.util.Map<String, Integer> causes = new java.util.TreeMap<>();
        for (var e : c.history().recent(100000)) {
            if (e.msg().key().equals("frostbound.msg.death") && e.msg().args().size() > 1) {
                causes.merge(e.msg().args().get(1).replace("#frostbound.cause.", ""), 1, Integer::sum);
            }
        }
        var p = c.population();
        System.out.println("  death causes: " + causes + " built=" + built);
        System.out.printf(Locale.ROOT, "%s seed=%d phase=%s ending=%s reason=%s alive=%d minAlive=%d deaths=%d lived=%d research=%d%n",
                d, seed, c.phase(), c.ending(), c.endReason(), p.alive, minAlive, p.deathsTotal, p.everLived, c.research().completed().size());
        if (!quiet) {
            for (var e : c.history().recent(12)) {
                System.out.println("  " + e.msg().key() + e.msg().args());
            }
        }
    }

    private static void answer(City c) {
        var ev = c.events().pending();
        var def = com.frostbound.core.events.Events.get(ev.id());
        int choice = 0;
        if (def != null) {
            for (int i = 0; i < def.choices.size(); i++) {
                if (def.choices.get(i).available().test(c)) {
                    choice = i;
                    break;
                }
            }
        }
        c.answerEvent(choice);
    }

    private static int count(City c, String type) {
        int n = 0;
        for (Building b : c.buildings()) {
            if (b.typeId.equals(type)) {
                n++;
            }
        }
        return n;
    }

    private static boolean building(City c) {
        for (Building b : c.buildings()) {
            if (b.state == Building.State.CONSTRUCTION) {
                return true;
            }
        }
        return false;
    }

    private static int constructing(City c) {
        int n = 0;
        for (Building b : c.buildings()) {
            if (b.state == Building.State.CONSTRUCTION) {
                n++;
            }
        }
        return n;
    }

    private static boolean place(City c, String type) {
        BuildingType t = BuildingTypes.get(type);
        if (t == null || !c.ledger().has(t.cost)) {
            return false;
        }
        int gx = c.generatorX();
        int gz = c.generatorZ();
        for (double r = 12; r <= 150; r += 3) {
            int n = (int) (r * 0.8);
            for (int i = 0; i < n; i++) {
                double ang = (i * 2 * Math.PI / n) + r * 0.37;
                int x = gx + (int) Math.round(Math.cos(ang) * r) - t.width / 2;
                int z = gz + (int) Math.round(Math.sin(ang) * r) - t.depth / 2;
                if (c.checkPlacement(type, x, 64, z, 0) == null) {
                    ActionResult res = c.placeBuilding(type, x, 64, z, 0);
                    if (res.ok()) {
                        built++;
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void manage(City c) {
        int day = c.clock().day();
        var pop = c.population();
        for (Building b : c.buildings()) {
            if (b.active() && b.type().employs()) {
                int max = b.type().maxWorkers(b.level);
                ResourceType out = b.type().output;
                int want = max;
                if (out != null && out != ResourceType.STEAM_CORE && out != ResourceType.MEDICINE
                        && c.ledger().amount(out) > c.ledger().capacity(out) * 0.8) {
                    want = max / 3;
                }
                c.setDesiredWorkers(b.id, want);
            }
        }
        double foodNeed = c.ledger().consumedPerDay(ResourceType.FOOD);
        double foodProd = c.ledger().producedPerDay(ResourceType.FOOD);
        double coalNeed = c.generator().burnPerDay(c.modifiers(), c.difficulty()) + 20 * count(c, "steelworks");
        double coalProd = c.ledger().producedPerDay(ResourceType.COAL);
        int busy = constructing(c);
        int slots = pop.unemployed > 8 ? 3 : 2;
        if (busy < slots) {
            build(c, day, pop, foodNeed, foodProd, coalNeed, coalProd);
        }
        if (c.research().current() == null) {
            for (String id : RESEARCH) {
                Tech t = Techs.get(id);
                if (t != null && c.research().check(t) == null && c.ledger().has(t.cost)) {
                    if (c.startResearch(id).ok()) {
                        break;
                    }
                }
            }
        }
        for (String id : LAWS) {
            Law law = Laws.get(id);
            if (law != null && c.society().checkEnact(law, c.clock().step()) == null) {
                if (id.equals("free_medicine") && c.ledger().amount(ResourceType.MEDICINE) < 15) {
                    continue;
                }
                if (c.enactLaw(id).ok()) {
                    break;
                }
            }
        }
        heat(c);
        if (day % 4 == 0 && c.clock().hourOfDay() == 9 && c.expeditions().active().isEmpty()) {
            Site best = null;
            for (Site s : c.expeditions().sites()) {
                if (!s.depleted && (!s.far || c.modifiers().longRange) && (best == null || s.distance < best.distance)) {
                    best = s;
                }
            }
            if (best != null && c.ledger().has(ExpeditionSystem.supplies(best, 4))) {
                c.launchExpedition(best.id, 4);
            }
        }
    }

    /** Builds the most urgent missing thing; waits (saves resources) when it cannot afford it. */
    private static void build(City c, int day, com.frostbound.core.population.Population pop, double foodNeed, double foodProd,
            double coalNeed, double coalProd) {
        boolean unlockedGreenhouse = c.modifiers().unlocked.contains("greenhouse");
        int food = count(c, "hunters_lodge") + count(c, "greenhouse") + count(c, "fishery");
        int medical = count(c, "medical_tent") + count(c, "clinic") + count(c, "hospital");
        if (food == 0) {
            place(c, "hunters_lodge");
            return;
        }
        if (count(c, "sawmill") == 0) {
            place(c, "sawmill");
            return;
        }
        if (pop.homeless > 0) {
            if (c.modifiers().unlocked.contains("apartment") && pop.homeless > 12) {
                place(c, "apartment");
            } else if (c.modifiers().unlocked.contains("wooden_house")) {
                place(c, "wooden_house");
            } else {
                place(c, pop.homeless > 6 ? "shelter" : "tent");
            }
            return;
        }
        if (medical == 0 && day >= 2) {
            place(c, "medical_tent");
            return;
        }
        if (day > 2 && food < pop.alive / 22 + 1 && foodProd < Math.max(foodNeed, pop.alive * 1.2) * 1.1
                && c.ledger().daysLeft(ResourceType.FOOD) < 6) {
            place(c, unlockedGreenhouse ? "greenhouse" : "hunters_lodge");
            return;
        }
        if (count(c, "research_lab") == 0 && day >= 2) {
            place(c, "research_lab");
            return;
        }
        int coalSites = count(c, "coal_pit") + count(c, "mine");
        double nextBurn = coalNeed * 1.5;
        if (coalProd < nextBurn && c.ledger().daysLeft(ResourceType.COAL) < 10 && coalSites < 3 + day / 12) {
            place(c, c.modifiers().unlocked.contains("mine") ? "mine" : "coal_pit");
            return;
        }
        if (pop.sick > bedsOf(c)) {
            place(c, c.modifiers().unlocked.contains("clinic") ? "clinic" : "medical_tent");
            return;
        }
        if (count(c, "steelworks") < (day > 30 ? 2 : 1) && day >= 4 && c.ledger().amount(ResourceType.IRON) < 80) {
            place(c, "steelworks");
            return;
        }
        if (c.ledger().amount(ResourceType.WOOD) < 60 && count(c, "sawmill") < 2) {
            place(c, "sawmill");
            return;
        }
        if (count(c, "workshop") == 0 && day > 6) {
            place(c, "workshop");
            return;
        }
        if (pop.avgDiscontent > 35 && count(c, "meeting_hall") == 0) {
            place(c, "meeting_hall");
            return;
        }
        if (c.modifiers().unlocked.contains("core_extractor") && count(c, "core_extractor") == 0) {
            place(c, "core_extractor");
            return;
        }
        if (c.modifiers().unlocked.contains("heated_house") && c.ledger().amount(ResourceType.STEAM_CORE) >= 2
                && count(c, "heated_house") < 4) {
            place(c, "heated_house");
            return;
        }
        if (count(c, "warehouse") < 2 && day > 30) {
            place(c, "warehouse");
        }
    }

    private static double coldestHome(City c) {
        double coldest = 99;
        for (Building b : c.buildings()) {
            if (b.active() && b.type().category == BuildingCategory.HOUSING) {
                coldest = Math.min(coldest, b.temperature);
            }
        }
        return coldest;
    }

    private static int bedsOf(City c) {
        return beds(c);
    }

    private static int beds(City c) {
        int n = 0;
        for (Building b : c.buildings()) {
            if (b.active()) {
                n += b.type().beds(b.level);
            }
        }
        return n;
    }

    private static void heat(City c) {
        var g = c.generator();
        if (g.broken()) {
            if (!g.repairing()) {
                c.repairGenerator();
            }
            return;
        }
        double coldest = 99;
        for (Building b : c.buildings()) {
            if (b.active() && b.type().category == BuildingCategory.HOUSING) {
                coldest = Math.min(coldest, b.temperature);
            }
        }
        double coalDays = c.ledger().daysLeft(ResourceType.COAL);
        double coal = c.ledger().amount(ResourceType.COAL);
        int lvl = g.level();
        int max = c.modifiers().maxGeneratorLevel;
        if (coldest < 3 && lvl < max && (coalDays > 3 || coal > 150)) {
            c.setGeneratorLevel(lvl + 1);
        } else if ((coldest > 18 && lvl > 1) || (coal < 30 && lvl > 1)) {
            c.setGeneratorLevel(lvl - 1);
        }
        boolean wave = c.weather().current().tempOffset <= -16;
        if (wave && !g.overload() && g.stress() < 50 && coldest < 0 && coalDays > 3) {
            c.setOverload(true);
        } else if (g.overload() && (g.stress() > 60 || !wave || coalDays < 1.5)) {
            c.setOverload(false);
        }
    }
}
