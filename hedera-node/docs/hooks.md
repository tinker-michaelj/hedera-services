---

hip: <HIP number (assigned by the HIP editor)>
title: Hiero hooks and an application to allowances
author: Michael Tinker <@tinker-michaelj>
working-group: Richard Bair <@rbair23>, Jasper Potts <@jasperpotts>, Atul Mahamuni <atul@hashgraph.com>,  Matthew DeLorenzo <@littletarzan>, Steven Sheehy <@steven-sheehy>
requested-by: Hashgraph
type: Standards Track
category: Service
needs-council-approval: Yes
status: Draft
created: 2025-02-19
discussions-to: <TODO>
updated: 2025-02-19
-------------------

## Abstract

We propose **hooks**, programmable Hiero extension points that let users customize the behavior of their entities.
Although in principle hooks could be programmed in any language, we begin with **EVM hooks**. Users program EVM
hooks by writing contracts in a language like Solidity that compiles to EVM bytecode. EVM hooks are either **pure**
(using neither storage nor external contracts); or **lambdas** (like code running in a cloud's event-driven compute
offering, which may access a database to use state or call other external services). Users can install multiple
hooks at different **indexes** on the same entity, until they reach a configured limit.

As a first Hiero extension point, we propose **allowance hooks**. Users can install these hooks on their accounts.
A Hiero API (HAPI) `CryptoTransfer` transaction can then reference a hook allowance just as it does an ERC-style
allowance defined in [HIP-376](https://hips.hedera.com/hip/hip-376). The network uses the hook by calling its EVM
bytecode at a specific function signature, passing in the details of the transfers proposed in the transaction. If
the hook returns `true`, the network continues executing the transfer; otherwise the network rejects the transfer.

Unlike smart contracts, which must encapsulate trust guarantees for multiple parties, lambdas belong to a single
owner who can directly update their storage via a `LambdaSStore` transaction. This streamlined design supports
fast, low-cost adjustments to a lambda with less overhead than a typical `ConsensusSubmitMessage`; and far less
than a `ContractCall`.

## Motivation

Hedera users often want to customize native entities instead of migrating their decentralized applications (dApps) to
purely EVM-based smart contracts. Consider the following examples:
- [HIP-18: Custom Hedera Token Service Fees](https://hips.hedera.com/hip/hip-18) introduced custom fee
payments for HTS transfers.
- [HIP-904: Frictionless Airdrops](https://hips.hedera.com/hip/hip-904) enabled more permissive token association
policies.
- [HIP-991: Permissionless revenue-generating Topic Ids for Topic Operators](https://hips.hedera.com/hip/hip-991)
proposed fee-based access control for message submissions to topics.

Hooks provide a more general solution to the problem of users needing custom business logic for their entities. For
example, a token issuer might need to enforce rules on every transaction involving their token for regulatory or
business reasons. A **transfer hook** installable on token types could enforce these rules without requiring the
issuer to take a full design proposal through the HIP process, _or_ lose the performance and simplicity of the native
APIs by moving everything into a smart contract.

That is, by avoiding protocol-level changes for every important customization, hooks can greatly streamline innovation
on a Hiero network while maintaining the performance and integrity of the native services.

## Specification

First we specify how a Hiero network will charge, throttle, and execute EVM hooks. (Other hook programming models would
require their own specifications.)

The protobuf API for hooks in general, and EVM hooks in particular, follows in subsequent sections.

### Charging

A primary concern for EVM hooks is deciding what account pays for the EVM gas upfront, before executing the hook. We
propose two charging patterns that should accommodate most use cases. The **installer** of an EVM hook chooses one of
the patterns at the time they install it.
1. `CALLER_PAYS` - The payer of the transaction that triggers the hook pays for its upfront gas cost. They receive the
normal refund for unused gas.
2. `CALLER_PAYS_ON_REVERT` - The payer of the triggering transaction again pays for the upfront gas cost, but receives
a _full refund_ if the hook succeeds (in sense appropriate to the extension point). On success, a designated account
that signed the lambda's installation pays for the gas actually consumed.

Regardless of the charging pattern, a triggering transaction can always set an explicit gas limit for an EVM hook's
execution, just as with a contract call. If no explicit limit is set on the transaction, the networks checks if the
EVM was installed with a default gas limit. If there is no default limit for the hook, the network uses a global
default, for example `hooks.evm.defaultGasLimit=25_000`.

We propose the same gas price for EVM hook execution as for other contract operations. However, unlike contract calls,
which are charged purely in gas, hook executions are already "gated" by the fee of their triggering transaction.
So it makes sense to waive the intrinsic gas cost of their execution. We propose adding two more properties,

```
hooks.evm.pureIntrinsicGasCost=0
hooks.evm.lambdaIntrinsicGasCost=0
```

### Throttling

We propose EVM hooks be subject to the same gas throttle as top-level contract calls. Specifically, when an EVM hook
executes, its initial EVM sender address is the payer of the referencing transaction. If this payer is throttle exempt,
no throttles are applied. Otherwise, if the network is at capacity for gas usage, EVM hook execution will be throttled
on that basis and the triggering transaction fail with a status of `EVM_HOOK_EXECUTION_THROTTLED`.

### EVM hook execution

The first message frame for the EVM transaction that executes an EVM hook will be a logical `DELEGATECALL` from the
**hook system contract address `0x16d`** to the hook's implementing `ContractID`, sent by the payer of the upfront gas
cost for the hook's execution.

As a concrete example, suppose account `0.0.A` installs an EVM lambda hook for the `ACCOUNT_ALLOWANCE_HOOK` extension
point at index `1`. The hook's implementing contract is `0.0.H` with EVM address `0xab...cd`. Now `0.0.B` with EVM
address `0x01...23` sends a `CryptoTransfer` transaction that references the hook `0.0.A.1` with gas limit `100_000`.

The network will construct an initial EVM message frame with,
1. `sender` address `0x01...23`;
2. `receiver` address `0x16d`;
3. `contract` address `0xab...cd` (hence the source of the executing bytecode);
4. Storage of the `0.0.A.1` lambda EVM hook; and,
5. Gas remaining of `100_000` (no intrinsic gas cost).

Since this hook is a lambda, it can then proceed with arbitrary EVM behaviors, including calls to external oracles,
`SLOAD` and `SSTORE` operations with its storage, and so on.

If the `0.0.A.1` hook is a pure EVM hook, it executes in an extremely restricted mode. Not only is the initial frame
marked **static**, prohibiting all state-changing operations; but the network also disables the `PREVRANDAO`, `SLOAD`,
and `CALL` opcodes. That is, a pure hook cannot do anything but apply a pure function to its input data.

Although users will doubtless uncover a very wide range of use cases for EVM hooks, we expect the single most common
type of hook contract to implement a single external method that reverts when not executed by the network _as a hook_.
That is,

```solidity
contract HookContract {
    function hookFunction(address installer) external payable {
        // Revert if we are not executing a DELEGATECALL from the hook system contract
        require(address(this) == 0x16d, "Contract can only be called as a hook");
        // Continue executing as a hook on behalf of the installer address
    }
}
```

### Core HAPI protobufs

A hook's extension point is one of an enumeration that now includes just the account allowance hook,

```protobuf
/***
 * The Hiero extension points that accept a hook.
 */
enum HookExtensionPoint {
  /**
   * Used to customize an account's allowances during a CryptoTransfer transaction.
   */
  ACCOUNT_ALLOWANCE_HOOK = 0;
}
```

Users install hooks by setting a `HookInstall` field on an appropriate create or update transaction. This message is,

```protobuf
/***
 * How to install a hook.
 */
message HookInstall {
  /**
   * The extension point for the hook.
   */
  HookExtensionPoint extension_point = 1;

  /**
   * The entity index to install the hook at.
   */
  uint64 index = 2;

  /**
   * The hook implementation.
   */
  oneof hook {
    /**
     * A hook programmed in EVM bytecode that does not require access to state
     * or interactions with external contracts.
     */
    PureEvmHook pure_evm_hook = 3;
    /**
     * A hook programmed in EVM bytecode that may access state or interact with
     * external contracts.
     */
    LambdaEvmHook lambda_evm_hook = 4;
  }

  /**
   * If set, a key that that can be used to remove or replace the hook; or (if
   * applicable, as with a lambda EVM hook) perform transactions that customize
   * the hook.
   */
  proto.Key admin_key = 5;

  /**
   * The charging pattern for the hook.
   */
  oneof charging_spec {
    /**
     * The payer of the transaction calling the hook pays for its execution.
     */
    CallerPays caller_pays = 6;
    /**
     * The payer of the transaction calling the hook pays for its execution if
     * the hook fails; otherwise, a specified payer pays.
     */
    CallerPaysOnFailure caller_pays_on_failure = 7;
  }
}
```

The `PureEvmHook` and `LambdaEvmHook` messages share a common `EvmHookSpec` message that specifies the source of the
hook's EVM bytecode and a default gas limit for its execution. The `LambdaEvmHook` message also includes the initial
storage slots for a lambda hook, if desired.

```protobuf
/**
 * Definition of a lambda EVM hook.
 */
message PureEvmHook {
  /**
   * The specification for the hook.
   */
  EvmHookSpec spec = 1;
}

/**
 * Definition of a lambda EVM hook.
 */
message LambdaEvmHook {
  /**
   * The specification for the hook.
   */
  EvmHookSpec spec = 1;

  /**
   * Initial storage contents for the lambda, if any.
   */
  repeated LambdaStorageSlot storage_slots = 2;
}

/**
 * Shared specifications for an EVM hook. May be used for any extension point.
 */
message EvmHookSpec {
  /**
   * The source of the EVM bytecode for the hook.
   */
  oneof bytecode_source {
    /**
     * The id of a contract that implements the extension point API with EVM bytecode.
     */
    ContractID contract_id = 1;
  }

  /**
   * If present, the default gas limit to use when executing the EVM hook.
   */
  google.protobuf.UInt64Value default_gas_limit = 2;
}

/**
 * A slot in the storage of a lambda EVM hook.
 */
message LambdaStorageSlot {
  /**
   * The 32-byte key of the slot; leading zeros may be omitted.
   */
  bytes key = 1;

  /**
   * If the slot is present and non-zero, the 32-byte value of the slot;
   * leaving this field empty or setting it to binary zeros in an update
   * removes the slot.
   */
  bytes value = 2;
}
```

The indexes of newly installed hooks will appear in the legacy `TransactionReceipt` if records streams are enabled,

```protobuf

message TransactionReceipt {
  // ...

  /**
   * In the receipt of a successful create or update transaction for an entity that supports hooks,
   * the indexes of any newly installed hooks.
   */
  repeated uint64 installed_hook_indexes = 16;
}
```

Once a hook is installed to an entity, a transaction generally references it by index relative to an implicit owner.
The details of the call are specified based on its type; for example, EVM hook calls are specified by an `EvmHookCall`
message that gives optional call data and gas limit.

If the called hook does not match the given call specification, the network will fail the transaction with
`BAD_HOOK_REQUEST`. If there is no hook installed at the specified index, the network will fail the transaction
with `HOOK_NOT_FOUND`.

```protobuf
/**
 * Specifies a call to a hook from within a transaction where
 * the hook owner is implied by the point of use. (For example,
 * it would never make sense to try to use an account allowance
 * hook for account 0.0.X inside an AccountAmount for account
 * 0.0.Y; hence we only need to give the index of which of
 * 0.0.Y's hooks we want to call.)
 */
message HookCall {
  /**
   * The index of the hook to call.
   */
  uint64 index = 1;

  /**
   * Specifies details of the call.
   */
  oneof call_spec {
    /**
     * Specification of how to call an EVM hook.
     */
    EvmHookCall evm_hook_call = 2;
  }
}

/**
 * Specifies details of a call to an EVM hook.
 */
message EvmHookCall {
  /**
   * Extra call data to pass.
   */
  bytes evm_call_data = 1;

  /**
   * If set, an explicit gas limit to use.
   */
  google.protobuf.UInt64Value gas_limit = 3;
}
```

### Core system protobufs

Once a hook is installed, it has an id in the network state.

```protobuf
/**
 * Once a hook is installed, its id.
 */
message HookId {
  /**
   * The id of the hook's installer.
   */
  HookInstallerId installer_id = 1;

  /**
   * A unique identifier for the hook given the installer.
   */
  uint64 index = 2;
}

/**
 * The id of an entity that has installed a hook.
 */
message HookInstallerId {
  oneof installer_id {
    /**
     * An account installing a hook.
     */
    AccountID account_id = 1;
  }
}
```

EVM hooks will be implemented by internal dispatch from each installing entity type's service to the `ContractService`.
(Here also, a hook with a different programming model would require very different implementation details, so we focus
on EVM hooks.)

The dispatch for executing EVM hooks is a new `HookDispatchTransactionBody` with a choice of three actions.
