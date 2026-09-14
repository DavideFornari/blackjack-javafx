package io.github.davidefornari.blackjack.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A shuffled multi-deck shoe that deals cards and tracks running counts for every
 * {@link CountingSystem} simultaneously, so the UI can display whichever one the
 * player picked without re-dealing.
 */
public final class Shoe {

    private final int deckCount;
    private final int penetrationPercent;
    private final List<Card> cards = new ArrayList<>();
    private final Map<CountingSystem, Integer> runningCounts = new EnumMap<>(CountingSystem.class);
    private int nextIndex;

    public Shoe(int deckCount, int penetrationPercent) {
        if (deckCount < 1) {
            throw new IllegalArgumentException("deckCount must be >= 1");
        }
        if (penetrationPercent < 10 || penetrationPercent > 100) {
            throw new IllegalArgumentException("penetrationPercent must be between 10 and 100");
        }
        this.deckCount = deckCount;
        this.penetrationPercent = penetrationPercent;
        shuffle();
    }

    /** Test-only seam: deals exactly this sequence, in order, with no shuffling. */
    Shoe(List<Card> fixedOrder) {
        this.deckCount = 0;
        this.penetrationPercent = 100;
        this.cards.addAll(fixedOrder);
        for (CountingSystem system : CountingSystem.values()) {
            runningCounts.put(system, 0);
        }
    }

    /** Rebuilds a full shoe and shuffles it, resetting the deal pointer and every running count. */
    public void shuffle() {
        cards.clear();
        for (int d = 0; d < deckCount; d++) {
            for (Suit suit : Suit.values()) {
                for (Rank rank : Rank.values()) {
                    cards.add(new Card(rank, suit));
                }
            }
        }
        Collections.shuffle(cards);
        nextIndex = 0;
        for (CountingSystem system : CountingSystem.values()) {
            runningCounts.put(system, 0);
        }
    }

    public Card draw() {
        if (nextIndex >= cards.size()) {
            // Defensive fallback only — callers should reshuffle between rounds via needsShuffle().
            shuffle();
        }
        Card card = cards.get(nextIndex++);
        for (CountingSystem system : CountingSystem.values()) {
            runningCounts.merge(system, system.tagFor(card), Integer::sum);
        }
        return card;
    }

    /** True once the configured penetration has been dealt; check between rounds, never mid-hand. */
    public boolean needsShuffle() {
        return (nextIndex * 100.0 / totalCards()) >= penetrationPercent;
    }

    public int totalCards() {
        return cards.size();
    }

    public int cardsRemaining() {
        return totalCards() - nextIndex;
    }

    public int runningCount(CountingSystem system) {
        return runningCounts.get(system);
    }
}
