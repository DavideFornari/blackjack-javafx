package io.github.davidefornari.blackjack.engine;

/**
 * The house. Dealt two cards like a player (one up-card, one concealed hole card) —
 * unlike the original project, where the dealer only ever held a single card until
 * after every player had finished acting, which made a dealer blackjack undetectable.
 */
public final class Dealer {

    private final Hand hand = new Hand();
    private boolean holeCardRevealed;

    public Hand hand() {
        return hand;
    }

    public Card upCard() {
        return hand.cards().get(0);
    }

    public boolean showsAceOrTen() {
        Rank rank = upCard().rank();
        return rank.isAce() || rank.isTenValue();
    }

    public boolean hasBlackjack() {
        return hand.isNaturalBlackjack();
    }

    public boolean isHoleCardRevealed() {
        return holeCardRevealed;
    }

    public void revealHoleCard() {
        holeCardRevealed = true;
    }

    /** Standard fixed drawing rule: hit below 17; on exactly 17, hit only if the table plays H17 and this 17 is soft. */
    public boolean shouldDraw(GameRules rules) {
        int total = hand.total();
        if (total < 17) {
            return true;
        }
        return total == 17 && hand.isSoft() && rules.dealerHitsSoftSeventeen();
    }
}
