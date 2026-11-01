package com.frostbound.core.events;

import static com.frostbound.core.resources.ResourceType.COAL;
import static com.frostbound.core.resources.ResourceType.FOOD;
import static com.frostbound.core.resources.ResourceType.IRON;
import static com.frostbound.core.resources.ResourceType.MEDICINE;
import static com.frostbound.core.resources.ResourceType.STEAM_CORE;
import static com.frostbound.core.resources.ResourceType.WOOD;

import com.frostbound.core.city.BuildingCategory;
import com.frostbound.core.city.BuildingTypes;
import com.frostbound.core.city.City;
import com.frostbound.core.population.Citizen;
import com.frostbound.core.research.Tech;
import com.frostbound.core.research.Techs;
import com.frostbound.core.sim.SimClock;
import com.frostbound.core.society.Law;
import com.frostbound.core.society.Laws;
import com.frostbound.core.util.Msg;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/** All event definitions (texts live in the language files under frostbound.event.*). */
public final class Events {
    private static final Map<String, EventDef> BY_ID = new LinkedHashMap<>();
    private static final Predicate<City> ALWAYS = c -> true;
    private static final Function<City, List<String>> NO_ARGS = c -> List.of();

    static {
        random("mining_accident", false,
                c -> c.workersIn(t -> t == BuildingTypes.COAL_PIT || t == BuildingTypes.MINE || t == BuildingTypes.CORE_EXTRACTOR) > 0,
                3.0, 5, NO_ARGS,
                choice("mining_accident", 0, ALWAYS, (c, a) -> {
                    c.take(WOOD, 15);
                    int n = c.sicken(2, Citizen.Condition.SEVERELY_ILL, x -> !x.child());
                    c.moodAll(3, 0);
                    return Msg.of("frostbound.event.mining_accident.r0", n);
                }),
                choice("mining_accident", 1, ALWAYS, (c, a) -> {
                    int deaths = c.difficulty().eventSeverity > 1.2 ? 2 : 1;
                    List<String> names = c.killRandom(deaths, "frostbound.cause.accident", x -> !x.child());
                    c.moodAll(0, 6);
                    return Msg.of("frostbound.event.mining_accident.r1", String.join(", ", names));
                }));

        random("ration_demand", false,
                c -> c.modifiers().rationing || (c.ledger().daysLeft(FOOD) >= 0 && c.ledger().daysLeft(FOOD) < 2.5),
                2.0, 6, NO_ARGS,
                choice("ration_demand", 0, ALWAYS, (c, a) -> {
                    c.events().schedule("ration_check", c.clock().step() + 2L * SimClock.STEPS_PER_DAY, List.of());
                    c.moodAll(4, -3);
                    return Msg.of("frostbound.event.ration_demand.r0");
                }),
                choice("ration_demand", 1, ALWAYS, (c, a) -> {
                    c.moodAll(-2, 7);
                    return Msg.of("frostbound.event.ration_demand.r1");
                }));
        scripted("ration_check", false,
                choice("ration_check", 0, ALWAYS, (c, a) -> {
                    if (c.ledger().amount(FOOD) >= c.population().present * 2) {
                        c.moodAll(6, -4);
                        return Msg.of("frostbound.event.ration_check.kept");
                    }
                    c.moodAll(-10, 10);
                    return Msg.of("frostbound.event.ration_check.broken");
                }));

        random("infection_outbreak", false, c -> c.population().sick >= 4, 2.0, 7, NO_ARGS,
                choice("infection_outbreak", 0, c -> hasMedical(c), (c, a) -> {
                    int n = c.sicken(2, Citizen.Condition.INFECTED, x -> true);
                    c.moodAll(-2, 2);
                    return Msg.of("frostbound.event.infection_outbreak.r0", n);
                }),
                choice("infection_outbreak", 1, ALWAYS, (c, a) -> {
                    int n = c.sicken(6, Citizen.Condition.INFECTED, x -> true);
                    c.moodAll(0, 4);
                    return Msg.of("frostbound.event.infection_outbreak.r1", n);
                }));

        random("survivors_arrive", false, c -> c.clock().day() >= 4, 1.2, 8,
                c -> List.of(String.valueOf(c.rng().range(4, 8))),
                choice("survivors_arrive", 0, ALWAYS, (c, a) -> {
                    int n = parse(a, 0, 5);
                    c.welcome(n);
                    c.take(FOOD, 10);
                    c.moodAll(4, 0);
                    return Msg.of("frostbound.event.survivors_arrive.r0", n);
                }),
                choice("survivors_arrive", 1, ALWAYS, (c, a) -> {
                    c.moodAll(-6, 2);
                    return Msg.of("frostbound.event.survivors_arrive.r1");
                }));
        scripted("survivors_found", false,
                choice("survivors_found", 0, ALWAYS, (c, a) -> {
                    int n = parse(a, 0, 5);
                    c.welcome(n);
                    c.moodAll(5, 0);
                    return Msg.of("frostbound.event.survivors_found.r0", n);
                }),
                choice("survivors_found", 1, ALWAYS, (c, a) -> {
                    c.moodAll(-6, 3);
                    return Msg.of("frostbound.event.survivors_found.r1");
                }));

        random("generator_strain", true, c -> c.generator().stress() >= 60 && c.generator().running(), 3.0, 4, NO_ARGS,
                choice("generator_strain", 0, c -> c.has(IRON, 25), (c, a) -> {
                    c.take(IRON, 25);
                    c.generator().addStress(-40);
                    return Msg.of("frostbound.event.generator_strain.r0");
                }),
                choice("generator_strain", 1, ALWAYS, (c, a) -> {
                    if (c.rng().chance(0.5)) {
                        c.generator().breakDown();
                        return Msg.of("frostbound.event.generator_strain.r1a");
                    }
                    return Msg.of("frostbound.event.generator_strain.r1b");
                }));

        scripted("strike", true,
                choice("strike", 0, c -> c.has(FOOD, 30), (c, a) -> {
                    c.take(FOOD, 30);
                    c.moodAll(2, -20);
                    return Msg.of("frostbound.event.strike.r0");
                }),
                choice("strike", 1, c -> hasGuards(c), (c, a) -> {
                    c.moodAll(-8, -12);
                    return Msg.of("frostbound.event.strike.r1");
                }),
                choice("strike", 2, ALWAYS, (c, a) -> {
                    c.society().startStrike(c.clock().step(), SimClock.STEPS_PER_DAY);
                    c.moodAll(0, -6);
                    return Msg.of("frostbound.event.strike.r2");
                }));

        scripted("cold_wave_warning", true,
                choice("cold_wave_warning", 0, ALWAYS, (c, a) -> Msg.of("frostbound.event.cold_wave_warning.r0")));
        scripted("great_cold_wave", true,
                choice("great_cold_wave", 0, ALWAYS, (c, a) -> {
                    c.moodAll(-3, 3);
                    return Msg.of("frostbound.event.great_cold_wave.r0");
                }));

        random("law_demand", false, c -> c.population().avgHope < 35 && suggestLaw(c) != null, 1.5, 8,
                c -> {
                    String id = suggestLaw(c);
                    return id == null ? List.of("medical_priority") : List.of(id);
                },
                choice("law_demand", 0, ALWAYS, (c, a) -> {
                    c.events().schedule("law_check", c.clock().step() + 2L * SimClock.STEPS_PER_DAY, a);
                    c.moodAll(5, -3);
                    return Msg.of("frostbound.event.law_demand.r0", lawName(a));
                }),
                choice("law_demand", 1, ALWAYS, (c, a) -> {
                    c.moodAll(-3, 6);
                    return Msg.of("frostbound.event.law_demand.r1");
                }));
        scripted("law_check", false,
                choice("law_check", 0, ALWAYS, (c, a) -> {
                    String id = a.isEmpty() ? "" : a.get(0);
                    if (c.society().isEnacted(id)) {
                        c.moodAll(6, -4);
                        return Msg.of("frostbound.event.law_check.kept", lawName(a));
                    }
                    c.moodAll(-10, 8);
                    return Msg.of("frostbound.event.law_check.broken", lawName(a));
                }));

        random("funeral", false, c -> c.population().deathsYesterday >= 3, 5.0, 3,
                c -> List.of(String.valueOf(c.population().deathsYesterday)),
                choice("funeral", 0, c -> c.has(WOOD, 10), (c, a) -> {
                    c.take(WOOD, 10);
                    c.moodAll(4, -2);
                    c.society().grief *= 0.5;
                    return Msg.of("frostbound.event.funeral.r0");
                }),
                choice("funeral", 1, ALWAYS, (c, a) -> {
                    c.moodAll(-4, 3);
                    return Msg.of("frostbound.event.funeral.r1");
                }));

        random("lost_in_storm", false, c -> c.weather().current().stormy() && c.workersIn(t -> t.outdoor) > 0, 3.0, 4, NO_ARGS,
                choice("lost_in_storm", 0, ALWAYS, (c, a) -> {
                    List<String> names = c.rng().chance(0.3) ? c.killRandom(1, "frostbound.cause.storm", x -> !x.child()) : List.of();
                    c.moodAll(2, 0);
                    return names.isEmpty() ? Msg.of("frostbound.event.lost_in_storm.r0a")
                            : Msg.of("frostbound.event.lost_in_storm.r0b", String.join(", ", names));
                }),
                choice("lost_in_storm", 1, ALWAYS, (c, a) -> {
                    int n = c.difficulty().eventSeverity > 1.2 ? 2 : 1;
                    List<String> names = c.killRandom(n, "frostbound.cause.storm", x -> !x.child());
                    c.moodAll(-2, 6);
                    return Msg.of("frostbound.event.lost_in_storm.r1", String.join(", ", names));
                }));

        scripted("unknown_facility", true,
                choice("unknown_facility", 0, ALWAYS, (c, a) -> {
                    String id = a.isEmpty() ? "" : a.get(0);
                    Tech t = Techs.get(id);
                    if (t != null && c.grantTech(id)) {
                        return Msg.of("frostbound.event.unknown_facility.r0", Msg.tr(t.key()));
                    }
                    c.addResearchPoints(80);
                    return Msg.of("frostbound.event.unknown_facility.r0b");
                }),
                choice("unknown_facility", 1, ALWAYS, (c, a) -> {
                    c.give(IRON, 40);
                    c.give(STEAM_CORE, 1);
                    return Msg.of("frostbound.event.unknown_facility.r1");
                }));

        random("theft", false, c -> c.population().avgDiscontent >= 45 && c.has(FOOD, 40), 1.5, 6, NO_ARGS,
                choice("theft", 0, ALWAYS, (c, a) -> {
                    if (hasGuards(c)) {
                        c.moodAll(0, 1);
                    } else {
                        c.moodAll(-2, 4);
                    }
                    return Msg.of("frostbound.event.theft.r0");
                }),
                choice("theft", 1, ALWAYS, (c, a) -> {
                    c.take(FOOD, 15);
                    c.moodAll(2, -2);
                    return Msg.of("frostbound.event.theft.r1");
                }));

        random("engineer_idea", false, c -> c.hasActive("research_lab") && c.clock().day() >= 6, 1.0, 10, NO_ARGS,
                choice("engineer_idea", 0, c -> c.has(IRON, 15), (c, a) -> {
                    c.take(IRON, 15);
                    c.addResearchPoints(60);
                    return Msg.of("frostbound.event.engineer_idea.r0");
                }),
                choice("engineer_idea", 1, ALWAYS, (c, a) -> {
                    c.moodAll(-1, 0);
                    return Msg.of("frostbound.event.engineer_idea.r1");
                }));

        random("frostbite_wave", false, c -> c.population().avgBodyTemp < 34.5 && c.population().present > 0, 3.0, 3, NO_ARGS,
                choice("frostbite_wave", 0, c -> c.has(COAL, 20), (c, a) -> {
                    c.take(COAL, 20);
                    int n = c.cure(3, Citizen.Condition.FROSTBITE);
                    c.moodAll(2, 0);
                    return Msg.of("frostbound.event.frostbite_wave.r0", n);
                }),
                choice("frostbite_wave", 1, ALWAYS, (c, a) -> {
                    int n = c.sicken(3, Citizen.Condition.FROSTBITE, x -> true);
                    return Msg.of("frostbound.event.frostbite_wave.r1", n);
                }));

        scripted("hope_crisis", true,
                choice("hope_crisis", 0, ALWAYS, (c, a) -> {
                    c.moodAll(10, -2);
                    return Msg.of("frostbound.event.hope_crisis.r0");
                }),
                choice("hope_crisis", 1, ALWAYS, (c, a) -> {
                    c.moodAll(-2, 5);
                    return Msg.of("frostbound.event.hope_crisis.r1");
                }));

        scripted("riot", true,
                choice("riot", 0, c -> c.has(FOOD, 40) && c.has(COAL, 40), (c, a) -> {
                    c.take(FOOD, 40);
                    c.take(COAL, 40);
                    c.moodAll(3, -25);
                    return Msg.of("frostbound.event.riot.r0");
                }),
                choice("riot", 1, c -> hasGuards(c), (c, a) -> {
                    List<String> names = c.killRandom(1, "frostbound.cause.riot", x -> !x.child());
                    c.moodAll(-15, -15);
                    return Msg.of("frostbound.event.riot.r1", String.join(", ", names));
                }),
                choice("riot", 2, ALWAYS, (c, a) -> {
                    c.moodAll(-5, 5);
                    return Msg.of("frostbound.event.riot.r2");
                }));

        random("sick_child", false, c -> c.population().children > 0 && c.population().sick >= 1, 1.0, 9, NO_ARGS,
                choice("sick_child", 0, c -> c.has(MEDICINE, 5), (c, a) -> {
                    c.take(MEDICINE, 5);
                    c.moodAll(3, 0);
                    return Msg.of("frostbound.event.sick_child.r0");
                }),
                choice("sick_child", 1, ALWAYS, (c, a) -> {
                    c.sicken(1, Citizen.Condition.SEVERELY_ILL, Citizen::child);
                    c.moodAll(-3, 2);
                    return Msg.of("frostbound.event.sick_child.r1");
                }));
    }

