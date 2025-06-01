// SPDX-License-Identifier: Apache-2.0
package org.hiero.otter.fixtures.result;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import org.hiero.consensus.model.hashgraph.ConsensusRound;
import org.hiero.consensus.model.node.NodeId;
import org.hiero.otter.fixtures.Node;

/**
 * Interface that provides access to the consensus results of a group of nodes that were created during a test.
 *
 * <p>The provided data is a snapshot of the state at the moment when the result was requested.
 */
@SuppressWarnings("unused")
public interface MultipleNodeConsensusResults extends OtterResult {

    /**
     * Returns an immutable list of {@link SingleNodeConsensusResult} for all nodes
     *
     * @return the list of results
     */
    @NonNull
    List<SingleNodeConsensusResult> results();

    /**
     * Subscribes to {@link ConsensusRound}s created by the nodes.
     *
     * <p>The subscriber will be notified every time one (or more) rounds reach consensus.
     *
     * @param subscriber the subscriber that will receive the rounds
     */
    void subscribe(@NonNull ConsensusRoundSubscriber subscriber);

    /**
     * Excludes the consensus results of a specific node from the results.
     *
     * @param nodeId the {@link NodeId} of the node which consensus results are to be excluded
     * @return a new instance of {@link MultipleNodeConsensusResults} with the specified node's results excluded
     */
    @NonNull
    MultipleNodeConsensusResults suppressingNode(@NonNull NodeId nodeId);

    /**
     * Excludes the consensus results of a specific node from the results.
     *
     * @param node the {@link Node} which consensus results are to be excluded
     * @return a new instance of {@link MultipleNodeConsensusResults} with the specified node's results excluded
     */
    @NonNull
    default MultipleNodeConsensusResults suppressingNode(@NonNull final Node node) {
        return suppressingNode(node.getSelfId());
    }
}
