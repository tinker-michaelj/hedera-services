// SPDX-License-Identifier: Apache-2.0
package com.hedera.services.bdd.suites.autumn;

import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.SpecOperation;
import com.hedera.services.bdd.spec.dsl.annotations.Contract;
import com.hedera.services.bdd.spec.dsl.entities.SpecContract;
import java.math.BigInteger;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;

public class AutumnTest {
    private static final SplittableRandom RANDOM = new SplittableRandom();

    @HapiTest
    final Stream<DynamicTest> createWithNOfferings(
            @Contract(contract = "TransparentSubscriptions", creationGas = 5_000_000) SpecContract contract) {
        final int n = 5;
        final int minSubInterval = 1;
        final int maxSubInterval = 5;
        final Map<Integer, Long> indices = new ConcurrentHashMap<>();
        return hapiTest(
                cryptoCreate("creator").advertisingCreation(),
                inParallel(IntStream.range(0, n)
                        .mapToObj(i -> contract.call(
                                        "registerOffering",
                                        BigInteger.ONE,
                                        BigInteger.valueOf(RANDOM.nextInt(minSubInterval, maxSubInterval)))
                                .gas(500_000L)
                                .exposingResultTo(res -> indices.put(i, (Long) res[0])))
                        .toArray(SpecOperation[]::new)),
                logIt(ignore -> "Created " + n + " offerings with indices: " + indices));
    }
}
