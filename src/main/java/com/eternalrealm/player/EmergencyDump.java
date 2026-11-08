package com.eternalrealm.player;

import com.eternalrealm.character.CharacterRecord;

/**
 * Last-resort preservation of character data that could not be saved through the normal repository during shutdown
 * (e.g. a separate emergency directory). Implementations must never throw.
 */
@FunctionalInterface
public interface EmergencyDump {
    void preserve(CharacterRecord record);
}
