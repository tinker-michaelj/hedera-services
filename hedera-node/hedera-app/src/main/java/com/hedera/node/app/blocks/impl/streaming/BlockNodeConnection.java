// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.blocks.impl.streaming;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.metrics.BlockStreamMetrics;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.BlockNodeConnectionConfig;
import com.hedera.node.internal.network.BlockNodeConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.grpc.stub.StreamObserver;
import io.helidon.webclient.grpc.GrpcServiceClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hiero.block.api.PublishStreamRequest;
import org.hiero.block.api.PublishStreamResponse;
import org.hiero.block.api.PublishStreamResponse.BlockAcknowledgement;
import org.hiero.block.api.PublishStreamResponse.EndOfStream;
import org.hiero.block.api.PublishStreamResponse.EndOfStream.Code;
import org.hiero.block.api.PublishStreamResponse.ResendBlock;
import org.hiero.block.api.PublishStreamResponse.SkipBlock;

/**
 * Manages a single gRPC bidirectional streaming connection to a block node. Each connection:
 * <ul>
 *   <li>Handles the streaming of block items to a configured Block ode</li>
 *   <li>Maintains connection state and handles responses from the Block Node</li>
 *   <li>Coordinates with {@link BlockNodeConnectionManager} for managing the connection lifecycle</li>
 *   <li>Processes block acknowledgements, retries, and error scenarios</li>
 * </ul>
 * <p>
 * The connection goes through multiple states defined in {@link ConnectionState} and
 * uses exponential backoff for retries when errors occur.
 */
public class BlockNodeConnection implements StreamObserver<PublishStreamResponse> {

    private static final Logger logger = LogManager.getLogger(BlockNodeConnection.class);

    /**
     * A longer retry delay for when the connection encounters an error.
     */
    public static final Duration LONGER_RETRY_DELAY = Duration.ofSeconds(30);
    /**
     * The configuration specific to the block node this connection is for.
     */
    private final BlockNodeConfig blockNodeConfig;
    /**
     * The gRPC client to use for creating bi-directional streams between the consensus node and block node.
     */
    private final GrpcServiceClient grpcServiceClient;
    /**
     * The "parent" connection manager that manages the lifecycle of this connection.
     */
    private final BlockNodeConnectionManager blockNodeConnectionManager;
    /**
     * Manager that maintains the system-wide state as it pertains to block streaming. Access here is used to retrieve
     * blocks for streaming and indicating which blocks have been acknowledged by the block node.
     */
    private final BlockBufferService blockBufferService;
    /**
     * Metrics API for block stream-specific metrics.
     */
    private final BlockStreamMetrics blockStreamMetrics;
    /**
     * Configuration property: the maximum number of EndOfStream responses permitted on this connection before taking
     * corrective action (e.g. reconnecting).
     */
    private final int maxEndOfStreamsAllowed;
    /**
     * Configuration property: the time window in which a certain number of EndOfStream responses is permitted before
     * corrective action is taken. This works alongside {@link BlockNodeConnection#maxEndOfStreamsAllowed}.
     */
    private final Duration endOfStreamTimeFrame;
    /**
     * If corrective action needs to be taken (e.g. reconnect) because of too many EndOfStream responses in the
     * permitted time frame, then this duration is used as the delay for acting upon that corrective action. For example,
     * if a reconnect is needed and the delay is configured to five seconds, then the reconnect will be scheduled in
     * five seconds.
     */
    private final Duration endOfStreamScheduleDelay;
    /**
     * Queue for tracking the instances of EndOfStream responses received from the block node for this connection. This
     * queue will be periodically pruned.
     */
    private final Queue<Instant> endOfStreamTimestamps = new ConcurrentLinkedQueue<>();
    /**
     * Flag that indicates if this stream is currently shutting down, as initiated by this consensus node.
     */
    private final AtomicBoolean streamShutdownInProgress = new AtomicBoolean(false);
    /**
     * Stream observer used to send messages to the block node.
     */
    private StreamObserver<PublishStreamRequest> blockNodeStreamObserver;
    /**
     * Reference to the current state of this connection.
     */
    private final AtomicReference<ConnectionState> connectionState;
    /**
     * The gRPC endpoint used to establish bi-directional communication between the consensus node and block node.
     */
    private final String grpcEndpoint;

