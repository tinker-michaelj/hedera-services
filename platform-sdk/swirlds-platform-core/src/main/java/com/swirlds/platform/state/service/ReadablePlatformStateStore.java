// SPDX-License-Identifier: Apache-2.0
package com.swirlds.platform.state.service;

import static com.swirlds.platform.state.service.schemas.V0540PlatformStateSchema.PLATFORM_STATE_KEY;
import static java.util.Objects.requireNonNull;
import static org.hiero.base.utility.CommonUtils.fromPbjTimestamp;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.platform.state.ConsensusSnapshot;
import com.hedera.hapi.platform.state.PlatformState;
import com.swirlds.platform.state.PlatformStateAccessor;
import com.swirlds.state.State;
import com.swirlds.state.spi.ReadableSingletonState;
import com.swirlds.state.spi.ReadableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import org.hiero.base.crypto.Hash;

/**
 * Gives read-only access to the platform state, encapsulating conversion from PBJ types to the current types
 * in use by the platform.
 */
public class ReadablePlatformStateStore implements PlatformStateAccessor {

    private final ReadableSingletonState<PlatformState> state;

    /**
     * Constructor
     * Must be used from within {@link State}.
     * @param readableStates the readable states
     */
    public ReadablePlatformStateStore(@NonNull final ReadableStates readableStates) {
        this.state = requireNonNull(readableStates).getSingleton(PLATFORM_STATE_KEY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public SemanticVersion getCreationSoftwareVersion() {
        return stateOrThrow().creationSoftwareVersionOrThrow();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getRound() {
        final var consensusSnapshot = stateOrThrow().consensusSnapshot();
        if (consensusSnapshot == null) {
            return GENESIS_ROUND;
        } else {
            return consensusSnapshot.round();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public Hash getLegacyRunningEventHash() {
        final var hash = stateOrThrow().legacyRunningEventHash();
        return hash.length() == 0 ? null : new Hash(hash);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public Instant getConsensusTimestamp() {
        final var consensusSnapshot = stateOrThrow().consensusSnapshot();
        if (consensusSnapshot == null) {
            return null;
        }
        return fromPbjTimestamp(consensusSnapshot.consensusTimestamp());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getAncientThreshold() {
        final var consensusSnapshot = stateOrThrow().consensusSnapshot();
        requireNonNull(consensusSnapshot, "No minimum judge info found in state for round, snapshot is null");
        final var minimumJudgeInfos = consensusSnapshot.minimumJudgeInfoList();
        if (minimumJudgeInfos.isEmpty()) {
            throw new IllegalStateException(
                    "No minimum judge info found in state for round " + consensusSnapshot.round() + ", list is empty");
        }
        return minimumJudgeInfos.getFirst().minimumJudgeAncientThreshold();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getRoundsNonAncient() {
        return stateOrThrow().roundsNonAncient();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public ConsensusSnapshot getSnapshot() {
        return stateOrThrow().consensusSnapshot();
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public Instant getFreezeTime() {
        return fromPbjTimestamp(stateOrThrow().freezeTime());
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public Instant getLastFrozenTime() {
        return fromPbjTimestamp(stateOrThrow().lastFrozenTime());
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public SemanticVersion getFirstVersionInBirthRoundMode() {
        return stateOrThrow().firstVersionInBirthRoundMode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getLastRoundBeforeBirthRoundMode() {
        return stateOrThrow().lastRoundBeforeBirthRoundMode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getLowestJudgeGenerationBeforeBirthRoundMode() {
        return stateOrThrow().lowestJudgeGenerationBeforeBirthRoundMode();
    }

    private @NonNull PlatformState stateOrThrow() {
        return requireNonNull(state.get());
    }
}
