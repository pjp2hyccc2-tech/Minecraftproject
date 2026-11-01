package com.frostbound.core.population;

import com.frostbound.core.util.Rng;
import com.frostbound.core.util.Tree;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** All citizens plus the aggregates the HUD shows. */
public final class Population {
    private final List<Citizen> citizens = new ArrayList<>();
    private final Map<Integer, Citizen> index = new HashMap<>();
    private int nextId = 1;
    private int nextFamily = 1;
    public int deathsTotal;
    public int arrivalsTotal;
    public int deathsToday;
    public int deathsYesterday;
    public int everLived;

    // Aggregates (recomputed every hour)
    public int alive;
    public int present;
    public int children;
    public int sick;
    public int homeless;
    public int employed;
    public int unemployed;
    public int onExpedition;
    public double avgHope = 55;
    public double avgDiscontent = 15;
    public double avgHealth = 100;
    public double avgBodyTemp = 36.8;
    public double avgSatiety = 80;
    public double avgFatigue = 10;
    public final int[] hungerBands = new int[4];
    public final int[] tempBands = new int[5];
    public final EnumMap<Profession, Integer> byProfession = new EnumMap<>(Profession.class);

    public List<Citizen> all() {
        return citizens;
    }

    public Citizen get(int id) {
        return index.get(id);
    }

    public int newFamily() {
        return nextFamily++;
    }

    public Citizen create(Rng rng, Profession profession, int age, int familyId, String family) {
        Citizen c = new Citizen(nextId++);
        int g = rng.nextInt(20);
        c.gender = g < 9 ? Citizen.Gender.MALE : g < 18 ? Citizen.Gender.FEMALE : Citizen.Gender.OTHER;
        c.name = Names.full(Names.given(rng, c.gender), family);
        c.age = age;
        c.profession = profession;
        c.familyId = familyId;
        c.look = rng.nextInt(4);
        c.hope = 50 + rng.nextInt(10);
        c.discontent = 10 + rng.nextInt(10);
        c.satiety = 70 + rng.nextInt(20);
        citizens.add(c);
        index.put(c.id, c);
        everLived++;
        return c;
    }

    /** Thirty founders in families: 27 adults (18 workers, 3 technicians, 2 medics, 2 scouts, 2 engineers) and 3 children. */
    public void createFounders(Rng rng) {
        List<Profession> adults = new ArrayList<>();
        for (int i = 0; i < 18; i++) {
            adults.add(Profession.WORKER);
        }
        for (int i = 0; i < 3; i++) {
            adults.add(Profession.TECHNICIAN);
        }
        adults.add(Profession.MEDIC);
        adults.add(Profession.MEDIC);
        adults.add(Profession.SCOUT);
        adults.add(Profession.SCOUT);
        adults.add(Profession.ENGINEER);
        adults.add(Profession.ENGINEER);
        int childrenLeft = 3;
        int k = 0;
        while (k < adults.size()) {
            int familyId = newFamily();
            String family = Names.family(rng);
            int size = Math.min(adults.size() - k, 1 + rng.nextInt(3));
            for (int j = 0; j < size; j++) {
                create(rng, adults.get(k++), 20 + rng.nextInt(40), familyId, family);
            }
            if (childrenLeft > 0 && size >= 2) {
                create(rng, Profession.WORKER, 6 + rng.nextInt(7), familyId, family);
                childrenLeft--;
            }
        }
        while (childrenLeft-- > 0) {
            create(rng, Profession.WORKER, 6 + rng.nextInt(7), newFamily(), Names.family(rng));
        }
    }

    /** New arrivals (survivors); returns them. */
    public List<Citizen> arrive(Rng rng, int count) {
        List<Citizen> out = new ArrayList<>();
        int familyId = newFamily();
        String family = Names.family(rng);
        Profession[] pool = {Profession.WORKER, Profession.WORKER, Profession.WORKER, Profession.TECHNICIAN,
            Profession.MEDIC, Profession.SCOUT, Profession.ENGINEER};
        for (int i = 0; i < count; i++) {
            if (i > 0 && rng.chance(0.35)) {
                familyId = newFamily();
                family = Names.family(rng);
            }
            boolean child = rng.chance(0.15);
            Citizen c = create(rng, child ? Profession.WORKER : pool[rng.nextInt(pool.length)],
                    child ? 5 + rng.nextInt(8) : 18 + rng.nextInt(45), familyId, family);
            c.satiety = 35 + rng.nextInt(20);
            c.bodyTemp = 35.5;
            c.hope = 45;
            out.add(c);
        }
        arrivalsTotal += count;
        return out;
    }

