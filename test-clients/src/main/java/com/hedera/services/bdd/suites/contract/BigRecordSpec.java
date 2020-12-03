package com.hedera.services.bdd.suites.contract;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;

public class BigRecordSpec extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(BigRecordSpec.class);

	final String PATH_TO_BIG_BIG_BYTECODE = "src/main/resource/contract/bytecodes/BigBig.bin";

	private static final String PICK_A_BIG_RESULT =
			"{\"constant\":true,\"inputs\":[{\"internalType\":\"uint32\",\"name\":\"how\",\"type\":\"uint32\"}]," +
					"\"name\":\"pick\",\"outputs\":[{\"internalType\":\"bytes\",\"name\":\"\",\"type\":\"bytes\"}]," +
					"\"payable\":false,\"stateMutability\":\"pure\",\"type\":\"function\"}";

	public static void main(String... args) {
		/* Has a static initializer whose behavior seems influenced by initialization of ForkJoinPool#commonPool. */
		new org.ethereum.crypto.HashUtil();

		new BigRecordSpec().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
				bigCall(),
		});
	}

	HapiApiSpec bigCall() {
		int byteArraySize = (int)(87.5 * 1_024);

		return defaultHapiSpec("BigRecord")
				.given(
						cryptoCreate("payer").balance( 10 * A_HUNDRED_HBARS),
						fileCreate("bytecode")
								.path(PATH_TO_BIG_BIG_BYTECODE),
						contractCreate("bigBig")
								.bytecode("bytecode")
				).when(
						contractCall("bigBig", PICK_A_BIG_RESULT, byteArraySize)
								.payingWith("payer")
								.gas(300_000L)
								.via("bigCall")
				).then(
						getTxnRecord("bigCall").logged()
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
