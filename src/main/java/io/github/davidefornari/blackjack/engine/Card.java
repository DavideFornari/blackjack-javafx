package io.github.davidefornari.blackjack.engine;

public record Card(Rank rank, Suit suit) {

    @Override
    public String toString() {
        return rank.symbol() + suit.symbol();
    }
}
