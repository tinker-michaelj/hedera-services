// SPDX-License-Identifier: Apache-2.0
package com.hedera.services.bdd.suites.autumn;

import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.SpecOperation;
import com.hedera.services.bdd.spec.dsl.annotations.Contract;
import com.hedera.services.bdd.spec.dsl.entities.SpecContract;
import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;

public class AutumnTest {
    private static final SplittableRandom RANDOM = new SplittableRandom();

    private record Subscription(String subscriber, long offeringId) {}

    @HapiTest
    final Stream<DynamicTest> createAndUse(
            @Contract(contract = "TransparentSubscriptions", creationGas = 5_000_000) SpecContract contract) {
        final int numCreators = 5;
        final int numSubscribers = 15;
        final int minSubInterval = 1;
        final int maxSubInterval = 5;
        final int numInitialOfferings = 5;
        final int timeBetweenNewSubs = 4;
        final int timeBetweenNewOfferings = 12;

        final Queue<Long> creatorIds = new ConcurrentLinkedQueue<>();
        final Queue<Long> offeringIds = new ConcurrentLinkedQueue<>();
        final Queue<Long> subscriberIds = new ConcurrentLinkedQueue<>();
        final Supplier<String> randomCreator = () -> "creator" + RANDOM.nextInt(creatorIds.size());
        final Supplier<String> randomSubscriber = () -> "subscriber" + RANDOM.nextInt(subscriberIds.size());
        final AtomicInteger secondsRemaining = new AtomicInteger(120);
        final Set<Subscription> existing = ConcurrentHashMap.newKeySet();
        return hapiTest(
                inParallel(IntStream.range(0, numCreators)
                        .mapToObj(i -> cryptoCreate("creator" + i)
                                .balance(ONE_HUNDRED_HBARS * 1000)
                                .exposingCreatedIdTo(creatorId -> creatorIds.add(creatorId.getAccountNum())))
                        .toArray(SpecOperation[]::new)),
                inParallel(IntStream.range(0, numSubscribers)
                        .mapToObj(i -> cryptoCreate("subscriber" + i)
                                .balance(ONE_HUNDRED_HBARS * 1000)
                                .exposingCreatedIdTo(subscriberId -> subscriberIds.add(subscriberId.getAccountNum())))
                        .toArray(SpecOperation[]::new)),
                inParallel(IntStream.range(0, numInitialOfferings)
                        .mapToObj(i -> {
                            final List<String> offering = OFFERINGS[RANDOM.nextInt(OFFERINGS.length)];
                            return contract.call(
                                            "registerOffering",
                                            offering.getFirst(),
                                            offering.getLast(),
                                            BigInteger.ONE,
                                            BigInteger.valueOf(RANDOM.nextInt(minSubInterval, maxSubInterval)))
                                    .with(op -> op.payingWith(randomCreator.get()))
                                    .gas(500_000L)
                                    .exposingResultTo(res -> offeringIds.add((Long) res[0]));
                        })
                        .toArray(SpecOperation[]::new)),
                logIt(ignore -> "Created " + numInitialOfferings + " initial offerings"),
                withOpContext((spec, opLog) -> {
                    opLog.info("Offering ids: {}", offeringIds);
                    opLog.info("Creator ids: {}", creatorIds);
                    opLog.info("Subscriber ids: {}", subscriberIds);
                    int tick;
                    while ((tick = secondsRemaining.decrementAndGet()) > 0) {
                        if (tick % timeBetweenNewOfferings == 0) {
                            final List<String> offering = OFFERINGS[RANDOM.nextInt(OFFERINGS.length)];
                            allRunFor(
                                    spec,
                                    contract.call(
                                                    "registerOffering",
                                                    offering.getFirst(),
                                                    offering.getLast(),
                                                    BigInteger.ONE,
                                                    BigInteger.valueOf(RANDOM.nextInt(minSubInterval, maxSubInterval)))
                                            .with(op -> op.payingWith(randomCreator.get()))
                                            .gas(500_000L)
                                            .exposingResultTo(res -> offeringIds.add((Long) res[0])));
                        }
                        if (tick % timeBetweenNewSubs == 0) {
                            final long choice = randomChoiceFrom(offeringIds);
                            final var subscriber = randomSubscriber.get();
                            final var subscription = new Subscription(subscriber, choice);
                            if (!existing.add(subscription)) {
                                continue;
                            }
                            final var subscriberId = spec.registry().getAccountID(subscriber);
                            opLog.info("SUBSCRIBING {} (0.0.{}) to offering #{}", subscriber, subscriberId.getAccountNum(), choice);
                            allRunFor(
                                    spec,
                                    contract.call("subscribe", choice)
                                            .with(op -> op.payingWith(subscriber))
                                            .gas(500_000L));
                        }
                        allRunFor(spec, contract.call("processTick").gas(5_000_000L));
                        TimeUnit.SECONDS.sleep(1);
                        opLog.info(">>>>");
                        opLog.info("==== {} TICKS REMAINING ====", tick);
                        opLog.info("<<<<");
                    }
                }));
    }

