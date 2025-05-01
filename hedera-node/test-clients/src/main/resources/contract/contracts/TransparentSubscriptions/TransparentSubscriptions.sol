// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.25;

/**
 * TransparentSubscriptions (v0.2 – linked‑list edition)
 * -----------------------------------------------------
 * Adds a **doubly‑linked list per offering** so we can iterate subscribers
 * deterministically and remove them in O(1) time:
 *
 *   • `Subscription` now stores `prevSubscriber` and `nextSubscriber`.
 *   • `firstSubs[offeringId]` is the head pointer; we walk from there.
 *   • Global cursors `offeringCursor` *and* `subscriberCursor` let the
 *     `processTick()` resume exactly where the previous tick left off.
 *
 *  ┌─────────────────────────┐
 *  │ **Gas / storage notes** │
 *  └─────────────────────────┘
 *  - Each `subscribe()` touches **3 SSTOREs** worst‑case (new subscription +
 *    patch head + patch old head.prev).
 *  - `unsubscribe()` also ≤3 SSTOREs, plus tweaks to cursors when needed.
 *  - The linked list means we no longer need a mapping iteration hack; every
 *    step is a single SLOAD to `nextSubscriber`.
 *  - For production you still want pruning or archival after long inactivity.
 */

/// @dev Hedera system contract interface (0x16b).
interface ICallScheduler {
    function scheduleCall(
        address payer,
        address contractAddr,
        uint64  expiry,
        uint256 gasLimit,
        bytes calldata data
    ) external payable returns (bool success);
}

interface IHederaTokenService {
    /* ----- leaf structs --------------------------------------------------- */
    struct AccountAmount   { address account;  int64 amount;  bool isApproval; }
    struct NftTransfer     { address from;     address to;    int64 serialNo;  bool isApproval; }

    /* ----- containers ----------------------------------------------------- */
    struct TransferList { AccountAmount[] transfers; }

    struct TokenTransferList {
        address           token;
        AccountAmount[]   fungibleTransfers;   // same layout as H-bar
        NftTransfer[]     nftTransfers;
    }

    /* ----- CRYPTO_TRANSFER_V2 precompile (0x167) -------------------------- */
    function cryptoTransfer(
        TransferList          calldata hbarTransfers,
        TokenTransferList[]   calldata tokenTransfers
    ) external returns (int64 responseCode);
}

