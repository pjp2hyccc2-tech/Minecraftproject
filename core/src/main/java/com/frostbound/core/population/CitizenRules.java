package com.frostbound.core.population;

import com.frostbound.core.city.Building;
import com.frostbound.core.city.City;
import com.frostbound.core.city.Modifiers;
import com.frostbound.core.population.Citizen.Condition;

/**
 * Hourly rules for one citizen: where they are, what temperature they feel, hunger, fatigue, illness,
 * healing, death, hope and discontent. Provisional values.
 */
public final class CitizenRules {
    private CitizenRules() {
    }

    /** Output multiplier from a workplace's indoor temperature. */
    public static double tempEfficiency(double t, boolean greenhouse) {
        double e;
        if (t >= 10) {
            e = 1.0;
        } else if (t >= 0) {
            e = 0.85 + 0.015 * t;
        } else if (t >= -10) {
            e = 0.65 + 0.02 * (t + 10);
        } else if (t >= -20) {
            e = 0.40 + 0.025 * (t + 20);
        } else {
            e = Math.max(0.15, 0.40 + 0.0125 * (t + 20));
        }
        if (greenhouse && t < 5) {
            e *= 0.5;
        }
        return e;
    }

    public static boolean canWorkNow(City city, Citizen c) {
        if (!c.present() || c.fatigue >= 98) {
            return false;
        }
        if (c.child() && !city.modifiers().childLabor) {
            return false;
        }
        return switch (c.condition) {
            case HEALTHY, RECOVERING -> true;
            case LIGHTLY_ILL -> !city.modifiers().medicalPriority;
            default -> false;
        };
    }

    /** How much of a full worker this citizen is right now (0 if they cannot work). */
    public static double workFactor(City city, Citizen c) {
        if (!canWorkNow(city, c)) {
            return 0;
        }
        double f = 1.0 - c.fatigue / 200.0;
        if (c.condition == Condition.LIGHTLY_ILL) {
            f *= 0.5;
        } else if (c.condition == Condition.RECOVERING) {
            f *= 0.7;
        }
        if (c.child()) {
            f *= 0.5;
        }
        return f;
    }

