// SPDX-License-Identifier: Apache-2.0
package com.swirlds.common.merkle.impl.destroyable;

import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.common.merkle.impl.internal.AbstractMerkleNode;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * A variant of {@link PartialMerkleLeaf} that stores a callback that is invoked when
 * {@link AbstractMerkleNode#destroyNode() destroyNode()} is called.
 */
public class DestroyableMerkleLeaf extends PartialMerkleLeaf {

    private final Runnable onDestroy;

    /**
     * Create a new abstract node.
     *
     * @param onDestroy
     * 		called when this node is destroyed
     */
    public DestroyableMerkleLeaf(@NonNull final Runnable onDestroy) {
        this.onDestroy = Objects.requireNonNull(onDestroy, "onDestroy must not be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void destroyNode() {
        onDestroy.run();
    }
}
