package io.github.davidefornari.blackjack.engine;

/**
 * Card-counting systems, each assigning a per-card tag used to track how rich in
 * high cards the remaining shoe is. Values verified against each system's published
 * tag chart (ported from the original project's Mazzo.java, where all four were
 * already implemented correctly).
 */
public enum CountingSystem {

    /** Beginner-friendly, balanced. 2-6: +1, 7-9: 0, 10-A: -1. */
    HI_LO("Hi-Lo") {
        @Override
        public int tagFor(Card card) {
            Rank rank = card.rank();
            if (rank.hardValue() >= 2 && rank.hardValue() <= 6) return 1;
            if (rank.isTenValue() || rank.isAce()) return -1;
            return 0;
        }
    },

    /** Balanced, high level 2. 2,3,7: +1, 4,5,6: +2, 9: -1, 10-K: -2, 8,A: 0. */
    OMEGA_II("Omega II") {
        @Override
        public int tagFor(Card card) {
            Rank rank = card.rank();
            if (rank == Rank.TWO || rank == Rank.THREE || rank == Rank.SEVEN) return 1;
            if (rank == Rank.FOUR || rank == Rank.FIVE || rank == Rank.SIX) return 2;
            if (rank == Rank.NINE) return -1;
            if (rank.isTenValue()) return -2;
            return 0;
        }
    },

    /**
     * Unbalanced, suit-sensitive on 7s: a full deck's tags sum to +2, not 0. Starts at the
     * conventional initial running count of -2 per deck, so a fully dealt shoe ends at 0 and
     * the published betting thresholds line up with the displayed count.
     */
    RED_SEVEN("Red Seven") {
        @Override
        public int tagFor(Card card) {
            Rank rank = card.rank();
            if (rank.hardValue() >= 2 && rank.hardValue() <= 6) return 1;
            if (rank == Rank.SEVEN && card.suit().isRed()) return 1;
            if (rank.isTenValue() || rank.isAce()) return -1;
            return 0;
        }

        @Override
        public int initialRunningCount(int deckCount) {
            return -2 * deckCount;
        }
    },

    /** Balanced, high level 2, treats Aces separately from ten-value cards. */
    ZEN_COUNT("Zen Count") {
        @Override
        public int tagFor(Card card) {
            Rank rank = card.rank();
            if (rank == Rank.TWO || rank == Rank.THREE || rank == Rank.SEVEN) return 1;
            if (rank == Rank.FOUR || rank == Rank.FIVE || rank == Rank.SIX) return 2;
            if (rank.isAce()) return -1;
            if (rank.isTenValue()) return -2;
            return 0;
        }
    };

    private final String displayName;

    CountingSystem(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** The display name, so a plain {@code ComboBox} shows it without a converter. */
    @Override
    public String toString() {
        return displayName;
    }

    public abstract int tagFor(Card card);

    /** The running count of a freshly shuffled shoe: 0 for balanced systems. */
    public int initialRunningCount(int deckCount) {
        return 0;
    }

    /** Balanced systems tag a full deck to 0 and are played on the true count; an unbalanced one (Red Seven) on the running count. */
    public boolean isBalanced() {
        return initialRunningCount(1) == 0;
    }
}