    private Long randomChoiceFrom(Collection<Long> choices) {
        final int n = choices.size();
        if (n == 0) {
            throw new IllegalStateException("No choices available");
        }
        final int randomIndex = RANDOM.nextInt(n);
        return choices.stream().skip(randomIndex).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static final List<String>[] OFFERINGS = (List<String>[]) new List[] {
        List.of(
                "HBARista Coffee Club",
                "A daily micro-dose of caffeine automatically extracted from your wallet—and probably your sanity."),
        List.of(
                "Graph & Snack",
                "Monthly delivery of artisanal trail mix, optimized for peak chain-analysis munchies."),
        List.of(
                "Infinite Zoom Yoga",
                "Auto-renewing zen sessions that never quite finish loading, but always bill on time."),
        List.of(
                "Hash-Tag Removal Service",
                "We delete embarrassing hashtags from your old tweets. (Results may vary; billing will not.)"),
        List.of(
                "Pay-per-Meow Cat Cam",
                "Stream our office feline 24/7; pay every time she blinks. Surge pricing during zoomies."),
        List.of(
                "Debugger’s Anonymous",
                "Weekly group therapy for devs who println in production. Unlimited tissue supply included."),
        List.of(
                "Proof-of-Steak Dinner Club",
                "Stake your appetite—receive a perfectly medium-rare steak on the first epoch of every month."),
        List.of("Nonce-Sense Joke Pack", "Fresh cryptography puns delivered daily until everyone in Slack rage-quits."),
        List.of(
                "Gaslight Candle Co.",
                "Scented candles that insist the room was always this dark. Auto-ship every fortnight."),
        List.of("404 Fitness", "“Workout not found.” But we keep charging until you locate it."),
        List.of(
                "FUD Busters Newsletters",
                "We show up in your inbox precisely when token prices drop, saying “told you so.”"),
        List.of("Hedera Hair Club", "Haircuts scheduled by consensus time. Look sharp, stay decentralized."),
        List.of("Byte-Sized Burritos", "Micro-burritos the size of USB-C ports. Forty-two delivered every Friday."),
        List.of("Alt-Tab Meditation", "Guided mindfulness whenever you switch windows. (Which is constantly.)"),
        List.of("Fork-Lift Moving Co.", "We promise not to fork your chain—just your furniture. Billing per crate."),
        List.of(
                "Doc-String Whisperers",
                "We rewrite your comments into actual English. Charged per passive-aggressive sigh."),
        List.of("Loop-less Crochet Club", "Hand-crafted sweaters with zero for-loops. Pure recursion, pure comfort."),
        List.of(
                "Payload Parcel Service",
                "We ship empty boxes to simulate faster block propagation. Fees definitely not empty."),
        List.of(
                "Merge-Conflict Choir",
                "A quartet harmonizing “¯\\\\\\(ツ)/¯” every time Git yells at you. Subscriptions include earplugs."),
        List.of(
                "Quantum Coffee “Maybe” Plan",
                "You both have and have not bought coffee until the scheduler debits you. Schrödinger’s latte."),
        List.of(
                "Shard-on-the-Rocks Bar",
                "Cocktails distributed across multiple mixologists. Strong eventual consistency guaranteed."),
        List.of("Retroactive Holiday Calendar", "Surprise! Yesterday was a holiday; we already billed you for it."),
        List.of("Stack Overflow Overflow", "Unlimited copies of the same answer you already found—now in NFT form."),
        List.of("Idle-Loop Lullabies", "Ambient CPU-fan noise streamed as bedtime music. Charged per spin cycle."),
        List.of("Rate-Limit Gym", "You can only work out 429 times a day. Overages incur 502 Bad Body errors."),
    };
}
