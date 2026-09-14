package io.github.davidefornari.blackjack.engine;

/**
 * The resolved result of one hand at the end of a round.
 *
 * @param hand    the resolved hand
 * @param outcome how it resolved against the dealer
 * @param payout  total amount credited back to the player's bankroll (0 on a loss/bust;
 *                the returned stake alone on a push; stake x2 on a plain win;
 *                stake x (1 + blackjackPayoutRatio) on a natural)
 */
public record Settlement(Hand hand, RoundOutcome outcome, long payout) {
}
