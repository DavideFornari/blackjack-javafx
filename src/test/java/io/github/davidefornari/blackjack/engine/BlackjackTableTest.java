package io.github.davidefornari.blackjack.engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BlackjackTable#startRound} always deals in the order player, dealer, player,
 * dealer — so a fixed test shoe's first four cards are [playerCard1, dealerUpCard,
 * playerCard2, dealerHoleCard], and any cards after that are consumed, in order, by
 * whatever hit()/split()/doubleDown()/dealer-draw calls happen next.
 */
class BlackjackTableTest {

    private static Card c(Rank rank, Suit suit) {
        return new Card(rank, suit);
    }

    private static Shoe fixedShoe(Card... cards) {
        return new Shoe(List.of(cards));
    }

    @Test
    void dealingDebitsTheBetAndDealsTwoCardsEachWithHoleCardHidden() {
        Player player = new Player("Ada", 1000);
        Shoe shoe = fixedShoe(
                c(Rank.NINE, Suit.CLUBS), c(Rank.TWO, Suit.SPADES),
                c(Rank.SEVEN, Suit.CLUBS), c(Rank.THREE, Suit.SPADES)
        );
        BlackjackTable table = new BlackjackTable(player, shoe, GameRules.standard());

        table.startRound(100);

        assertEquals(900, player.bankroll());
        assertEquals(2, player.firstHand().size());
        assertEquals(2, table.dealer().hand().size());
        assertFalse(table.dealer().isHoleCardRevealed());
    }

    @Test
    void dealerBlackjackEndsTheRoundBeforeThePlayerCanAct() {
        Player player = new Player("Ada", 1000);
        // player: 9,7 = 16 (no blackjack). dealer up = Ace, hole = King -> dealer natural.
        Shoe shoe = fixedShoe(
                c(Rank.NINE, Suit.CLUBS), c(Rank.ACE, Suit.SPADES),
                c(Rank.SEVEN, Suit.CLUBS), c(Rank.KING, Suit.SPADES)
        );
        BlackjackTable table = new BlackjackTable(player, shoe, GameRules.standard());

        table.startRound(100);

        assertTrue(table.isPlayerTurnComplete());
        assertTrue(table.dealer().isHoleCardRevealed());
        assertTrue(table.dealer().hasBlackjack());

        table.playDealerTurn();
        List<Settlement> settlements = table.settle();

        assertEquals(1, settlements.size());
        assertEquals(RoundOutcome.LOSS, settlements.get(0).outcome());
        assertEquals(0, settlements.get(0).payout());
        assertEquals(900, player.bankroll());
    }

    @Test
    void dealerBlackjackAgainstPlayerBlackjackIsAPush() {
        Player player = new Player("Ada", 1000);
        // player: A,K = natural. dealer up = Ace, hole = King -> dealer natural too.
        Shoe shoe = fixedShoe(
                c(Rank.ACE, Suit.CLUBS), c(Rank.ACE, Suit.SPADES),
                c(Rank.KING, Suit.CLUBS), c(Rank.KING, Suit.SPADES)
        );
        BlackjackTable table = new BlackjackTable(player, shoe, GameRules.standard());

        table.startRound(100);
        table.playDealerTurn();
        List<Settlement> settlements = table.settle();

        assertEquals(RoundOutcome.PUSH, settlements.get(0).outcome());
        assertEquals(100, settlements.get(0).payout());
        assertEquals(1000, player.bankroll());
    }

    @Test
    void playerBlackjackPaysThreeToTwoWhenDealerHasNone() {
        Player player = new Player("Ada", 1000);
        // player: A,K = natural. dealer up = 9 (no peek), hole = 5 -> dealer total 14, draws more from filler.
        Shoe shoe = fixedShoe(
                c(Rank.ACE, Suit.CLUBS), c(Rank.NINE, Suit.SPADES),
                c(Rank.KING, Suit.CLUBS), c(Rank.FIVE, Suit.SPADES),
                c(Rank.TWO, Suit.CLUBS), c(Rank.TWO, Suit.SPADES),
                c(Rank.TWO, Suit.CLUBS), c(Rank.TWO, Suit.SPADES)
        );
        BlackjackTable table = new BlackjackTable(player, shoe, GameRules.standard());

        table.startRound(100);
        assertTrue(table.isPlayerTurnComplete());

        table.playDealerTurn();
        List<Settlement> settlements = table.settle();

        assertEquals(RoundOutcome.BLACKJACK_WIN, settlements.get(0).outcome());
        assertEquals(250, settlements.get(0).payout()); // stake back (100) + 3:2 profit (150)
        assertEquals(1150, player.bankroll());
    }

