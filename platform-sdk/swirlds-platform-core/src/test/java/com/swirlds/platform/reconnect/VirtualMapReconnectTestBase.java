// SPDX-License-Identifier: Apache-2.0
package com.swirlds.platform.reconnect;

import static com.swirlds.common.test.fixtures.io.ResourceLoader.loadLog4jContext;
import static com.swirlds.platform.test.fixtures.config.ConfigUtils.CONFIGURATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig_;
import com.swirlds.common.test.fixtures.merkle.dummy.DummyMerkleInternal;
import com.swirlds.common.test.fixtures.merkle.dummy.DummyMerkleLeaf;
import com.swirlds.common.test.fixtures.merkle.util.MerkleTestUtils;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.merkledb.test.fixtures.MerkleDbTestUtils;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.config.VirtualMapConfig;
import com.swirlds.virtualmap.datasource.VirtualDataSource;
import com.swirlds.virtualmap.datasource.VirtualDataSourceBuilder;
import com.swirlds.virtualmap.datasource.VirtualHashRecord;
import com.swirlds.virtualmap.datasource.VirtualLeafBytes;
import com.swirlds.virtualmap.internal.merkle.VirtualMapState;
import com.swirlds.virtualmap.internal.merkle.VirtualRootNode;
import com.swirlds.virtualmap.serialize.KeySerializer;
import com.swirlds.virtualmap.serialize.ValueSerializer;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.hiero.base.constructable.ClassConstructorPair;
import org.hiero.base.constructable.ConstructableRegistry;
import org.hiero.base.constructable.ConstructableRegistryException;
import org.hiero.base.crypto.Hash;
import org.hiero.base.io.streams.SerializableDataInputStream;
import org.hiero.base.io.streams.SerializableDataOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

public abstract class VirtualMapReconnectTestBase {

    protected static final TestKey A_KEY = new TestKey('a');
    protected static final TestKey B_KEY = new TestKey('b');
    protected static final TestKey C_KEY = new TestKey('c');
    protected static final TestKey D_KEY = new TestKey('d');
    protected static final TestKey E_KEY = new TestKey('e');
    protected static final TestKey F_KEY = new TestKey('f');
    protected static final TestKey G_KEY = new TestKey('g');

    protected static final TestValue APPLE = new TestValue("APPLE");
    protected static final TestValue BANANA = new TestValue("BANANA");
    protected static final TestValue CHERRY = new TestValue("CHERRY");
    protected static final TestValue DATE = new TestValue("DATE");
    protected static final TestValue EGGPLANT = new TestValue("EGGPLANT");
    protected static final TestValue FIG = new TestValue("FIG");
    protected static final TestValue GRAPE = new TestValue("GRAPE");

    protected static final TestValue AARDVARK = new TestValue("AARDVARK");
    protected static final TestValue BEAR = new TestValue("BEAR");
    protected static final TestValue CUTTLEFISH = new TestValue("CUTTLEFISH");
    protected static final TestValue DOG = new TestValue("DOG");
    protected static final TestValue EMU = new TestValue("EMU");
    protected static final TestValue FOX = new TestValue("FOX");
    protected static final TestValue GOOSE = new TestValue("GOOSE");

    protected static final KeySerializer<TestKey> KEY_SERIALIZER = new TestKeySerializer();
    protected static final ValueSerializer<TestValue> VALUE_SERIALIZER = new TestValueSerializer();

    protected VirtualMap<TestKey, TestValue> teacherMap;
    protected VirtualMap<TestKey, TestValue> learnerMap;
    protected BrokenBuilder teacherBuilder;
    protected BrokenBuilder learnerBuilder;

    protected final ReconnectConfig reconnectConfig = new TestConfigBuilder()
            // This is lower than the default, helps test that is supposed to fail to finish faster.
            .withValue(ReconnectConfig_.ASYNC_STREAM_TIMEOUT, "5s")
            .withValue(ReconnectConfig_.MAX_ACK_DELAY, "1000ms")
            .getOrCreateConfig()
            .getConfigData(ReconnectConfig.class);

    protected abstract VirtualDataSourceBuilder createBuilder() throws IOException;

