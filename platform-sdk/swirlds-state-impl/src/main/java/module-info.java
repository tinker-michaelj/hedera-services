// SPDX-License-Identifier: Apache-2.0
module com.swirlds.state.impl {
    exports com.swirlds.state.merkle.singleton;
    exports com.swirlds.state.merkle.queue;
    exports com.swirlds.state.merkle.memory;
    exports com.swirlds.state.merkle.disk;
    exports com.swirlds.state.merkle;

    // allow reflective access for tests
    opens com.swirlds.state.merkle.disk to
            com.hedera.node.app;

    requires transitive com.hedera.pbj.runtime;
    requires transitive com.swirlds.base;
    requires transitive com.swirlds.common;
    requires transitive com.swirlds.merkle;
    requires transitive com.swirlds.metrics.api;
    requires transitive com.swirlds.state.api;
    requires transitive com.swirlds.virtualmap;
    requires transitive org.hiero.base.utility;
    requires com.hedera.node.hapi;
    requires com.swirlds.fcqueue;
    requires com.swirlds.logging;
    requires org.hiero.base.crypto;
    requires org.apache.logging.log4j;
    requires static transitive com.github.spotbugs.annotations;
}
