package com.eternalrealm.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Wire ids are part of protocol version 2 and must never change. */
public class WireIdsTest {
    @Test
    public void requestIdsAreStable() {
        assertEquals(0, CombatRequest.LIGHT.networkId());
        assertEquals(1, CombatRequest.HEAVY_START.networkId());
        assertEquals(2, CombatRequest.HEAVY_RELEASE.networkId());
        assertEquals(3, CombatRequest.DODGE.networkId());
        assertEquals(4, CombatRequest.SKILL.networkId());
        for (CombatRequest request : CombatRequest.values()) {
            assertEquals(request, CombatRequest.byNetworkId(request.networkId()).orElseThrow());
        }
        assertFalse(CombatRequest.byNetworkId(-1).isPresent());
        assertFalse(CombatRequest.byNetworkId(5).isPresent());
    }

    @Test
    public void outcomeIdsAreStable() {
        assertEquals(0, RequestOutcome.ACCEPTED.networkId());
        assertEquals(1, RequestOutcome.BUFFERED.networkId());
        assertEquals(2, RequestOutcome.IGNORED.networkId());
        assertEquals(9, RequestOutcome.REJECTED_NOT_ALLOWED.networkId());
        for (RequestOutcome outcome : RequestOutcome.values()) {
            assertEquals(outcome, RequestOutcome.byNetworkId(outcome.networkId()).orElseThrow());
            assertEquals(outcome.name().startsWith("REJECTED"), outcome.isRejected());
        }
        assertTrue(RequestOutcome.REJECTED_STAMINA.messageKey().isPresent());
        assertFalse(RequestOutcome.byNetworkId(10).isPresent());
    }
}