    @Test
    void bustingOnAHitEndsThePlayerTurnAndLosesTheBet() {
        Player player = new Player("Ada", 1000);
        // player: 10,9 = 19, hits into a 10 -> 29 bust. dealer up = 2 (no peek).
        Shoe shoe = fixedShoe(
                c(Rank.TEN, Suit.CLUBS), c(Rank.TWO, Suit.SPADES),
                c(Rank.NINE, Suit.CLUBS), c(Rank.THREE, Suit.SPADES),
                c(Rank.TEN, Suit.HEARTS)
        );
        BlackjackTable table = new BlackjackTable(player, shoe, GameRules.standard());

        table.startRound(100);
        table.hit();

        assertTrue(player.firstHand().isBust());
        assertTrue(table.isPlayerTurnComplete());

        table.playDealerTurn();
        assertEquals(2, table.dealer().hand().size()); // all player hands bust -> dealer doesn't draw further

        List<Settlement> settlements = table.settle();
        assertEquals(RoundOutcome.BUST, settlements.get(0).outcome());
        assertEquals(900, player.bankroll());
    }

    @Test
    void doubleDownDoublesTheWagerAndForcesExactlyOneMoreCard() {
        Player player = new Player("Ada", 1000);
        // player: 5,6 = 11 (prime double candidate). dealer up = 2 (no peek).
        Shoe shoe = fixedShoe(
                c(Rank.FIVE, Suit.CLUBS), c(Rank.TWO, Suit.SPADES),
                c(Rank.SIX, Suit.CLUBS), c(Rank.THREE, Suit.SPADES),
                c(Rank.NINE, Suit.HEARTS)
        );
        BlackjackTable table = new BlackjackTable(player, shoe, GameRules.standard());

        table.startRound(100);
        assertTrue(table.canDouble());
        table.doubleDown();

        Hand hand = player.firstHand();
        assertEquals(200, hand.wager());
        assertTrue(hand.isDoubled());
        assertEquals(3, hand.size());
        assertEquals(20, hand.total());
        assertFalse(hand.isActionable());
        assertTrue(table.isPlayerTurnComplete());
        assertEquals(800, player.bankroll()); // 1000 - 100 initial - 100 more on doubling
    }

    @Test
    void splittingCreatesTwoIndependentHandsWithSeparateWagers() {
        Player player = new Player("Ada", 1000);
        // player: 8,8 = pair. dealer up = 2 (no peek).
        Shoe shoe = fixedShoe(
                c(Rank.EIGHT, Suit.CLUBS), c(Rank.TWO, Suit.SPADES),
                c(Rank.EIGHT, Suit.HEARTS), c(Rank.THREE, Suit.SPADES),
                c(Rank.TWO, Suit.HEARTS), c(Rank.THREE, Suit.HEARTS)
        );
        BlackjackTable table = new BlackjackTable(player, shoe, GameRules.standard());

        table.startRound(100);
        assertTrue(table.canSplit());
        table.split();

        assertEquals(2, player.hands().size());
        assertEquals(100, player.hands().get(0).wager());
        assertEquals(100, player.hands().get(1).wager());
        assertEquals(2, player.hands().get(0).size());
        assertEquals(2, player.hands().get(1).size());
        assertEquals(800, player.bankroll()); // 1000 - 100 - 100 (second hand's bet)
        assertEquals(0, table.activeHandIndex());

        table.stand();
        assertEquals(1, table.activeHandIndex());
        table.stand();
        assertTrue(table.isPlayerTurnComplete());
    }

