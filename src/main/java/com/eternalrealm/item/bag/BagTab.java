package com.eternalrealm.item.bag;

import com.eternalrealm.core.ModInfo;

import java.util.Locale;

/** The tabs of the bag screen: 全て / 装備 / 消耗品 / 素材 / その他. */
public enum BagTab {
    ALL(null),
    EQUIPMENT(BagCategory.EQUIPMENT),
    CONSUMABLE(BagCategory.CONSUMABLE),
    MATERIAL(BagCategory.MATERIAL),
    OTHER(BagCategory.OTHER);

    private final BagCategory category;

    BagTab(BagCategory category) {
        this.category = category;
    }

    public boolean accepts(BagCategory of) {
        return category == null || category == of;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "screen." + ModInfo.MOD_ID + ".bag.tab." + id();
    }
}
