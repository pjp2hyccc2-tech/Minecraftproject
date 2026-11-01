package com.frostbound.core.expedition;

import com.frostbound.core.city.City;
import com.frostbound.core.city.Notice;
import com.frostbound.core.population.Citizen;
import com.frostbound.core.population.Profession;
import com.frostbound.core.research.Tech;
import com.frostbound.core.research.Techs;
import com.frostbound.core.resources.Cost;
import com.frostbound.core.resources.ResourceLedger;
import com.frostbound.core.resources.ResourceType;
import com.frostbound.core.util.Msg;
import com.frostbound.core.util.Rng;
import com.frostbound.core.util.Tree;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Sites beyond the city and the teams sent to them. A team needs at least two healthy adults (scouts
 * first, then people without a job) and food and coal for the round trip.
 */
public final class ExpeditionSystem {
    public static final int MIN_TEAM = 2;
    public static final int MAX_TEAM = 6;
    public static final int FOOD_PER_MEMBER_DAY = 3;
    public static final int COAL_PER_MEMBER_DAY = 2;

    private final List<Site> sites = new ArrayList<>();
    private final List<Expedition> active = new ArrayList<>();
    private int nextId = 1;

    public void generate(Rng rng) {
        sites.clear();
        SiteType[] near = {SiteType.ABANDONED_TOWN, SiteType.RESOURCE_SITE, SiteType.OLD_FACTORY, SiteType.SURVIVOR_CAMP,
            SiteType.ABANDONED_MINE, SiteType.ABANDONED_TOWN, SiteType.RESOURCE_SITE, SiteType.GLACIER_CAVE};
        SiteType[] far = {SiteType.RESEARCH_FACILITY, SiteType.GLACIER_CAVE, SiteType.OLD_FACTORY, SiteType.SURVIVOR_CAMP};
        int id = 1;
        for (SiteType t : near) {
            sites.add(makeSite(rng, id++, t, 1.0 + rng.nextDouble() * 2.0, false));
        }
        for (SiteType t : far) {
            sites.add(makeSite(rng, id++, t, 3.5 + rng.nextDouble() * 2.5, true));
        }
    }

    private static Site makeSite(Rng rng, int id, SiteType type, double distance, boolean far) {
        double angle = rng.nextDouble() * Math.PI * 2;
        double danger = Math.min(0.9, type.danger + (far ? 0.1 : 0) + rng.nextDouble() * 0.05);
        return new Site(id, type, Math.round(distance * 10) / 10.0, danger, far,
                (int) Math.round(Math.cos(angle) * distance * 30), (int) Math.round(Math.sin(angle) * distance * 30));
    }

    public List<Site> sites() {
        return sites;
    }

    public List<Expedition> active() {
        return active;
    }

    public Site site(int id) {
        for (Site s : sites) {
            if (s.id == id) {
                return s;
            }
        }
        return null;
    }

    public static Cost supplies(Site s, int members) {
        int days = (int) Math.ceil(s.distance * 2);
        return Cost.of(ResourceType.FOOD, days * members * FOOD_PER_MEMBER_DAY, ResourceType.COAL, days * members * COAL_PER_MEMBER_DAY);
    }

    /** Null if the expedition can leave, otherwise a language key for the reason. */
    public String check(City city, Site s, int members) {
        if (s == null) {
            return "frostbound.expedition.reason.unknown";
        }
        if (s.far && !city.modifiers().longRange) {
            return "frostbound.expedition.reason.far";
        }
        if (s.depleted) {
            return "frostbound.expedition.reason.depleted";
        }
        if (members < MIN_TEAM || members > MAX_TEAM) {
            return "frostbound.expedition.reason.size";
        }
        for (Expedition e : active) {
            if (e.siteId == s.id) {
                return "frostbound.expedition.reason.busy";
            }
        }
        if (candidates(city).size() < members) {
            return "frostbound.expedition.reason.people";
        }
        if (!city.ledger().has(supplies(s, members))) {
            return "frostbound.expedition.reason.supplies";
        }
        return null;
    }

    private static List<Citizen> candidates(City city) {
        List<Citizen> scouts = new ArrayList<>();
        List<Citizen> idle = new ArrayList<>();
        for (Citizen c : city.population().all()) {
            if (!c.present() || c.child() || c.condition.sick() || c.health < 60) {
                continue;
            }
            if (c.profession == Profession.SCOUT) {
                scouts.add(c);
            } else if (c.workId < 0) {
                idle.add(c);
            }
        }
        scouts.addAll(idle);
        return scouts;
    }

    public Expedition launch(City city, Site s, int members) {
        List<Citizen> team = candidates(city).subList(0, members);
        city.ledger().spend(supplies(s, members));
        Expedition e = new Expedition(nextId++, s.id);
        for (Citizen c : team) {
            c.status = Citizen.Status.EXPEDITION;
            c.workId = -1;
            e.members.add(c.id);
        }
        active.add(e);
        return e;
    }

    private static double scoutShare(City city, Expedition e) {
        int scouts = 0;
        for (int id : e.members) {
            Citizen c = city.population().get(id);
            if (c != null && c.profession == Profession.SCOUT) {
                scouts++;
            }
        }
        return e.members.isEmpty() ? 0 : scouts / (double) e.members.size();
    }

