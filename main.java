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
