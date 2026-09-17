package io.github.davidefornari.blackjack.engine;

/**
 * The resolved result of an insurance side bet, independent of how the main hand settles.
 *
 * @param amountWagered amount placed on insurance (0 if declined)
 * @param won            whether an insurance bet was placed and the dealer had blackjack
 * @param payout         total credited back to the bankroll (stake x3 on a win, 0 otherwise)
 */
public record InsuranceSettlement(long amountWagered, boolean won, long payout) {
}
