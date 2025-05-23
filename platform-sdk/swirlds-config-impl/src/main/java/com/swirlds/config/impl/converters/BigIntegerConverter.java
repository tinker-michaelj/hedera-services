// SPDX-License-Identifier: Apache-2.0
package com.swirlds.config.impl.converters;

import com.swirlds.config.api.converter.ConfigConverter;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;

/**
 * Concrete {@link ConfigConverter} implementation that provides the support for {@link BigInteger} values in the
 * configuration.
 */
public final class BigIntegerConverter implements ConfigConverter<BigInteger> {

    /**
     * {@inheritDoc}
     */
    @Override
    public BigInteger convert(@NonNull final String value) throws IllegalArgumentException {
        return new BigInteger(value);
    }
}
