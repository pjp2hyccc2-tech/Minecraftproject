package com.frostbound.core.city;

import com.frostbound.core.Difficulty;
import com.frostbound.core.events.EventDef;
import com.frostbound.core.events.EventSystem;
import com.frostbound.core.events.Events;
import com.frostbound.core.expedition.Expedition;
import com.frostbound.core.expedition.ExpeditionSystem;
import com.frostbound.core.expedition.Site;
import com.frostbound.core.heat.Generator;
import com.frostbound.core.heat.HeatNetwork;
import com.frostbound.core.population.Citizen;
import com.frostbound.core.population.CitizenRules;
import com.frostbound.core.population.Population;
import com.frostbound.core.population.Profession;
import com.frostbound.core.research.ResearchSystem;
import com.frostbound.core.research.Tech;
import com.frostbound.core.research.Techs;
import com.frostbound.core.resources.Cost;
import com.frostbound.core.resources.ResourceLedger;
import com.frostbound.core.resources.ResourceType;
import com.frostbound.core.save.SaveMigrations;
import com.frostbound.core.sim.SimClock;
import com.frostbound.core.society.Law;
import com.frostbound.core.society.Laws;
import com.frostbound.core.society.Society;
import com.frostbound.core.util.Msg;
import com.frostbound.core.util.Rng;
import com.frostbound.core.util.Tree;
import com.frostbound.core.weather.WeatherState;
import com.frostbound.core.weather.WeatherSystem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The whole city simulation. The Forge layer calls {@link #step()} as many times per server tick as the
 * clock asks for, sends {@link #snapshot()} to clients, drains {@link #drainNotices()} and persists
 * {@link #save()}. Player commands are the public methods returning {@link ActionResult}.
 */
public final class City {
    public static final int CITY_RADIUS = 160;

    public enum Phase {
        RUNNING,
        ENDED,
        ENDLESS,
        FAILED
    }

    private final long seed;
    private final Rng rng;
    private final Difficulty difficulty;
    private String name = "";
    private int dayLengthMinutes = 20;
    private final SimClock clock = new SimClock();
    private final ResourceLedger ledger = new ResourceLedger();
    private final WeatherSystem weather;
    private final Generator generator = new Generator();
    private final HeatNetwork heat = new HeatNetwork();
    private final List<Building> buildings = new ArrayList<>();
    private final Map<Integer, Building> buildingIndex = new HashMap<>();
    private int nextBuildingId = 1;
    private final Population population = new Population();
    private final Society society = new Society();
    private final EventSystem events = new EventSystem();
    private final ResearchSystem research = new ResearchSystem();
    private final ExpeditionSystem expeditions = new ExpeditionSystem();
    private final HistoryLog history = new HistoryLog();
    private final Tutorial tutorial = new Tutorial();
    private Modifiers mods = new Modifiers();
    private Phase phase = Phase.RUNNING;
    private Ending ending;
    private String endReason;
    private int gx;
    private int gy;
    private int gz;
    private double outside = -20;
    private double researchThisHour;
    private boolean medicineShort;
    private boolean anyDied;
    private final List<Notice> outbox = new ArrayList<>();
    private final Map<Integer, List<Citizen>> staff = new HashMap<>();
    private final Map<String, Long> lastWarning = new HashMap<>();
    private WorldAccess world = WorldAccess.NONE;

    private City(long seed, Difficulty difficulty) {
        this.seed = seed;
        this.rng = new Rng(seed ^ 0x5DEECE66DL);
        this.difficulty = difficulty;
        this.weather = new WeatherSystem(difficulty);
    }

    // ------------------------------------------------------------------ founding

    /** Founds a new city around a generator whose centre column is (gx, gz) with its floor at gy. */
    public static City found(long seed, Difficulty difficulty, int dayLengthMinutes, String name, boolean tutorialOn,
            int gx, int gy, int gz, WorldAccess world) {
        City c = new City(seed, difficulty);
        c.name = name == null ? "" : name;
        c.dayLengthMinutes = Math.max(5, Math.min(120, dayLengthMinutes));
        c.tutorial.setEnabled(tutorialOn);
        c.world = world == null ? WorldAccess.NONE : world;
        c.gx = gx;
        c.gy = gy;
        c.gz = gz;
        c.weather.init(c.rng);
        c.expeditions.generate(c.rng);
        c.outside = c.weather.outsideTemperature(c.clock);
        Building g = new Building(c.nextBuildingId++, BuildingTypes.GENERATOR.id, gx - 6, gy, gz - 6, 0);
        g.state = Building.State.ACTIVE;
        g.temperature = c.outside;
        c.addBuilding(g);
        c.placeInitial(BuildingTypes.SHELTER, gx + 12, gz - 9);
        c.placeInitial(BuildingTypes.SHELTER, gx + 12, gz + 4);
        c.placeInitial(BuildingTypes.WAREHOUSE, gx - 19, gz - 3);
        Building pit = c.placeInitial(BuildingTypes.COAL_PIT, gx - 3, gz + 13);
        pit.desiredWorkers = 6;
        c.recomputeCapacity();
        double s = difficulty.startResources;
        c.ledger.set(ResourceType.COAL, Math.round(120 * s) * ResourceLedger.MILLI);
        c.ledger.set(ResourceType.WOOD, Math.round(80 * s) * ResourceLedger.MILLI);
        c.ledger.set(ResourceType.IRON, Math.round(20 * s) * ResourceLedger.MILLI);
        c.ledger.set(ResourceType.FOOD, Math.round(120 * s) * ResourceLedger.MILLI);
        c.ledger.set(ResourceType.MEDICINE, Math.round(10 * s) * ResourceLedger.MILLI);
        c.population.createFounders(c.rng);
        c.recomputeModifiers();
        c.assignHousing();
        c.assignWork();
        c.population.aggregate();
        for (Building b : c.buildings) {
            b.temperature = c.outside + b.type().insulation;
        }
        c.history.add(0, "info", Msg.of("frostbound.msg.founded", c.name, Msg.tr(difficulty.key())));
        return c;
    }

    private Building placeInitial(BuildingType type, int x, int z) {
        int y = world.groundHeight(x + type.width / 2, z + type.depth / 2);
        Building b = new Building(nextBuildingId++, type.id, x, y, z, 0);
        b.state = Building.State.ACTIVE;
        b.temperature = outside;
        addBuilding(b);
        world.constructionFinished(b);
        return b;
    }

    private void addBuilding(Building b) {
        buildings.add(b);
        buildingIndex.put(b.id, b);
    }

    // ------------------------------------------------------------------ accessors

    public long seed() {
        return seed;
    }

    public Rng rng() {
        return rng;
    }

    public Difficulty difficulty() {
        return difficulty;
    }

    public String name() {
        return name;
    }

    public int dayLengthMinutes() {
        return dayLengthMinutes;
    }

    public void setDayLengthMinutes(int minutes) {
        dayLengthMinutes = Math.max(5, Math.min(120, minutes));
    }

    public SimClock clock() {
        return clock;
    }

    public ResourceLedger ledger() {
        return ledger;
    }

    public WeatherSystem weather() {
        return weather;
    }

    public Generator generator() {
        return generator;
    }

    public HeatNetwork heat() {
        return heat;
    }

    public List<Building> buildings() {
        return buildings;
    }

    public Building building(int id) {
        return buildingIndex.get(id);
    }

    public Population population() {
        return population;
    }

    public Society society() {
        return society;
    }

    public EventSystem events() {
        return events;
    }

    public ResearchSystem research() {
        return research;
    }

    public ExpeditionSystem expeditions() {
        return expeditions;
    }

    public HistoryLog history() {
        return history;
    }

    public Tutorial tutorial() {
        return tutorial;
    }

    public Modifiers modifiers() {
        return mods;
    }

    public Phase phase() {
        return phase;
    }

    public Ending ending() {
        return ending;
    }

    public String endReason() {
        return endReason;
    }

    public int generatorX() {
        return gx;
    }

    public int generatorY() {
        return gy;
    }

    public int generatorZ() {
        return gz;
    }

    public double outsideTemperature() {
        return outside;
    }

    public boolean medicineShortage() {
        return medicineShort;
    }

    public void setWorld(WorldAccess world) {
        this.world = world == null ? WorldAccess.NONE : world;
    }

    public boolean isShiftHour(int hour) {
        if (society.emergencyActive(clock.step())) {
            return true;
        }
        return hour >= mods.shiftStartHour && hour < mods.shiftEndHour;
    }

    public boolean running() {
        return phase == Phase.RUNNING || phase == Phase.ENDLESS;
    }

    public void notify(Notice.Kind kind, Msg msg, String sound) {
        outbox.add(new Notice(kind, msg, sound));
        if (outbox.size() > 50) {
            outbox.remove(0);
        }
    }

    public List<Notice> drainNotices() {
        List<Notice> out = new ArrayList<>(outbox);
        outbox.clear();
        return out;
    }

    // ------------------------------------------------------------------ simulation

    /** Advances the simulation by one step (1/1200 of a day). */
    public void step() {
        if (!running()) {
            return;
        }
        clock.advanceOne();
        long s = clock.step();
        if (weather.step(s, rng)) {
            onWeatherChanged();
        }
        outside = weather.outsideTemperature(clock);
        Generator.Change change = generator.step(ledger, mods, difficulty);
        onGeneratorChange(change);
        heat.step(buildings, gx + 0.5, gz + 0.5, generator, mods, outside);
        for (Building b : buildings) {
            if (b.upgradeStepsLeft > 0 && --b.upgradeStepsLeft == 0) {
                finishUpgrade(b);
            }
        }
        if (clock.isHourBoundary()) {
            hourly();
        }
        int bucket = (int) (s % SimClock.STEPS_PER_HOUR);
        for (Citizen c : population.all()) {
            if (c.present() && c.id % SimClock.STEPS_PER_HOUR == bucket) {
                CitizenRules.updateHour(this, c);
            }
        }
        if (clock.isDayBoundary()) {
            daily();
        }
        if (anyDied) {
            anyDied = false;
            population.purgeDead();
        }
    }

    private void onWeatherChanged() {
        WeatherState w = weather.current();
        history.add(clock.step(), "weather", Msg.of("frostbound.msg.weather_changed", Msg.tr(w.key())));
        if (w.stormy() || w == WeatherState.EXTREME_COLD) {
            notify(Notice.Kind.WARNING, Msg.of("frostbound.msg.weather_changed", Msg.tr(w.key())), "alert");
        } else {
            notify(Notice.Kind.INFO, Msg.of("frostbound.msg.weather_changed", Msg.tr(w.key())), null);
        }
    }

    private void onGeneratorChange(Generator.Change change) {
        switch (change) {
            case FUEL_OUT -> {
                history.add(clock.step(), "generator", Msg.of("frostbound.msg.generator_fuel_out"));
                notify(Notice.Kind.MAJOR, Msg.of("frostbound.msg.generator_fuel_out"), "alert");
            }
            case FUEL_RESTORED -> notify(Notice.Kind.GOOD, Msg.of("frostbound.msg.generator_fuel_restored"), "steam_hiss");
            case BROKE -> {
                history.add(clock.step(), "generator", Msg.of("frostbound.msg.generator_broke"));
                notify(Notice.Kind.MAJOR, Msg.of("frostbound.msg.generator_broke"), "alert");
            }
            case REPAIRED -> {
                history.add(clock.step(), "generator", Msg.of("frostbound.msg.generator_repaired"));
                notify(Notice.Kind.GOOD, Msg.of("frostbound.msg.generator_repaired"), "steam_hiss");
            }
            default -> {
            }
        }
    }

    private void hourly() {
        int hour = clock.hourOfDay();
        int previous = (hour + 23) % 24;
        recomputeModifiers();
        assignHousing();
        assignWork();
        boolean shift = isShiftHour(previous);
        production(shift);
        construction(shift);
        if (hour == 7 || hour == 19) {
            meal();
        }
        treatment();
        Tech done = research.addPoints(researchThisHour);
        if (done != null) {
            onResearchDone(done);
        }
        expeditions.hourly(this);
        societyHourly();
        events.hourly(this);
        tutorial.check();
        ledger.rotateHour();
        population.aggregate();
        warnings();
        checkFailure();
    }

    private void daily() {
        int day = clock.day();
        population.deathsYesterday = population.deathsToday;
        population.deathsToday = 0;
        society.dailyFade();
        for (String id : society.enacted()) {
            Law law = Laws.get(id);
            if (law != null && !law.dailyCost.isEmpty() && !ledger.spend(law.dailyCost)) {
                moodAll(-1, 3);
                notify(Notice.Kind.WARNING, Msg.of("frostbound.msg.law_upkeep_failed", Msg.tr(law.key())), "alert");
            }
        }
        WeatherSystem.Wave wave = weather.nextWaveAfter(day);
        if (wave != null && wave.startDay() - day == 3) {
            if (wave.state() == WeatherState.GREAT_COLD_WAVE) {
                events.trigger(this, "great_cold_wave", List.of("3"));
            } else {
                int t = (int) Math.round(weather.expectedTemperature(wave.startDay(), wave.state()));
                events.trigger(this, "cold_wave_warning", List.of("3", String.valueOf(t), String.valueOf(wave.days())));
            }
        }
        history.add(clock.step(), "day", Msg.of("frostbound.msg.day_started", day, population.present));
        if (day == WeatherSystem.FINAL_DAY + 1 && phase == Phase.RUNNING) {
            evaluateEnding();
        }
    }

    private void production(boolean shift) {
        researchThisHour = 0;
        boolean strike = society.strikeActive(clock.step());
        for (Building b : buildings) {
            BuildingType t = b.type();
            if (!b.working() || !t.employs()) {
                b.efficiency = 0;
                b.lastOutput = 0;
                continue;
            }
            double workforce = 0;
            for (Citizen c : staff.getOrDefault(b.id, List.of())) {
                workforce += CitizenRules.workFactor(this, c);
            }
            boolean allDay = t.category == BuildingCategory.MEDICAL || t.category == BuildingCategory.SOCIAL;
            if (!allDay && (!shift || strike)) {
                workforce = 0;
            }
            double tempEff = CitizenRules.tempEfficiency(b.temperature, t == BuildingTypes.GREENHOUSE);
            double lvl = BuildingType.levelMult(b.level);
            double cat = 1.0;
            if (t.category == BuildingCategory.RESOURCE) {
                cat *= mods.resourceMult;
            }
            if (t == BuildingTypes.COAL_PIT) {
                cat *= mods.coalPitMult;
            }
            if (t.category == BuildingCategory.FOOD) {
                cat *= mods.foodMult;
            }
            if (t.outdoor) {
                cat *= weather.current().outdoorWork * mods.huntingMult;
            }
            double eff = tempEff * lvl * cat * mods.lawProductionMult;
            int max = t.maxWorkers(b.level);
            b.efficiency = max > 0 ? Math.min(1.0, workforce / max) * tempEff : 0;
            double inputRatio = 1.0;
            if (t.input != null && workforce > 0) {
                double need = workforce * t.inputRate * lvl;
                long taken = ledger.take(t.input, Math.round(need * ResourceLedger.MILLI));
                inputRatio = need > 0 ? taken / (need * ResourceLedger.MILLI) : 1.0;
            }
            double out = workforce * t.outputRate * eff * inputRatio;
            b.lastOutput = out;
            if (t.output != null && out > 0) {
                ledger.add(t.output, Math.round(out * ResourceLedger.MILLI));
            }
            if (t.output2 != null && workforce > 0) {
                ledger.add(t.output2, Math.round(workforce * t.output2Rate * eff * inputRatio * ResourceLedger.MILLI));
            }
            if (t.research > 0) {
                researchThisHour += workforce * t.research * tempEff * lvl * mods.researchMult;
            }
        }
        scavenge(shift && !strike);
    }

    /** Wood gathered per idle adult per shift hour (scrap from the snow), and coal. */
    public static final double SCAVENGE_WOOD = 0.25;
    public static final double SCAVENGE_COAL = 0.1;

    /**
     * Idle adults pick scrap wood and coal out of the snow during the shift, so a city that spent all its wood
     * can always build a sawmill again (slow, weather-dependent).
     */
    private void scavenge(boolean shift) {
        if (!shift) {
            return;
        }
        double idle = 0;
        for (Citizen c : population.all()) {
            if (c.present() && !c.child() && c.workId < 0) {
                idle += CitizenRules.workFactor(this, c);
            }
        }
        if (idle <= 0) {
            return;
        }
        double f = idle * weather.current().outdoorWork * mods.resourceMult;
        ledger.add(ResourceType.WOOD, Math.round(f * SCAVENGE_WOOD * ResourceLedger.MILLI));
        ledger.add(ResourceType.COAL, Math.round(f * SCAVENGE_COAL * ResourceLedger.MILLI));
    }

    private void construction(boolean shift) {
        boolean strike = society.strikeActive(clock.step());
        double weatherMult = weather.current().outdoorWork;
        double coldMult = Math.max(0.4, Math.min(1.0, 0.4 + (outside + 40) / 60.0));
        for (Building b : new ArrayList<>(buildings)) {
            if (b.state != Building.State.CONSTRUCTION) {
                continue;
            }
            double wf = 0;
            for (Citizen c : staff.getOrDefault(b.id, List.of())) {
                wf += CitizenRules.workFactor(this, c);
            }
            if (!shift || strike) {
                wf = 0;
            }
            b.buildProgress += wf * mods.buildSpeedMult * weatherMult * coldMult;
            BuildingType t = b.type();
            if (b.buildProgress >= t.buildHours * (double) t.builders) {
                finishConstruction(b);
            }
        }
    }

    private void finishConstruction(Building b) {
        BuildingType t = b.type();
        b.state = Building.State.ACTIVE;
        b.buildProgress = t.buildHours * (double) t.builders;
        b.temperature = outside;
        b.desiredWorkers = t.employs() ? t.maxWorkers(b.level) : 0;
        world.constructionFinished(b);
        recomputeCapacity();
        recomputeModifiers();
        history.add(clock.step(), "build", Msg.of("frostbound.msg.construction_done", Msg.tr(t.key())));
        notify(Notice.Kind.GOOD, Msg.of("frostbound.msg.construction_done", Msg.tr(t.key())), "build");
    }

    private void finishUpgrade(Building b) {
        BuildingType t = b.type();
        b.level = Math.min(t.maxLevel, b.level + 1);
        if (t.employs() && b.desiredWorkers >= t.maxWorkers(b.level - 1)) {
            b.desiredWorkers = t.maxWorkers(b.level);
        }
        world.buildingUpgraded(b);
        recomputeCapacity();
        history.add(clock.step(), "build", Msg.of("frostbound.msg.upgrade_done", Msg.tr(t.key()), b.level));
        notify(Notice.Kind.GOOD, Msg.of("frostbound.msg.upgrade_done", Msg.tr(t.key()), b.level), "build");
    }

    private void meal() {
        int eaters = 0;
        for (Citizen c : population.all()) {
            if (c.present()) {
                eaters++;
            }
        }
        double need = eaters * 0.5 * mods.foodUseMult;
        long taken = ledger.take(ResourceType.FOOD, Math.round(need * ResourceLedger.MILLI));
        double fed = need > 0 ? Math.min(1.0, taken / (need * ResourceLedger.MILLI)) : 1.0;
        for (Citizen c : population.all()) {
            if (c.present()) {
                c.satiety = Math.min(100, c.satiety + 45 * fed * mods.satietyGainMult);
            }
        }
        if (fed < 0.999) {
            history.add(clock.step(), "warning", Msg.of("frostbound.msg.food_shortage", Math.round(fed * 100)));
            notify(Notice.Kind.WARNING, Msg.of("frostbound.msg.food_shortage", Math.round(fed * 100)), "alert");
        }
    }

    private void treatment() {
        double beds = 0;
        for (Building b : buildings) {
            BuildingType t = b.type();
            if (!b.working() || t.beds <= 0) {
                continue;
            }
            int max = t.maxWorkers(b.level);
            double ratio = max > 0 ? Math.min(1.0, b.workers / (double) max) : 1.0;
            beds += t.beds(b.level) * ratio;
        }
        List<Citizen> patients = new ArrayList<>();
        for (Citizen c : population.all()) {
            c.treated = false;
            if (c.present() && c.condition.sick()) {
                patients.add(c);
            }
        }
        patients.sort(Comparator.comparingDouble(c -> c.health));
        int n = Math.min(patients.size(), (int) Math.floor(beds + 1e-6));
        for (int i = 0; i < n; i++) {
            patients.get(i).treated = true;
        }
        double need = n * 0.03 * mods.medicineUseMult;
        long taken = ledger.take(ResourceType.MEDICINE, Math.round(need * ResourceLedger.MILLI));
        medicineShort = need > 0 && taken < need * ResourceLedger.MILLI * 0.99;
    }

    private void onResearchDone(Tech t) {
        recomputeModifiers();
        tutorial.check();
        history.add(clock.step(), "research", Msg.of("frostbound.msg.research_done", Msg.tr(t.key())));
        notify(Notice.Kind.MAJOR, Msg.of("frostbound.msg.research_done", Msg.tr(t.key())), "event_major");
    }

    private void societyHourly() {
        double hopeSoc = 0;
        double discSoc = 0;
        int pop = Math.max(1, population.present);
        for (Building b : buildings) {
            BuildingType t = b.type();
            if (!b.working() || t.category != BuildingCategory.SOCIAL) {
                continue;
            }
            int max = t.maxWorkers(b.level);
            double staffRatio = max > 0 ? Math.min(1.0, b.workers / (double) max) : 1.0;
            double coverage = Math.min(1.0, t.socialCapacity * BuildingType.levelMult(b.level) / pop);
            double scale = coverage * staffRatio;
            if (t == BuildingTypes.TAVERN) {
                scale *= mods.entertainment ? 1.5 : 1.0;
                scale *= mods.curfew ? 0.5 : 1.0;
            }
            if (t == BuildingTypes.GUARD_POST && mods.strictControl) {
                scale *= 2.0;
            }
            hopeSoc += t.hopeEffect * scale;
            discSoc += t.discontentEffect * scale;
        }
        society.socialHope = Math.min(15, hopeSoc);
        society.socialDiscontent = Math.max(-15, discSoc);
        double disc = population.avgDiscontent;
        double hope = population.avgHope;
        society.highDiscontentHours = disc >= 85 ? society.highDiscontentHours + 1 : 0;
        if (society.highDiscontentHours == 24 && !events.onCooldown("strike", clock.step())) {
            events.trigger(this, "strike", List.of());
        }
        society.riotHours = disc >= 95 ? society.riotHours + 1 : 0;
        if (society.riotHours == 24) {
            events.trigger(this, "riot", List.of());
        }
        society.lowHopeHours = hope <= 15 ? society.lowHopeHours + 1 : 0;
        if (society.lowHopeHours == 24 && !events.onCooldown("hope_crisis", clock.step())) {
            events.trigger(this, "hope_crisis", List.of());
        }
        if (population.present > 0 && (disc >= 98 || hope <= 2)) {
            society.criticalHours++;
        } else {
            society.criticalHours = Math.max(0, society.criticalHours - 2);
        }
    }

    private void warnings() {
        if (ledger.amount(ResourceType.COAL) < 20 && generator.level() > 0) {
            warnOnce("coal", Notice.Kind.WARNING, Msg.of("frostbound.msg.warn_coal"));
        }
        if (ledger.amount(ResourceType.FOOD) < population.present) {
            warnOnce("food", Notice.Kind.WARNING, Msg.of("frostbound.msg.warn_food"));
        }
        if (population.tempBands[3] + population.tempBands[4] > Math.max(2, population.present / 5)) {
            warnOnce("cold", Notice.Kind.WARNING, Msg.of("frostbound.msg.warn_cold"));
        }
        if (population.sick > Math.max(3, population.present / 6)) {
            warnOnce("sick", Notice.Kind.WARNING, Msg.of("frostbound.msg.warn_sick", population.sick));
        }
    }

    private void warnOnce(String key, Notice.Kind kind, Msg msg) {
        long now = clock.step();
        Long last = lastWarning.get(key);
        if (last == null || now - last >= 6L * SimClock.STEPS_PER_HOUR) {
            lastWarning.put(key, now);
            notify(kind, msg, "alert");
        }
    }

    private void checkFailure() {
        if (!running()) {
            return;
        }
        if (population.alive == 0) {
            fail("frostbound.ending.reason.all_dead");
        } else if (society.criticalHours >= 48) {
            fail(population.avgDiscontent >= 98 ? "frostbound.ending.reason.revolt" : "frostbound.ending.reason.despair");
        }
    }

    private void fail(String reason) {
        phase = Phase.FAILED;
        ending = Ending.ETERNAL_WINTER;
        endReason = reason;
        history.add(clock.step(), "ending", Msg.of(reason));
        notify(Notice.Kind.MAJOR, Msg.of(Ending.ETERNAL_WINTER.titleKey()), "event_major");
    }

    private void evaluateEnding() {
        double deathShare = population.deathsTotal / (double) Math.max(1, population.everLived);
        Ending e;
        if (deathShare >= 0.4) {
            e = Ending.LAST_FLAME;
        } else if (society.isEnacted("strict_control")) {
            e = Ending.CITY_OF_STEEL;
        } else if (research.completed().size() >= 18) {
            e = Ending.REBIRTH;
        } else if (population.avgHope >= 55 && deathShare < 0.15) {
            e = Ending.HOPE_OF_HUMANITY;
        } else {
            e = Ending.LAST_FLAME;
        }
        ending = e;
        phase = Phase.ENDED;
        endReason = null;
        history.add(clock.step(), "ending", Msg.of("frostbound.msg.ending", Msg.tr(e.titleKey())));
        notify(Notice.Kind.MAJOR, Msg.of("frostbound.msg.ending", Msg.tr(e.titleKey())), "event_major");
    }

    // ------------------------------------------------------------------ assignments

    void recomputeModifiers() {
        Modifiers m = new Modifiers();
        for (BuildingType t : BuildingTypes.all()) {
            if (BuildingTypes.initiallyUnlocked(t)) {
                m.unlocked.add(t.id);
            }
            if (t.unlockLaw != null && (society.isEnacted(t.unlockLaw)
                    || (t.unlockLaw.equals("curfew") && society.isEnacted("strict_control")))) {
                m.unlocked.add(t.id);
            }
        }
        research.apply(m);
        society.applyLaws(m);
        double factoryBonus = 0;
        for (Building b : buildings) {
            if (!b.working()) {
                continue;
            }
            BuildingType t = b.type();
            int max = t.maxWorkers(b.level);
            double ratio = max > 0 ? Math.min(1.0, b.workers / (double) max) : 0;
            if (t == BuildingTypes.MACHINE_FACTORY) {
                factoryBonus += 0.12 * ratio * BuildingType.levelMult(b.level);
            } else if (t == BuildingTypes.STEAM_ENGINE_FACTORY) {
                m.coalBurnMult *= 1.0 - 0.15 * ratio;
                m.heatCapacityBonus += 0.1 * ratio;
            }
        }
        m.resourceMult += Math.min(0.36, factoryBonus);
        mods = m;
        if (generator.level() > m.maxGeneratorLevel) {
            generator.setLevel(m.maxGeneratorLevel);
        }
    }

    void recomputeCapacity() {
        long extra = 0;
        long extraCores = 0;
        for (Building b : buildings) {
            if (b.active() && b.type().storage > 0) {
                extra += (long) b.type().storage * b.level;
                extraCores += 10L * b.level;
            }
        }
        for (ResourceType t : ResourceType.values()) {
            long base = ResourceLedger.baseCapacity(t);
            ledger.setCapacityUnits(t, t == ResourceType.STEAM_CORE ? base + extraCores : base + extra);
        }
    }

    private static int priority(Building b) {
        if (b.state == Building.State.CONSTRUCTION) {
            return 3;
        }
        return switch (b.type().category) {
            case MEDICAL -> 0;
            case FOOD -> 1;
            case RESOURCE -> b.type() == BuildingTypes.COAL_PIT ? 1 : 2;
            case INDUSTRY -> 4;
            case SOCIAL -> 5;
            default -> 6;
        };
    }

    private int desired(Building b) {
        if (b.paused) {
            return 0;
        }
        BuildingType t = b.type();
        if (b.state == Building.State.CONSTRUCTION) {
            return Math.min(b.desiredWorkers, t.builders);
        }
        return t.employs() ? Math.min(b.desiredWorkers, t.maxWorkers(b.level)) : 0;
    }

    private boolean eligible(Citizen c, Building b) {
        if (!c.present()) {
            return false;
        }
        Profession job = b.state == Building.State.CONSTRUCTION ? Profession.WORKER : b.type().job;
        if (c.child()) {
            return mods.childLabor && job == Profession.WORKER && b.state == Building.State.ACTIVE
                    && b.type().category != BuildingCategory.MEDICAL;
        }
        return c.profession.canWork(job);
    }

    void assignHousing() {
        for (Building b : buildings) {
            b.residents = 0;
        }
        List<Citizen> homeless = new ArrayList<>();
        for (Citizen c : population.all()) {
            if (!c.alive()) {
                continue;
            }
            Building h = c.homeId >= 0 ? buildingIndex.get(c.homeId) : null;
            if (h != null && h.active() && h.type().category == BuildingCategory.HOUSING
                    && h.residents < h.type().housing(h.level)) {
                h.residents++;
            } else {
                c.homeId = -1;
                if (c.present()) {
                    homeless.add(c);
                }
            }
        }
        if (homeless.isEmpty()) {
            return;
        }
        List<Building> houses = new ArrayList<>();
        for (Building b : buildings) {
            if (b.active() && b.type().category == BuildingCategory.HOUSING) {
                houses.add(b);
            }
        }
        houses.sort(Comparator.comparingDouble((Building b) -> -b.temperature));
        Map<Integer, Integer> familyHome = new HashMap<>();
        for (Citizen c : population.all()) {
            if (c.homeId >= 0 && c.familyId >= 0) {
                familyHome.putIfAbsent(c.familyId, c.homeId);
            }
        }
        for (Citizen c : homeless) {
            Building chosen = null;
            Integer fh = familyHome.get(c.familyId);
            if (fh != null) {
                Building h = buildingIndex.get(fh);
                if (h != null && h.residents < h.type().housing(h.level)) {
                    chosen = h;
                }
            }
            if (chosen == null) {
                for (Building h : houses) {
                    if (h.residents < h.type().housing(h.level)) {
                        chosen = h;
                        break;
                    }
                }
            }
            if (chosen == null) {
                continue;
            }
            c.homeId = chosen.id;
            chosen.residents++;
            if (c.familyId >= 0) {
                familyHome.putIfAbsent(c.familyId, chosen.id);
            }
        }
    }

    void assignWork() {
        staff.clear();
        for (Building b : buildings) {
            b.workers = 0;
        }
        List<Citizen> idle = new ArrayList<>();
        // Open places for specialists (medics, engineers, technicians, scouts) in buildings of their own trade.
        Map<Profession, Integer> tradeSlots = new java.util.EnumMap<>(Profession.class);
        for (Building b : buildings) {
            if (b.active() && b.type().employs() && b.type().job != Profession.WORKER) {
                tradeSlots.merge(b.type().job, desired(b), Integer::sum);
            }
        }
        List<Citizen> keep = new ArrayList<>();
        for (Citizen c : population.all()) {
            if (!c.present()) {
                if (c.status == Citizen.Status.EXPEDITION) {
                    c.workId = -1;
                }
                continue;
            }
            Building b = c.workId >= 0 ? buildingIndex.get(c.workId) : null;
            if (b != null && b.active() && b.type().job == c.profession && c.profession != Profession.WORKER) {
                tradeSlots.merge(c.profession, -1, Integer::sum);
            }
            keep.add(c);
        }
        for (Citizen c : keep) {
            Building b = c.workId >= 0 ? buildingIndex.get(c.workId) : null;
            boolean valid = b != null && (b.state == Building.State.CONSTRUCTION || (b.active() && b.type().employs()))
                    && eligible(c, b) && b.workers < desired(b);
            // a specialist doing someone else's job goes back to their trade when it has room for them
            if (valid && c.profession != Profession.WORKER && b.type().job != c.profession
                    && tradeSlots.getOrDefault(c.profession, 0) > 0) {
                valid = false;
                tradeSlots.merge(c.profession, -1, Integer::sum);
            }
            if (valid) {
                b.workers++;
                staff.computeIfAbsent(b.id, k -> new ArrayList<>()).add(c);
            } else {
                c.workId = -1;
                idle.add(c);
            }
        }
        List<Building> order = new ArrayList<>(buildings);
        order.sort(Comparator.comparingInt(City::priority).thenComparingInt(b -> b.id));
        // pass 0: people of the right trade everywhere first, then anyone able (1), then children if allowed (2)
        for (int pass = 0; pass < 3; pass++) {
            for (Building b : order) {
                int need = desired(b) - b.workers;
                if (need <= 0) {
                    continue;
                }
                Profession job = b.state == Building.State.CONSTRUCTION ? Profession.WORKER : b.type().job;
                for (int i = 0; i < idle.size() && need > 0; i++) {
                    Citizen c = idle.get(i);
                    boolean match = switch (pass) {
                        case 0 -> !c.child() && c.profession == job;
                        case 1 -> !c.child() && c.profession.canWork(job);
                        default -> c.child() && eligible(c, b);
                    };
                    if (!match) {
                        continue;
                    }
                    c.workId = b.id;
                    b.workers++;
                    staff.computeIfAbsent(b.id, k -> new ArrayList<>()).add(c);
                    idle.remove(i--);
                    need--;
                }
            }
        }
        borrowBuilders();
    }

    /**
     * Construction sites that still lack builders borrow adult workers from running workplaces, least important
     * first (never from medical buildings), so new buildings always get built. The borrowed workers return when
     * the site is finished.
     */
    private void borrowBuilders() {
        for (Building site : buildings) {
            if (site.state != Building.State.CONSTRUCTION) {
                continue;
            }
            int need = desired(site) - site.workers;
            while (need > 0) {
                Building donor = null;
                Citizen moved = null;
                for (Building b : buildings) {
                    if (!b.active() || b.workers <= 0 || b.type().category == BuildingCategory.MEDICAL) {
                        continue;
                    }
                    if (donor != null && (priority(b) < priority(donor)
                            || (priority(b) == priority(donor) && b.workers <= donor.workers))) {
                        continue;
                    }
                    for (Citizen c : staff.getOrDefault(b.id, List.of())) {
                        if (!c.child() && c.profession.canWork(Profession.WORKER)) {
                            donor = b;
                            moved = c;
                            break;
                        }
                    }
                }
                if (donor == null) {
                    return;
                }
                staff.get(donor.id).remove(moved);
                donor.workers--;
                moved.workId = site.id;
                site.workers++;
                staff.computeIfAbsent(site.id, k -> new ArrayList<>()).add(moved);
                need--;
            }
        }
    }

    // ------------------------------------------------------------------ helpers for events

    public void killCitizen(Citizen c, String causeKey) {
        if (!c.alive()) {
            return;
        }
        c.status = Citizen.Status.DEAD;
        c.homeId = -1;
        c.workId = -1;
        anyDied = true;
        population.deathsTotal++;
        population.deathsToday++;
        society.grief = Math.min(30, society.grief + 1.5);
        for (Citizen o : population.all()) {
            if (o != c && o.alive() && o.familyId == c.familyId && c.familyId >= 0) {
                o.hope = Math.max(0, o.hope - 15);
            }
        }
        history.add(clock.step(), "death", Msg.of("frostbound.msg.death", c.name, Msg.tr(causeKey)));
        notify(Notice.Kind.WARNING, Msg.of("frostbound.msg.death", c.name, Msg.tr(causeKey)), null);
    }

    /** Kills up to {@code n} present citizens matching the filter; returns their names. */
    public List<String> killRandom(int n, String causeKey, Predicate<Citizen> filter) {
        List<Citizen> pool = new ArrayList<>();
        for (Citizen c : population.all()) {
            if (c.present() && filter.test(c)) {
                pool.add(c);
            }
        }
        List<String> names = new ArrayList<>();
        for (int i = 0; i < n && !pool.isEmpty(); i++) {
            Citizen c = pool.remove(rng.nextInt(pool.size()));
            names.add(c.name);
            killCitizen(c, causeKey);
        }
        return names;
    }

    /** Makes up to {@code n} healthy citizens ill with the given condition; returns how many. */
    public int sicken(int n, Citizen.Condition condition, Predicate<Citizen> filter) {
        List<Citizen> pool = new ArrayList<>();
        for (Citizen c : population.all()) {
            if (c.present() && !c.condition.sick() && filter.test(c)) {
                pool.add(c);
            }
        }
        int done = 0;
        for (int i = 0; i < n && !pool.isEmpty(); i++) {
            Citizen c = pool.remove(rng.nextInt(pool.size()));
            c.condition = condition;
            c.sickHours = 0;
            c.health = Math.min(c.health, condition == Citizen.Condition.LIGHTLY_ILL ? 80 : 60);
            done++;
        }
        return done;
    }

    /** Cures up to {@code n} citizens with the given condition; returns how many. */
    public int cure(int n, Citizen.Condition condition) {
        int done = 0;
        for (Citizen c : population.all()) {
            if (done >= n) {
                break;
            }
            if (c.present() && c.condition == condition) {
                c.condition = Citizen.Condition.RECOVERING;
                c.recoverHours = 12;
                done++;
            }
        }
        return done;
    }

    public List<Citizen> welcome(int n) {
        List<Citizen> list = population.arrive(rng, n);
        assignHousing();
        history.add(clock.step(), "arrival", Msg.of("frostbound.msg.arrivals", n));
        return list;
    }

    /** Shifts every present citizen's hope and discontent now, and adds a fading city-wide effect. */
    public void moodAll(double hope, double discontent) {
        double h = hope;
        double d = discontent;
        if (h < 0) {
            h *= difficulty.eventSeverity;
        }
        if (d > 0) {
            d *= difficulty.eventSeverity;
        }
        for (Citizen c : population.all()) {
            if (c.present()) {
                c.hope = Math.max(0, Math.min(100, c.hope + h));
                c.discontent = Math.max(0, Math.min(100, c.discontent + d));
            }
        }
        society.eventHope = Math.max(-30, Math.min(30, society.eventHope + h * 0.5));
        society.eventDiscontent = Math.max(-30, Math.min(30, society.eventDiscontent + d * 0.5));
        population.aggregate();
    }

    public boolean hasActive(String typeId) {
        for (Building b : buildings) {
            if (b.working() && b.typeId.equals(typeId)) {
                return true;
            }
        }
        return false;
    }

    public int workersIn(Predicate<BuildingType> filter) {
        int n = 0;
        for (Building b : buildings) {
            if (b.working() && filter.test(b.type())) {
                n += b.workers;
            }
        }
        return n;
    }

    public void addResearchPoints(double points) {
        Tech done = research.addPoints(points);
        if (done != null) {
            onResearchDone(done);
        }
    }

    public boolean grantTech(String id) {
        boolean granted = research.grant(id);
        if (granted) {
            Tech t = Techs.get(id);
            if (t != null) {
                onResearchDone(t);
            }
        }
        return granted;
    }

    public void give(ResourceType t, int units) {
        ledger.add(t, units * ResourceLedger.MILLI);
    }

    public boolean has(ResourceType t, int units) {
        return ledger.amountMilli(t) >= units * ResourceLedger.MILLI;
    }

    public void take(ResourceType t, int units) {
        ledger.take(t, units * ResourceLedger.MILLI);
    }

    // ------------------------------------------------------------------ commands

    private ActionResult notRunning() {
        return ActionResult.fail("frostbound.reason.game_over");
    }

    public ActionResult setGeneratorLevel(int level) {
        if (!running()) {
            return notRunning();
        }
        if (generator.broken()) {
            return ActionResult.fail("frostbound.reason.generator_broken");
        }
        int l = Math.max(0, Math.min(mods.maxGeneratorLevel, level));
        if (level > mods.maxGeneratorLevel) {
            return ActionResult.fail("frostbound.reason.generator_max", mods.maxGeneratorLevel);
        }
        generator.setLevel(l);
        tutorial.flag("generator");
        history.add(clock.step(), "generator", Msg.of("frostbound.msg.generator_level", l));
        return ActionResult.ok("frostbound.msg.generator_level", l);
    }

    public ActionResult setOverload(boolean on) {
        if (!running()) {
            return notRunning();
        }
        if (on && !generator.running()) {
            return ActionResult.fail("frostbound.reason.generator_off");
        }
        generator.setOverload(on);
        tutorial.flag("generator");
        Msg msg = Msg.of(on ? "frostbound.msg.overload_on" : "frostbound.msg.overload_off");
        history.add(clock.step(), "generator", msg);
        return new ActionResult(true, msg);
    }

    public ActionResult repairGenerator() {
        if (!generator.broken()) {
            return ActionResult.fail("frostbound.reason.not_broken");
        }
        if (generator.repairing()) {
            return ActionResult.fail("frostbound.reason.already_repairing");
        }
        Cost cost = Cost.of(ResourceType.IRON, 20);
        if (!ledger.spend(cost)) {
            return ActionResult.fail("frostbound.reason.resources", String.join(",", ledger.missing(cost)));
        }
        boolean engineers = population.byProfession.getOrDefault(Profession.ENGINEER, 0) > 0;
        long steps = SimClock.stepsForHours(engineers ? 6 : 18);
        generator.startRepair(steps);
        history.add(clock.step(), "generator", Msg.of("frostbound.msg.repair_started", engineers ? 6 : 18));
        return ActionResult.ok("frostbound.msg.repair_started", engineers ? 6 : 18);
    }

    /** Validation only (also used for the client preview); null when the building can be placed. */
    public Msg checkPlacement(String typeId, int x, int y, int z, int rotation) {
        BuildingType t = BuildingTypes.get(typeId);
        if (t == null || !t.buildable) {
            return Msg.of("frostbound.reason.unknown_building");
        }
        if (!running()) {
            return Msg.of("frostbound.reason.game_over");
        }
        if (!mods.unlocked.contains(t.id)) {
            return Msg.of("frostbound.reason.locked");
        }
        int sx = rotation % 2 == 0 ? t.width : t.depth;
        int sz = rotation % 2 == 0 ? t.depth : t.width;
        double cx = x + sx / 2.0;
        double cz = z + sz / 2.0;
        if (Math.hypot(cx - (gx + 0.5), cz - (gz + 0.5)) > CITY_RADIUS) {
            return Msg.of("frostbound.reason.too_far");
        }
        for (Building b : buildings) {
            if (b.overlaps(x, z, x + sx - 1, z + sz - 1, 1)) {
                return Msg.of("frostbound.reason.overlap");
            }
        }
        String terrain = world.checkPlacement(t, x, y, z, sx, sz);
        if (terrain != null) {
            return Msg.of(terrain);
        }
        if (!ledger.has(t.cost)) {
            return Msg.of("frostbound.reason.resources", String.join(",", ledger.missing(t.cost)));
        }
        return null;
    }

    public ActionResult placeBuilding(String typeId, int x, int y, int z, int rotation) {
        Msg problem = checkPlacement(typeId, x, y, z, Math.floorMod(rotation, 4));
        if (problem != null) {
            return new ActionResult(false, problem);
        }
        BuildingType t = BuildingTypes.get(typeId);
        ledger.spend(t.cost);
        Building b = new Building(nextBuildingId++, t.id, x, y, z, rotation);
        b.state = Building.State.CONSTRUCTION;
        b.desiredWorkers = t.builders;
        b.temperature = outside;
        addBuilding(b);
        world.constructionStarted(b);
        assignWork();
        if (t.category == BuildingCategory.HOUSING) {
            tutorial.flag("house");
        } else if (t.category == BuildingCategory.FOOD) {
            tutorial.flag("food");
        }
        history.add(clock.step(), "build", Msg.of("frostbound.msg.construction_started", Msg.tr(t.key())));
        return ActionResult.ok("frostbound.msg.construction_started", Msg.tr(t.key()));
    }

    public ActionResult upgradeBuilding(int id) {
        Building b = buildingIndex.get(id);
        if (b == null || b.type() == BuildingTypes.GENERATOR) {
            return ActionResult.fail("frostbound.reason.no_building");
        }
        BuildingType t = b.type();
        if (!b.active()) {
            return ActionResult.fail("frostbound.reason.under_construction");
        }
        if (b.level >= t.maxLevel) {
            return ActionResult.fail("frostbound.reason.max_level");
        }
        if (b.upgradeStepsLeft > 0) {
            return ActionResult.fail("frostbound.reason.upgrading");
        }
        Cost cost = t.upgradeCost(b.level);
        if (!ledger.spend(cost)) {
            return ActionResult.fail("frostbound.reason.resources", String.join(",", ledger.missing(cost)));
        }
        b.upgradeStepsLeft = SimClock.stepsForHours(t.upgradeHours(b.level) / mods.buildSpeedMult);
        history.add(clock.step(), "build", Msg.of("frostbound.msg.upgrade_started", Msg.tr(t.key())));
        return ActionResult.ok("frostbound.msg.upgrade_started", Msg.tr(t.key()));
    }

    public ActionResult demolishBuilding(int id) {
        Building b = buildingIndex.get(id);
        if (b == null || b.type() == BuildingTypes.GENERATOR) {
            return ActionResult.fail("frostbound.reason.no_building");
        }
        BuildingType t = b.type();
        ledger.refund(t.cost, b.state == Building.State.CONSTRUCTION ? 0.75 : 0.5);
        buildings.remove(b);
        buildingIndex.remove(b.id);
        world.buildingRemoved(b);
        recomputeCapacity();
        ledger.clampToCapacity();
        assignHousing();
        assignWork();
        history.add(clock.step(), "build", Msg.of("frostbound.msg.demolished", Msg.tr(t.key())));
        return ActionResult.ok("frostbound.msg.demolished", Msg.tr(t.key()));
    }

    public ActionResult setPaused(int id, boolean paused) {
        Building b = buildingIndex.get(id);
        if (b == null || b.type() == BuildingTypes.GENERATOR) {
            return ActionResult.fail("frostbound.reason.no_building");
        }
        b.paused = paused;
        assignWork();
        return ActionResult.ok(paused ? "frostbound.msg.building_paused" : "frostbound.msg.building_resumed", Msg.tr(b.type().key()));
    }

    public ActionResult setDesiredWorkers(int id, int count) {
        Building b = buildingIndex.get(id);
        if (b == null || b.type() == BuildingTypes.GENERATOR) {
            return ActionResult.fail("frostbound.reason.no_building");
        }
        BuildingType t = b.type();
        int max = b.state == Building.State.CONSTRUCTION ? t.builders : t.maxWorkers(b.level);
        b.desiredWorkers = Math.max(0, Math.min(max, count));
        assignWork();
        tutorial.flag("workers");
        return ActionResult.ok("frostbound.msg.workers_set", Msg.tr(t.key()), b.workers, b.desiredWorkers);
    }

    public ActionResult enactLaw(String id) {
        if (!running()) {
            return notRunning();
        }
        Law law = Laws.get(id);
        if (law == null) {
            return ActionResult.fail("frostbound.reason.unknown_law");
        }
        String reason = society.checkEnact(law, clock.step());
        if (reason != null) {
            return ActionResult.fail(reason);
        }
        society.enact(law, clock.step());
        moodAll(law.hopeNow, law.discontentNow);
        recomputeModifiers();
        tutorial.flag("law");
        if (law.decree && difficulty.eventSeverity > 0 && rng.chance(0.3)) {
            events.trigger(this, "mining_accident", List.of());
        }
        history.add(clock.step(), "law", Msg.of("frostbound.msg.law_enacted", Msg.tr(law.key())));
        notify(Notice.Kind.INFO, Msg.of("frostbound.msg.law_enacted", Msg.tr(law.key())), "event_major");
        return ActionResult.ok("frostbound.msg.law_enacted", Msg.tr(law.key()));
    }

    public ActionResult startResearch(String id) {
        if (!running()) {
            return notRunning();
        }
        Tech t = Techs.get(id);
        String reason = research.check(t);
        if (reason != null) {
            return ActionResult.fail(reason);
        }
        if (!ledger.spend(t.cost)) {
            return ActionResult.fail("frostbound.reason.resources", String.join(",", ledger.missing(t.cost)));
        }
        research.start(t);
        tutorial.flag("research");
        history.add(clock.step(), "research", Msg.of("frostbound.msg.research_started", Msg.tr(t.key())));
        return ActionResult.ok("frostbound.msg.research_started", Msg.tr(t.key()));
    }

    public ActionResult launchExpedition(int siteId, int members) {
        if (!running()) {
            return notRunning();
        }
        Site s = expeditions.site(siteId);
        String reason = expeditions.check(this, s, members);
        if (reason != null) {
            return ActionResult.fail(reason);
        }
        Expedition e = expeditions.launch(this, s, members);
        assignWork();
        population.aggregate();
        history.add(clock.step(), "expedition", Msg.of("frostbound.msg.expedition_left", Msg.tr(s.nameKey()), e.members.size()));
        return ActionResult.ok("frostbound.msg.expedition_left", Msg.tr(s.nameKey()), e.members.size());
    }

    public ActionResult answerEvent(int choice) {
        Msg result = events.answer(this, choice);
        if (result == null) {
            return ActionResult.fail("frostbound.reason.no_event");
        }
        population.aggregate();
        return new ActionResult(true, result);
    }

    public ActionResult setSpeed(int speed) {
        clock.setSpeed(speed);
        return ActionResult.ok("frostbound.msg.speed", clock.speed());
    }

    public ActionResult setSimPaused(boolean paused) {
        clock.setPaused(paused);
        return ActionResult.ok(paused ? "frostbound.msg.paused" : "frostbound.msg.resumed");
    }

    public ActionResult continueEndless() {
        if (phase != Phase.ENDED) {
            return ActionResult.fail("frostbound.reason.not_ended");
        }
        phase = Phase.ENDLESS;
        history.add(clock.step(), "ending", Msg.of("frostbound.msg.endless"));
        return ActionResult.ok("frostbound.msg.endless");
    }

    public void forceWeather(WeatherState state) {
        weather.force(state);
        outside = weather.outsideTemperature(clock);
        onWeatherChanged();
    }

    // ------------------------------------------------------------------ snapshot for clients

    public Map<String, Object> snapshot(int historyEntries) {
        Map<String, Object> m = Tree.map();
        m.put("phase", phase.name());
        if (ending != null) {
            m.put("ending", ending.name());
        }
        if (endReason != null) {
            m.put("endReason", endReason);
        }
        m.put("name", name);
        m.put("difficulty", difficulty.name());
        m.put("dayLength", dayLengthMinutes);
        m.put("day", clock.day());
        m.put("hour", clock.hourOfDay());
        m.put("minute", clock.minuteOfHour());
        m.put("step", clock.step());
        m.put("speed", clock.speed());
        m.put("paused", clock.paused());
        m.put("outside", outside);
        m.put("weather", weather.current().name());
        List<Object> fc = new ArrayList<>();
        for (WeatherState w : weather.forecast()) {
            fc.add(w.name());
        }
        m.put("forecast", fc);
        WeatherSystem.Wave wave = weather.nextWaveAfter(clock.day() - 1);
        if (wave != null) {
            m.put("waveDay", wave.startDay());
            m.put("waveState", wave.state().name());
        }
        m.put("forecastBase", WeatherSystem.baseTemperature(clock.day() + 1) + difficulty.tempOffset);

        Map<String, Object> g = Tree.map();
        g.put("level", generator.level());
        g.put("maxLevel", mods.maxGeneratorLevel);
        g.put("overload", generator.overload());
        g.put("stress", generator.stress());
        g.put("broken", generator.broken());
        g.put("repairing", generator.repairing());
        g.put("repairHours", generator.repairStepsLeft() / (double) SimClock.STEPS_PER_HOUR);
        g.put("fuelOut", generator.fuelOut());
        g.put("buffer", generator.bufferMilli() / (double) ResourceLedger.MILLI);
        g.put("burn", generator.burnPerDay(mods, difficulty));
        g.put("capacity", heat.capacity());
        g.put("boost", heat.boost());
        g.put("range", heat.range());
        g.put("nominalRange", generator.nominalRange(mods));
        g.put("supply", heat.supplyRatio());
        g.put("demand", heat.totalDemand());
        g.put("x", gx);
        g.put("y", gy);
        g.put("z", gz);
        m.put("generator", g);

        List<Object> res = new ArrayList<>();
        for (ResourceType t : ResourceType.values()) {
            Map<String, Object> r = Tree.map();
            r.put("id", t.id());
            r.put("amount", ledger.amountMilli(t) / (double) ResourceLedger.MILLI);
            r.put("cap", ledger.capacity(t));
            r.put("prod", ledger.producedPerDay(t));
            r.put("cons", ledger.consumedPerDay(t));
            r.put("days", ledger.daysLeft(t));
            res.add(r);
        }
        m.put("resources", res);

        Map<String, Object> p = Tree.map();
        p.put("alive", population.alive);
        p.put("present", population.present);
        p.put("children", population.children);
        p.put("sick", population.sick);
        p.put("homeless", population.homeless);
        p.put("employed", population.employed);
        p.put("unemployed", population.unemployed);
        p.put("expedition", population.onExpedition);
        p.put("hope", population.avgHope);
        p.put("disc", population.avgDiscontent);
        p.put("health", population.avgHealth);
        p.put("body", population.avgBodyTemp);
        p.put("satiety", population.avgSatiety);
        p.put("fatigue", population.avgFatigue);
        p.put("deaths", population.deathsTotal);
        p.put("deathsToday", population.deathsToday);
        p.put("arrivals", population.arrivalsTotal);
        List<Object> hb = new ArrayList<>();
        for (int v : population.hungerBands) {
            hb.add(v);
        }
        List<Object> tb = new ArrayList<>();
        for (int v : population.tempBands) {
            tb.add(v);
        }
        p.put("hunger", hb);
        p.put("temp", tb);
        Map<String, Object> prof = Tree.map();
        population.byProfession.forEach((k, v) -> prof.put(k.id(), v));
        p.put("prof", prof);
        m.put("pop", p);

        List<Object> bl = new ArrayList<>();
        for (Building b : buildings) {
            BuildingType t = b.type();
            Map<String, Object> e = Tree.map();
            e.put("id", b.id);
            e.put("type", b.typeId);
            e.put("x", b.x);
            e.put("y", b.y);
            e.put("z", b.z);
            e.put("rot", b.rotation);
            e.put("sx", b.sizeX());
            e.put("sz", b.sizeZ());
            e.put("level", b.level);
            e.put("state", b.state.name());
            e.put("progress", b.state == Building.State.CONSTRUCTION
                    ? Math.min(1.0, b.buildProgress / Math.max(1.0, t.buildHours * (double) t.builders)) : 1.0);
            e.put("upgradeH", b.upgradeStepsLeft / (double) SimClock.STEPS_PER_HOUR);
            e.put("workers", b.workers);
            e.put("desired", b.desiredWorkers);
            e.put("max", b.state == Building.State.CONSTRUCTION ? t.builders : t.maxWorkers(b.level));
            e.put("temp", b.temperature);
            e.put("eta", b.eta);
            e.put("eff", b.efficiency);
            e.put("out", b.lastOutput);
            e.put("paused", b.paused);
            e.put("residents", b.residents);
            e.put("housing", t.housing(b.level));
            e.put("beds", t.beds(b.level));
            bl.add(e);
        }
        m.put("buildings", bl);

        Map<String, Object> laws = Tree.map();
        laws.put("enacted", new ArrayList<Object>(society.enacted()));
        laws.put("readyIn", Math.max(0, society.lawReadyAt() - clock.step()) / (double) SimClock.STEPS_PER_HOUR);
        laws.put("emergency", society.emergencyActive(clock.step()));
        laws.put("emergencyReadyIn", Math.max(0, society.emergencyReadyAt() - clock.step()) / (double) SimClock.STEPS_PER_HOUR);
        laws.put("strikeH", Math.max(0, society.strikeUntil() - clock.step()) / (double) SimClock.STEPS_PER_HOUR);
        Map<String, Object> lawState = Tree.map();
        for (Law law : Laws.all()) {
            String reason = society.checkEnact(law, clock.step());
            lawState.put(law.id, reason == null ? "" : reason);
        }
        laws.put("state", lawState);
        m.put("laws", laws);

        Map<String, Object> rs = Tree.map();
        if (research.current() != null) {
            rs.put("current", research.current());
        }
        rs.put("progress", research.progress());
        rs.put("points", research.lastHourPoints());
        rs.put("completed", new ArrayList<Object>(research.completed()));
        m.put("research", rs);

        Map<String, Object> ex = Tree.map();
        List<Object> sites = new ArrayList<>();
        for (Site s : expeditions.sites()) {
            Map<String, Object> sm = s.save();
            sm.put("open", (!s.far || mods.longRange) && !s.depleted);
            sites.add(sm);
        }
        ex.put("sites", sites);
        List<Object> act = new ArrayList<>();
        for (Expedition e : expeditions.active()) {
            Map<String, Object> em = Tree.map();
            em.put("id", e.id);
            em.put("site", e.siteId);
            em.put("members", e.members.size());
            em.put("returning", e.returning);
            em.put("progress", e.progress);
            act.add(em);
        }
        ex.put("active", act);
        m.put("expeditions", ex);

        EventSystem.Pending pending = events.pending();
        if (pending != null) {
            EventDef def = Events.get(pending.id());
            if (def != null) {
                Map<String, Object> ev = Tree.map();
                ev.put("id", def.id);
                ev.put("major", def.major);
                ev.put("args", new ArrayList<Object>(pending.args()));
                ev.put("hoursLeft", Math.max(0, EventSystem.TIMEOUT - (clock.step() - pending.start()))
                        / (double) SimClock.STEPS_PER_HOUR);
                List<Object> choices = new ArrayList<>();
                for (EventDef.Choice c : def.choices) {
                    Map<String, Object> cm = Tree.map();
                    cm.put("key", c.key());
                    cm.put("ok", c.available().test(this));
                    choices.add(cm);
                }
                ev.put("choices", choices);
                m.put("event", ev);
            }
        }

        List<Object> hist = new ArrayList<>();
        for (HistoryLog.Entry e : history.recent(historyEntries)) {
            Map<String, Object> hm = e.msg().save();
            hm.put("step", e.step());
            hm.put("kind", e.kind());
            hist.add(hm);
        }
        m.put("history", hist);

        Map<String, Object> tut = Tree.map();
        tut.put("enabled", tutorial.enabled());
        tut.put("step", tutorial.step().name());
        m.put("tutorial", tut);
        m.put("unlocked", new ArrayList<Object>(mods.unlocked));
        m.put("shiftEnd", mods.shiftEndHour);
        return m;
    }

    /** Detailed citizen list for the population tab (one page). */
    public Map<String, Object> citizenPage(int page, int pageSize) {
        Map<String, Object> m = Tree.map();
        List<Citizen> list = new ArrayList<>();
        for (Citizen c : population.all()) {
            if (c.alive()) {
                list.add(c);
            }
        }
        int from = Math.max(0, Math.min(list.size(), page * pageSize));
        int to = Math.min(list.size(), from + pageSize);
        List<Object> out = new ArrayList<>();
        for (Citizen c : list.subList(from, to)) {
            Map<String, Object> cm = c.save();
            Building w = c.workId >= 0 ? buildingIndex.get(c.workId) : null;
            if (w != null) {
                cm.put("workType", w.typeId);
            }
            out.add(cm);
        }
        m.put("page", page);
        m.put("total", list.size());
        m.put("citizens", out);
        return m;
    }

    // ------------------------------------------------------------------ persistence

    public Map<String, Object> save() {
        Map<String, Object> m = Tree.map();
        m.put("schema", SaveMigrations.CURRENT);
        m.put("seed", seed);
        m.put("rng", rng.state());
        m.put("difficulty", difficulty.name());
        m.put("name", name);
        m.put("dayLength", dayLengthMinutes);
        m.put("phase", phase.name());
        if (ending != null) {
            m.put("ending", ending.name());
        }
        if (endReason != null) {
            m.put("endReason", endReason);
        }
        m.put("gx", gx);
        m.put("gy", gy);
        m.put("gz", gz);
        m.put("clock", clock.save());
        m.put("resources", ledger.save());
        m.put("weather", weather.save());
        m.put("generator", generator.save());
        List<Object> bl = new ArrayList<>();
        for (Building b : buildings) {
            bl.add(b.save());
        }
        m.put("buildings", bl);
        m.put("nextBuilding", nextBuildingId);
        m.put("population", population.save());
        m.put("society", society.save());
        m.put("events", events.save());
        m.put("research", research.save());
        m.put("expeditions", expeditions.save());
        m.put("history", history.save());
        m.put("tutorial", tutorial.save());
        return m;
    }

    /**
     * Restores a city from a save tree, repairing what it can (see {@link Tree#repairs()}).
     *
     * @throws SaveMigrations.NewerSaveException if the tree comes from a newer schema
     */
    public static City load(Map<String, Object> root, WorldAccess world) {
        Map<String, Object> m = SaveMigrations.migrate(root);
        long seed = Tree.l(m, "seed", 0L);
        Difficulty d = Tree.e(m, "difficulty", Difficulty.class, Difficulty.NORMAL);
        City c = new City(seed, d);
        c.world = world == null ? WorldAccess.NONE : world;
        c.rng.setState(Tree.l(m, "rng", seed));
        c.name = Tree.s(m, "name", "");
        c.dayLengthMinutes = Tree.i(m, "dayLength", 20, 5, 120);
        c.phase = Tree.e(m, "phase", Phase.class, Phase.RUNNING);
        c.ending = Tree.e(m, "ending", Ending.class, null);
        c.endReason = Tree.s(m, "endReason", null);
        c.gx = Tree.i(m, "gx", 0);
        c.gy = Tree.i(m, "gy", 64, -64, 320);
        c.gz = Tree.i(m, "gz", 0);
        c.clock.load(Tree.map(m, "clock"));
        c.weather.load(Tree.map(m, "weather"), c.rng);
        c.generator.load(Tree.map(m, "generator"));
        int maxId = 0;
        boolean hasGenerator = false;
        for (Map<String, Object> bm : Tree.maps(m, "buildings")) {
            Building b = Building.load(bm);
            if (b == null || c.buildingIndex.containsKey(b.id)) {
                continue;
            }
            if (b.type() == BuildingTypes.GENERATOR) {
                if (hasGenerator) {
                    continue;
                }
                hasGenerator = true;
                b.state = Building.State.ACTIVE;
            }
            c.addBuilding(b);
            maxId = Math.max(maxId, b.id);
        }
        c.nextBuildingId = Math.max(maxId + 1, Tree.i(m, "nextBuilding", 1));
        if (!hasGenerator) {
            Tree.repaired("generator record recreated");
            Building g = new Building(c.nextBuildingId++, BuildingTypes.GENERATOR.id, c.gx - 6, c.gy, c.gz - 6, 0);
            g.state = Building.State.ACTIVE;
            c.addBuilding(g);
        }
        c.population.load(Tree.map(m, "population"));
        c.society.load(Tree.map(m, "society"));
        c.events.load(Tree.map(m, "events"));
        c.research.load(Tree.map(m, "research"));
        c.expeditions.load(Tree.map(m, "expeditions"), c.rng);
        c.history.load(Tree.map(m, "history"));
        c.tutorial.load(Tree.map(m, "tutorial"));
        c.recomputeCapacity();
        c.ledger.load(Tree.map(m, "resources"));
        c.ledger.clampToCapacity();
        c.recomputeModifiers();
        c.assignHousing();
        c.assignWork();
        c.population.aggregate();
        c.outside = c.weather.outsideTemperature(c.clock);
        return c;
    }
}
