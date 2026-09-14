package io.github.davidefornari.blackjack.engine;

/**
 * A card rank and its blackjack value. The Ace's {@link #hardValue()} is 11 ("soft");
 * {@link Hand} is responsible for counting it as 1 instead whenever 11 would bust the hand.
 */
public enum Rank {
    TWO("2", 2), THREE("3", 3), FOUR("4", 4), FIVE("5", 5), SIX("6", 6),
    SEVEN("7", 7), EIGHT("8", 8), NINE("9", 9), TEN("10", 10),
    JACK("J", 10), QUEEN("Q", 10), KING("K", 10),
    ACE("A", 11);

    private final String symbol;
    private final int hardValue;

    Rank(String symbol, int hardValue) {
        this.symbol = symbol;
        this.hardValue = hardValue;
    }

    public String symbol() {
        return symbol;
    }

    /** Face value before any soft-Ace reduction: 2-9 as printed, 10/J/Q/K as 10, Ace as 11. */
    public int hardValue() {
        return hardValue;
    }

    public boolean isAce() {
        return this == ACE;
    }

    /** A card worth 10 (10, J, Q or K) — relevant for naturals and split eligibility. */
    public boolean isTenValue() {
        return hardValue == 10;
    }
}
