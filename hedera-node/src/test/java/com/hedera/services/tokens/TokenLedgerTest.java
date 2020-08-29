package com.hedera.services.tokens;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.serdes.DomainSerdes;
import com.hedera.services.state.serdes.IoReadingFunction;
import com.hedera.services.state.serdes.IoWritingConsumer;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.MiscUtils;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenCreation;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.InOrder;

import java.io.IOException;

import static com.hedera.services.state.merkle.MerkleAccountState.MAX_NUM_TOKEN_BALANCES;
import static com.hedera.services.state.merkle.MerkleAccountState.NO_TOKEN_BALANCES;
import static com.hedera.test.utils.IdUtils.tokenWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.anyInt;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.times;
import static org.mockito.Mockito.inOrder;

@RunWith(JUnitPlatform.class)
class TokenLedgerTest {
	EntityIdSource ids;
	PropertySource properties;
	FCMap<MerkleEntityId, MerkleToken> tokens;

	Key adminKey, freezeKey;
	String symbol = "NotHbar";
	long tokenFloat = 1_000_000;
	int divisibility = 10;
	boolean freezeDefault = true;
	AccountID treasury = IdUtils.asAccount("1.2.3");
	AccountID sponsor = IdUtils.asAccount("1.2.666");
	TokenID created = IdUtils.asToken("1.2.666666");

	TokenLedger subject;

	@BeforeEach
	public void setup() {
		adminKey = TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT.asKey();
		freezeKey = TxnHandlingScenario.CARELESS_SIGNING_PAYER_KT.asKey();

		ids = mock(EntityIdSource.class);
		given(ids.newTokenId(sponsor)).willReturn(created);
		tokens = (FCMap<MerkleEntityId, MerkleToken>)mock(FCMap.class);
		properties = mock(PropertySource.class);

		subject = new TokenLedger(ids, properties, () -> tokens);
	}

	@Test
	public void happyPathWorks() {
		// given:
		var req = fullyValidAttempt().build();

		// when:
		var result = subject.create(req, sponsor);

		// then:
		assertEquals(ResponseCodeEnum.OK, result.getStatus());
		assertEquals(created, result.getCreated().get());
	}

	@Test
	public void rejectsMissingAdminKey() {
		// given:
		var req = fullyValidAttempt()
				.clearAdminKey()
				.build();

		// when:
		var result = subject.create(req, sponsor);

		// then:
		assertEquals(ResponseCodeEnum.INVALID_ADMIN_KEY, result.getStatus());
		assertTrue(result.getCreated().isEmpty());
	}

	@Test
	public void rejectsInvalidAdminKey() {
		// given:
		var req = fullyValidAttempt()
				.setAdminKey(Key.getDefaultInstance())
				.build();

		// when:
		var result = subject.create(req, sponsor);

		// then:
		assertEquals(ResponseCodeEnum.INVALID_ADMIN_KEY, result.getStatus());
		assertTrue(result.getCreated().isEmpty());
	}

	private TokenCreation.Builder fullyValidAttempt() {
		return TokenCreation.newBuilder()
				.setAdminKey(adminKey)
				.setFreezeKey(freezeKey)
				.setSymbol(symbol)
				.setFloat(tokenFloat)
				.setTreasury(treasury)
				.setDivisibility(divisibility)
				.setFreezeDefault(freezeDefault);
	}
}