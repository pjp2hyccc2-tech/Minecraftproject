package com.eternalrealm.combat;

import java.util.Optional;

/**
 * Combat intents a client can send. {@link #networkId()} is the stable wire id: never reorder or reuse it.
 */
public enum CombatRequest {
    LIGHT(0, -1),
    HEAVY_START(1, -1),
    HEAVY_RELEASE(2, -1),
    DODGE(3, -1),
    /** The weapon's own skill (skill slot 0). */
    SKILL(4, 0),
    /** Job skills in the four job skill slots (skill slots 1..4; slot 4 is the ultimate). */
    JOB_SKILL_1(5, 1),
    JOB_SKILL_2(6, 2),
    JOB_SKILL_3(7, 3),
    JOB_SKILL_4(8, 4);

    private static final CombatRequest[] BY_NETWORK_ID = new CombatRequest[values().length];

    static {
        for (CombatRequest request : values()) {
            if (BY_NETWORK_ID[request.networkId] != null) {
                throw new ExceptionInInitializerError("Duplicate combat request id " + request.networkId);
            }
            BY_NETWORK_ID[request.networkId] = request;
        }
    }

    private final int networkId;
    private final int skillSlot;

    CombatRequest(int networkId, int skillSlot) {
        this.networkId = networkId;
        this.skillSlot = skillSlot;
    }

    public int networkId() {
        return networkId;
    }

    /** Skill slot ({@code 0} weapon skill, {@code 1..4} job skills) or {@code -1} for non-skill requests. */
    public int skillSlot() {
        return skillSlot;
    }

    public boolean isSkill() {
        return skillSlot >= 0;
    }

    /** The request that uses job skill slot {@code slot} (1..4). */
    public static CombatRequest jobSkill(int slot) {
        return switch (slot) {
            case 1 -> JOB_SKILL_1;
            case 2 -> JOB_SKILL_2;
            case 3 -> JOB_SKILL_3;
            case 4 -> JOB_SKILL_4;
            default -> throw new IllegalArgumentException("job skill slot out of range [1, 4]: " + slot);
        };
    }

    public static Optional<CombatRequest> byNetworkId(int networkId) {
        if (networkId < 0 || networkId >= BY_NETWORK_ID.length) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_NETWORK_ID[networkId]);
    }
}
