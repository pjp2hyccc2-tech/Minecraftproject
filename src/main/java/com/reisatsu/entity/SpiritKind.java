package com.reisatsu.entity;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * The six dead of HIDAMARI (docs/DESIGN.md §1, §5). The order is the order of the album slots and of the exorcism.
 * {@code eyeAlpha} is how much of the spirit the naked eye sees (the finder always shows them fully).
 */
public enum SpiritKind {
    VISITOR("visitor", 0.0f),
    WAITRESS("waitress", 0.3f),
    CHEF("chef", 0.75f),
    CHILD("child", 0.0f),
    MIRROR_WOMAN("mirror_woman", 0.25f),
    MANAGER("manager", 0.0f);

    public static final SpiritKind[] ALL = values();
    /** The five of the investigation (closing time comes when all five are in photos). */
    public static final SpiritKind[] FIVE = {VISITOR, WAITRESS, CHEF, CHILD, MIRROR_WOMAN};

    public final String id;
    public final float eyeAlpha;

    SpiritKind(String id, float eyeAlpha) {
        this.id = id;
        this.eyeAlpha = eyeAlpha;
    }

    public int bit() {
        return 1 << ordinal();
    }

    /** Translation key of the spirit's role ("来店者"). */
    public String roleKey() {
        return "entity.reisatsu." + id;
    }

    /** Translation key of the true name written on the photo margin ("三浦 健司 (24)"). */
    public String nameKey() {
        return "spirit.reisatsu." + id + ".name";
    }

    @Nullable
    public static SpiritKind byId(String id) {
        if (id == null) return null;
        String k = id.toLowerCase(Locale.ROOT);
        for (SpiritKind s : ALL) if (s.id.equals(k)) return s;
        return null;
    }

    @Nullable
    public static SpiritKind byIndex(int i) {
        return i >= 0 && i < ALL.length ? ALL[i] : null;
    }

    public static int mask(Iterable<SpiritKind> kinds) {
        int m = 0;
        for (SpiritKind k : kinds) m |= k.bit();
        return m;
    }
}