    private Events() {
    }

    private static EventDef.Choice choice(String id, int n, Predicate<City> available, EventDef.Effect effect) {
        return new EventDef.Choice("frostbound.event." + id + ".c" + n, available, effect);
    }

    private static void random(String id, boolean major, Predicate<City> condition, double weight, int cooldownDays,
            Function<City, List<String>> args, EventDef.Choice... choices) {
        BY_ID.put(id, new EventDef(id, major, false, condition, weight, cooldownDays, args, Arrays.asList(choices)));
    }

    private static void scripted(String id, boolean major, EventDef.Choice... choices) {
        BY_ID.put(id, new EventDef(id, major, true, c -> false, 0, 0, NO_ARGS, Arrays.asList(choices)));
    }

    public static EventDef get(String id) {
        return BY_ID.get(id);
    }

    public static List<EventDef> all() {
        return Collections.unmodifiableList(new ArrayList<>(BY_ID.values()));
    }

    private static boolean hasMedical(City c) {
        return c.workersIn(t -> t.category == BuildingCategory.MEDICAL) > 0;
    }

    private static boolean hasGuards(City c) {
        return c.hasActive("guard_post") || c.modifiers().strictControl;
    }

    private static String suggestLaw(City c) {
        for (String id : List.of("medical_priority", "free_medicine", "entertainment", "self_governance")) {
            Law law = Laws.get(id);
            if (law != null && !c.society().isEnacted(id) && !conflicts(c, law)) {
                return id;
            }
        }
        return null;
    }

    private static boolean conflicts(City c, Law law) {
        for (String x : law.conflicts) {
            if (c.society().isEnacted(x)) {
                return true;
            }
        }
        return false;
    }

    private static String lawName(List<String> args) {
        return Msg.tr("frostbound.law." + (args.isEmpty() ? "" : args.get(0)));
    }

    private static int parse(List<String> args, int index, int def) {
        try {
            return args.size() > index ? Integer.parseInt(args.get(index)) : def;
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
