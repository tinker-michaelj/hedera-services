/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.services.bdd.junit.hedera.embedded;

import static com.hedera.node.app.hapi.utils.CommonPbjConverters.fromPbj;
import static com.hedera.services.bdd.junit.hedera.ExternalPath.ADDRESS_BOOK;
import static com.hedera.services.bdd.junit.hedera.embedded.fakes.FakePlatformContext.PLATFORM_CONFIG;
import static com.swirlds.platform.roster.RosterUtils.rosterFrom;
import static com.swirlds.platform.system.InitTrigger.GENESIS;
import static com.swirlds.platform.system.InitTrigger.RESTART;
import static com.swirlds.platform.system.status.PlatformStatus.ACTIVE;
import static com.swirlds.platform.system.status.PlatformStatus.FREEZE_COMPLETE;
import static java.util.Objects.requireNonNull;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.StreamSupport.stream;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.node.app.Hedera;
import com.hedera.node.app.ServicesMain;
import com.hedera.node.app.fixtures.state.FakeServiceMigrator;
import com.hedera.node.app.fixtures.state.FakeServicesRegistry;
import com.hedera.node.app.fixtures.state.FakeState;
import com.hedera.node.app.hints.impl.HintsServiceImpl;
import com.hedera.node.app.history.impl.HistoryServiceImpl;
import com.hedera.node.app.info.DiskStartupNetworks;
import com.hedera.node.app.version.ServicesSoftwareVersion;
import com.hedera.node.internal.network.Network;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.bdd.junit.hedera.embedded.fakes.AbstractFakePlatform;
import com.hedera.services.bdd.junit.hedera.embedded.fakes.FakeHintsService;
import com.hedera.services.bdd.junit.hedera.embedded.fakes.FakeHistoryService;
import com.hedera.services.bdd.junit.hedera.embedded.fakes.LapsingBlockHashSigner;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.swirlds.base.utility.Pair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.metrics.config.MetricsConfig;
import com.swirlds.common.metrics.platform.DefaultPlatformMetrics;
import com.swirlds.common.metrics.platform.MetricKeyRegistry;
import com.swirlds.common.metrics.platform.PlatformMetricsFactoryImpl;
import com.swirlds.common.platform.NodeId;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.platform.config.legacy.LegacyConfigPropertiesLoader;
import com.swirlds.platform.listeners.PlatformStatusChangeNotification;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.state.notifications.StateHashedNotification;
import com.swirlds.platform.test.fixtures.addressbook.RandomAddressBookBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Implementation support for {@link EmbeddedHedera}.
 */
public abstract class AbstractEmbeddedHedera implements EmbeddedHedera {
    private static final Logger log = LogManager.getLogger(AbstractEmbeddedHedera.class);

    private static final int NANOS_IN_A_SECOND = 1_000_000_000;
    private static final SemanticVersion EARLIER_SEMVER =
            SemanticVersion.newBuilder().patch(1).build();
    private static final SemanticVersion LATER_SEMVER =
            SemanticVersion.newBuilder().major(999).build();

    protected static final NodeId MISSING_NODE_ID = NodeId.of(666L);
    protected static final int MAX_PLATFORM_TXN_SIZE = 1024 * 6;
    protected static final int MAX_QUERY_RESPONSE_SIZE = 1024 * 1024 * 2;
    protected static final Hash FAKE_START_OF_STATE_HASH = new Hash(new byte[48]);
    protected static final TransactionResponse OK_RESPONSE = TransactionResponse.getDefaultInstance();
    protected static final PlatformStatusChangeNotification ACTIVE_NOTIFICATION =
            new PlatformStatusChangeNotification(ACTIVE);
    protected static final PlatformStatusChangeNotification FREEZE_COMPLETE_NOTIFICATION =
            new PlatformStatusChangeNotification(FREEZE_COMPLETE);

    private final boolean blockStreamEnabled;

    protected final Map<AccountID, NodeId> nodeIds;
    protected final Map<NodeId, com.hedera.hapi.node.base.AccountID> accountIds;
    protected final AccountID defaultNodeAccountId;
    protected final AddressBook addressBook;
    protected final Network network;
    protected final Roster roster;
    protected final NodeId defaultNodeId;
    protected final AtomicInteger nextNano = new AtomicInteger(0);
    protected final Metrics metrics;
    protected final Hedera hedera;
    protected final ServicesSoftwareVersion version;
    protected final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    /**
     * Non-final because a "saved state" may be provided via {@link EmbeddedHedera#restart(FakeState)}.
     */
    protected FakeState state;
    /**
     * Non-final because the compiler can't tell that the {@link com.hedera.node.app.Hedera.BlockHashSignerFactory}
     * lambda we give the {@link Hedera} constructor will always set this (the fake's delegate will ultimately need
     * needs to be constructed from the Hedera instance's {@code HintsService} and {@code HistoryService}).
     */
    protected LapsingBlockHashSigner blockHashSigner;
    /**
     * Non-final because the compiler can't tell that the {@link com.hedera.node.app.Hedera.HintsServiceFactory} lambda we give the
     * {@link Hedera} constructor will always set this (the fake's {@link HintsServiceImpl}
     * delegate needs to be constructed from the Hedera instance's {@link com.hedera.node.app.spi.AppContext}).
     */
    protected FakeHintsService hintsService;
    /**
     * Non-final because the compiler can't tell that the {@link com.hedera.node.app.Hedera.HistoryServiceFactory}
     * lambda we give the {@link Hedera} constructor will always set this (the fake's
     * {@link HistoryServiceImpl} delegate needs to be constructed from the Hedera
     * instance's {@link com.hedera.node.app.spi.AppContext}).
     */
    protected FakeHistoryService historyService;