    /**
     * Represents the possible states of a Block Node connection.
     */
    public enum ConnectionState {
        /**
         * bidi RequestObserver needs to be created.
         */
        UNINITIALIZED,
        /**
         * bidi RequestObserver is established but this connection has not been chosen as the active one (priority based).
         */
        PENDING,
        /**
         * Connection is active. Block Stream Worker Thread is sending PublishStreamRequest's to the block node through async bidi stream.
         */
        ACTIVE,
        /**
         * The connection is currently trying to connect to the block node.
         */
        CONNECTING
    }

    /**
     * Construct a new BlockNodeConnection.
     *
     * @param configProvider the configuration to use
     * @param nodeConfig the configuration for the block node
     * @param blockNodeConnectionManager the connection manager coordinating block node connections
     * @param blockBufferService the block stream state manager for block node connections
     * @param grpcServiceClient the gRPC client to establish the bidirectional streaming to block node connections
     * @param blockStreamMetrics the block stream metrics for block node connections
     */
    public BlockNodeConnection(
            @NonNull final ConfigProvider configProvider,
            @NonNull final BlockNodeConfig nodeConfig,
            @NonNull final BlockNodeConnectionManager blockNodeConnectionManager,
            @NonNull final BlockBufferService blockBufferService,
            @NonNull final GrpcServiceClient grpcServiceClient,
            @NonNull final BlockStreamMetrics blockStreamMetrics,
            @NonNull final String grpcEndpoint) {
        requireNonNull(configProvider, "configProvider must not be null");
        this.blockNodeConfig = requireNonNull(nodeConfig, "nodeConfig must not be null");
        this.blockNodeConnectionManager =
                requireNonNull(blockNodeConnectionManager, "blockNodeConnectionManager must not be null");
        this.blockBufferService = requireNonNull(blockBufferService, "blockBufferService must not be null");
        this.grpcServiceClient = requireNonNull(grpcServiceClient, "grpcServiceClient must not be null");
        this.blockStreamMetrics = requireNonNull(blockStreamMetrics, "blockStreamMetrics must not be null");
        this.connectionState = new AtomicReference<>(ConnectionState.UNINITIALIZED);
        this.grpcEndpoint = requireNonNull(grpcEndpoint);

        final var blockNodeConnectionConfig =
                configProvider.getConfiguration().getConfigData(BlockNodeConnectionConfig.class);

        this.maxEndOfStreamsAllowed = blockNodeConnectionConfig.maxEndOfStreamsAllowed();
        this.endOfStreamTimeFrame = blockNodeConnectionConfig.endOfStreamTimeFrame();
        this.endOfStreamScheduleDelay = blockNodeConnectionConfig.endOfStreamScheduleDelay();
    }

    /**
     * Creates a new bidi request observer for this block node connection.
     */
    public void createRequestObserver() {
        if (blockNodeStreamObserver == null) {
            blockNodeStreamObserver = grpcServiceClient.bidi(grpcEndpoint, this);
            updateConnectionState(ConnectionState.PENDING);
        }
    }

    /**
     * Updates the connection's state.
     * @param newState the new state to transition to
     */
    public void updateConnectionState(@NonNull final ConnectionState newState) {
        requireNonNull(newState);
        final ConnectionState oldState = connectionState.getAndSet(newState);
        logger.debug("[{}] Connection state transitioned from {} to {}", this, oldState, newState);
    }

    /**
     * Handles the failure of the stream by closing the connection and notifying the connection manager.
     */
    public void handleStreamFailure() {
        close();
        blockNodeConnectionManager.rescheduleAndSelectNewNode(this, LONGER_RETRY_DELAY);
    }

