package io.github.davidefornari.blackjack.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandTest {

    @Test
    void softAceCountsAsElevenWhenSafe() {
        Hand hand = new Hand();
        hand.addCard(new Card(Rank.ACE, Suit.SPADES));
        hand.addCard(new Card(Rank.SIX, Suit.HEARTS));

        assertEquals(17, hand.total());
        assertTrue(hand.isSoft());
    }

    @Test
    void twoAcesScoreAsTwelveNotABust() {
        // This is the exact bug in the original project: Carta.java hardcoded every
        // Ace to value 11 with no downgrade path, so two Aces scored as 22 and busted
        // immediately instead of the correct soft 12.
        Hand hand = new Hand();
        hand.addCard(new Card(Rank.ACE, Suit.SPADES));
        hand.addCard(new Card(Rank.ACE, Suit.HEARTS));

        assertEquals(12, hand.total());
        assertFalse(hand.isBust());
        assertTrue(hand.isSoft());
    }

    @Test
    void aceDowngradesToAvoidBustWithMultipleCards() {
        Hand hand = new Hand();
        hand.addCard(new Card(Rank.ACE, Suit.SPADES));
        hand.addCard(new Card(Rank.NINE, Suit.HEARTS));
        hand.addCard(new Card(Rank.FIVE, Suit.CLUBS));

        assertEquals(15, hand.total()); // 11 + 9 + 5 = 25 -> downgrade the Ace to 1
        assertFalse(hand.isBust());
        assertFalse(hand.isSoft());
    }

    @Test
    void bustIsDetectedOnceEveryAceIsAlreadyHard() {
        Hand hand = new Hand();
        hand.addCard(new Card(Rank.TEN, Suit.SPADES));
        hand.addCard(new Card(Rank.NINE, Suit.HEARTS));
        hand.addCard(new Card(Rank.FIVE, Suit.CLUBS));

        assertEquals(24, hand.total());
        assertTrue(hand.isBust());
        assertEquals(Hand.Status.BUST, hand.status());
    }

    @Test
    void naturalBlackjackIsDetected() {
        Hand hand = new Hand();
        hand.addCard(new Card(Rank.ACE, Suit.SPADES));
        hand.addCard(new Card(Rank.KING, Suit.HEARTS));

        assertTrue(hand.isNaturalBlackjack());
        assertEquals(Hand.Status.BLACKJACK, hand.status());
        assertFalse(hand.isActionable());
    }

    @Test
    void twentyOneAfterASplitIsNotANaturalBlackjack() {
        Hand hand = new Hand();
        hand.markFromSplit();
        hand.addCard(new Card(Rank.ACE, Suit.SPADES));
        hand.addCard(new Card(Rank.KING, Suit.HEARTS));

        assertEquals(21, hand.total());
        assertFalse(hand.isNaturalBlackjack());
    }

    @Test
    void handLocksAutomaticallyOnReachingTwentyOne() {
        Hand hand = new Hand();
        hand.addCard(new Card(Rank.SEVEN, Suit.SPADES));
        hand.addCard(new Card(Rank.SEVEN, Suit.HEARTS));
        assertTrue(hand.isActionable());

        hand.addCard(new Card(Rank.SEVEN, Suit.CLUBS));

        assertEquals(21, hand.total());
        assertFalse(hand.isActionable());
    }

    @Test
    void pairDetectionIsByBlackjackValueNotExactRank() {
        Hand hand = new Hand();
        hand.addCard(new Card(Rank.KING, Suit.SPADES));
        hand.addCard(new Card(Rank.QUEEN, Suit.HEARTS));

        assertTrue(hand.isPair());
        assertFalse(hand.isPairOfAces());
    }
}
