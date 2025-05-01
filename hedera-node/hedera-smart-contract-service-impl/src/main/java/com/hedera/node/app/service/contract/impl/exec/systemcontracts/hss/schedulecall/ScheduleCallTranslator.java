// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hss.schedulecall;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.gas.DispatchType;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.metrics.ContractMetrics;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.AbstractCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.Call;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hss.HssCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallFactory;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.node.app.service.contract.impl.exec.utils.SystemContractMethod;
import com.hedera.node.app.service.contract.impl.exec.utils.SystemContractMethod.Category;
import com.hedera.node.app.service.contract.impl.exec.utils.SystemContractMethodRegistry;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.tuweni.bytes.Bytes;

@Singleton
public class ScheduleCallTranslator extends AbstractCallTranslator<HssCallAttempt> {

    public static final SystemContractMethod SCHEDULED_CALL = SystemContractMethod.declare(
                    "scheduleCall(address,address,uint64,uint256,bytes)", ReturnTypes.BOOL)
            .withCategories(Category.SCHEDULE);
    private static final int SCHEDULE_PAYER_ADDRESS = 0;
    private static final int SCHEDULE_CONTRACT_ADDRESS = 1;
    private static final int SCHEDULE_EXPIRY = 2;
    private static final int SCHEDULE_GAS_LIMIT = 3;
    private static final int SCHEDULE_CALL_DATA = 4;

    private final HtsCallFactory htsCallFactory;

    @Inject
    public ScheduleCallTranslator(
            @NonNull final HtsCallFactory htsCallFactory,
            @NonNull final SystemContractMethodRegistry systemContractMethodRegistry,
            @NonNull final ContractMetrics contractMetrics) {
        // Dagger
        super(SystemContractMethod.SystemContract.HAS, systemContractMethodRegistry, contractMetrics);
        registerMethods(SCHEDULED_CALL);

        requireNonNull(htsCallFactory);
        this.htsCallFactory = htsCallFactory;
    }

    @Override
    @NonNull
    public Optional<SystemContractMethod> identifyMethod(@NonNull final HssCallAttempt attempt) {
        return Optional.of(SCHEDULED_CALL);
    }

    @Override
    public Call callFrom(@NonNull final HssCallAttempt attempt) {
        final var call = SCHEDULED_CALL.decodeCall(attempt.inputBytes());
        final var innerCallData = Bytes.wrap((byte[]) call.get(SCHEDULE_CALL_DATA));
        final var payerID = attempt.addressIdConverter().convert(call.get(SCHEDULE_PAYER_ADDRESS));
        final var contractID = attempt.addressIdConverter().convert(call.get(SCHEDULE_CONTRACT_ADDRESS));
        final BigInteger expiry = call.get(SCHEDULE_EXPIRY);
        final BigInteger gasLimit = call.get(SCHEDULE_GAS_LIMIT);
        return new ScheduleCall(
                attempt.systemContractID(),
                attempt.systemContractGasCalculator(),
                attempt.enhancement(),
                attempt.defaultVerificationStrategy(),
                payerID,
                ScheduleCallTranslator::gasRequirement,
                attempt.keySetFor(),
                innerCallData,
                htsCallFactory,
                expiry.longValue(),
                gasLimit,
                attempt.enhancement()
                        .nativeOperations()
                        .entityIdFactory()
                        .newContractId(contractID.accountNumOrThrow()),
                true);
    }

    /**
     * Calculates the gas requirement for a {@code SCHEDULE_CREATE} call.
     *
     * @param body the transaction body
     * @param systemContractGasCalculator the gas calculator
     * @param enhancement the enhancement
     * @param payerId the payer account ID
     * @return the gas requirement
     */
    public static long gasRequirement(
            @NonNull final TransactionBody body,
            @NonNull final SystemContractGasCalculator systemContractGasCalculator,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final AccountID payerId) {
        return systemContractGasCalculator.gasRequirement(body, DispatchType.SCHEDULE_CREATE, payerId);
    }
}