    public void hourly(City city) {
        Iterator<Expedition> it = active.iterator();
        while (it.hasNext()) {
            Expedition e = it.next();
            Site s = site(e.siteId);
            if (s == null || e.members.isEmpty()) {
                it.remove();
                continue;
            }
            double speed = city.modifiers().expeditionSpeedMult * city.weather().current().travel
                    * (scoutShare(city, e) >= 0.5 ? 1.2 : 1.0);
            e.progress += speed / 24.0;
            if (e.progress < s.distance) {
                continue;
            }
            if (!e.returning) {
                arrive(city, e, s);
                e.returning = true;
                e.progress = 0;
                if (e.members.isEmpty()) {
                    it.remove();
                }
            } else {
                returnHome(city, e, s);
                it.remove();
            }
        }
    }

    private void arrive(City city, Expedition e, Site s) {
        Rng rng = city.rng();
        double risk = s.danger * city.modifiers().expeditionRiskMult * city.difficulty().eventSeverity
                * (scoutShare(city, e) >= 0.5 ? 0.6 : 1.0) * (city.weather().current().stormy() ? 1.5 : 1.0)
                * (1.0 + 0.15 * s.visits);
        if (rng.chance(Math.min(0.85, risk))) {
            int lost = 1 + rng.nextInt(Math.max(1, e.members.size() - 1));
            for (int i = 0; i < lost && !e.members.isEmpty(); i++) {
                int idx = rng.nextInt(e.members.size());
                Citizen c = city.population().get(e.members.remove(idx));
                if (c != null) {
                    city.killCitizen(c, "frostbound.cause.lost");
                }
                e.lost++;
            }
            city.history().add(city.clock().step(), "expedition",
                    Msg.of("frostbound.msg.expedition_losses", Msg.tr(s.nameKey()), e.lost));
            city.notify(Notice.Kind.WARNING, Msg.of("frostbound.msg.expedition_losses", Msg.tr(s.nameKey()), e.lost), "alert");
        }
        if (e.members.isEmpty()) {
            return;
        }
        double scale = (e.members.size() / 4.0) / (1.0 + 0.5 * s.visits);
        e.loot = s.type.rollLoot(rng, scale);
        if (s.type == SiteType.RESEARCH_FACILITY) {
            e.researchPoints = rng.range(60, 120) / (1.0 + s.visits);
            if (s.visits == 0) {
                List<Tech> options = new ArrayList<>();
                for (Tech t : Techs.all()) {
                    if (t.tier <= 2 && !city.research().isCompleted(t.id)) {
                        options.add(t);
                    }
                }
                if (!options.isEmpty() && rng.chance(0.5)) {
                    e.findTech = options.get(rng.nextInt(options.size())).id;
                }
            }
        }
        if (s.type == SiteType.SURVIVOR_CAMP) {
            e.survivors = s.visits == 0 ? rng.range(5, 12) : rng.range(0, 3);
        }
        s.visits++;
        if (s.visits >= 3) {
            s.depleted = true;
        }
        city.history().add(city.clock().step(), "expedition", Msg.of("frostbound.msg.expedition_arrived", Msg.tr(s.nameKey())));
    }

    private void returnHome(City city, Expedition e, Site s) {
        for (int id : e.members) {
            Citizen c = city.population().get(id);
            if (c != null && c.status == Citizen.Status.EXPEDITION) {
                c.status = Citizen.Status.ACTIVE;
                c.fatigue = Math.min(100, c.fatigue + 30);
            }
        }
        e.loot.asMap().forEach((t, v) -> city.ledger().add(t, v * ResourceLedger.MILLI));
        if (e.researchPoints > 0) {
            city.addResearchPoints(e.researchPoints);
        }
        city.history().add(city.clock().step(), "expedition", Msg.of("frostbound.msg.expedition_returned", Msg.tr(s.nameKey()),
                describe(e.loot)));
        city.notify(Notice.Kind.GOOD, Msg.of("frostbound.msg.expedition_returned", Msg.tr(s.nameKey()), describe(e.loot)), "ui_open");
        if (e.survivors > 0) {
            city.events().trigger(city, "survivors_found", List.of(String.valueOf(e.survivors)));
        }
        if (e.findTech != null) {
            city.events().trigger(city, "unknown_facility", List.of(e.findTech));
        }
    }

    public static String describe(Cost loot) {
        StringBuilder sb = new StringBuilder();
        loot.asMap().forEach((t, v) -> {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(t.id()).append(" +").append(v);
        });
        return sb.length() == 0 ? "-" : sb.toString();
    }

    public Map<String, Object> save() {
        Map<String, Object> m = Tree.map();
        List<Object> s = new ArrayList<>();
        for (Site site : sites) {
            s.add(site.save());
        }
        List<Object> a = new ArrayList<>();
        for (Expedition e : active) {
            a.add(e.save());
        }
        m.put("sites", s);
        m.put("active", a);
        m.put("nextId", nextId);
        return m;
    }

    public void load(Map<String, Object> m, Rng rng) {
        sites.clear();
        active.clear();
        for (Map<String, Object> sm : Tree.maps(m, "sites")) {
            Site s = Site.load(sm);
            if (s != null) {
                sites.add(s);
            }
        }
        if (sites.isEmpty()) {
            generate(rng);
        }
        for (Map<String, Object> em : Tree.maps(m, "active")) {
            Expedition e = Expedition.load(em);
            if (site(e.siteId) != null) {
                active.add(e);
            }
        }
        nextId = Math.max(1, Tree.i(m, "nextId", 1));
    }
}
