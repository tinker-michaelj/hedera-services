/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.usage.token;

import static com.hedera.services.usage.EstimatorUtils.MAX_ENTITY_LIFETIME;
import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hedera.services.usage.token.entities.TokenEntitySizes.TOKEN_ENTITY_SIZES;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.LONG_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.getAccountKeyStorageSize;

import com.google.protobuf.ByteString;
import com.hedera.services.usage.token.entities.TokenEntitySizes;
import com.hedera.services.usage.token.meta.TokenBurnMeta;
import com.hedera.services.usage.token.meta.TokenCreateMeta;
import com.hedera.services.usage.token.meta.TokenFreezeMeta;
import com.hedera.services.usage.token.meta.TokenMintMeta;
import com.hedera.services.usage.token.meta.TokenPauseMeta;
import com.hedera.services.usage.token.meta.TokenUnfreezeMeta;
import com.hedera.services.usage.token.meta.TokenUnpauseMeta;
import com.hedera.services.usage.token.meta.TokenWipeMeta;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Predicate;

public enum TokenOpsUsageUtils {
    TOKEN_OPS_USAGE_UTILS;

    private static final int AMOUNT_REPR_BYTES = 8;

    public TokenCreateMeta tokenCreateUsageFrom(final TransactionBody txn) {
        final var baseSize = getTokenTxnBaseSize(txn);

        final var op = txn.getTokenCreation();
        var lifetime =
                op.hasAutoRenewAccount()
                        ? op.getAutoRenewPeriod().getSeconds()
                        : ESTIMATOR_UTILS.relativeLifetime(txn, op.getExpiry().getSeconds());
        lifetime = Math.min(lifetime, MAX_ENTITY_LIFETIME);

        final var tokenOpsUsage = new TokenOpsUsage();
        final var feeSchedulesSize =
                op.getCustomFeesCount() > 0
                        ? tokenOpsUsage.bytesNeededToRepr(op.getCustomFeesList())
                        : 0;

        SubType chosenType;
        final var usesCustomFees = op.hasFeeScheduleKey() || op.getCustomFeesCount() > 0;
        if (op.getTokenType() == NON_FUNGIBLE_UNIQUE) {
            chosenType =
                    usesCustomFees
                            ? TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES
                            : TOKEN_NON_FUNGIBLE_UNIQUE;
        } else {
            chosenType =
                    usesCustomFees ? TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES : TOKEN_FUNGIBLE_COMMON;
        }

        return new TokenCreateMeta.Builder()
                .baseSize(baseSize)
                .lifeTime(lifetime)
                .customFeeScheleSize(feeSchedulesSize)
                .fungibleNumTransfers(op.getInitialSupply() > 0 ? 1 : 0)
                .nftsTranfers(0)
                .numTokens(1)
                .networkRecordRb(BASIC_ENTITY_ID_SIZE)
                .subType(chosenType)
                .build();
    }

    public TokenMintMeta tokenMintUsageFrom(
            final TransactionBody txn, final SubType subType, final long expectedLifeTime) {
        var op = txn.getTokenMint();
        int bpt = 0;
        long rbs = 0;
        int transferRecordRb = 0;
        if (subType == TOKEN_NON_FUNGIBLE_UNIQUE) {
            var metadataBytes = 0;
            for (ByteString o : op.getMetadataList()) {
                metadataBytes += o.size();
            }
            bpt = metadataBytes;
            rbs = metadataBytes * expectedLifeTime;
            transferRecordRb =
                    TOKEN_ENTITY_SIZES.bytesUsedToRecordTokenTransfers(1, 0, op.getMetadataCount());
        } else {
            bpt = AMOUNT_REPR_BYTES;
            transferRecordRb = TOKEN_ENTITY_SIZES.bytesUsedToRecordTokenTransfers(1, 1, 0);
        }
        bpt += BASIC_ENTITY_ID_SIZE;
        return new TokenMintMeta(bpt, subType, transferRecordRb, rbs);
    }

    public TokenFreezeMeta tokenFreezeUsageFrom() {
        return new TokenFreezeMeta(2 * BASIC_ENTITY_ID_SIZE);
    }

    public TokenUnfreezeMeta tokenUnfreezeUsageFrom() {
        return new TokenUnfreezeMeta(2 * BASIC_ENTITY_ID_SIZE);
    }

    public TokenPauseMeta tokenPauseUsageFrom() {
        return new TokenPauseMeta(BASIC_ENTITY_ID_SIZE);
    }

