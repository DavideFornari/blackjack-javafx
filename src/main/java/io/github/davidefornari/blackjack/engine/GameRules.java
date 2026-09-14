package io.github.davidefornari.blackjack.engine;

/**
 * Configurable house rules. The original project hardcoded all of these; making them
 * explicit here both documents the exact variant being played and lets the UI offer
 * alternate table rules later without touching game logic.
 *
 * @param deckCount               number of 52-card decks in the shoe
 * @param penetrationPercent      percentage of the shoe dealt before an automatic reshuffle
 * @param dealerHitsSoftSeventeen if true, dealer hits soft 17 (H17); if false, stands on all 17s (S17)
 * @param blackjackPayoutRatio    profit multiplier on a natural, paid on top of the returned stake (3:2 = 1.5)
 * @param doubleAfterSplitAllowed whether a hand created by a split may also be doubled down
 * @param maxSplitHands           maximum simultaneous hands a single starting pair may become
 */
public record GameRules(
        int deckCount,
        int penetrationPercent,
        boolean dealerHitsSoftSeventeen,
        double blackjackPayoutRatio,
        boolean doubleAfterSplitAllowed,
        int maxSplitHands
) {
    public GameRules {
        if (deckCount < 1) throw new IllegalArgumentException("deckCount must be >= 1");
        if (penetrationPercent < 10 || penetrationPercent > 100)
            throw new IllegalArgumentException("penetrationPercent must be between 10 and 100");
        if (blackjackPayoutRatio <= 0) throw new IllegalArgumentException("blackjackPayoutRatio must be positive");
        if (maxSplitHands < 1) throw new IllegalArgumentException("maxSplitHands must be >= 1");
    }

    /** 6-deck shoe, stand on all 17s, 3:2 blackjack, DAS on, split up to 4 hands, 50% penetration. */
    public static GameRules standard() {
        return new GameRules(6, 50, false, 1.5, true, 4);
    }
}
