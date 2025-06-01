// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.config.data;

import com.hedera.node.config.NodeProperty;
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import java.time.Duration;

/**
 * Configuration for Connecting to Block Nodes.
 * @param shutdownNodeOnNoBlockNodes whether to shut down the consensus node if there are no block node connections
 * @param blockNodeConnectionFileDir the directory to get the block node configuration file
 * @param blockNodeConfigFile the file containing the block nodes configurations
 * @param maxEndOfStreamsAllowed the limit of EndOfStream responses allowed in a time frame
 * @param endOfStreamTimeFrame the time frame in seconds to check for EndOfStream responses
 * @param endOfStreamScheduleDelay the delay in seconds to schedule connections after the limit is reached
 */
@ConfigData("blockNode")
public record BlockNodeConnectionConfig(
        @ConfigProperty(defaultValue = "false") @NodeProperty boolean shutdownNodeOnNoBlockNodes,
        @ConfigProperty(defaultValue = "data/config") @NodeProperty String blockNodeConnectionFileDir,
        @ConfigProperty(defaultValue = "block-nodes.json") @NodeProperty String blockNodeConfigFile,
        @ConfigProperty(defaultValue = "5") @NodeProperty int maxEndOfStreamsAllowed,
        @ConfigProperty(defaultValue = "30s") @NodeProperty Duration endOfStreamTimeFrame,
        @ConfigProperty(defaultValue = "30s") @NodeProperty Duration endOfStreamScheduleDelay) {}
