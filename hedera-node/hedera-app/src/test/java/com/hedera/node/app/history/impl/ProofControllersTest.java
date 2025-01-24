/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.history.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.state.history.HistoryProof;
import com.hedera.hapi.node.state.history.HistoryProofConstruction;
import com.hedera.node.app.history.HistoryLibrary;
import com.hedera.node.app.history.ReadableHistoryStore;
import com.hedera.node.app.roster.ActiveRosters;
import com.hedera.node.app.roster.RosterTransitionWeights;
import com.swirlds.state.lifecycle.info.NodeInfo;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProofControllersTest {
    @Mock
    private Executor executor;

    @Mock
    private ProofKeysAccessor keyAccessor;

    @Mock
    private HistoryLibrary library;

    @Mock
    private HistoryLibraryCodec codec;

    @Mock
    private HistorySubmissions submissions;

    @Mock
    private Supplier<NodeInfo> selfNodeInfoSupplier;

    @Mock
    private Consumer<HistoryProof> proofConsumer;

    @Mock
    private ActiveRosters activeRosters;

    @Mock
    private RosterTransitionWeights weights;

    @Mock
    private ReadableHistoryStore historyStore;

    private ProofControllers subject;

    @BeforeEach
    void setUp() {
        subject = new ProofControllers(
                executor, keyAccessor, library, codec, submissions, selfNodeInfoSupplier, proofConsumer);
    }

    @Test
    void getsAndCreatesInertControllersAsExpected() {
        given(activeRosters.transitionWeights()).willReturn(weights);

        final var oneConstruction =
                HistoryProofConstruction.newBuilder().constructionId(1L).build();
        final var twoConstruction =
                HistoryProofConstruction.newBuilder().constructionId(2L).build();

        assertTrue(subject.getAnyInProgress().isEmpty());
        final var firstController = subject.getOrCreateFor(activeRosters, oneConstruction, historyStore);
        assertTrue(subject.getAnyInProgress().isEmpty());
        assertTrue(subject.getInProgressById(1L).isEmpty());
        assertTrue(subject.getInProgressById(2L).isEmpty());
        assertInstanceOf(InertProofController.class, firstController);
        final var secondController = subject.getOrCreateFor(activeRosters, twoConstruction, historyStore);
        assertNotSame(firstController, secondController);
        assertInstanceOf(InertProofController.class, secondController);
    }
}