    /**
     * Handles the {@link BlockAcknowledgement} response received from the block node.
     *
     * @param acknowledgement the acknowledgement received from the block node
     */
    private void handleAcknowledgement(@NonNull final BlockAcknowledgement acknowledgement) {
        final long acknowledgedBlockNumber = acknowledgement.blockNumber();
        final boolean blockAlreadyExists = acknowledgement.blockAlreadyExists();
        final long currentBlockStreaming = blockNodeConnectionManager.currentStreamingBlockNumber();
        final long currentBlockProducing = blockBufferService.getLastBlockNumberProduced();

        // Update the last verified block by the current connection
        blockNodeConnectionManager.updateLastVerifiedBlock(blockNodeConfig, acknowledgedBlockNumber);

        if (currentBlockStreaming == -1) {
            logger.warn(
                    "[{}] Received acknowledgement for block {}, but we haven't streamed anything to the node",
                    this,
                    acknowledgedBlockNumber);
            return;
        }

        logger.debug(
                "[{}] Acknowledgement received for block {} (alreadyExists={})",
                this,
                acknowledgedBlockNumber,
                blockAlreadyExists);

        if (acknowledgedBlockNumber > currentBlockProducing || acknowledgedBlockNumber > currentBlockStreaming) {
            /*
            We received an acknowledgement for a block that the consensus node is either currently streaming or
            producing. This likely indicates this consensus node is behind other consensus nodes (since the
            block node would have received the block from another consensus node.) Because of this, we can go
            ahead and jump to the block after the acknowledged one as the next block to send to the block node.
             */
            final long blockToJumpTo = acknowledgedBlockNumber + 1;
            logger.debug(
                    "[{}] Received acknowledgement for block {}, however this is later than the current "
                            + "block being streamed ({}) or the block being currently produced ({}); skipping ahead to block {}",
                    this,
                    acknowledgedBlockNumber,
                    currentBlockStreaming,
                    currentBlockProducing,
                    blockToJumpTo);
            jumpToBlock(blockToJumpTo);
        }
    }

    /**
     * Handles the {@link EndOfStream} response received from the block node.
     * In most cases it indicates that the block node is unable to continue processing.
     * @param endOfStream the EndOfStream response received from the block node
     */
    private void handleEndOfStream(@NonNull final EndOfStream endOfStream) {
        requireNonNull(endOfStream);
        final long blockNumber = endOfStream.blockNumber();
        final EndOfStream.Code responseCode = endOfStream.status();

        logger.debug("[{}] Received EndOfStream response (block={}, responseCode={})", this, blockNumber, responseCode);

        // Always end the stream when we receive an end of stream message
        close();

        // Include this new EoS response in our set that tracks the occurrences of EoS responses
        endOfStreamTimestamps.add(Instant.now());

        // Check if we've exceeded the EndOfStream rate limit
        if (hasExceededEndOfStreamLimit()) {
            logger.warn(
                    "[{}] Block node has exceeded the allowed number of EndOfStream responses (received={}, "
                            + "permitted={}, timeWindow={}); reconnection scheduled for {}",
                    this,
                    endOfStreamTimestamps.size(),
                    maxEndOfStreamsAllowed,
                    endOfStreamTimeFrame,
                    endOfStreamScheduleDelay);

            // Schedule delayed retry through connection manager
            blockNodeConnectionManager.rescheduleAndSelectNewNode(this, endOfStreamScheduleDelay);
            return;
        }

        switch (responseCode) {
            case Code.INTERNAL_ERROR, Code.PERSISTENCE_FAILED -> {
                // The block node had an end of stream error and cannot continue processing.
                // We should wait for a short period before attempting to retry
                // to avoid overwhelming the node if it's having issues
                logger.warn(
                        "[{}] Block node reported an error at block {}. Will attempt to reestablish the stream later.",
                        this,
                        blockNumber);
                blockNodeConnectionManager.rescheduleAndSelectNewNode(this, LONGER_RETRY_DELAY);
            }
            case Code.TIMEOUT, Code.OUT_OF_ORDER, Code.BAD_STATE_PROOF -> {
                // We should restart the stream at the block immediately
                // following the block where the node fell behind.
                final long restartBlockNumber = blockNumber == Long.MAX_VALUE ? 0 : blockNumber + 1;
                logger.warn(
                        "[{}] Block node reported status indicating immediate restart should be attempted. "
                                + "Will restart stream at block {}.",
                        this,
                        restartBlockNumber);

                restartStreamAtBlock(restartBlockNumber);
            }
            case Code.SUCCESS -> {
                // The block node orderly ended the stream. In this case, no errors occurred.
                // We should wait for a longer period before attempting to retry.
                logger.warn("[{}] Block node orderly ended the stream at block {}", this, blockNumber);
                blockNodeConnectionManager.rescheduleAndSelectNewNode(this, LONGER_RETRY_DELAY);
            }
            case Code.BEHIND -> {
                // The block node is behind us, check if we have the last verified block still available in order to
                // restart the stream from there
                final long restartBlockNumber = blockNumber == Long.MAX_VALUE ? 0 : blockNumber + 1;
                if (blockBufferService.getBlockState(restartBlockNumber) != null) {
                    logger.warn(
                            "[{}] Block node reported it is behind. Will restart stream at block {}.",
                            this,
                            restartBlockNumber);

                    restartStreamAtBlock(restartBlockNumber);
                } else {
                    // If we don't have the block state, we schedule retry for this connection and establish new one
                    // with different block node
                    logger.warn("[{}] Block node is behind and block state is not available.", this);

                    blockNodeConnectionManager.rescheduleAndSelectNewNode(this, LONGER_RETRY_DELAY);
                }
            }
            case Code.UNKNOWN -> {
                // This should never happen, but if it does, schedule this connection for a retry attempt and in the
                // meantime select a new node to stream to
                logger.error("[{}] Block node reported an unknown error at block {}.", this, blockNumber);
                blockNodeConnectionManager.rescheduleAndSelectNewNode(this, LONGER_RETRY_DELAY);
            }
        }
    }

