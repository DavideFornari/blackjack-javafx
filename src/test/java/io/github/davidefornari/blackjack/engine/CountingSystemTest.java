package io.github.davidefornari.blackjack.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Spot-checks each system's per-card tags against its published count chart, to catch
 * any transcription mistake in porting {@code Mazzo.java}'s (already-correct) counting
 * logic to {@link CountingSystem}.
 */
class CountingSystemTest {

    @Test
    void hiLoTags() {
        assertEquals(1, CountingSystem.HI_LO.tagFor(card(Rank.TWO, Suit.CLUBS)));
        assertEquals(0, CountingSystem.HI_LO.tagFor(card(Rank.SEVEN, Suit.CLUBS)));
        assertEquals(-1, CountingSystem.HI_LO.tagFor(card(Rank.TEN, Suit.CLUBS)));
        assertEquals(-1, CountingSystem.HI_LO.tagFor(card(Rank.ACE, Suit.CLUBS)));
    }

    @Test
    void omegaTwoTags() {
        assertEquals(1, CountingSystem.OMEGA_II.tagFor(card(Rank.TWO, Suit.CLUBS)));
        assertEquals(2, CountingSystem.OMEGA_II.tagFor(card(Rank.FOUR, Suit.CLUBS)));
        assertEquals(-1, CountingSystem.OMEGA_II.tagFor(card(Rank.NINE, Suit.CLUBS)));
        assertEquals(-2, CountingSystem.OMEGA_II.tagFor(card(Rank.KING, Suit.CLUBS)));
        assertEquals(0, CountingSystem.OMEGA_II.tagFor(card(Rank.EIGHT, Suit.CLUBS)));
        assertEquals(0, CountingSystem.OMEGA_II.tagFor(card(Rank.ACE, Suit.CLUBS)));
    }

    @Test
    void redSevenTags() {
        assertEquals(1, CountingSystem.RED_SEVEN.tagFor(card(Rank.FIVE, Suit.CLUBS)));
        assertEquals(1, CountingSystem.RED_SEVEN.tagFor(card(Rank.SEVEN, Suit.HEARTS)));
        assertEquals(0, CountingSystem.RED_SEVEN.tagFor(card(Rank.SEVEN, Suit.CLUBS)));
        assertEquals(0, CountingSystem.RED_SEVEN.tagFor(card(Rank.NINE, Suit.CLUBS)));
        assertEquals(-1, CountingSystem.RED_SEVEN.tagFor(card(Rank.QUEEN, Suit.CLUBS)));
    }

    @Test
    void zenCountTags() {
        assertEquals(1, CountingSystem.ZEN_COUNT.tagFor(card(Rank.THREE, Suit.CLUBS)));
        assertEquals(2, CountingSystem.ZEN_COUNT.tagFor(card(Rank.SIX, Suit.CLUBS)));
        assertEquals(-1, CountingSystem.ZEN_COUNT.tagFor(card(Rank.ACE, Suit.CLUBS)));
        assertEquals(-2, CountingSystem.ZEN_COUNT.tagFor(card(Rank.TEN, Suit.CLUBS)));
        assertEquals(0, CountingSystem.ZEN_COUNT.tagFor(card(Rank.NINE, Suit.CLUBS)));
    }

    private static Card card(Rank rank, Suit suit) {
        return new Card(rank, suit);
    }
}
