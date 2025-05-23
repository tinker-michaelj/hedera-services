// SPDX-License-Identifier: Apache-2.0
module com.swirlds.platform.core.test.fixtures {
    requires transitive com.hedera.node.hapi;
    requires transitive com.swirlds.base;
    requires transitive com.swirlds.common.test.fixtures;
    requires transitive com.swirlds.common;
    requires transitive com.swirlds.component.framework;
    requires transitive com.swirlds.config.api;
    requires transitive com.swirlds.config.extensions.test.fixtures;
    requires transitive com.swirlds.merkle;
    requires transitive com.swirlds.platform.core;
    requires transitive com.swirlds.state.api;
    requires transitive com.swirlds.state.impl.test.fixtures;
    requires transitive com.swirlds.state.impl;
    requires transitive com.swirlds.virtualmap;
    requires transitive org.hiero.base.crypto;
    requires transitive org.hiero.base.utility;
    requires transitive org.hiero.consensus.gossip;
    requires transitive org.hiero.consensus.model.test.fixtures;
    requires transitive org.hiero.consensus.model;
    requires transitive org.junit.jupiter.api;
    requires com.hedera.pbj.runtime;
    requires com.swirlds.base.test.fixtures;
    requires com.swirlds.logging;
    requires com.swirlds.merkledb;
    requires com.swirlds.state.api.test.fixtures;
    requires org.hiero.base.crypto.test.fixtures;
    requires org.hiero.base.utility.test.fixtures;
    requires org.hiero.consensus.utility;
    requires com.github.spotbugs.annotations;
    requires com.google.common;
    requires java.desktop;
    requires org.assertj.core;
    requires org.mockito;

    exports com.swirlds.platform.test.fixtures;
    exports com.swirlds.platform.test.fixtures.stream;
    exports com.swirlds.platform.test.fixtures.event;
    exports com.swirlds.platform.test.fixtures.event.source;
    exports com.swirlds.platform.test.fixtures.event.generator;
    exports com.swirlds.platform.test.fixtures.state;
    exports com.swirlds.platform.test.fixtures.addressbook;
    exports com.swirlds.platform.test.fixtures.crypto;
    exports com.swirlds.platform.test.fixtures.gui;
    exports com.swirlds.platform.test.fixtures.turtle.consensus;
    exports com.swirlds.platform.test.fixtures.turtle.gossip;
    exports com.swirlds.platform.test.fixtures.turtle.runner;
}
