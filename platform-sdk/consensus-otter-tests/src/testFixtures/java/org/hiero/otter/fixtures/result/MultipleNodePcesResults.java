// SPDX-License-Identifier: Apache-2.0
package org.hiero.otter.fixtures.result;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import org.hiero.otter.fixtures.Node;

/**
 * Interface that provides access to the results related to PCES files.
 *
 * <p>The provided data is a snapshot of the state at the moment when the result was requested.
 */
@SuppressWarnings("unused")
public interface MultipleNodePcesResults {

    /**
     * Returns the list of {@link SingleNodePcesResult} for all nodes
     *
     * @return the list of results for all nodes
     */
    @NonNull
    List<SingleNodePcesResult> pcesResults();

    /**
     * Excludes the results of a specific node from the current results.
     *
     * @param node the node which result is to be excluded
     * @return a new instance of {@link MultipleNodePcesResults} with the specified node's result excluded
     */
    @NonNull
    MultipleNodePcesResults ignoring(@NonNull Node node);
}
