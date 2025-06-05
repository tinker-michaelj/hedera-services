// SPDX-License-Identifier: Apache-2.0
package com.swirlds.platform.consensus;

import com.hedera.hapi.platform.state.ConsensusSnapshot;
import com.hedera.hapi.platform.state.JudgeId;
import com.hedera.hapi.platform.state.MinimumJudgeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.List;
import java.util.stream.LongStream;
import org.hiero.base.utility.CommonUtils;
import org.hiero.consensus.model.event.AncientMode;
import org.hiero.consensus.model.event.PlatformEvent;
import org.hiero.consensus.model.hashgraph.ConsensusConstants;

/**
 * Utility class for generating "synthetic" snapshots
 *
 * @deprecated this class is deprecated and will be removed in a future version. Use {@link ConsensusSnapshot} instead.
 */
@Deprecated(forRemoval = true, since = "0.63.0")
public final class SyntheticSnapshot {
    /**
     * Utility class, should not be instantiated
     */
    private SyntheticSnapshot() {}

    /**
     * Generate a {@link ConsensusSnapshot} based on the supplied data. This snapshot is not the result of consensus
     * but is instead generated to be used as a starting point for consensus. The snapshot will contain a single
     * judge whose generation will be almost ancient. All events older than the judge will be considered ancient.
     * The judge is the only event needed to continue consensus operations. Once the judge is added to
     * {@link com.swirlds.platform.Consensus}, it will be marked as already having reached consensus beforehand, so it
     * will not reach consensus again.
     *
     * @param round              the round of the snapshot
     * @param lastConsensusOrder the last consensus order of all events that have reached consensus
     * @param roundTimestamp     the timestamp of the round
     * @param config             the consensus configuration
     * @param ancientMode        the ancient mode
     * @param judge              the judge event
     * @return the synthetic snapshot
     */
    public static @NonNull ConsensusSnapshot generateSyntheticSnapshot(
            final long round,
            final long lastConsensusOrder,
            @NonNull final Instant roundTimestamp,
            @NonNull final ConsensusConfig config,
            @NonNull final AncientMode ancientMode,
            @NonNull final PlatformEvent judge) {
        final List<MinimumJudgeInfo> minimumJudgeInfos = LongStream.range(
                        RoundCalculationUtils.getOldestNonAncientRound(config.roundsNonAncient(), round), round + 1)
                .mapToObj(r -> new MinimumJudgeInfo(r, ancientMode.selectIndicator(judge)))
                .toList();
        return ConsensusSnapshot.newBuilder()
                .round(round)
                .judgeIds(List.of(JudgeId.newBuilder()
                        .creatorId(judge.getCreatorId().id())
                        .judgeHash(judge.getHash().getBytes())
                        .build()))
                .minimumJudgeInfoList(minimumJudgeInfos)
                .nextConsensusNumber(lastConsensusOrder + 1)
                .consensusTimestamp(
                        CommonUtils.toPbjTimestamp(ConsensusUtils.calcMinTimestampForNextEvent(roundTimestamp)))
                .build();
    }

    /**
     * Create a genesis snapshot. This snapshot is not the result of consensus but is instead generated to be used as a
     * starting point for consensus.
     *
     * @return the genesis snapshot, when loaded by consensus, it will start from genesis
     */
    public static @NonNull ConsensusSnapshot getGenesisSnapshot() {
        return ConsensusSnapshot.newBuilder()
                .round(ConsensusConstants.ROUND_FIRST)
                .judgeIds(List.of())
                .minimumJudgeInfoList(
                        List.of(new MinimumJudgeInfo(ConsensusConstants.ROUND_FIRST, ConsensusConstants.ROUND_FIRST)))
                .nextConsensusNumber(ConsensusConstants.FIRST_CONSENSUS_NUMBER)
                .consensusTimestamp(CommonUtils.toPbjTimestamp(Instant.EPOCH))
                .build();
    }
}
