// SPDX-License-Identifier: Apache-2.0
package org.hiero.otter.fixtures.turtle;

import com.hedera.hapi.node.state.roster.Roster;
import com.swirlds.base.time.Time;
import com.swirlds.common.test.fixtures.Randotron;
import com.swirlds.platform.test.fixtures.turtle.gossip.SimulatedNetwork;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hiero.consensus.model.node.KeysAndCerts;
import org.hiero.consensus.model.node.NodeId;
import org.hiero.otter.fixtures.InstrumentedNode;

/**
 * An implementation of {@link InstrumentedNode} for the Turtle framework.
 */
public class TurtleInstrumentedNode extends TurtleNode implements InstrumentedNode {

    private final Logger log = LogManager.getLogger(TurtleInstrumentedNode.class);

    public TurtleInstrumentedNode(
            @NonNull final Randotron randotron,
            @NonNull final Time time,
            @NonNull final NodeId nodeId,
            @NonNull final Roster roster,
            @NonNull final KeysAndCerts privateKey,
            @NonNull final SimulatedNetwork network,
            @NonNull final TurtleLogging logging,
            @NonNull final Path rootOutputDirectory) {
        super(randotron, time, nodeId, roster, privateKey, network, logging, rootOutputDirectory);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBranchingProbability(final double probability) {
        log.warn("Setting branching probability is not implemented yet.");
    }
}