    /**
     * Handles the {@link SkipBlock} response received from the block node.
     * @param skipBlock the SkipBlock response received from the block node
     */
    private void handleSkipBlock(@NonNull final SkipBlock skipBlock) {
        requireNonNull(skipBlock);
        final long skipBlockNumber = skipBlock.blockNumber();
        final long streamingBlockNumber = blockNodeConnectionManager.currentStreamingBlockNumber();

        // Only jump if the skip is for the block we are currently processing
        if (skipBlockNumber == streamingBlockNumber) {
            final long nextBlock = skipBlockNumber + 1L;
            logger.debug("[{}] Received SkipBlock response; skipping to block {}", this, nextBlock);
            jumpToBlock(nextBlock); // Now uses signaling instead of thread interruption
        } else {
            logger.debug(
                    "[{}] Received SkipBlock response for block {}, but we are not streaming that block so it will be ignored",
                    this,
                    skipBlockNumber);
        }
    }

    /**
     * Handles the {@link ResendBlock} response received from the block node.
     * If the consensus node has the requested block state available, it will start streaming it.
     * Otherwise, it will close the connection and retry with a different block node.
     *
     * @param resendBlock the ResendBlock response received from the block node
     */
    private void handleResendBlock(@NonNull final ResendBlock resendBlock) {
        requireNonNull(resendBlock);

        final long resendBlockNumber = resendBlock.blockNumber();
        logger.debug("[{}] Received ResendBlock response for block {}", this, resendBlockNumber);

        if (blockBufferService.getBlockState(resendBlockNumber) != null) {
            jumpToBlock(resendBlockNumber);
        } else {
            // If we don't have the block state, we schedule retry for this connection and establish new one
            // with different block node
            logger.warn(
                    "[{}] Block node requested a ResendBlock for block {} but that block does not exist on this "
                            + "consensus node. Closing connection and will retry later",
                    this,
                    resendBlockNumber);
            close();
            blockNodeConnectionManager.rescheduleAndSelectNewNode(this, LONGER_RETRY_DELAY);
        }
    }

    /**
     * Checks if the EndOfStream rate limit has been exceeded.
     * This method maintains a queue of timestamps for the last EndOfStream responses
     * and checks if the number of responses exceeds the configurable limit
     * within the configurable time frame.
     *
     * @return true if the rate limit has been exceeded, false otherwise
     */
    private boolean hasExceededEndOfStreamLimit() {
        final Instant now = Instant.now();
        final Instant cutoff = now.minus(endOfStreamTimeFrame);

        // Remove expired timestamps
        final Iterator<Instant> it = endOfStreamTimestamps.iterator();
        while (it.hasNext()) {
            final Instant timestamp = it.next();
            if (timestamp.isBefore(cutoff)) {
                it.remove();
            } else {
                break;
            }
        }

        // Check if we've exceeded the limit
        return endOfStreamTimestamps.size() > maxEndOfStreamsAllowed;
    }

    /**
     * If connection is active sends a stream request to the block node, otherwise does nothing.
     *
     * @param request the request to send
     */
    public void sendRequest(@NonNull final PublishStreamRequest request) {
        requireNonNull(request);
        if (connectionState.get() == ConnectionState.ACTIVE && blockNodeStreamObserver != null) {
            blockNodeStreamObserver.onNext(request);
        }
    }

    /**
     * Idempotent operation that closes this connection (if active) and releases associated resources. If there is a
     * failure in closing the connection, the error will be logged and not propagated back to the caller.
     */
    public void close() {
        try {
            logger.debug("[{}] Closing connection...", this);

            updateConnectionState(ConnectionState.UNINITIALIZED);
            closeObserver();
            jumpToBlock(-1L);

            logger.debug("[{}] Connection successfully closed", this);
        } catch (final RuntimeException e) {
            logger.warn("[{}] Error occurred while attempting to close connection", this);
        }
    }

