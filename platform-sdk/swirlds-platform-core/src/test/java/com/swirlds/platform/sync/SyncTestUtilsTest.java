// SPDX-License-Identifier: Apache-2.0
package com.swirlds.platform.sync;

import static org.hiero.consensus.model.event.AncientMode.BIRTH_ROUND_THRESHOLD;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.platform.gossip.shadowgraph.ShadowEvent;
import com.swirlds.platform.gossip.shadowgraph.SyncUtils;
import com.swirlds.platform.test.fixtures.sync.EventFactory;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import org.hiero.base.utility.test.fixtures.RandomUtils;
import org.hiero.consensus.model.hashgraph.EventWindow;
import org.hiero.consensus.model.test.fixtures.hashgraph.EventWindowBuilder;
import org.junit.jupiter.api.Test;

class SyncTestUtilsTest {

    @Test
    void testUnknownNonAncient() {
        // the following graph is used for this test
        //  BR
        //   4  e7
        //      | \
        //   3  e5 e6
        //		|  |
        //   2  e3 e4
        //     	 \ | \
        //   1     e1 e2

        final Random random = RandomUtils.getRandomPrintSeed();

        final ShadowEvent e1 = EventFactory.makeShadow(random);
        final ShadowEvent e2 = EventFactory.makeShadow(random);
        final ShadowEvent e3 = EventFactory.makeShadow(random, null, e1);
        final ShadowEvent e4 = EventFactory.makeShadow(random, e1, e2);
        final ShadowEvent e5 = EventFactory.makeShadow(random, e3);
        final ShadowEvent e6 = EventFactory.makeShadow(random, e4);
        final ShadowEvent e7 = EventFactory.makeShadow(random, e5, e6);

        final Set<ShadowEvent> knownSet = new HashSet<>();
        knownSet.add(e5);
        knownSet.add(e3);
        knownSet.add(e1);

        final EventWindow eventWindow =
                EventWindowBuilder.builder().setAncientThreshold(2).build();

        final Predicate<ShadowEvent> unknownNonAncient =
                SyncUtils.unknownNonAncient(knownSet, eventWindow, eventWindow, BIRTH_ROUND_THRESHOLD);

        assertFalse(unknownNonAncient.test(e1), "e1 is both ancient and known, should be false");
        assertFalse(unknownNonAncient.test(e2), "e2 is ancient, should be false");
        assertFalse(unknownNonAncient.test(e3), "e3 is known, should be false");
        assertTrue(unknownNonAncient.test(e4), "e4 is unknown and non-ancient, should be true");
        assertFalse(unknownNonAncient.test(e5), "e5 is known, should be false");
        assertTrue(unknownNonAncient.test(e6), "e6 is unknown and non-ancient, should be true");
        assertTrue(unknownNonAncient.test(e7), "e7 is unknown and non-ancient, should be true");
    }
}