    @BeforeEach
    void setupEach() throws Exception {
        // Some tests set custom default VirtualMap settings, e.g. StreamEventParserTest calls
        // Browser.populateSettingsCommon(). These custom settings can't be used to run VM reconnect
        // tests. As a workaround, set default settings here explicitly
        final VirtualDataSourceBuilder dataSourceBuilder = createBuilder();
        teacherBuilder = new BrokenBuilder(dataSourceBuilder);
        learnerBuilder = new BrokenBuilder(dataSourceBuilder);
        teacherMap = new VirtualMap<>("Teacher", KEY_SERIALIZER, VALUE_SERIALIZER, teacherBuilder, CONFIGURATION);
        learnerMap = new VirtualMap<>("Learner", KEY_SERIALIZER, VALUE_SERIALIZER, learnerBuilder, CONFIGURATION);
    }

    @BeforeAll
    public static void startup() throws ConstructableRegistryException, FileNotFoundException {
        loadLog4jContext();
        final ConstructableRegistry registry = ConstructableRegistry.getInstance();
        registry.registerConstructables("com.swirlds.common");
        registry.registerConstructables("org.hiero");
        registry.registerConstructable(new ClassConstructorPair(DummyMerkleInternal.class, DummyMerkleInternal::new));
        registry.registerConstructable(new ClassConstructorPair(DummyMerkleLeaf.class, DummyMerkleLeaf::new));
        registry.registerConstructable(new ClassConstructorPair(VirtualMap.class, () -> new VirtualMap(CONFIGURATION)));
        registry.registerConstructable(new ClassConstructorPair(VirtualMapState.class, VirtualMapState::new));
        registry.registerConstructable(new ClassConstructorPair(
                VirtualRootNode.class,
                () -> new VirtualRootNode<>(CONFIGURATION.getConfigData(VirtualMapConfig.class))));
        registry.registerConstructable(new ClassConstructorPair(TestKey.class, TestKey::new));
        registry.registerConstructable(new ClassConstructorPair(TestValue.class, TestValue::new));
        registry.registerConstructable(new ClassConstructorPair(BrokenBuilder.class, BrokenBuilder::new));
    }

    protected MerkleInternal createTreeForMap(VirtualMap<TestKey, TestValue> map) {
        final var tree = MerkleTestUtils.buildLessSimpleTree();
        tree.getChild(1).asInternal().setChild(3, map);
        tree.reserve();
        return tree;
    }

    protected void reconnect() throws Exception {
        reconnectMultipleTimes(1);
    }

    protected void reconnectMultipleTimes(int attempts) {
        final MerkleInternal teacherTree = createTreeForMap(teacherMap);
        final VirtualMap<TestKey, TestValue> copy = teacherMap.copy();
        final MerkleInternal learnerTree = createTreeForMap(learnerMap);
        try {
            for (int i = 0; i < attempts; i++) {
                try {
                    final var node =
                            MerkleTestUtils.hashAndTestSynchronization(learnerTree, teacherTree, reconnectConfig);
                    node.release();
                    assertEquals(attempts - 1, i, "We should only succeed on the last try");
                } catch (Exception e) {
                    if (i == attempts - 1) {
                        fail("We did not expect an exception on this reconnect attempt!", e);
                    }
                }
            }
        } finally {
            teacherTree.release();
            learnerTree.release();
            copy.release();
        }
    }

    protected static final class BrokenBuilder implements VirtualDataSourceBuilder {

        private static final long CLASS_ID = 0x5a79654cd0f96dcfL;
        private VirtualDataSourceBuilder delegate;
        private int numCallsBeforeThrow = Integer.MAX_VALUE;
        private int numCalls = 0;
        private int numTimesToBreak = 0;
        private int numTimesBroken = 0;

        public BrokenBuilder() {}

        public BrokenBuilder(VirtualDataSourceBuilder delegate) {
            this.delegate = delegate;
        }

        @Override
        public long getClassId() {
            return CLASS_ID;
        }

        @Override
        public int getVersion() {
            return 1;
        }

        @Override
        public void serialize(final SerializableDataOutputStream out) throws IOException {
            delegate.serialize(out);
            out.writeInt(numCallsBeforeThrow);
            out.writeInt(numTimesToBreak);
            out.writeInt(numCalls);
            out.writeInt(numTimesBroken);
        }

        @Override
        public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
            delegate.deserialize(in, version);
            numCallsBeforeThrow = in.readInt();
            numTimesToBreak = in.readInt();
            numCalls = in.readInt();
            numTimesBroken = in.readInt();
        }

        @Override
        public BreakableDataSource build(final String label, final boolean withDbCompactionEnabled) {
            return new BreakableDataSource(this, delegate.build(label, withDbCompactionEnabled));
        }

