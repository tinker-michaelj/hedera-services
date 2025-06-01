// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.tokenkey.address_0x16c;

import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HtsSystemContract.HTS_16C_CONTRACT_ID;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.burn.BurnTranslator.BURN_TOKEN_V2;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.tokenkey.address_0x16c.TokenKeyTranslator.TOKEN_KEY_16C;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN_HEADLONG_ADDRESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.contract.impl.exec.metrics.ContractMetrics;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.tokenkey.TokenKeyCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.tokenkey.TokenKeyCommons;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.tokenkey.address_0x16c.TokenKeyTranslator;
import com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.common.CallAttemptTestBase;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.math.BigInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenKeyTranslatorTest extends CallAttemptTestBase {
    @Mock
    private HtsCallAttempt attempt;

    @Mock
    private ContractMetrics contractMetrics;

    private TokenKeyTranslator subject;

    @BeforeEach
    void setUp() {
        subject = new TokenKeyTranslator(systemContractMethodRegistry, contractMetrics);
    }

    @Test
    void matchesTokenKeyTranslatorTest() {
        attempt = createHtsCallAttempt(HTS_16C_CONTRACT_ID, TOKEN_KEY_16C, subject);
        assertThat(subject.identifyMethod(attempt)).isPresent();
    }

    @Test
    void matchesFailsIfIncorrectSelectorTest() {
        attempt = createHtsCallAttempt(HTS_16C_CONTRACT_ID, BURN_TOKEN_V2, subject);
        assertThat(subject.identifyMethod(attempt)).isEmpty();
    }

    @Test
    void callFromTest() {
        final Tuple tuple = Tuple.of(FUNGIBLE_TOKEN_HEADLONG_ADDRESS, BigInteger.ZERO);
        final var inputBytes = org.apache.tuweni.bytes.Bytes.wrapByteBuffer(TOKEN_KEY_16C.encodeCall(tuple));
        given(attempt.input()).willReturn(inputBytes);
        given(attempt.enhancement()).willReturn(mockEnhancement());
        given(attempt.systemContractGasCalculator()).willReturn(gasCalculator);

        final var call = subject.callFrom(attempt);
        assertThat(call).isInstanceOf(TokenKeyCall.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 8, 16, 32, 64, 128})
    void testTokenKey(final int keyType) {
        final Token token = Token.newBuilder()
                .adminKey(keyBuilder("adminKey"))
                .kycKey(keyBuilder("kycKey"))
                .freezeKey(keyBuilder("freezeKey"))
                .wipeKey(keyBuilder("wipeKey"))
                .supplyKey(keyBuilder("supplyKey"))
                .feeScheduleKey(keyBuilder("feeScheduleKey"))
                .pauseKey(keyBuilder("pauseKey"))
                .metadataKey(keyBuilder("metadataKey"))
                .build();

        final Key result = TokenKeyCommons.getTokenKey(token, keyType, HTS_16C_CONTRACT_ID);
        assertThat(result).isNotNull();
    }

    private Key keyBuilder(final String keyName) {
        return Key.newBuilder().ed25519(Bytes.wrap(keyName.getBytes())).build();
    }
}
