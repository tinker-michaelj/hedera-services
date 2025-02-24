// SPDX-License-Identifier: Apache-2.0
package com.swirlds.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.state.roster.Roster;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.common.test.fixtures.Randotron;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.test.fixtures.event.TestingEventBuilder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ConsensusRoundTests {

    @Test
    void testConstructor() {
        final Randotron r = Randotron.create();
        final ConsensusSnapshot snapshot = mock(ConsensusSnapshot.class);

        when(snapshot.round()).thenReturn(1L);

        final List<PlatformEvent> events = List.of(
                new TestingEventBuilder(r).build(),
                new TestingEventBuilder(r).build(),
                new TestingEventBuilder(r).build());

        final ConsensusRound round =
                new ConsensusRound(mock(Roster.class), events, mock(EventWindow.class), snapshot, false, Instant.now());

        assertEquals(events, round.getConsensusEvents(), "consensus event list does not match the provided list.");
        assertEquals(events.size(), round.getNumEvents(), "numEvents does not match the events provided.");
        assertEquals(1, round.getRoundNum(), "roundNum does not match the events provided.");
    }

    @Test
    void testNumApplicationTransactions() {
        final Random r = RandomUtils.initRandom(null);

        int numActualTransactions = 0;
        final List<PlatformEvent> events = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            final int numTransactions = r.nextInt(200);
            numActualTransactions += numTransactions;
            final PlatformEvent event = new TestingEventBuilder(r)
                    .setAppTransactionCount(numTransactions)
                    .setSystemTransactionCount(0)
                    .build();
            events.add(event);
        }

        final ConsensusRound round = new ConsensusRound(
                mock(Roster.class),
                events,
                mock(EventWindow.class),
                mock(ConsensusSnapshot.class),
                false,
                Instant.now());

        assertEquals(
                numActualTransactions, round.getNumAppTransactions(), "Incorrect number of application transactions.");
    }
}