    public TokenUnpauseMeta tokenUnpauseUsageFrom() {
        return new TokenUnpauseMeta(BASIC_ENTITY_ID_SIZE);
    }

    public TokenBurnMeta tokenBurnUsageFrom(final TransactionBody txn) {
        var op = txn.getTokenBurn();
        final var subType =
                op.getSerialNumbersCount() > 0 ? TOKEN_NON_FUNGIBLE_UNIQUE : TOKEN_FUNGIBLE_COMMON;
        return tokenBurnUsageFrom(txn, subType);
    }

    public TokenBurnMeta tokenBurnUsageFrom(final TransactionBody txn, final SubType subType) {
        var op = txn.getTokenBurn();
        return retrieveRawDataFrom(subType, op::getSerialNumbersCount, TokenBurnMeta::new);
    }

    public TokenWipeMeta tokenWipeUsageFrom(final TransactionBody txn) {
        var op = txn.getTokenWipe();
        final var subType =
                op.getSerialNumbersCount() > 0 ? TOKEN_NON_FUNGIBLE_UNIQUE : TOKEN_FUNGIBLE_COMMON;
        return tokenWipeUsageFrom(txn, subType);
    }

    public TokenWipeMeta tokenWipeUsageFrom(final TransactionBody txn, final SubType subType) {
        var op = txn.getTokenWipe();

        return retrieveRawDataFrom(subType, op::getSerialNumbersCount, TokenWipeMeta::new);
    }

    public <R> R retrieveRawDataFrom(
            SubType subType, IntSupplier getDataForNFT, Producer<R> producer) {
        int serialNumsCount = 0;
        int bpt = 0;
        int transferRecordRb = 0;
        if (subType == TOKEN_NON_FUNGIBLE_UNIQUE) {
            serialNumsCount = getDataForNFT.getAsInt();
            transferRecordRb =
                    TOKEN_ENTITY_SIZES.bytesUsedToRecordTokenTransfers(1, 0, serialNumsCount);
            bpt = serialNumsCount * LONG_SIZE;
        } else {
            bpt = AMOUNT_REPR_BYTES;
            transferRecordRb = TOKEN_ENTITY_SIZES.bytesUsedToRecordTokenTransfers(1, 1, 0);
        }
        bpt += BASIC_ENTITY_ID_SIZE;

        return producer.create(bpt, subType, transferRecordRb, serialNumsCount);
    }

    @FunctionalInterface
    interface Producer<R> {
        R create(int bpt, SubType subType, long recordDb, int t);
    }

    public int getTokenTxnBaseSize(final TransactionBody txn) {
        final var op = txn.getTokenCreation();

        final var tokenEntitySizes = TokenEntitySizes.TOKEN_ENTITY_SIZES;
        var baseSize = tokenEntitySizes.totalBytesInTokenReprGiven(op.getSymbol(), op.getName());
        baseSize +=
                keySizeIfPresent(
                        op,
                        TokenCreateTransactionBody::hasKycKey,
                        TokenCreateTransactionBody::getKycKey);
        baseSize +=
                keySizeIfPresent(
                        op,
                        TokenCreateTransactionBody::hasWipeKey,
                        TokenCreateTransactionBody::getWipeKey);
        baseSize +=
                keySizeIfPresent(
                        op,
                        TokenCreateTransactionBody::hasAdminKey,
                        TokenCreateTransactionBody::getAdminKey);
        baseSize +=
                keySizeIfPresent(
                        op,
                        TokenCreateTransactionBody::hasSupplyKey,
                        TokenCreateTransactionBody::getSupplyKey);
        baseSize +=
                keySizeIfPresent(
                        op,
                        TokenCreateTransactionBody::hasFreezeKey,
                        TokenCreateTransactionBody::getFreezeKey);
        baseSize +=
                keySizeIfPresent(
                        op,
                        TokenCreateTransactionBody::hasFeeScheduleKey,
                        TokenCreateTransactionBody::getFeeScheduleKey);
        baseSize +=
                keySizeIfPresent(
                        op,
                        TokenCreateTransactionBody::hasPauseKey,
                        TokenCreateTransactionBody::getPauseKey);
        baseSize += op.getMemoBytes().size();
        if (op.hasAutoRenewAccount()) {
            baseSize += BASIC_ENTITY_ID_SIZE;
        }
        return baseSize;
    }

    public static <T> long keySizeIfPresent(
            final T op, final Predicate<T> check, final Function<T, Key> getter) {
        return check.test(op) ? getAccountKeyStorageSize(getter.apply(op)) : 0L;
    }
}