    public static void updateHour(City city, Citizen c) {
        Modifiers m = city.modifiers();
        int hour = city.clock().hourOfDay();
        double risk = city.difficulty().healthRisk;
        Building work = c.workId >= 0 ? city.building(c.workId) : null;
        Building home = c.homeId >= 0 ? city.building(c.homeId) : null;
        boolean onShift = city.isShiftHour(hour) && !city.society().strikeActive(city.clock().step());
        c.atWork = work != null && onShift && (work.working() || work.state == Building.State.CONSTRUCTION) && canWorkNow(city, c);
        double exposure;
        if (c.atWork) {
            c.location = work.id;
            exposure = work.state == Building.State.CONSTRUCTION ? city.outsideTemperature() : work.temperature;
        } else if (home != null) {
            c.location = home.id;
            exposure = home.temperature;
        } else {
            c.location = -1;
            exposure = city.heat().openAir(14, city.outsideTemperature());
        }

        // Body temperature drifts toward an exposure-dependent equilibrium (colder air, lower floor)
        double target = Math.max(28, Math.min(37.0, 37.0 + (exposure - 12) * 0.08 * risk));
        if (c.bodyTemp < target) {
            c.bodyTemp = Math.min(target, c.bodyTemp + 0.6);
        } else {
            c.bodyTemp = Math.max(target, c.bodyTemp - 0.25);
        }
        c.bodyTemp = Math.max(26, Math.min(37.2, c.bodyTemp));

        // Hunger and fatigue
        c.satiety = Math.max(0, c.satiety - (c.atWork ? 4.0 : 3.3));
        boolean sleeping = !c.atWork && (hour >= 22 || hour < 6);
        if (c.atWork) {
            double emergency = city.society().emergencyActive(city.clock().step()) ? 1.5 : 1.0;
            c.fatigue += 5.0 * m.fatigueMult * emergency;
        } else {
            c.fatigue -= sleeping ? 8.0 : 3.0;
        }
        c.fatigue = Math.max(0, Math.min(100, c.fatigue));

        // Health
        if (c.bodyTemp < 35.0) {
            c.health -= (35.0 - c.bodyTemp) * 1.5 * risk;
            if (c.bodyTemp < 33.5 && (c.condition == Condition.HEALTHY || c.condition == Condition.RECOVERING)
                    && city.rng().chance(0.03 * risk)) {
                setCondition(c, Condition.FROSTBITE);
            }
        }
        if (c.satiety <= 0) {
            c.health -= 3.0 * risk;
        } else if (c.satiety < 15) {
            c.health -= 1.5 * risk;
        }
        if (c.fatigue >= 95) {
            c.health -= 1.0;
        }
        if (c.condition == Condition.HEALTHY) {
            double p = 0.0008;
            if (c.bodyTemp < 35.5) {
                p += 0.003;
            }
            if (c.satiety < 25) {
                p += 0.003;
            }
            if (c.fatigue > 80) {
                p += 0.002;
            }
            if (city.rng().chance(p * m.illnessMult * risk)) {
                setCondition(c, Condition.LIGHTLY_ILL);
            }
            if (c.bodyTemp >= 36 && c.satiety >= 30) {
                c.health = Math.min(100, c.health + 0.5);
            }
        } else if (c.condition.sick()) {
            c.sickHours++;
            if (c.treated) {
                c.health += 3.0 * m.healingMult * (city.medicineShortage() ? 0.5 : 1.0);
                if (c.health >= 75) {
                    setCondition(c, Condition.RECOVERING);
                    c.recoverHours = 12;
                }
            } else if (c.condition == Condition.LIGHTLY_ILL) {
                if (c.sickHours > 36 && city.rng().chance(0.05 * risk)) {
                    setCondition(c, Condition.SEVERELY_ILL);
                } else if (city.rng().chance(0.03)) {
                    setCondition(c, Condition.HEALTHY);
                }
            } else {
                c.health -= 0.8 * risk;
            }
        } else if (c.condition == Condition.RECOVERING) {
            c.health = Math.min(100, c.health + 1.0);
            if (--c.recoverHours <= 0) {
                setCondition(c, Condition.HEALTHY);
            }
        }
        c.health = Math.max(0, Math.min(100, c.health));
        if (c.health <= 0) {
            String cause = c.bodyTemp < 32 ? "frostbound.cause.cold" : c.satiety <= 0 ? "frostbound.cause.hunger"
                    : "frostbound.cause.illness";
            city.killCitizen(c, cause);
            return;
        }

        // Hope and discontent
        boolean sick = c.condition.sick();
        double hopeT = 55 + (home != null ? 5 : -15)
                + (c.bodyTemp >= 36 ? 5 : c.bodyTemp < 34 ? -15 : -5)
                + (c.satiety >= 40 ? 3 : c.satiety < 15 ? -15 : -5)
                + (sick ? -8 : 0)
                + m.hopeTarget + city.society().socialHope + city.society().eventHope - city.society().grief;
        double discT = 15 + (c.fatigue > 70 ? 15 : 0) + (c.bodyTemp < 35 ? 12 : 0) + (c.satiety < 25 ? 15 : 0)
                + (home == null ? 10 : 0) + (sick && !c.treated ? 8 : 0)
                + m.discontentTarget + city.society().socialDiscontent + city.society().eventDiscontent
                + city.society().grief * 0.5;
        c.hope += (clamp(hopeT) - c.hope) * 0.06;
        double dd = (clamp(discT) - c.discontent) * 0.06;
        c.discontent += dd > 0 ? dd * m.discontentRateMult : dd;
        c.hope = clamp(c.hope);
        c.discontent = clamp(c.discontent);
    }

    private static void setCondition(Citizen c, Condition condition) {
        if (c.condition != condition) {
            c.condition = condition;
            c.sickHours = 0;
        }
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(100, v));
    }
}
