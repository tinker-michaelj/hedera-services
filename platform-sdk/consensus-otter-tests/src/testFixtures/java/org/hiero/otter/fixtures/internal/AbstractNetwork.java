// SPDX-License-Identifier: Apache-2.0
package org.hiero.otter.fixtures.internal;

import static org.assertj.core.api.Assertions.fail;
import static org.hiero.consensus.model.status.PlatformStatus.ACTIVE;
import static org.hiero.consensus.model.status.PlatformStatus.FREEZE_COMPLETE;

import com.hedera.hapi.node.base.SemanticVersion;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hiero.consensus.model.status.PlatformStatus;
import org.hiero.otter.fixtures.AsyncNetworkActions;
import org.hiero.otter.fixtures.Network;
import org.hiero.otter.fixtures.Node;
import org.hiero.otter.fixtures.TimeManager;
import org.hiero.otter.fixtures.TransactionGenerator;
import org.hiero.otter.fixtures.internal.result.MultipleNodeConsensusResultsImpl;
import org.hiero.otter.fixtures.internal.result.MultipleNodeLogResultsImpl;
import org.hiero.otter.fixtures.internal.result.MultipleNodePcesResultsImpl;
import org.hiero.otter.fixtures.internal.result.MultipleNodeStatusProgressionImpl;
import org.hiero.otter.fixtures.result.MultipleNodeConsensusResults;
import org.hiero.otter.fixtures.result.MultipleNodeLogResults;
import org.hiero.otter.fixtures.result.MultipleNodePcesResults;
import org.hiero.otter.fixtures.result.MultipleNodeStatusProgression;
import org.hiero.otter.fixtures.result.SingleNodeConsensusResult;
import org.hiero.otter.fixtures.result.SingleNodeLogResult;
import org.hiero.otter.fixtures.result.SingleNodePcesResult;
import org.hiero.otter.fixtures.result.SingleNodeStatusProgression;

/**
 * An abstract base class for a network implementation that provides common functionality shared by the different
 * environments.
 */
public abstract class AbstractNetwork implements Network {

    private static final Logger log = LogManager.getLogger();

    private static final Duration FREEZE_DELAY = Duration.ofSeconds(10);

    /**
     * The state of the network.
     */
    protected enum State {
        INIT,
        RUNNING,
        SHUTDOWN
    }

    protected State state = State.INIT;

    private final AsyncNetworkActions defaultStartAction;
    private final AsyncNetworkActions defaultFreezeAction;
    private final AsyncNetworkActions defaultShutdownAction;

    /**
     * Constructs an instance of {@link AbstractNetwork} with the specified default timeouts for start, freeze, and
     * shutdown actions.
     *
     * @param defaultStartTimeout    the default timeout for starting the network
     * @param defaultFreezeTimeout   the default timeout for freezing the network
     * @param defaultShutdownTimeout the default timeout for shutting down the network
     */
    protected AbstractNetwork(
            @NonNull final Duration defaultStartTimeout,
            @NonNull final Duration defaultFreezeTimeout,
            @NonNull final Duration defaultShutdownTimeout) {
        this.defaultStartAction = withTimeout(defaultStartTimeout);
        this.defaultFreezeAction = withTimeout(defaultFreezeTimeout);
        this.defaultShutdownAction = withTimeout(defaultShutdownTimeout);
    }

    /**
     * Returns the time manager for this network.
     *
     * @return the {@link TimeManager} instance
     */
    @NonNull
    protected abstract TimeManager timeManager();

    /**
     * The factory for creating freeze transactions.
     *
     * @param freezeTime the freeze time for the transaction
     * @return the byte array representing the freeze transaction
     */
    @NonNull
    protected abstract byte[] createFreezeTransaction(@NonNull final Instant freezeTime);

