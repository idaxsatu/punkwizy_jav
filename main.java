import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * PunkWizy — neon-chain blackjack pit for mohawk punks and ledger cowboys.
 * Shuffled shoes, split/double/surrender lanes, side-bet moons, and treasury rails
 * wired to constructor-injected venue addresses. Single-file runtime; no external CSV.
 */

// ======================== Enums ========================

enum PunkSuit {
    SPADES(0, "\u2660", "Void Spade"),
    HEARTS(1, "\u2665", "Bleed Heart"),
    CLUBS(2, "\u2663", "Riot Club"),
    DIAMONDS(3, "\u2666", "Chrome Diamond");

    private final int code;
    private final String glyph;
    private final String lane;

    PunkSuit(int code, String glyph, String lane) {
        this.code = code;
        this.glyph = glyph;
        this.lane = lane;
    }

    public int getCode() { return code; }
    public String getGlyph() { return glyph; }
    public String getLane() { return lane; }

    public static PunkSuit fromCode(int c) {
        for (PunkSuit s : values()) if (s.code == c) return s;
        throw new PwzRuleException("PWZ_SUIT", "Unknown suit code " + c);
    }
}

enum PunkRank {
    ACE(1, "A", 11, 1),
    TWO(2, "2", 2, 2),
    THREE(3, "3", 3, 3),
    FOUR(4, "4", 4, 4),
    FIVE(5, "5", 5, 5),
    SIX(6, "6", 6, 6),
    SEVEN(7, "7", 7, 7),
    EIGHT(8, "8", 8, 8),
    NINE(9, "9", 9, 9),
    TEN(10, "10", 10, 10),
    JACK(11, "J", 10, 10),
    QUEEN(12, "Q", 10, 10),
    KING(13, "K", 10, 10);

    private final int code;
    private final String label;
    private final int softValue;
    private final int hardValue;

    PunkRank(int code, String label, int softValue, int hardValue) {
        this.code = code;
        this.label = label;
        this.softValue = softValue;
        this.hardValue = hardValue;
    }

    public int getCode() { return code; }
    public String getLabel() { return label; }
    public int softValue() { return softValue; }
    public int hardValue() { return hardValue; }
    public boolean isTenCard() { return hardValue == 10; }
    public boolean isAce() { return this == ACE; }

    public static PunkRank fromCode(int c) {
        for (PunkRank r : values()) if (r.code == c) return r;
        throw new PwzRuleException("PWZ_RANK", "Unknown rank code " + c);
    }
}

enum PunkArchetype {
    STREET_DEALER(0, 0, "Street Dealer", 1.00),
    MOHAWK_RIDER(1, 120, "Mohawk Rider", 1.04),
    SPIKE_COLLAR(2, 250, "Spike Collar", 1.07),
    CHAIN_VEST(3, 500, "Chain Vest", 1.10),
    NEON_KING(4, 900, "Neon King", 1.14),
    CHAOS_ACE(5, 1500, "Chaos Ace", 1.18);

    private final int id;
    private final int xpGate;
    private final String title;
    private final double payoutBoost;

    PunkArchetype(int id, int xpGate, String title, double payoutBoost) {
        this.id = id;
        this.xpGate = xpGate;
        this.title = title;
        this.payoutBoost = payoutBoost;
    }

    public int getId() { return id; }
    public int getXpGate() { return xpGate; }
    public String getTitle() { return title; }
    public double getPayoutBoost() { return payoutBoost; }

    public static PunkArchetype forXp(int xp) {
        PunkArchetype best = STREET_DEALER;
        for (PunkArchetype a : values()) if (xp >= a.xpGate) best = a;
        return best;
    }
}

enum PitPhase {
    WAITING(0),
    WAGER_LOCK(1),
    DEAL_OPEN(2),
    PLAYER_TURN(3),
    DEALER_REVEAL(4),
    SETTLE(5),
    COOLDOWN(6);

    private final int code;
    PitPhase(int code) { this.code = code; }
    public int getCode() { return code; }
}

enum HandVerdict {
    BUST(0),
    LOSE(1),
    PUSH(2),
    WIN(3),
    BLACKJACK(4),
    SURRENDER(5);

    private final int code;
    HandVerdict(int code) { this.code = code; }
    public int getCode() { return code; }
}

enum SideBetKind {
    PUNK_PAIR(0, "Punk Pair", 11),
    CHAIN_BLEED(1, "Chain Bleed", 25),
    MOON_21(2, "Moon 21", 150);

    private final int id;
    private final String label;
    private final int payoutMultiple;

    SideBetKind(int id, String label, int payoutMultiple) {
        this.id = id;
        this.label = label;
        this.payoutMultiple = payoutMultiple;
    }

    public int getId() { return id; }
    public String getLabel() { return label; }
    public int getPayoutMultiple() { return payoutMultiple; }
}

enum ChainRail {
    MAINNET(1, 1, "Ethereum Main"),
    BASE(8453, 6, "Base L2"),
    ARBITRUM(42161, 18, "Arbitrum One"),
    OPTIMISM(10, 12, "Optimism"),
    POLYGON(137, 30, "Polygon PoS");

    private final int chainId;
    private final int confirmBlocks;
    private final String label;

    ChainRail(int chainId, int confirmBlocks, String label) {
        this.chainId = chainId;
        this.confirmBlocks = confirmBlocks;
        this.label = label;
    }

    public int getChainId() { return chainId; }
    public int getConfirmBlocks() { return confirmBlocks; }
    public String getLabel() { return label; }