    protected AbstractEmbeddedHedera(@NonNull final EmbeddedNode node) {
        requireNonNull(node);
        addressBook = loadAddressBook(node.getExternalPath(ADDRESS_BOOK));
        network = node.startupNetwork().orElseThrow();
        roster = rosterFrom(network);
        nodeIds = network.nodeMetadata().stream()
                .map(metadata -> Pair.of(
                        fromPbj(metadata.nodeOrThrow().accountIdOrThrow()),
                        NodeId.of(metadata.rosterEntryOrThrow().nodeId())))
                .collect(toMap(Pair::left, Pair::right));
        accountIds = network.nodeMetadata().stream()
                .map(metadata -> Pair.of(
                        NodeId.of(metadata.rosterEntryOrThrow().nodeId()),
                        metadata.nodeOrThrow().accountIdOrThrow()))
                .collect(toMap(Pair::left, Pair::right));
        defaultNodeId = NodeId.FIRST_NODE_ID;
        defaultNodeAccountId = fromPbj(accountIds.get(defaultNodeId));
        final var metricsConfig = PLATFORM_CONFIG.getConfigData(MetricsConfig.class);
        metrics = new DefaultPlatformMetrics(
                defaultNodeId,
                new MetricKeyRegistry(),
                executorService,
                new PlatformMetricsFactoryImpl(metricsConfig),
                metricsConfig);
        hedera = new Hedera(
                ConstructableRegistry.getInstance(),
                FakeServicesRegistry.FACTORY,
                new FakeServiceMigrator(),
                this::now,
                DiskStartupNetworks::new,
                (appContext, bootstrapConfig) -> this.hintsService = new FakeHintsService(appContext, bootstrapConfig),
                (appContext, bootstrapConfig) -> this.historyService = new FakeHistoryService(),
                (hints, history, configProvider) ->
                        this.blockHashSigner = new LapsingBlockHashSigner(hints, history, configProvider),
                metrics);
        version = (ServicesSoftwareVersion) hedera.getSoftwareVersion();
        blockStreamEnabled = hedera.isBlockStreamEnabled();
        Runtime.getRuntime().addShutdownHook(new Thread(executorService::shutdownNow));
    }

    @Override
    public void restart(@NonNull final FakeState state) {
        this.state = requireNonNull(state);
        start();
    }

    @Override
    public void start() {
        final InitTrigger trigger;
        if (state == null) {
            trigger = GENESIS;
            state = new FakeState();
        } else {
            trigger = RESTART;
        }
        hedera.initializeStatesApi(state, trigger, network, ServicesMain.buildPlatformConfig());

        hedera.setInitialStateHash(FAKE_START_OF_STATE_HASH);
        hedera.onStateInitialized(state, fakePlatform(), GENESIS);
        hedera.init(fakePlatform(), defaultNodeId);
        fakePlatform().start();
        fakePlatform().notifyListeners(ACTIVE_NOTIFICATION);
    }

    @Override
    public Hedera hedera() {
        return hedera;
    }

    @Override
    public Roster roster() {
        return roster;
    }

    @Override
    public void stop() {
        fakePlatform().notifyListeners(FREEZE_COMPLETE_NOTIFICATION);
        executorService.shutdownNow();
    }

    @Override
    public FakeState state() {
        return state;
    }

    @Override
    public SoftwareVersion version() {
        return version;
    }

    @Override
    public Timestamp nextValidStart() {
        var candidateNano = nextNano.getAndIncrement();
        if (candidateNano >= NANOS_IN_A_SECOND) {
            candidateNano = 0;
            nextNano.set(1);
        }
        final var then = now().minusSeconds(validStartOffsetSecs()).minusNanos(candidateNano);
        return Timestamp.newBuilder()
                .setSeconds(then.getEpochSecond())
                .setNanos(then.getNano())
                .build();
    }