    /**
     * The {@link TransactionGenerator} for this network.
     *
     * @return the {@link TransactionGenerator} instance
     */
    @NonNull
    protected abstract TransactionGenerator transactionGenerator();

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public AsyncNetworkActions withTimeout(@NonNull final Duration timeout) {
        return new AsyncNetworkActionsImpl(timeout);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() throws InterruptedException {
        defaultStartAction.start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void freeze() throws InterruptedException {
        defaultFreezeAction.freeze();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void shutdown() throws InterruptedException {
        defaultShutdownAction.shutdown();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setVersion(@NonNull final SemanticVersion version) {
        for (final Node node : getNodes()) {
            node.setVersion(version);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void bumpConfigVersion() {
        for (final Node node : getNodes()) {
            node.bumpConfigVersion();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public MultipleNodeConsensusResults getConsensusResults() {
        final List<SingleNodeConsensusResult> results =
                getNodes().stream().map(Node::getConsensusResult).toList();
        return new MultipleNodeConsensusResultsImpl(results);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public MultipleNodeLogResults getLogResults() {
        final List<SingleNodeLogResult> results =
                getNodes().stream().map(Node::getLogResult).toList();

        return new MultipleNodeLogResultsImpl(results);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public MultipleNodeStatusProgression getStatusProgression() {
        final List<SingleNodeStatusProgression> statusProgressions =
                getNodes().stream().map(Node::getStatusProgression).toList();
        return new MultipleNodeStatusProgressionImpl(statusProgressions);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public MultipleNodePcesResults getPcesResults() {
        final List<SingleNodePcesResult> results =
                getNodes().stream().map(Node::getPcesResult).toList();
        return new MultipleNodePcesResultsImpl(results);
    }

    /**
     * Creates a {@link BooleanSupplier} that returns {@code true} if all nodes are in the given {@link PlatformStatus}.
     *
     * @param status the status to check
     * @return the {@link BooleanSupplier}
     */
    protected BooleanSupplier allNodesInStatus(@NonNull final PlatformStatus status) {
        return () -> getNodes().stream().allMatch(node -> node.platformStatus() == status);
    }

    /**
     * Throws an {@link IllegalStateException} if the network is in the given state.
     *
     * @param expected the state that will cause the exception to be thrown
     * @param message  the message to include in the exception
     * @throws IllegalStateException if the network is in the expected state
     */
    protected void throwIfInState(@NonNull final State expected, @NonNull final String message) {
        if (state == expected) {
            throw new IllegalStateException(message);
        }
    }

    /**
     * Default implementation of {@link AsyncNetworkActions}
     */
    protected class AsyncNetworkActionsImpl implements AsyncNetworkActions {

        private final Duration timeout;

        /**
         * Constructs an instance of {@link AsyncNetworkActionsImpl} with the specified timeout.
         *
         * @param timeout the duration to wait for actions to complete
         */
        public AsyncNetworkActionsImpl(@NonNull final Duration timeout) {
            this.timeout = timeout;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void start() throws InterruptedException {
            throwIfInState(State.RUNNING, "Network is already running.");

            log.info("Starting network...");
            state = State.RUNNING;
            for (final Node node : getNodes()) {
                node.start();
            }

            transactionGenerator().start();

            log.debug("Waiting for nodes to become active...");
            if (!timeManager().waitForCondition(allNodesInStatus(ACTIVE), timeout)) {
                fail("Timeout while waiting for nodes to become active.");
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void freeze() {
            throwIfInState(State.INIT, "Network has not been started yet.");
            throwIfInState(State.SHUTDOWN, "Network has been shut down.");

            log.info("Sending freeze transaction...");
            final byte[] freezeTransaction =
                    createFreezeTransaction(timeManager().now().plus(FREEZE_DELAY));
            getNodes().stream()
                    .filter(Node::isActive)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No active node found to send freeze transaction to."))
                    .submitTransaction(freezeTransaction);

            log.debug("Waiting for nodes to freeze...");
            if (!timeManager().waitForCondition(allNodesInStatus(FREEZE_COMPLETE), timeout)) {
                fail("Timeout while waiting for all nodes to freeze.");
            }

            transactionGenerator().stop();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void shutdown() throws InterruptedException {
            throwIfInState(State.INIT, "Network has not been started yet.");
            throwIfInState(State.SHUTDOWN, "Network has already been shut down.");

            log.info("Killing nodes immediately...");
            for (final Node node : getNodes()) {
                node.killImmediately();
            }

            state = State.SHUTDOWN;

            transactionGenerator().stop();
        }
    }
}
