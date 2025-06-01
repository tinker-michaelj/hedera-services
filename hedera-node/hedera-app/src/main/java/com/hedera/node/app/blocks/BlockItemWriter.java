// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.blocks;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.node.internal.network.PendingProof;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Writes serialized block items to a destination stream.
 */
public interface BlockItemWriter {
    /**
     * Opens a block for writing.
     *
     * @param blockNumber the number of the block to open
     */
    void openBlock(long blockNumber);

    /**
     * Writes an item and/or its serialized bytes to the destination stream.
     *
     * @param item the item to write
     * @param bytes the serialized item to write
     */
    default void writePbjItemAndBytes(@NonNull final BlockItem item, @NonNull final Bytes bytes) {
        requireNonNull(item);
        requireNonNull(bytes);
        writeItem(bytes.toByteArray());
    }

    /**
     * Writes a serialized item to the destination stream.
     *
     * @param bytes the serialized item to write
     */
    void writeItem(@NonNull byte[] bytes);

    /**
     * Writes a PBJ item to the destination stream.
     * @param item the item to write
     */
    void writePbjItem(@NonNull final BlockItem item);

    /**
     * Closes a block that is complete with a proof.
     */
    void closeCompleteBlock();

    /**
     * Flushes to disk a block that is still waiting for a complete proof.
     * @param pendingProof the proof pending a signature
     */
    void flushPendingBlock(@NonNull PendingProof pendingProof);

    /**
     * Performs any actions that need to be done before the block proof is complete.
     */
    void writePreBlockProofItems();
}