contract TransparentSubscriptions {
    address constant HTS_PRECOMPILE = address(uint160(0x167));
    IHederaTokenService constant HTS = IHederaTokenService(HTS_PRECOMPILE);

    /*──────────────────────────────────────────────────────────────────*/
    /*  Constants & Immutables                                         */
    /*──────────────────────────────────────────────────────────────────*/
    ICallScheduler internal constant SCHEDULER =
        ICallScheduler(0x000000000000000000000000000000000000016B);

    uint256 public constant MAX_SETTLEMENTS_PER_TICK = 8;
    uint256 public constant GAS_LIMIT                = 5000000;
    uint64  public constant TICK_INTERVAL_SECONDS    = 1;

    /*──────────────────────────────────────────────────────────────────*/
    /*  Data Structures                                                */
    /*──────────────────────────────────────────────────────────────────*/

    struct Offering {
        address provider;
        uint256 price;   // in tinybars / token units
        uint64  period;  // seconds
        bool    active;
    }

    struct Subscription {
        uint64  nextPaymentTime;
        bool    active;
        address prevSubscriber;
        address nextSubscriber;
    }

    mapping(uint32 => Offering) public offerings;
    uint32 public nextOfferingId;

    mapping(uint32 => mapping(address => Subscription)) internal subs;
    mapping(uint32 => address) internal firstSubs;    // head pointer per offering

    // Cursors for round‑robin ticking
    uint32 internal offeringCursor;
    address internal subscriberCursor;

    /*──────────────────────────────────────────────────────────────────*/
    /*  Events                                                         */
    /*──────────────────────────────────────────────────────────────────*/
    event OfferingRegistered(
        uint32  indexed id,
        address indexed provider,
        uint256 price,
        uint64  period,
        string  name,
        string  description
    );
    event OfferingUpdated   (uint32 indexed id, uint256 price, uint64 period, bool active);

    event Subscribed  (uint32 indexed id, address subscriber, uint64 nextPay);
    event Unsubscribed(uint32 indexed id, address subscriber);

    event Settled(uint32 indexed id, address subscriber, uint256 amount);

    constructor() payable {
    }

    /*──────────────────────────────────────────────────────────────────*/
    /*  Modifiers                                                      */
    /*──────────────────────────────────────────────────────────────────*/
    modifier onlyProvider(uint32 id) {
        require(msg.sender == offerings[id].provider, "not provider");
        _;
    }

    /*──────────────────────────────────────────────────────────────────*/
    /*  Provider API                                                   */
    /*──────────────────────────────────────────────────────────────────*/
    /**
     * Register a new recurring offering.
     *
     * @param name         Short display name (emitted only)
     * @param description  Free‑form description (emitted only)
     * @param price        Amount charged *per period*
     * @param period       Billing period in seconds (≥ 60)
     */
    function registerOffering(
        string calldata name,
        string calldata description,
        uint256 price,
        uint64  period
    ) external returns (uint32 id) {
        require(price > 0, "price=0");
        require(period >= TICK_INTERVAL_SECONDS, "period too short");

        id = nextOfferingId++;
        offerings[id] = Offering(msg.sender, price, period, true);

        emit OfferingRegistered(id, msg.sender, price, period, name, description);

        if (id == 0) _scheduleNextTick();
    }

    function updateOffering(uint32 id, uint256 price, uint64 period, bool active)
        external onlyProvider(id)
    {
        Offering storage o = offerings[id];
        o.price = price;
        o.period = period;
        o.active = active;
        emit OfferingUpdated(id, price, period, active);
    }

    /*──────────────────────────────────────────────────────────────────*/
    /*  Subscriber API                                                 */
    /*──────────────────────────────────────────────────────────────────*/
    function subscribe(uint32 id) external {
        Offering storage o = offerings[id];
        require(o.active, "inactive");

        Subscription storage s = subs[id][msg.sender];
        require(!s.active, "already sub");

        // link into list (insert at head for O(1))
        address oldHead = firstSubs[id];
        s.nextPaymentTime = uint64(block.timestamp) + o.period;
        s.active          = true;
        s.prevSubscriber  = address(0);
        s.nextSubscriber  = oldHead;
        if (oldHead != address(0)) {
            subs[id][oldHead].prevSubscriber = msg.sender;
        }
        firstSubs[id] = msg.sender;
        // If this is the only subscriber, ensure nextSubscriber is null (end of the list)
        if (oldHead == address(0)) {
            s.nextSubscriber = address(0);
        }
        emit Subscribed(id, msg.sender, s.nextPaymentTime);
    }

    function unsubscribe(uint32 id) external {
        Subscription storage s = subs[id][msg.sender];
        require(s.active, "not sub");
        _unlink(id, msg.sender, s);
        delete subs[id][msg.sender];
        emit Unsubscribed(id, msg.sender);
    }

    /*──────────────────────────────────────────────────────────────────*/
    /*  Tick Processing                                                */
    /*──────────────────────────────────────────────────────────────────*/
    function processTick() external {
        uint256 settled = 0;
        uint32 totalOfferings = nextOfferingId;
        if (totalOfferings == 0) { 
          _scheduleNextTick(); 
          return; 
        }

        // Ensure cursors point to a valid starting place.
        _seekNextNonEmptyOffering(totalOfferings);

        uint256 safety = MAX_SETTLEMENTS_PER_TICK * 2;
        while (safety > 0) {
            safety--;
            address sub = subscriberCursor;
            Subscription storage s = subs[offeringCursor][sub];
            Offering     storage o = offerings[offeringCursor];

            if (s.active && uint64(block.timestamp) >= s.nextPaymentTime) {
                _settleSubscription(o, offeringCursor, sub, s);
                settled++;
            }

            // advance within list
            subscriberCursor = s.nextSubscriber;
            if (subscriberCursor == address(0)) {
                // end of list → next offering w/ subscribers
                offeringCursor = (offeringCursor + 1) % totalOfferings;
                _seekNextNonEmptyOffering(totalOfferings);
            }
        }

        _scheduleNextTick();
    }


    /*──────────────────────────────────────────────────────────────────*/
    /*  Internal Helpers                                               */
    /*──────────────────────────────────────────────────────────────────*/
    function _settleSubscription(
        Offering storage o,
        uint32 id,
        address sub,
        Subscription storage s
    ) internal {
        /* ── build the H-bar TransferList using the allowance flag ─────────── */
        IHederaTokenService.TransferList memory hbar;
        hbar.transfers = new IHederaTokenService.AccountAmount[](2);

        int64 tinybar = int64(int256(o.price));          // cast uint256 → int64

        // debit via allowance  (negative, isApproval = true)
        hbar.transfers[0] = IHederaTokenService.AccountAmount({
            account:     sub,
            amount:     -tinybar,
            isApproval:  true
        });

        // credit the creator   (positive, no allowance flag needed)
        hbar.transfers[1] = IHederaTokenService.AccountAmount({
            account:     o.provider,
            amount:      tinybar,
            isApproval:  false
        });

        /* no token or NFT transfers this time */
        IHederaTokenService.TokenTransferList[] memory none =
            new IHederaTokenService.TokenTransferList[](0);

        int64 rc = HTS.cryptoTransfer(hbar, none);       // 0x167 call
        require(rc == int64(22), "HTS transfer failed"); // 22 == SUCCESS

        emit Settled(id, sub, o.price);
        s.nextPaymentTime += o.period;
    }

    function _unlink(uint32 id, address sub, Subscription storage s) internal {
        address prev = s.prevSubscriber;
        address next = s.nextSubscriber;
        if (prev != address(0)) {
            subs[id][prev].nextSubscriber = next;
        } else {
            firstSubs[id] = next;
        }
        if (next != address(0)) {
            subs[id][next].prevSubscriber = prev;
        }
        // keep cursors valid if they pointed at the removed subscriber
        if (subscriberCursor == sub) {
            subscriberCursor = next;
        }
    }

    // Ensure offeringCursor & subscriberCursor point to a non‑empty list
    function _seekNextNonEmptyOffering(uint32 total) internal {
        for (uint32 i = 0; i < total; i++) {
            if (firstSubs[offeringCursor] != address(0)) {
                if (subscriberCursor == address(0)) {
                    subscriberCursor = firstSubs[offeringCursor];
                }
                return;
            }
            offeringCursor = (offeringCursor + 1) % total;
        }
        // No subscribers anywhere
        subscriberCursor = address(0);
    }

    function _scheduleNextTick() internal {
        uint64 expiry = uint64(block.timestamp + 2 * TICK_INTERVAL_SECONDS);
        bytes memory data = abi.encodeWithSelector(this.processTick.selector);
        require(SCHEDULER.scheduleCall(
            address(this), 
            address(this), 
            expiry, 
            GAS_LIMIT,
            data), "schedule fail");
    }

    /*──────────────────────────────────────────────────────────────────*/
    /*  Housekeeping                                                   */
    /*──────────────────────────────────────────────────────────────────*/
    receive() external payable {}
}
