// SPDX-License-Identifier: Apache-2.0
package org.hiero.otter.fixtures.assertions;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.assertj.core.api.AbstractAssert;
import org.hiero.otter.fixtures.OtterAssertions;
import org.hiero.otter.fixtures.result.MultipleNodePcesResults;
import org.hiero.otter.fixtures.result.SingleNodePcesResult;

/**
 * Assertions for {@link MultipleNodePcesResults}.
 */
@SuppressWarnings({"UnusedReturnValue", "unused"})
public class MultipleNodePcesResultsAssert
        extends AbstractAssert<MultipleNodePcesResultsAssert, MultipleNodePcesResults> {

    /**
     * Constructor of {@link MultipleNodePcesResultsAssert}
     *
     * @param actual the actual {@link MultipleNodePcesResults} to assert
     */
    public MultipleNodePcesResultsAssert(@Nullable final MultipleNodePcesResults actual) {
        super(actual, MultipleNodePcesResultsAssert.class);
    }

    /**
     * Creates an assertion for the given {@link MultipleNodePcesResults}.
     *
     * @param actual the {@link MultipleNodePcesResults} to assert
     * @return an assertion for the given {@link MultipleNodePcesResults}
     */
    @NonNull
    public static MultipleNodePcesResultsAssert assertThat(@Nullable final MultipleNodePcesResults actual) {
        return new MultipleNodePcesResultsAssert(actual);
    }

    /**
     * Asserts that all events stored in the PCES files of all nodes have a birth round equal to the given value.
     *
     * @param expected the expected birth round
     * @return this assertion object for method chaining
     */
    @NonNull
    public MultipleNodePcesResultsAssert hasAllBirthRoundsEqualTo(final long expected) {
        // assert that actual is not null
        isNotNull();

        for (final SingleNodePcesResult pcesResult : actual.pcesResults()) {
            OtterAssertions.assertThat(pcesResult).hasAllBirthRoundsEqualTo(expected);
        }

        return this;
    }

    /**
     * Asserts that the maximum birth round of all events stored in the PCES files of all nodes is equal to the given value.
     *
     * @param expected the expected maximum birth round
     * @return this assertion object for method chaining
     */
    @NonNull
    public MultipleNodePcesResultsAssert hasMaxBirthRoundEqualTo(final long expected) {
        // assert that actual is not null
        isNotNull();

        for (final SingleNodePcesResult pcesResult : actual.pcesResults()) {
            OtterAssertions.assertThat(pcesResult).hasMaxBirthRoundEqualTo(expected);
        }

        return this;
    }

    /**
     * Asserts that the maximum birth round of all events stored in the PCES files of all nodes is less than the given value.
     *
     * @param expected the expected maximum birth round
     * @return this assertion object for method chaining
     */
    @NonNull
    public MultipleNodePcesResultsAssert hasMaxBirthRoundLessThan(final long expected) {
        // assert that actual is not null
        isNotNull();

        for (final SingleNodePcesResult pcesResult : actual.pcesResults()) {
            OtterAssertions.assertThat(pcesResult).hasMaxBirthRoundLessThan(expected);
        }

        return this;
    }

    /**
     * Asserts that the maximum birth round of all events stored in the PCES files of all nodes is less than or equal to the given value.
     * value.
     *
     * @param expected the expected maximum birth round
     * @return this assertion object for method chaining
     */
    @NonNull
    public MultipleNodePcesResultsAssert hasMaxBirthRoundLessThanOrEqualTo(final long expected) {
        // assert that actual is not null
        isNotNull();

        for (final SingleNodePcesResult pcesResult : actual.pcesResults()) {
            OtterAssertions.assertThat(pcesResult).hasMaxBirthRoundLessThanOrEqualTo(expected);
        }

        return this;
    }

    /**
     * Asserts that the maximum birth round of all events stored in the PCES files of all nodes is greater than the given value.
     *
     * @param expected the expected minimum birth round
     * @return this assertion object for method chaining
     */
    @NonNull
    public MultipleNodePcesResultsAssert hasMaxBirthRoundGreaterThan(final long expected) {
        // assert that actual is not null
        isNotNull();

        for (final SingleNodePcesResult pcesResult : actual.pcesResults()) {
            OtterAssertions.assertThat(pcesResult).hasMaxBirthRoundGreaterThan(expected);
        }

        return this;
    }

    /**
     * Asserts that the maximum birth round of all events stored in the PCES files of all nodes is greater than or equal to the given value.
     *
     * @param expected the expected minimum birth round
     * @return this assertion object for method chaining
     */
    @NonNull
    public MultipleNodePcesResultsAssert hasMaxBirthRoundGreaterThanOrEqualTo(final long expected) {
        // assert that actual is not null
        isNotNull();

        for (final SingleNodePcesResult pcesResult : actual.pcesResults()) {
            OtterAssertions.assertThat(pcesResult).hasMaxBirthRoundGreaterThanOrEqualTo(expected);
        }

        return this;
    }
}
