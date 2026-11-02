package com.clockworkdiver.clock;

import com.clockworkdiver.customer.CustomerProfile;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/** Creates the clocks customers bring in. */
public final class CommissionGenerator {
    private CommissionGenerator() {}

    /** The tutorial job: old Gregory's pocket watch with a rusted barrel gear and a chipped escape wheel. */
    public static ClockData tutorial(long id) {
        ClockData data = new ClockData();
        data.type = ClockType.POCKET_WATCH;
        data.profile = CustomerProfile.GREGORY;
        data.commissionId = id;
        data.tutorial = true;
        data.reward = 12;
        data.defects.add(new Defect(0, DefectType.RUST, "barrel_gear", -0.42f, 0.28f));
        data.defects.add(new Defect(1, DefectType.TEETH, "escape_wheel", 0.38f, -0.30f));
        return data;
    }

    public static ClockData random(RandomSource random, int reputation, long id) {
        List<ClockType> unlocked = new ArrayList<>();
        for (ClockType t : ClockType.values()) {
            if (reputation >= t.minReputation) unlocked.add(t);
        }
        // Prefer the newest unlocked kinds a little so progress feels rewarding.
        ClockType type = unlocked.get(random.nextInt(unlocked.size()));
        if (unlocked.size() > 2 && random.nextFloat() < 0.35f) type = unlocked.get(unlocked.size() - 1 - random.nextInt(2));

        List<CustomerProfile> fans = new ArrayList<>();
        for (CustomerProfile p : CustomerProfile.values()) {
            if (p != CustomerProfile.GREGORY && p.favourites.contains(type)) fans.add(p);
        }
        CustomerProfile profile = fans.isEmpty() ? CustomerProfile.byIndex(1 + random.nextInt(CustomerProfile.values().length - 1))
                : fans.get(random.nextInt(fans.size()));

        ClockData data = new ClockData();
        data.type = type;
        data.profile = profile;
        data.commissionId = id;
        data.tutorial = false;

        int count = type.minDefects + random.nextInt(type.maxDefects - type.minDefects + 1);
        List<float[]> anchors = new ArrayList<>(List.of(type.anchors));
        List<DefectType> pool = new ArrayList<>(type.defectPool);
        boolean nest = false;
        for (int i = 0; i < count && !anchors.isEmpty(); i++) {
            DefectType kind;
            if (type == ClockType.ORRERY && i == count - 1 && !nest) {
                kind = DefectType.NEST;
            } else {
                kind = pool.get(random.nextInt(pool.size()));
                if (kind == DefectType.NEST && (nest || i < count - 1 || reputation < 4)) {
                    kind = DefectType.RUST;
                }
            }
            if (kind == DefectType.NEST) nest = true;
            float[] a = anchors.remove(random.nextInt(anchors.size()));
            String part = kind.parts[random.nextInt(kind.parts.length)];
            data.defects.add(new Defect(i, kind, part, a[0], a[1]));
        }
        data.reward = type.baseReward + data.defects.size() * 3 + random.nextInt(5) + Math.min(10, reputation / 2);
        return data;
    }
}
