package com.frostbound.core.save;

import com.frostbound.core.util.Tree;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Brings an older save tree up to the current schema, one version at a time. A tree from a newer schema
 * is refused so that an older mod never overwrites data it does not understand.
 */
public final class SaveMigrations {
    public static final int CURRENT = 1;

    private SaveMigrations() {
    }

    public static final class NewerSaveException extends RuntimeException {
        public final int schema;

        public NewerSaveException(int schema) {
            super("Save schema " + schema + " is newer than supported schema " + CURRENT);
            this.schema = schema;
        }
    }

    public static Map<String, Object> migrate(Map<String, Object> tree) {
        int version = Tree.i(tree, "schema", 1);
        if (version > CURRENT) {
            throw new NewerSaveException(version);
        }
        Map<String, Object> t = new LinkedHashMap<>(tree);
        while (version < CURRENT) {
            // Future migrations: switch (version) { case 1 -> t = v1ToV2(t); ... }
            version++;
        }
        t.put("schema", CURRENT);
        return t;
    }
}