    /** Removes dead citizens from the list (after the caller has logged them). */
    public int purgeDead() {
        int n = 0;
        Iterator<Citizen> it = citizens.iterator();
        while (it.hasNext()) {
            Citizen c = it.next();
            if (c.status == Citizen.Status.DEAD) {
                it.remove();
                index.remove(c.id);
                n++;
            }
        }
        return n;
    }

    public void aggregate() {
        alive = 0;
        present = 0;
        children = 0;
        sick = 0;
        homeless = 0;
        employed = 0;
        unemployed = 0;
        onExpedition = 0;
        double hope = 0;
        double disc = 0;
        double health = 0;
        double body = 0;
        double sat = 0;
        double fat = 0;
        java.util.Arrays.fill(hungerBands, 0);
        java.util.Arrays.fill(tempBands, 0);
        byProfession.clear();
        for (Citizen c : citizens) {
            if (!c.alive()) {
                continue;
            }
            alive++;
            byProfession.merge(c.profession, 1, Integer::sum);
            if (c.status == Citizen.Status.EXPEDITION) {
                onExpedition++;
                continue;
            }
            present++;
            if (c.child()) {
                children++;
            }
            if (c.condition.sick()) {
                sick++;
            }
            if (c.homeId < 0) {
                homeless++;
            }
            if (c.workId >= 0) {
                employed++;
            } else if (!c.child()) {
                unemployed++;
            }
            hope += c.hope;
            disc += c.discontent;
            health += c.health;
            body += c.bodyTemp;
            sat += c.satiety;
            fat += c.fatigue;
            hungerBands[c.hungerBand()]++;
            tempBands[c.tempBand()]++;
        }
        if (present > 0) {
            avgHope = hope / present;
            avgDiscontent = disc / present;
            avgHealth = health / present;
            avgBodyTemp = body / present;
            avgSatiety = sat / present;
            avgFatigue = fat / present;
        }
    }

    public Map<String, Object> save() {
        Map<String, Object> m = Tree.map();
        List<Object> list = new ArrayList<>();
        for (Citizen c : citizens) {
            if (c.alive()) {
                list.add(c.save());
            }
        }
        m.put("citizens", list);
        m.put("nextId", nextId);
        m.put("nextFamily", nextFamily);
        m.put("deaths", deathsTotal);
        m.put("arrivals", arrivalsTotal);
        m.put("deathsToday", deathsToday);
        m.put("deathsYesterday", deathsYesterday);
        m.put("everLived", everLived);
        return m;
    }

    public void load(Map<String, Object> m) {
        citizens.clear();
        index.clear();
        int maxId = 0;
        for (Map<String, Object> cm : Tree.maps(m, "citizens")) {
            Citizen c = Citizen.load(cm);
            if (c == null || index.containsKey(c.id) || c.status == Citizen.Status.DEAD) {
                continue;
            }
            citizens.add(c);
            index.put(c.id, c);
            maxId = Math.max(maxId, c.id);
        }
        nextId = Math.max(maxId + 1, Tree.i(m, "nextId", 1));
        nextFamily = Math.max(1, Tree.i(m, "nextFamily", 1));
        deathsTotal = Tree.i(m, "deaths", 0, 0, 1_000_000);
        arrivalsTotal = Tree.i(m, "arrivals", 0, 0, 1_000_000);
        deathsToday = Tree.i(m, "deathsToday", 0, 0, 1_000_000);
        deathsYesterday = Tree.i(m, "deathsYesterday", 0, 0, 1_000_000);
        everLived = Math.max(citizens.size(), Tree.i(m, "everLived", citizens.size()));
    }
}
