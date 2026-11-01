import com.frostbound.core.Difficulty;
import com.frostbound.core.city.BuildingCategory;
import com.frostbound.core.city.BuildingType;
import com.frostbound.core.city.BuildingTypes;
import com.frostbound.core.city.Ending;
import com.frostbound.core.city.Tutorial;
import com.frostbound.core.events.EventDef;
import com.frostbound.core.events.Events;
import com.frostbound.core.expedition.SiteType;
import com.frostbound.core.population.Citizen;
import com.frostbound.core.population.Profession;
import com.frostbound.core.research.Tech;
import com.frostbound.core.research.Techs;
import com.frostbound.core.resources.ResourceType;
import com.frostbound.core.society.Law;
import com.frostbound.core.society.Laws;
import com.frostbound.core.weather.WeatherState;
import java.util.TreeSet;

/**
 * Prints every language key that the core registries generate (one per line). Literal keys in the
 * sources are collected separately by tools/collect_keys.py.
 *
 * <p>javac -cp core-classes -d /tmp/kd tools/KeyDump.java && java -cp core-classes:/tmp/kd KeyDump
 */
public final class KeyDump {
    public static void main(String[] args) {
        TreeSet<String> keys = new TreeSet<>();
        for (BuildingType t : BuildingTypes.all()) {
            keys.add(t.key());
            keys.add(t.key() + ".desc");
        }
        for (ResourceType t : ResourceType.values()) {
            keys.add(t.key());
        }
        for (WeatherState w : WeatherState.values()) {
            keys.add(w.key());
        }
        for (BuildingCategory c : BuildingCategory.values()) {
            keys.add(c.key());
        }
        for (Profession p : Profession.values()) {
            keys.add(p.key());
        }
        for (Citizen.Condition c : Citizen.Condition.values()) {
            keys.add(c.key());
        }
        for (Difficulty d : Difficulty.values()) {
            keys.add(d.key());
            keys.add(d.key() + ".desc");
        }
        for (Law l : Laws.all()) {
            keys.add(l.key());
            keys.add(l.key() + ".desc");
        }
        for (Tech t : Techs.all()) {
            keys.add(t.key());
            keys.add(t.descKey());
        }
        for (String cat : Techs.CATEGORIES) {
            keys.add("frostbound.research.category." + cat);
        }
        for (SiteType s : SiteType.values()) {
            keys.add(s.key());
            keys.add(s.key() + ".desc");
        }
        for (EventDef e : Events.all()) {
            keys.add(e.titleKey());
            keys.add(e.textKey());
            for (EventDef.Choice c : e.choices) {
                keys.add(c.key());
            }
        }
        for (Ending e : Ending.values()) {
            keys.add(e.titleKey());
            keys.add(e.textKey());
        }
        for (Tutorial.Step s : Tutorial.Step.values()) {
            keys.add(s.key());
        }
        for (String k : keys) {
            System.out.println(k);
        }
    }
}