    private void closeObserver() {
        if (blockNodeStreamObserver != null) {
            logger.debug("[{}] Closing request observer for block node", this);
            streamShutdownInProgress.set(true);

            try {
                blockNodeStreamObserver.onCompleted();
                logger.debug("[{}] Request observer successfully closed", this);
            } catch (final Exception e) {
                logger.warn("[{}] Error while completing request observer", this, e);
            }
            blockNodeStreamObserver = null;
        }
    }

    /**
     * Returns the block node configuration for this connection.
     *
     * @return the block node configuration
     */
    public BlockNodeConfig getNodeConfig() {
        return blockNodeConfig;
    }

    /**
     * Restarts a new stream at a specified block number.
     * This method will establish a new stream and start processing from the specified block number.
     *
     * @param blockNumber the block number to restart at
     */
    private void restartStreamAtBlock(final long blockNumber) {
        logger.debug("[{}] Scheduling stream restart at block {}}", this, blockNumber);
        blockNodeConnectionManager.scheduleConnectionAttempt(
                this, BlockNodeConnectionManager.INITIAL_RETRY_DELAY, blockNumber);
    }

    /**
     * Restarts the worker thread at a specific block number without ending the stream.
     * This method will interrupt the current worker thread if it exists,
     * set the new block number and request index, and start a new worker thread.
     * The gRPC stream with the block node is maintained.
     *
     * @param blockNumber the block number to jump to
     */
    private void jumpToBlock(final long blockNumber) {
        logger.debug("[{}] Jumping to block {}", this, blockNumber);
        // Set the target block for the worker loop to pick up
        blockNodeConnectionManager.jumpToBlock(blockNumber);
    }

    /**
     * Processes responses received from the block node through the bidirectional gRPC stream.
     * Handles {@link BlockAcknowledgement}s, {@link EndOfStream} response signals, {@link SkipBlock} and {@link ResendBlock}.
     *
     * @param response the response received from block node
     */
    @Override
    public void onNext(final @NonNull PublishStreamResponse response) {
        requireNonNull(response);

        if (response.hasAcknowledgement()) {
            blockStreamMetrics.incrementAcknowledgedBlockCount();
            handleAcknowledgement(response.acknowledgement());
        } else if (response.hasEndStream()) {
            blockStreamMetrics.incrementEndOfStreamCount(response.endStream().status());
            handleEndOfStream(response.endStream());
        } else if (response.hasSkipBlock()) {
            blockStreamMetrics.incrementSkipBlockCount();
            handleSkipBlock(response.skipBlock());
        } else if (response.hasResendBlock()) {
            blockStreamMetrics.incrementResendBlockCount();
            handleResendBlock(response.resendBlock());
        } else {
            blockStreamMetrics.incrementUnknownResponseCount();
            logger.warn("[{}] Unexpected response received: {}", this, response);
        }
    }

    /**
     * Handles errors received on the gRPC stream.
     * Triggers connection retry with appropriate backoff.
     *
     * @param error the error that occurred on the stream
     */
    @Override
    public void onError(final Throwable error) {
        logger.warn("[{}] Stream encountered an error", this, error);
        blockStreamMetrics.incrementOnErrorCount();
        handleStreamFailure();
    }

    /**
     * Handles normal stream completion or termination.
     * Triggers reconnection if completion was not initiated by this side.
     */
    @Override
    public void onCompleted() {
        if (streamShutdownInProgress.get()) {
            logger.debug("[{}] Stream completed (stream close was in progress)", this);
            streamShutdownInProgress.set(false);
        } else {
            logger.warn("[{}] Stream completed unexpectedly", this);
            handleStreamFailure();
        }
    }

    /**
     * Returns the connection state for this connection.
     *
     * @return the connection state
     */
    public ConnectionState getConnectionState() {
        return connectionState.get();
    }

    @Override
    public String toString() {
        return blockNodeConfig.address() + ":" + blockNodeConfig.port() + "/" + connectionState.get();
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final BlockNodeConnection that = (BlockNodeConnection) o;
        return Objects.equals(blockNodeConfig, that.blockNodeConfig) && Objects.equals(grpcEndpoint, that.grpcEndpoint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(blockNodeConfig, grpcEndpoint);
    }
}