    @Override
    public Response send(
            @NonNull final Query query, @NonNull final AccountID nodeAccountId, final boolean asNodeOperator) {
        requireNonNull(query);
        requireNonNull(nodeAccountId);
        if (!defaultNodeAccountId.equals(nodeAccountId) && !isFree(query)) {
            // It's possible this was intentional, but make a little noise to remind test author this happens
            log.warn("All paid queries get INVALID_NODE_ACCOUNT for non-default nodes in embedded mode");
        }
        final var responseBuffer = BufferedData.allocate(MAX_QUERY_RESPONSE_SIZE);
        if (asNodeOperator) {
            hedera.operatorQueryWorkflow().handleQuery(Bytes.wrap(query.toByteArray()), responseBuffer);
        } else {
            hedera.queryWorkflow().handleQuery(Bytes.wrap(query.toByteArray()), responseBuffer);
        }
        return parseQueryResponse(responseBuffer);
    }

    @Override
    public TransactionResponse submit(
            @NonNull final Transaction transaction,
            @NonNull final AccountID nodeAccountId,
            @NonNull final SyntheticVersion syntheticVersion) {
        if (defaultNodeAccountId.equals(nodeAccountId)) {
            assertCurrent(syntheticVersion);
        }
        return submit(
                transaction,
                nodeAccountId,
                switch (syntheticVersion) {
                    case PAST -> EARLIER_SEMVER;
                    case PRESENT -> version.getPbjSemanticVersion();
                    case FUTURE -> LATER_SEMVER;
                });
    }

    /**
     * If block stream is enabled, notifies the block stream manager of the state hash at the end of the round
     * given by {@code roundNumber}. (The block stream manager must have this information to construct the
     * block hash for round {@code roundNumber + 1}.)
     * @param roundNumber the round number of the state hash
     */
    protected void notifyStateHashed(final long roundNumber) {
        if (blockStreamEnabled) {
            hedera.blockStreamManager().notify(new StateHashedNotification(roundNumber, FAKE_START_OF_STATE_HASH));
        }
    }

    protected abstract TransactionResponse submit(
            @NonNull Transaction transaction, @NonNull AccountID nodeAccountId, @NonNull SemanticVersion version);

    /**
     * Returns the number of seconds to offset the next valid start time.
     * @return the number of seconds to offset the next valid start time
     */
    protected abstract long validStartOffsetSecs();

    /**
     * Returns the fake platform to start and stop.
     *
     * @return the fake platform
     */
    protected abstract AbstractFakePlatform fakePlatform();

    /**
     * Fails fast if somehow a user tries to manipulate the version when submitting to the default node account.
     *
     * @param syntheticVersion the synthetic version
     */
    private void assertCurrent(@NonNull final SyntheticVersion syntheticVersion) {
        if (syntheticVersion != SyntheticVersion.PRESENT) {
            throw new UnsupportedOperationException("Event version used at ingest by default node is always PRESENT");
        }
    }

    protected static TransactionResponse parseTransactionResponse(@NonNull final BufferedData responseBuffer) {
        try {
            return TransactionResponse.parseFrom(AbstractEmbeddedHedera.usedBytesFrom(responseBuffer));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected static Response parseQueryResponse(@NonNull final BufferedData responseBuffer) {
        try {
            return Response.parseFrom(AbstractEmbeddedHedera.usedBytesFrom(responseBuffer));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected static void warnOfSkippedIngestChecks(
            @NonNull final AccountID nodeAccountId, @NonNull final NodeId nodeId) {
        requireNonNull(nodeAccountId);
        requireNonNull(nodeId);
        // Bypass ingest for any other node, but make a little noise to remind test author this happens
        log.warn("Bypassing ingest checks for transaction to node{} (0.0.{})", nodeId, nodeAccountId.getAccountNum());
    }

    private static boolean isFree(@NonNull final Query query) {
        return query.hasCryptogetAccountBalance() || query.hasTransactionGetReceipt();
    }

    private static byte[] usedBytesFrom(@NonNull final BufferedData responseBuffer) {
        final byte[] bytes = new byte[Math.toIntExact(responseBuffer.position())];
        responseBuffer.resetPosition();
        responseBuffer.readBytes(bytes);
        return bytes;
    }

    private static AddressBook loadAddressBook(@NonNull final Path path) {
        requireNonNull(path);
        final var configFile = LegacyConfigPropertiesLoader.loadConfigFile(path.toAbsolutePath());
        final var randomAddressBook = RandomAddressBookBuilder.create(new Random())
                .withSize(1)
                .withRealKeysEnabled(true)
                .build();
        final var sigCert = requireNonNull(randomAddressBook.iterator().next().getSigCert());
        final var addressBook = configFile.getAddressBook();
        return new AddressBook(stream(spliteratorUnknownSize(addressBook.iterator(), 0), false)
                .map(address -> address.copySetSigCert(sigCert))
                .toList());
    }
}
