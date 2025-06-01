// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.service.token.impl.test.schemas;

import static com.hedera.node.app.ids.schemas.V0590EntityIdSchema.ENTITY_COUNTS_KEY;
import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.ALIASES_KEY;
import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.STAKING_INFO_KEY;
import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.STAKING_NETWORK_REWARDS_KEY;
import static com.hedera.node.app.service.token.impl.test.schemas.SyntheticAccountsData.DEFAULT_NUM_SYSTEM_ACCOUNTS;
import static com.hedera.node.app.service.token.impl.test.schemas.SyntheticAccountsData.buildConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.entity.EntityCounts;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.StakingNodeInfo;
import com.hedera.node.app.ids.schemas.V0490EntityIdSchema;
import com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema;
import com.hedera.node.app.services.MigrationContextImpl;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.lifecycle.StartupNetworks;
import com.swirlds.state.lifecycle.info.NodeInfo;
import com.swirlds.state.spi.EmptyReadableStates;
import com.swirlds.state.spi.WritableSingletonState;
import com.swirlds.state.spi.WritableSingletonStateBase;
import com.swirlds.state.spi.WritableStates;
import com.swirlds.state.test.fixtures.MapWritableKVState;
import com.swirlds.state.test.fixtures.MapWritableStates;
import java.util.HashMap;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class V0490TokenSchemaTest {
    private static final long BEGINNING_ENTITY_ID = 1000;

    private static final AccountID[] ACCT_IDS = new AccountID[1001];

    static {
        IntStream.rangeClosed(1, 1000).forEach(i -> ACCT_IDS[i] = asAccount(0L, 0L, i));
    }

    private MapWritableKVState<AccountID, Account> accounts;
    private WritableStates newStates;
    private Configuration config;

    @Mock
    private StartupNetworks startupNetworks;

    @BeforeEach
    void setUp() {
        accounts = MapWritableKVState.<AccountID, Account>builder(V0490TokenSchema.ACCOUNTS_KEY)
                .build();

        newStates = newStatesInstance(
                accounts,
                MapWritableKVState.<Bytes, AccountID>builder(ALIASES_KEY).build(),
                newWritableEntityIdState(),
                new WritableSingletonStateBase<>(
                        ENTITY_COUNTS_KEY, () -> EntityCounts.newBuilder().build(), c -> {}));

        config = buildConfig(DEFAULT_NUM_SYSTEM_ACCOUNTS, true);
    }

    @Test
    void initializesStakingDataOnGenesisStart() {
        final var schema = newSubjectWithAllExpected();
        final var migrationContext = new MigrationContextImpl(
                EmptyReadableStates.INSTANCE, newStates, config, config, null, 0L, new HashMap<>(), startupNetworks);

        schema.migrate(migrationContext);

        final var nodeRewardsStateResult = newStates.<NodeInfo>getSingleton(STAKING_NETWORK_REWARDS_KEY);
        assertThat(nodeRewardsStateResult.isModified()).isTrue();
        final var nodeInfoStateResult = newStates.<EntityNumber, StakingNodeInfo>get(STAKING_INFO_KEY);
        assertThat(nodeInfoStateResult.isModified()).isFalse();
    }

    private WritableSingletonState<EntityNumber> newWritableEntityIdState() {
        return new WritableSingletonStateBase<>(
                V0490EntityIdSchema.ENTITY_ID_STATE_KEY, () -> new EntityNumber(BEGINNING_ENTITY_ID), c -> {});
    }

    private MapWritableStates newStatesInstance(
            final MapWritableKVState<AccountID, Account> accts,
            final MapWritableKVState<Bytes, AccountID> aliases,
            final WritableSingletonState<EntityNumber> entityIdState,
            final WritableSingletonState<EntityCounts> entityCountsState) {
        //noinspection ReturnOfNull
        return MapWritableStates.builder()
                .state(accts)
                .state(aliases)
                .state(MapWritableKVState.builder(V0490TokenSchema.STAKING_INFO_KEY)
                        .build())
                .state(new WritableSingletonStateBase<>(STAKING_NETWORK_REWARDS_KEY, () -> null, c -> {}))
                .state(entityIdState)
                .state(entityCountsState)
                .build();
    }

    private V0490TokenSchema newSubjectWithAllExpected() {
        return new V0490TokenSchema();
    }
}
