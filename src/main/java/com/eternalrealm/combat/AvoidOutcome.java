package com.eternalrealm.combat;

/** Whether an incoming attack is negated before any damage is computed. */
public enum AvoidOutcome {
    NONE,
    /** Inside the dodge invulnerability. */
    DODGED,
    /** Guard started within the parry window. */
    PARRIED
}