    @Test
    void splitAcesGetExactlyOneCardEachAndCannotBeHitAgain() {
        Player player = new Player("Ada", 1000);
        // player: A,A. dealer up = 2 (no peek). Hand 0 draws K (=21, would auto-lock anyway),
        // hand 1 draws 9 (soft 20 — NOT auto-locked by the >=21 rule, only by the split-aces rule).
        Shoe shoe = fixedShoe(
                c(Rank.ACE, Suit.CLUBS), c(Rank.TWO, Suit.SPADES),
                c(Rank.ACE, Suit.HEARTS), c(Rank.THREE, Suit.SPADES),
                c(Rank.KING, Suit.HEARTS), c(Rank.NINE, Suit.HEARTS)
        );
        BlackjackTable table = new BlackjackTable(player, shoe, GameRules.standard());

        table.startRound(100);
        table.split();

        Hand first = player.hands().get(0);
        Hand second = player.hands().get(1);

        assertTrue(first.isSplitAces());
        assertTrue(second.isSplitAces());
        assertEquals(2, first.size());
        assertEquals(2, second.size());
        assertEquals(20, second.total());
        assertFalse(first.isActionable());
        assertFalse(second.isActionable());
        assertTrue(table.isPlayerTurnComplete());
    }

    @Test
    void matchingTotalsAgainstTheDealerIsAPush() {
        Player player = new Player("Ada", 1000);
        // player: 10,9 = 19, stands. dealer up = 10 (peeked, no blackjack since hole isn't an ace), hole = 9 -> 19.
        Shoe shoe = fixedShoe(
                c(Rank.TEN, Suit.CLUBS), c(Rank.TEN, Suit.SPADES),
                c(Rank.NINE, Suit.CLUBS), c(Rank.NINE, Suit.SPADES)
        );
        BlackjackTable table = new BlackjackTable(player, shoe, GameRules.standard());

        table.startRound(100);
        table.stand();
        table.playDealerTurn();

        assertEquals(19, table.dealer().hand().total());
        List<Settlement> settlements = table.settle();
        assertEquals(RoundOutcome.PUSH, settlements.get(0).outcome());
        assertEquals(1000, player.bankroll());
    }

    @Test
    void dealerStandsOnSoftSeventeenByDefault() {
        Player player = new Player("Ada", 1000);
        // player: 10,7 = 17, stands (stays in play). dealer up = Ace, hole = 6 -> soft 17.
        Shoe shoe = fixedShoe(
                c(Rank.TEN, Suit.CLUBS), c(Rank.ACE, Suit.SPADES),
                c(Rank.SEVEN, Suit.CLUBS), c(Rank.SIX, Suit.SPADES)
        );
        BlackjackTable table = new BlackjackTable(player, shoe, GameRules.standard());

        table.startRound(100);
        table.stand();
        table.playDealerTurn();

        assertEquals(2, table.dealer().hand().size());
        assertEquals(17, table.dealer().hand().total());
        assertTrue(table.dealer().hand().isSoft());
    }

    @Test
    void dealerHitsSoftSeventeenWhenTheRuleIsEnabled() {
        Player player = new Player("Ada", 1000);
        Shoe shoe = fixedShoe(
                c(Rank.TEN, Suit.CLUBS), c(Rank.ACE, Suit.SPADES),
                c(Rank.SEVEN, Suit.CLUBS), c(Rank.SIX, Suit.SPADES),
                c(Rank.TWO, Suit.HEARTS)
        );
        GameRules h17 = new GameRules(6, 50, true, 1.5, true, 4, false);
        BlackjackTable table = new BlackjackTable(player, shoe, h17);

        table.startRound(100);
        table.stand();
        table.playDealerTurn();

        assertEquals(3, table.dealer().hand().size());
    }

    @Test
    void insuranceIsNotOfferedByDefaultEvenAgainstAnAceUpCard() {
        Player player = new Player("Ada", 1000);
        // Same deal as dealerBlackjackEndsTheRoundBeforeThePlayerCanAct, but via GameRules.standard()
        // (insurance off) — the round should resolve immediately, exactly as it always has.
        Shoe shoe = fixedShoe(
                c(Rank.NINE, Suit.CLUBS), c(Rank.ACE, Suit.SPADES),
                c(Rank.SEVEN, Suit.CLUBS), c(Rank.KING, Suit.SPADES)
        );
        BlackjackTable table = new BlackjackTable(player, shoe, GameRules.standard());

        table.startRound(100);

        assertFalse(table.isInsurancePending());
        assertTrue(table.isPlayerTurnComplete());
        assertTrue(table.lastInsuranceSettlement().isEmpty());
    }

    private static GameRules withInsurance() {
        GameRules standard = GameRules.standard();
        return new GameRules(
                standard.deckCount(), standard.penetrationPercent(), standard.dealerHitsSoftSeventeen(),
                standard.blackjackPayoutRatio(), standard.doubleAfterSplitAllowed(), standard.maxSplitHands(), true);
    }

