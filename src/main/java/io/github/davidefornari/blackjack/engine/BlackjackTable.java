package io.github.davidefornari.blackjack.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Drives one player against the dealer, round after round, sharing a single shoe.
 *
 * <p>Usage per round:
 * <pre>
 *   table.startRound(bet);
 *   if (table.isInsurancePending()) {
 *       // offer takeInsurance(amount up to maxInsuranceBet())/declineInsurance()
 *   }
 *   while (!table.isPlayerTurnComplete()) {
 *       // offer hit()/stand()/doubleDown() if canDouble()/split() if canSplit()
 *       // for table.player().hands().get(table.activeHandIndex())
 *   }
 *   table.playDealerTurn();
 *   List&lt;Settlement&gt; results = table.settle();
 *   // table.lastInsuranceSettlement() holds this round's side-bet result, if offered
 * </pre>
 *
 * <p>This is also where the original project's biggest structural gap is fixed: the
 * dealer is dealt two cards up front (one up, one hole) and, whenever the up-card is
 * a ten-value card, the hole card is peeked immediately (on an Ace up-card, the peek
 * waits on an insurance decision — see {@link #isInsurancePending()} — when
 * {@link GameRules#insuranceAllowed()} is on, otherwise it's immediate there too). If
 * that is a dealer natural, the round ends right there — a player blackjack pushes,
 * anything else loses — instead of letting players hit, double or split into a hand
 * that was already lost.
 */
public final class BlackjackTable {

    private final Player player;
    private final Shoe shoe;
    private final GameRules rules;

    private Dealer dealer = new Dealer();
    private int activeHandIndex = -1;
    private boolean dealerBlackjackShortCircuit;
    private boolean insurancePending;
    private boolean insuranceOffered;
    private InsuranceSettlement insuranceSettlement;

    public BlackjackTable(Player player, Shoe shoe, GameRules rules) {
        this.player = player;
        this.shoe = shoe;
        this.rules = rules;
    }

    public Player player() {
        return player;
    }

    public Dealer dealer() {
        return dealer;
    }

    public GameRules rules() {
        return rules;
    }

    public int cardsRemainingInShoe() {
        return shoe.cardsRemaining();
    }

    public int runningCount(CountingSystem system) {
        return shoe.runningCount(system);
    }

    /** Deducts the bet, reshuffles if the shoe has hit its penetration limit, and deals the opening hands. */
    public void startRound(long bet) {
        if (bet <= 0) {
            throw new IllegalArgumentException("bet must be positive");
        }
        if (bet > player.bankroll()) {
            throw new IllegalArgumentException("bet exceeds bankroll");
        }
        if (shoe.needsShuffle()) {
            shoe.shuffle();
        }

        dealer = new Dealer();
        player.resetForNewRound();
        dealerBlackjackShortCircuit = false;
        insurancePending = false;
        insuranceOffered = false;
        insuranceSettlement = null;

        player.debit(bet);
        Hand hand = player.firstHand();
        hand.setWager(bet);

        hand.addCard(shoe.draw());
        dealer.hand().addCard(shoe.draw());
        hand.addCard(shoe.draw());
        dealer.hand().addCard(shoe.draw());

        if (rules.insuranceAllowed() && dealer.upCard().rank().isAce()) {
            insurancePending = true;
            insuranceOffered = true;
            activeHandIndex = -1;
            return;
        }

        resolveDealerPeek();
    }

    /**
     * Checks for a dealer natural and either short-circuits the round or starts the
     * player's turn. Called directly from {@link #startRound} when insurance isn't in
     * play, and again from {@link #resolveInsuranceDecision} once an insurance decision
     * (on an Ace up-card) has been made.
     */
    private void resolveDealerPeek() {
        if (dealer.showsAceOrTen() && dealer.hasBlackjack()) {
            dealer.revealHoleCard();
            dealerBlackjackShortCircuit = true;
            activeHandIndex = -1;
            return;
        }
        Hand hand = player.firstHand();
        activeHandIndex = hand.isActionable() ? 0 : -1;
    }

    /** Whether the round is paused waiting for an insurance take/decline decision (Ace up-card only). */
    public boolean isInsurancePending() {
        return insurancePending;
    }

    /** Maximum insurance bet: half the original wager, the standard casino limit. */
    public long maxInsuranceBet() {
        return player.firstHand().wager() / 2;
    }

    public void declineInsurance() {
        if (!insurancePending) {
            throw new IllegalStateException("No insurance decision is pending");
        }
        resolveInsuranceDecision(0);
    }

    public void takeInsurance(long amount) {
        if (!insurancePending) {
            throw new IllegalStateException("No insurance decision is pending");
        }
        if (amount <= 0 || amount > maxInsuranceBet()) {
            throw new IllegalArgumentException("Insurance amount must be between 1 and " + maxInsuranceBet());
        }
        if (amount > player.bankroll()) {
            throw new IllegalArgumentException("Insurance amount exceeds bankroll");
        }
        player.debit(amount);
        resolveInsuranceDecision(amount);
    }

    private void resolveInsuranceDecision(long amount) {
        insurancePending = false;
        boolean dealerBlackjack = dealer.hasBlackjack();
        boolean won = amount > 0 && dealerBlackjack;
        long payout = won ? amount * 3 : 0;
        if (payout > 0) {
            player.credit(payout);
        }
        insuranceSettlement = new InsuranceSettlement(amount, won, payout);
        resolveDealerPeek();
    }

    /** The last round's insurance result, if insurance was offered (Ace up-card, rule enabled) this round. */
    public Optional<InsuranceSettlement> lastInsuranceSettlement() {
        return insuranceOffered ? Optional.ofNullable(insuranceSettlement) : Optional.empty();
    }

    public int activeHandIndex() {
        return activeHandIndex;
    }

    public boolean isPlayerTurnComplete() {
        return activeHandIndex == -1;
    }

    private Hand activeHand() {
        if (activeHandIndex < 0) {
            throw new IllegalStateException("No active hand");
        }
        return player.hands().get(activeHandIndex);
    }

    public void hit() {
        Hand hand = activeHand();
        hand.addCard(shoe.draw());
        if (!hand.isActionable()) {
            moveToNextHand();
        }
    }

    public void stand() {
        activeHand().stand();
        moveToNextHand();
    }

    public boolean canDouble() {
        if (activeHandIndex < 0) {
            return false;
        }
        Hand hand = activeHand();
        if (hand.size() != 2) {
            return false;
        }
        if (hand.isFromSplit() && !rules.doubleAfterSplitAllowed()) {
            return false;
        }
        return player.bankroll() >= hand.wager();
    }

    public void doubleDown() {
        if (!canDouble()) {
            throw new IllegalStateException("Double down is not available on this hand");
        }
        Hand hand = activeHand();
        player.debit(hand.wager());
        hand.setWager(hand.wager() * 2);
        hand.markDoubled();
        hand.addCard(shoe.draw());
        hand.stand();
        moveToNextHand();
    }

    public boolean canSplit() {
        if (activeHandIndex < 0) {
            return false;
        }
        Hand hand = activeHand();
        if (!hand.isPair()) {
            return false;
        }
        if (player.hands().size() >= rules.maxSplitHands()) {
            return false;
        }
        return player.bankroll() >= hand.wager();
    }

    public void split() {
        if (!canSplit()) {
            throw new IllegalStateException("Split is not available on this hand");
        }
        Hand original = activeHand();
        boolean aces = original.isPairOfAces();

        Hand first = new Hand();
        first.setWager(original.wager());
        first.markFromSplit();
        first.addCard(original.cards().get(0));

        Hand second = new Hand();
        second.setWager(original.wager());
        second.markFromSplit();
        second.addCard(original.cards().get(1));

        player.debit(original.wager());
        player.hands().set(activeHandIndex, first);
        player.hands().add(activeHandIndex + 1, second);

        first.addCard(shoe.draw());
        second.addCard(shoe.draw());

        if (aces) {
            first.markSplitAces();
            second.markSplitAces();
            first.stand();
            second.stand();
        }

        if (!first.isActionable()) {
            moveToNextHand();
        }
    }

    private void moveToNextHand() {
        List<Hand> hands = player.hands();
        for (int i = activeHandIndex + 1; i < hands.size(); i++) {
            if (hands.get(i).isActionable()) {
                activeHandIndex = i;
                return;
            }
        }
        activeHandIndex = -1;
    }

    /** Reveals the hole card and draws per the house rule, unless every player hand already busted. */
    public void playDealerTurn() {
        if (dealerBlackjackShortCircuit) {
            return;
        }
        dealer.revealHoleCard();
        boolean anyHandStillInPlay = player.hands().stream().anyMatch(h -> !h.isBust());
        if (!anyHandStillInPlay) {
            return;
        }
        while (dealer.shouldDraw(rules)) {
            dealer.hand().addCard(shoe.draw());
        }
    }

    /** Resolves every player hand against the final dealer hand and credits winnings to the bankroll. */
    public List<Settlement> settle() {
        List<Settlement> settlements = new ArrayList<>();
        boolean dealerBlackjack = dealer.hasBlackjack();
        boolean dealerBust = dealer.hand().isBust();
        int dealerTotal = dealer.hand().total();

        for (Hand hand : player.hands()) {
            RoundOutcome outcome;
            long payout;

            if (hand.status() == Hand.Status.BLACKJACK) {
                if (dealerBlackjack) {
                    outcome = RoundOutcome.PUSH;
                    payout = hand.wager();
                } else {
                    outcome = RoundOutcome.BLACKJACK_WIN;
                    payout = Math.round(hand.wager() * (1 + rules.blackjackPayoutRatio()));
                }
            } else if (hand.isBust()) {
                outcome = RoundOutcome.BUST;
                payout = 0;
            } else if (dealerBlackjack) {
                outcome = RoundOutcome.LOSS;
                payout = 0;
            } else if (dealerBust || hand.total() > dealerTotal) {
                outcome = RoundOutcome.WIN;
                payout = hand.wager() * 2;
            } else if (hand.total() == dealerTotal) {
                outcome = RoundOutcome.PUSH;
                payout = hand.wager();
            } else {
                outcome = RoundOutcome.LOSS;
                payout = 0;
            }

            if (payout > 0) {
                player.credit(payout);
            }
            settlements.add(new Settlement(hand, outcome, payout));
        }
        return settlements;
    }
}