    public static ChainRail byId(int id) {
        for (ChainRail r : values()) if (r.chainId == id) return r;
        return MAINNET;
    }
}

// ======================== Constants ========================

final class PwzVenueConfig {
    private PwzVenueConfig() {}

    static final String ADDRESS_HOUSE = "0x1f1C7f55AF1d8CFe4B20DdFe19Ffa2f33BEA7b8C";
    static final String ADDRESS_FEE_SINK = "0x26386486b7409a6a8D17fAf9A52eDC723Bf2Dca9";
    static final String ADDRESS_ORACLE = "0xbFd87A10AF48696EbbaAd6eFFc382C54823fdEc4";
    static final String ADDRESS_RAKE_VAULT = "0xDaF6BBaD2AB5FedEE0fF56E8e5deE362cE02d499";
    static final String ADDRESS_GUILD = "0xcDDa5ebbD3E6c9B7CD0c9AbDcB0Aa8A1700E49DA";
    static final String ADDRESS_REWARDS = "0xEA35CF423afCe099Eb8bCaaD4b01Ee2Ed2ddf35c";
    static final String ADDRESS_PAUSE_GUARD = "0xa057bB4aEFD0AF7eB6CBeaD3BA1BdA826A6f1dca";
    static final String ADDRESS_SIDE_POOL = "0x3de74FfbeaD47E22ad3Fe236F4cEFcF04A6E18d3";
    static final String ADDRESS_BRIGADE = "0x731aECB313eA9251FFc49bcbdec6caa9Ca8d1926";
    static final String ADDRESS_TOURNEY = "0xA6d9EEfA1045D5FffECf9c57d5eae36b979479b9";
    static final String ADDRESS_BURN_SINK = "0xfAAb0d98EeBB268e5FA90Fe8456b8Ec0eD1dc61d";
    static final String ADDRESS_REFERRAL = "0xEceCb2CAAc7BEBFccd098b561B59E4E21a4e0620";

    static final String DOMAIN_SEPARATOR = "0xd7dDD5eAd8E0B7e5d7A5c648D3Cc6183B9eBa1cDacF46d11CE7Cbb31Fd011eCd";
    static final String CHAIN_SALT = "0x9f3A2c1E8b7046D5aF11e0C3B7d92E4f6A8c0D1e2B3f4A5c6D7e8F9a0B1c2D3e4F5";

    static final int BPS_DENOM = 10_000;
    static final int HOUSE_EDGE_BPS = 185;
    static final int RAKE_CAP_BPS = 420;
    static final int BLACKJACK_PAYOUT_BPS = 15_000;
    static final int STANDARD_WIN_BPS = 20_000;
    static final int INSURANCE_OFFER_BPS = 5_000;
    static final int MAX_SHOE_DECKS = 8;
    static final int MIN_SHOE_DECKS = 2;
    static final int CUT_CARD_MARGIN = 14;
    static final int MAX_SPLIT_HANDS = 4;
    static final int DEALER_STAND_TOTAL = 17;
    static final int DEALER_SOFT_STAND = 18;
    static final BigDecimal MIN_WAGER_ETH = new BigDecimal("0.002");
    static final BigDecimal MAX_WAGER_ETH = new BigDecimal("25");
    static final BigDecimal MIN_SIDE_ETH = new BigDecimal("0.0005");
    static final int MAX_ROUNDS_PER_SESSION = 2_400;
    static final int LEADERBOARD_CAP = 128;
    static final int HISTORY_CAP = 600;
}

// ======================== Exceptions ========================

final class PwzRuleException extends RuntimeException {
    private final String pwzCode;

    PwzRuleException(String pwzCode, String detail) {
        super(detail);
        this.pwzCode = pwzCode;
    }

    public String getPwzCode() { return pwzCode; }
}

final class PwzWagerException extends RuntimeException {
    private final String stakeCode;

    PwzWagerException(String stakeCode, String detail) {
        super(detail);
        this.stakeCode = stakeCode;
    }

    public String getStakeCode() { return stakeCode; }
}

final class PwzPauseException extends RuntimeException {
    PwzPauseException(String detail) { super(detail); }
}

// ======================== Events ========================

interface PwzPitListener {
    void onRoundOpened(long roundId, String playerId);
    void onCardDealt(long roundId, String seat, PunkRank rank, PunkSuit suit);
    void onVerdict(long roundId, HandVerdict verdict, BigDecimal deltaEth);
    void onTreasuryMove(String lane, BigDecimal amountEth, String targetAddr);
    void onPhaseShift(PitPhase phase);
}

final class PwzPitEventBus {
    private final List<PwzPitListener> listeners = new ArrayList<>();

    void subscribe(PwzPitListener listener) {
        if (listener != null) listeners.add(listener);
    }

    void emitRound(long roundId, String playerId) {
        for (PwzPitListener l : listeners) l.onRoundOpened(roundId, playerId);
    }

    void emitCard(long roundId, String seat, PunkRank rank, PunkSuit suit) {
        for (PwzPitListener l : listeners) l.onCardDealt(roundId, seat, rank, suit);
    }

    void emitVerdict(long roundId, HandVerdict verdict, BigDecimal delta) {
        for (PwzPitListener l : listeners) l.onVerdict(roundId, verdict, delta);
    }

    void emitTreasury(String lane, BigDecimal amount, String target) {
        for (PwzPitListener l : listeners) l.onTreasuryMove(lane, amount, target);
    }

    void emitPhase(PitPhase phase) {
        for (PwzPitListener l : listeners) l.onPhaseShift(phase);
    }
}

// ======================== Card model ========================

final class PunkCard {
    private final PunkRank rank;