    @Test
    void insuranceIsOnlyOfferedOnAnAceUpCardNotATen() {
        Player player = new Player("Ada", 1000);
        // dealer up = 10, hole = Ace -> a natural, but insurance is never offered on a ten
        // up-card in real rules, so this must resolve immediately just like before.
        Shoe shoe = fixedShoe(
                c(Rank.NINE, Suit.CLUBS), c(Rank.TEN, Suit.SPADES),
                c(Rank.SEVEN, Suit.CLUBS), c(Rank.ACE, Suit.SPADES)
        );
        BlackjackTable table = new BlackjackTable(player, shoe, withInsurance());

        table.startRound(100);

        assertFalse(table.isInsurancePending());
        assertTrue(table.isPlayerTurnComplete());
        assertTrue(table.dealer().hasBlackjack());
    }

    @Test
    void takingInsuranceAgainstADealerBlackjackPaysTwoToOne() {
        Player player = new Player("Ada", 1000);
        // player: 9,7 = 16. dealer up = Ace, hole = King -> dealer natural.
        Shoe shoe = fixedShoe(
                c(Rank.NINE, Suit.CLUBS), c(Rank.ACE, Suit.SPADES),
                c(Rank.SEVEN, Suit.CLUBS), c(Rank.KING, Suit.SPADES)
        );
        BlackjackTable table = new BlackjackTable(player, shoe, withInsurance());

        table.startRound(100);
        assertTrue(table.isInsurancePending());
        assertFalse(table.dealer().isHoleCardRevealed());
        assertEquals(50, table.maxInsuranceBet());
        assertEquals(900, player.bankroll()); // bet debited, insurance not yet placed

        table.takeInsurance(50);

        assertFalse(table.isInsurancePending());
        assertTrue(table.dealer().isHoleCardRevealed());
        assertTrue(table.isPlayerTurnComplete());
        assertEquals(1000, player.bankroll()); // -100 bet, -50 insurance, +150 insurance payout
        assertEquals(new InsuranceSettlement(50, true, 150), table.lastInsuranceSettlement().orElseThrow());

        table.playDealerTurn();
        List<Settlement> settlements = table.settle();
        assertEquals(RoundOutcome.LOSS, settlements.get(0).outcome());
        assertEquals(1000, player.bankroll()); // main hand's loss pays nothing more; insurance already made it whole
    }

    @Test
    void decliningInsuranceForfeitsNothingButStillLosesToADealerBlackjack() {
        Player player = new Player("Ada", 1000);
        Shoe shoe = fixedShoe(
                c(Rank.NINE, Suit.CLUBS), c(Rank.ACE, Suit.SPADES),
                c(Rank.SEVEN, Suit.CLUBS), c(Rank.KING, Suit.SPADES)
        );
        BlackjackTable table = new BlackjackTable(player, shoe, withInsurance());

        table.startRound(100);
        table.declineInsurance();

        assertTrue(table.isPlayerTurnComplete());
        assertEquals(new InsuranceSettlement(0, false, 0), table.lastInsuranceSettlement().orElseThrow());

        table.playDealerTurn();
        List<Settlement> settlements = table.settle();
        assertEquals(RoundOutcome.LOSS, settlements.get(0).outcome());
        assertEquals(900, player.bankroll());
    }

    @Test
    void insuranceIsLostWhenTheDealerDoesNotHaveBlackjack() {
        Player player = new Player("Ada", 1000);
        // dealer up = Ace, hole = 6 -> soft 17, no dealer blackjack.
        Shoe shoe = fixedShoe(
                c(Rank.TEN, Suit.CLUBS), c(Rank.ACE, Suit.SPADES),
                c(Rank.SEVEN, Suit.CLUBS), c(Rank.SIX, Suit.SPADES)
        );
        BlackjackTable table = new BlackjackTable(player, shoe, withInsurance());

        table.startRound(100);
        table.takeInsurance(50);

        assertFalse(table.isPlayerTurnComplete());
        assertEquals(850, player.bankroll()); // -100 bet, -50 insurance, no payout
        assertEquals(new InsuranceSettlement(50, false, 0), table.lastInsuranceSettlement().orElseThrow());
    }
}
