package io.github.davidefornari.blackjack.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A set of cards in play, with correct soft-Ace scoring.
 *
 * <p>This is the direct fix for the original project's main rules bug: there, an Ace
 * ({@code Carta}) was hardcoded to value 11 with no downgrade path, so any hand with
 * two Aces scored as a 22 "bust" instead of a soft 12, and a hand like A+5 could never
 * be played as the soft 16 it actually is. Here, one Ace counts as 11 whenever that
 * keeps the hand at 21 or under, and every other Ace counts as 1.
 */
public final class Hand {

    public enum Status { ACTIVE, STOOD, BUST, BLACKJACK }

    private final List<Card> cards = new ArrayList<>();
    private long wager;
    private boolean fromSplit;
    private Status decision = Status.ACTIVE;

    public void addCard(Card card) {
        cards.add(card);
        if (decision == Status.ACTIVE && total() >= 21) {
            decision = Status.STOOD; // 21 can't be hit further; over 21 is derived as BUST regardless
        }
    }

    public List<Card> cards() {
        return Collections.unmodifiableList(cards);
    }

    public int size() {
        return cards.size();
    }

    /**
     * Best total achievable. Two Aces at 11 always bust, so at most one can count as 11:
     * count them all as 1, then add 10 back for one of them if that stays at 21 or under.
     */
    public int total() {
        return isSoft() ? lowTotal() + 10 : lowTotal();
    }

    /** True if, at the best total, an Ace is still being counted as 11. */
    public boolean isSoft() {
        return cards.stream().anyMatch(c -> c.rank().isAce()) && lowTotal() + 10 <= 21;
    }

    /** Every Ace counted as 1. */
    private int lowTotal() {
        return cards.stream().mapToInt(c -> c.rank().isAce() ? 1 : c.rank().hardValue()).sum();
    }

    public boolean isBust() {
        return total() > 21;
    }

    /** A natural: exactly two cards totalling 21, dealt before any split. */
    public boolean isNaturalBlackjack() {
        return !fromSplit && cards.size() == 2 && total() == 21;
    }

    /** Splittable: exactly two cards of equal blackjack value (so 10-J counts as a pair). */
    public boolean isPair() {
        return cards.size() == 2 && cards.get(0).rank().hardValue() == cards.get(1).rank().hardValue();
    }

    public boolean isPairOfAces() {
        return isPair() && cards.get(0).rank().isAce();
    }

    public long wager() {
        return wager;
    }

    public void setWager(long wager) {
        this.wager = wager;
    }

    public boolean isFromSplit() {
        return fromSplit;
    }

    public void markFromSplit() {
        this.fromSplit = true;
    }

    /** Live status: BUST and BLACKJACK are derived from the cards; STOOD is a player decision. */
    public Status status() {
        if (isBust()) {
            return Status.BUST;
        }
        if (isNaturalBlackjack()) {
            return Status.BLACKJACK;
        }
        return decision;
    }

    public void stand() {
        if (decision == Status.ACTIVE) {
            decision = Status.STOOD;
        }
    }

    /** Whether the player may still act on this hand (hit / double / split / stand). */
    public boolean isActionable() {
        return status() == Status.ACTIVE;
    }
}
