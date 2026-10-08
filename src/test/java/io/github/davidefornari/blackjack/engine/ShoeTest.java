package io.github.davidefornari.blackjack.engine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShoeTest {

    private static final List<CountingSystem> BALANCED =
            List.of(CountingSystem.HI_LO, CountingSystem.OMEGA_II, CountingSystem.ZEN_COUNT);

    @Test
    void aFreshShoeHoldsEveryCardOncePerDeck() {
        Shoe shoe = new Shoe(2, 100);

        assertEquals(104, shoe.totalCards());
        assertEquals(104, shoe.cardsRemaining());

        Map<Card, Integer> copies = new HashMap<>();
        for (int i = 0; i < 104; i++) {
            copies.merge(shoe.draw(), 1, Integer::sum);
        }
        assertEquals(52, copies.size());
        assertTrue(copies.values().stream().allMatch(n -> n == 2));
        assertEquals(0, shoe.cardsRemaining());
    }

    @Test
    void needsShuffleTripsExactlyAtThePenetrationPoint() {
        Shoe shoe = new Shoe(1, 50);

        for (int i = 0; i < 25; i++) {
            shoe.draw();
        }
        assertFalse(shoe.needsShuffle()); // 25/52 = 48%

        shoe.draw();
        assertTrue(shoe.needsShuffle()); // 26/52 = 50%
    }

    @Test
    void runningCountsAccumulateTheTagOfEveryDrawnCard() {
        Shoe shoe = new Shoe(List.of(
                new Card(Rank.FIVE, Suit.HEARTS), new Card(Rank.KING, Suit.SPADES),
                new Card(Rank.SEVEN, Suit.DIAMONDS), new Card(Rank.ACE, Suit.CLUBS)));

        for (int i = 0; i < 4; i++) {
            shoe.draw();
        }

        assertEquals(-1, shoe.runningCount(CountingSystem.HI_LO));     // +1 -1  0 -1
        assertEquals(1, shoe.runningCount(CountingSystem.OMEGA_II));   // +2 -2 +1  0
        assertEquals(0, shoe.runningCount(CountingSystem.RED_SEVEN));  // +1 -1 +1 -1 (red 7)
        assertEquals(0, shoe.runningCount(CountingSystem.ZEN_COUNT));  // +2 -2 +1 -1
    }

    @Test
    void trueCountIsTheRunningCountPerDeckRemaining() {
        Shoe shoe = new Shoe(1, 100);
        for (int i = 0; i < 26; i++) {
            shoe.draw();
        }

        // Half a deck left, so the true count is double the running count.
        assertEquals(2.0 * shoe.runningCount(CountingSystem.HI_LO), shoe.trueCount(CountingSystem.HI_LO));
    }

    @Test
    void balancedCountsStartAtZeroAndFinishAFullShoeAtZero() {
        Shoe shoe = new Shoe(2, 100);
        for (CountingSystem system : BALANCED) {
            assertEquals(0, shoe.runningCount(system), system.displayName());
        }

        for (int i = 0; i < 104; i++) {
            shoe.draw();
        }

        for (CountingSystem system : BALANCED) {
            assertEquals(0, shoe.runningCount(system), system.displayName());
        }
    }

    @Test
    void redSevenStartsAtMinusTwoPerDeckAndFinishesAFullShoeAtZero() {
        Shoe shoe = new Shoe(2, 100);
        assertEquals(-4, shoe.runningCount(CountingSystem.RED_SEVEN));

        for (int i = 0; i < 104; i++) {
            shoe.draw();
        }

        assertEquals(0, shoe.runningCount(CountingSystem.RED_SEVEN));
    }

    @Test
    void shufflingRefillsTheShoeAndResetsEveryRunningCount() {
        Shoe shoe = new Shoe(1, 50);
        for (int i = 0; i < 30; i++) {
            shoe.draw();
        }

        shoe.shuffle();

        assertEquals(52, shoe.cardsRemaining());
        assertFalse(shoe.needsShuffle());
        for (CountingSystem system : CountingSystem.values()) {
            assertEquals(system.initialRunningCount(1), shoe.runningCount(system), system.displayName());
        }
    }

    @Test
    void runningDryMidRoundReshufflesWithoutTheCardsStillOnTheTable() {
        // A single deck, so every card is unique and "not dealt twice" is checkable by identity.
        Shoe shoe = new Shoe(1, 100);
        for (int i = 0; i < 45; i++) {
            shoe.draw(); // earlier rounds: discards, eligible for the reshuffle
        }
        shoe.beginRound();
        List<Card> onTable = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            onTable.add(shoe.draw());
        }
        assertEquals(0, shoe.cardsRemaining());

        List<Card> afterReshuffle = new ArrayList<>();
        afterReshuffle.add(shoe.draw());
        assertEquals(45, shoe.totalCards()); // 52 minus the 7 on the table
        while (shoe.cardsRemaining() > 0) {
            afterReshuffle.add(shoe.draw());
        }

        assertEquals(45, afterReshuffle.size());
        assertTrue(afterReshuffle.stream().noneMatch(onTable::contains));
        // The on-table cards stayed counted, so seeing the other 45 completes exactly one deck.
        assertEquals(0, shoe.runningCount(CountingSystem.HI_LO));
        assertEquals(0, shoe.runningCount(CountingSystem.RED_SEVEN));
    }

    @Test
    void anExhaustedFixedTestShoeFailsWithAClearMessage() {
        Shoe shoe = new Shoe(List.of(new Card(Rank.TWO, Suit.CLUBS)));
        shoe.draw();

        IllegalStateException e = assertThrows(IllegalStateException.class, shoe::draw);
        assertTrue(e.getMessage().contains("exhausted"), e.getMessage());
    }
}
