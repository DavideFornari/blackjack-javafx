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
 * be played as the soft 16 it actually is. Here, every Ace counts as 11 unless that
 * would push the total over 21, in which case Aces are downgraded to 1 one at a time
 * until the hand is 21 or under (or every Ace has been downgraded).
 */
public final class Hand {

    public enum Status { ACTIVE, STOOD, BUST, BLACKJACK }

    private final List<Card> cards = new ArrayList<>();
    private long wager;
    private boolean doubled;
    private boolean fromSplit;
    private boolean splitAces;
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
     * Best total achievable: sums hard values (Ace = 11), then downgrades one Ace
     * from 11 to 1 at a time (subtracting 10) for as long as the total is over 21
     * and an Ace is still being counted as 11.
     */
    public int total() {
        int total = 0;
        int acesAsEleven = 0;
        for (Card card : cards) {
            total += card.rank().hardValue();
            if (card.rank().isAce()) {
                acesAsEleven++;
            }
        }
        while (total > 21 && acesAsEleven > 0) {
            total -= 10;
            acesAsEleven--;
        }
        return total;
    }

    /** True if, at the best total, at least one Ace is still being counted as 11. */
    public boolean isSoft() {
        int total = 0;
        int acesAsEleven = 0;
        for (Card card : cards) {
            total += card.rank().hardValue();
            if (card.rank().isAce()) {
                acesAsEleven++;
            }
        }
        while (total > 21 && acesAsEleven > 0) {
            total -= 10;
            acesAsEleven--;
        }
        return acesAsEleven > 0;
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

    public boolean isDoubled() {
        return doubled;
    }

    public void markDoubled() {
        this.doubled = true;
    }

    public boolean isFromSplit() {
        return fromSplit;
    }

    public void markFromSplit() {
        this.fromSplit = true;
    }

    /** Split Aces get exactly one more card each and cannot be hit again (standard house rule). */
    public boolean isSplitAces() {
        return splitAces;
    }

    public void markSplitAces() {
        this.splitAces = true;
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

    public String cardsToString() {
        StringBuilder sb = new StringBuilder();
        for (Card card : cards) {
            sb.append(card).append(' ');
        }
        return sb.toString().trim();
    }
}
