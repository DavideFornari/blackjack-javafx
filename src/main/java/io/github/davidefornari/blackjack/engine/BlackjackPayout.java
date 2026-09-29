package io.github.davidefornari.blackjack.engine;

/**
 * What a natural pays, kept as an exact ratio instead of a {@code double} multiplier.
 *
 * <p>Keeping money off floating point is the whole point. The earlier {@code double} ratio
 * was settled with {@code Math.round(wager * (1 + ratio))}, which paid a natural on an odd
 * wager half a chip <em>above</em> the true 3:2 — a wager of 15 rounded a 22.5 profit up to
 * 23. Integer arithmetic here drops the part-chip instead, which is the direction the house
 * rounds.
 */
public enum BlackjackPayout {

    /** The standard table rule: a natural returns the stake plus one and a half times it. */
    THREE_TO_TWO(3, 2, "3:2"),

    /** The player-unfriendly variant common on modern 6-deck shoes. */
    SIX_TO_FIVE(6, 5, "6:5");

    private final int numerator;
    private final int denominator;
    private final String displayName;

    BlackjackPayout(int numerator, int denominator, String displayName) {
        this.numerator = numerator;
        this.denominator = denominator;
        this.displayName = displayName;
    }

    /** Profit on a natural of this wager, paid on top of the returned stake, with any part-chip dropped. */
    public long profitOn(long wager) {
        return wager * numerator / denominator;
    }

    public String displayName() {
        return displayName;
    }
}
