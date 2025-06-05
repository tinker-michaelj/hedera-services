// SPDX-License-Identifier: Apache-2.0
package org.hiero.otter.fixtures.turtle;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.fail;
import static org.hiero.consensus.model.status.PlatformStatus.ACTIVE;
import static org.hiero.consensus.model.status.PlatformStatus.FREEZE_COMPLETE;
import static org.hiero.otter.fixtures.turtle.TurtleTestEnvironment.AVERAGE_NETWORK_DELAY;
import static org.hiero.otter.fixtures.turtle.TurtleTestEnvironment.STANDARD_DEVIATION_NETWORK_DELAY;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.roster.Roster;
import com.swirlds.common.test.fixtures.Randotron;
import com.swirlds.platform.test.fixtures.addressbook.RandomRosterBuilder;
import com.swirlds.platform.test.fixtures.turtle.gossip.SimulatedNetwork;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hiero.consensus.model.node.KeysAndCerts;
import org.hiero.consensus.model.node.NodeId;
import org.hiero.consensus.model.status.PlatformStatus;
import org.hiero.consensus.roster.RosterUtils;
import org.hiero.otter.fixtures.AsyncNetworkActions;
import org.hiero.otter.fixtures.InstrumentedNode;
import org.hiero.otter.fixtures.Network;
import org.hiero.otter.fixtures.Node;
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
import org.hiero.otter.fixtures.turtle.app.TurtleTransaction;

/**
 * An implementation of {@link Network} that is based on the Turtle framework.
 */
public class TurtleNetwork implements Network, TurtleTimeManager.TimeTickReceiver {

    private static final Logger log = LogManager.getLogger(TurtleNetwork.class);

    private static final Duration DEFAULT_START_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration DEFAULT_FREEZE_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration FREEZE_DELAY = Duration.ofSeconds(10);
    private static final Duration DEFAULT_SHUTDOWN_TIMEOUT = Duration.ZERO;

    private enum State {
        INIT,
        RUNNING,
        SHUTDOWN
    }

    private final Randotron randotron;
    private final TurtleTimeManager timeManager;
    private final TurtleLogging logging;
    private final Path rootOutputDirectory;
    private final List<TurtleNode> nodes = new ArrayList<>();
    private final TurtleTransactionGenerator transactionGenerator;

    private List<Node> publicNodes = List.of();
    private ExecutorService executorService;
    private SimulatedNetwork simulatedNetwork;

    private State state = State.INIT;

