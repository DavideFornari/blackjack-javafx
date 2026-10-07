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
    /** Cards dealt since the last {@link #beginRound()} — still face-up on the table. */
    private final List<Card> inPlay = new ArrayList<>();
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
        inPlay.clear();
        rebuildWithout(List.of());
    }

    /**
     * Marks a round boundary: the previous round's cards go to the discards, and every card
     * drawn from here on counts as on the table until the next call. Only matters if the shoe
     * runs dry mid-round — see {@link #draw()}.
     */
    public void beginRound() {
        inPlay.clear();
    }

    /**
     * Deals the next card. If the shoe runs dry mid-round (possible on a small shoe at deep
     * penetration, since {@link #needsShuffle()} is only checked between rounds), the discards
     * are reshuffled the way a dealer would: the cards still on the table stay out of the new
     * shoe, and since they have already been seen they stay in the running counts too.
     */
    public Card draw() {
        if (nextIndex >= cards.size()) {
            if (deckCount == 0) {
                throw new IllegalStateException("Fixed test shoe exhausted after " + cards.size()
                        + " cards — the test needs to supply more");
            }
            rebuildWithout(inPlay);
        }
        Card card = cards.get(nextIndex++);
        inPlay.add(card);
        for (CountingSystem system : CountingSystem.values()) {
            runningCounts.merge(system, system.tagFor(card), Integer::sum);
        }
        return card;
    }

    private void rebuildWithout(List<Card> excluded) {
        cards.clear();
        for (int d = 0; d < deckCount; d++) {
            for (Suit suit : Suit.values()) {
                for (Rank rank : Rank.values()) {
                    cards.add(new Card(rank, suit));
                }
            }
        }
        for (Card card : excluded) {
            cards.remove(card); // one copy only — a multi-deck shoe holds deckCount of each
        }
        Collections.shuffle(cards);
        nextIndex = 0;
        for (CountingSystem system : CountingSystem.values()) {
            int count = system.initialRunningCount(deckCount);
            for (Card card : excluded) {
                count += system.tagFor(card);
            }
            runningCounts.put(system, count);
        }
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
