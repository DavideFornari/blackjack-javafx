package io.github.davidefornari.blackjack.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * A player's bankroll and current hands (more than one only while a split is in play).
 */
public final class Player {

    private final String name;
    private long bankroll;
    private final List<Hand> hands = new ArrayList<>();
    private CountingSystem preferredCountingSystem = CountingSystem.HI_LO;

    public Player(String name, long startingBankroll) {
        if (startingBankroll <= 0) {
            throw new IllegalArgumentException("startingBankroll must be positive");
        }
        this.name = name;
        this.bankroll = startingBankroll;
        this.hands.add(new Hand());
    }

    public String name() {
        return name;
    }

    public long bankroll() {
        return bankroll;
    }

    public void debit(long amount) {
        if (amount < 0 || amount > bankroll) {
            throw new IllegalArgumentException("Cannot debit " + amount + " from bankroll of " + bankroll);
        }
        bankroll -= amount;
    }

    public void credit(long amount) {
        bankroll += amount;
    }

    public boolean isBankrupt() {
        return bankroll <= 0;
    }

    /** Live, mutable — {@code BlackjackRound} appends hands here when the player splits. */
    public List<Hand> hands() {
        return hands;
    }

    public Hand firstHand() {
        return hands.get(0);
    }

    void resetForNewRound() {
        hands.clear();
        hands.add(new Hand());
    }

    public CountingSystem preferredCountingSystem() {
        return preferredCountingSystem;
    }

    public void setPreferredCountingSystem(CountingSystem system) {
        this.preferredCountingSystem = system;
    }
}