    /**
     * Constructor for TurtleNetwork.
     *
     * @param randotron           the random generator
     * @param timeManager         the time manager
     * @param logging             the logging utility
     * @param rootOutputDirectory the directory where the node output will be stored, like saved state and so on
     * @param transactionGenerator the transaction generator that generates a steady flow of transactions to all nodes
     */
    public TurtleNetwork(
            @NonNull final Randotron randotron,
            @NonNull final TurtleTimeManager timeManager,
            @NonNull final TurtleLogging logging,
            @NonNull final Path rootOutputDirectory,
            @NonNull final TurtleTransactionGenerator transactionGenerator) {
        this.randotron = requireNonNull(randotron);
        this.timeManager = requireNonNull(timeManager);
        this.logging = requireNonNull(logging);
        this.rootOutputDirectory = requireNonNull(rootOutputDirectory);
        this.transactionGenerator = requireNonNull(transactionGenerator);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public List<Node> addNodes(final int count) {
        throwIfInState(State.RUNNING, "Cannot add nodes after the network has been started.");
        throwIfInState(State.SHUTDOWN, "Cannot add nodes after the network has been started.");
        if (!nodes.isEmpty()) {
            throw new UnsupportedOperationException("Adding nodes incrementally is not supported yet.");
        }

        executorService = Executors.newFixedThreadPool(
                Math.min(count, Runtime.getRuntime().availableProcessors()));

        final RandomRosterBuilder rosterBuilder =
                RandomRosterBuilder.create(randotron).withSize(count).withRealKeysEnabled(true);
        final Roster roster = rosterBuilder.build();

        simulatedNetwork =
                new SimulatedNetwork(randotron, roster, AVERAGE_NETWORK_DELAY, STANDARD_DEVIATION_NETWORK_DELAY);

        final List<TurtleNode> nodeList = roster.rosterEntries().stream()
                .map(RosterUtils::getNodeId)
                .sorted()
                .map(nodeId -> createTurtleNode(nodeId, roster, rosterBuilder.getPrivateKeys(nodeId)))
                .toList();
        nodes.addAll(nodeList);

        publicNodes = nodes.stream().map(Node.class::cast).toList();
        return publicNodes;
    }

    private TurtleNode createTurtleNode(
            @NonNull final NodeId nodeId, @NonNull final Roster roster, @NonNull final KeysAndCerts privateKeys) {
        final Path outputDir = rootOutputDirectory.resolve("node-" + nodeId.id());
        return new TurtleNode(
                randotron, timeManager.time(), nodeId, roster, privateKeys, simulatedNetwork, logging, outputDir);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() throws InterruptedException {
        withTimeout(DEFAULT_START_TIMEOUT).start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public InstrumentedNode addInstrumentedNode() {
        throw new UnsupportedOperationException("Adding instrumented nodes is not implemented yet.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public List<Node> getNodes() {
        return publicNodes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void freeze() throws InterruptedException {
        withTimeout(DEFAULT_FREEZE_TIMEOUT).freeze();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void shutdown() throws InterruptedException {
        withTimeout(DEFAULT_SHUTDOWN_TIMEOUT).shutdown();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public AsyncNetworkActions withTimeout(@NonNull final Duration timeout) {
        return new TurtleAsyncNetworkActions(timeout);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setVersion(@NonNull final SemanticVersion version) {
        for (final TurtleNode node : nodes) {
            node.setVersion(version);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void bumpConfigVersion() {
        for (final TurtleNode node : nodes) {
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
                nodes.stream().map(Node::getConsensusResult).toList();
        return new MultipleNodeConsensusResultsImpl(results);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public MultipleNodeLogResults getLogResults() {
        final List<SingleNodeLogResult> results =
                nodes.stream().map(Node::getLogResult).toList();

        return new MultipleNodeLogResultsImpl(results);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public MultipleNodeStatusProgression getStatusProgression() {
        final List<SingleNodeStatusProgression> statusProgressions =
                nodes.stream().map(Node::getStatusProgression).toList();
        return new MultipleNodeStatusProgressionImpl(statusProgressions);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public MultipleNodePcesResults getPcesResults() {
        final List<SingleNodePcesResult> results =
                nodes.stream().map(Node::getPcesResult).toList();
        return new MultipleNodePcesResultsImpl(results);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void tick(@NonNull final Instant now) {
        if (state != State.RUNNING) {
            return;
        }

        simulatedNetwork.tick(now);
        transactionGenerator.tick(now, publicNodes);

        // Iteration order over nodes does not need to be deterministic -- nodes are not permitted to communicate with
        // each other during the tick phase, and they run on separate threads to boot.
        CompletableFuture.allOf(nodes.stream()
                        .map(node -> CompletableFuture.runAsync(() -> node.tick(now), executorService))
                        .toArray(CompletableFuture[]::new))
                .join();
    }

    /**
     * Shuts down the network and cleans up resources. Once this method is called, the network cannot be started again.
     * This method is idempotent and can be called multiple times without any side effects.
     *
     * @throws InterruptedException if the thread is interrupted while the network is being destroyed
     */
    public void destroy() throws InterruptedException {
        log.info("Destroying network...");
        transactionGenerator.stop();
        for (final TurtleNode node : nodes) {
            node.destroy();
        }
        executorService.shutdownNow();
    }

    /**
     * Creates a {@link BooleanSupplier} that returns {@code true} if all nodes are in the given
     * {@link PlatformStatus}.
     *
     * @param status the status to check
     * @return the {@link BooleanSupplier}
     */
    private BooleanSupplier allNodesInStatus(@NonNull final PlatformStatus status) {
        return () -> nodes.stream().allMatch(node -> node.platformStatus() == status);
    }

    private void throwIfInState(@NonNull final State expected, @NonNull final String message) {
        if (state == expected) {
            throw new IllegalStateException(message);
        }
    }

    /**
     * Turtle-specific implementation of {@link AsyncNetworkActions}
     */
    private class TurtleAsyncNetworkActions implements AsyncNetworkActions {

        private final Duration timeout;

        private TurtleAsyncNetworkActions(@NonNull final Duration timeout) {
            this.timeout = requireNonNull(timeout);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void start() {
            throwIfInState(State.RUNNING, "Network is already running.");

            log.info("Starting network...");
            state = State.RUNNING;
            for (final TurtleNode node : nodes) {
                node.start();
            }

            transactionGenerator.start();

            log.debug("Waiting for nodes to become active...");
            if (!timeManager.waitForCondition(allNodesInStatus(ACTIVE), timeout)) {
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
            final TurtleTransaction freezeTransaction =
                    TransactionFactory.createFreezeTransaction(timeManager.now().plus(FREEZE_DELAY));
            nodes.getFirst().submitTransaction(freezeTransaction.toByteArray());

            log.debug("Waiting for nodes to freeze...");
            if (!timeManager.waitForCondition(allNodesInStatus(FREEZE_COMPLETE), timeout)) {
                fail("Timeout while waiting for all nodes to freeze.");
            }

            transactionGenerator.stop();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void shutdown() throws InterruptedException {
            throwIfInState(State.INIT, "Network has not been started yet.");
            throwIfInState(State.SHUTDOWN, "Network has already been shut down.");

            log.info("Killing nodes immediately...");
            for (final TurtleNode node : nodes) {
                node.killImmediately();
            }

            state = State.SHUTDOWN;

            transactionGenerator.stop();
        }
    }
}