        @Override
        public BreakableDataSource copy(
                final VirtualDataSource snapshotMe, final boolean makeCopyActive, final boolean offlineUse) {
            final var breakableSnapshot = (BreakableDataSource) snapshotMe;
            return new BreakableDataSource(this, delegate.copy(breakableSnapshot.delegate, makeCopyActive, offlineUse));
        }

        @Override
        public void snapshot(final Path destination, final VirtualDataSource snapshotMe) {
            final var breakableSnapshot = (BreakableDataSource) snapshotMe;
            delegate.snapshot(destination, breakableSnapshot.delegate);
        }

        @Override
        public BreakableDataSource restore(final String label, final Path from) {
            return new BreakableDataSource(this, delegate.restore(label, from));
        }

        public void setNumCallsBeforeThrow(int num) {
            this.numCallsBeforeThrow = num;
        }

        public void setNumTimesToBreak(int num) {
            this.numTimesToBreak = num;
        }
    }

    protected static final class BreakableDataSource implements VirtualDataSource {

        private final VirtualDataSource delegate;
        private final BrokenBuilder builder;

        public BreakableDataSource(final BrokenBuilder builder, final VirtualDataSource delegate) {
            this.delegate = Objects.requireNonNull(delegate);
            this.builder = Objects.requireNonNull(builder);
        }

        @Override
        public void saveRecords(
                final long firstLeafPath,
                final long lastLeafPath,
                @NonNull final Stream<VirtualHashRecord> pathHashRecordsToUpdate,
                @NonNull final Stream<VirtualLeafBytes> leafRecordsToAddOrUpdate,
                @NonNull final Stream<VirtualLeafBytes> leafRecordsToDelete,
                final boolean isReconnectContext)
                throws IOException {
            final List<VirtualLeafBytes> leaves = leafRecordsToAddOrUpdate.toList();

            if (builder.numTimesBroken < builder.numTimesToBreak) {
                builder.numCalls += leaves.size();
                if (builder.numCalls > builder.numCallsBeforeThrow) {
                    builder.numCalls = 0;
                    builder.numTimesBroken++;
                    delegate.close();
                    throw new IOException("Something bad on the DB!");
                }
            }

            delegate.saveRecords(
                    firstLeafPath,
                    lastLeafPath,
                    pathHashRecordsToUpdate,
                    leaves.stream(),
                    leafRecordsToDelete,
                    isReconnectContext);
        }

        @Override
        public void close(final boolean keepData) throws IOException {
            delegate.close(keepData);
        }

        @Override
        public VirtualLeafBytes loadLeafRecord(final Bytes key, final int keyHashCode) throws IOException {
            return delegate.loadLeafRecord(key, keyHashCode);
        }

        @Override
        public VirtualLeafBytes loadLeafRecord(final long path) throws IOException {
            return delegate.loadLeafRecord(path);
        }

        @Override
        public long findKey(final Bytes key, final int keyHashCode) throws IOException {
            return delegate.findKey(key, keyHashCode);
        }

        @Override
        public Hash loadHash(final long path) throws IOException {
            return delegate.loadHash(path);
        }

        @Override
        public void snapshot(final Path snapshotDirectory) throws IOException {
            delegate.snapshot(snapshotDirectory);
        }

        @Override
        public void copyStatisticsFrom(final VirtualDataSource that) {
            delegate.copyStatisticsFrom(that);
        }

        @Override
        public void registerMetrics(final Metrics metrics) {
            delegate.registerMetrics(metrics);
        }

        @Override
        public long getFirstLeafPath() {
            return delegate.getFirstLeafPath();
        }

        @Override
        public long getLastLeafPath() {
            return delegate.getLastLeafPath();
        }

        @Override
        public void enableBackgroundCompaction() {
            delegate.enableBackgroundCompaction();
        }

        @Override
        public void stopAndDisableBackgroundCompaction() {
            delegate.stopAndDisableBackgroundCompaction();
        }

        @Override
        @SuppressWarnings("rawtypes")
        public KeySerializer getKeySerializer() {
            throw new UnsupportedOperationException("This method should never be called");
        }

        @Override
        @SuppressWarnings("rawtypes")
        public ValueSerializer getValueSerializer() {
            throw new UnsupportedOperationException("This method should never be called");
        }
    }

    @AfterEach
    void tearDown() {
        MerkleDbTestUtils.assertAllDatabasesClosed();
    }
}
