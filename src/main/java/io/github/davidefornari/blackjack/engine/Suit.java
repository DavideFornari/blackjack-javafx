package io.github.davidefornari.blackjack.engine;

public enum Suit {
    CLUBS("♣", Color.BLACK),
    DIAMONDS("♦", Color.RED),
    HEARTS("♥", Color.RED),
    SPADES("♠", Color.BLACK);

    public enum Color { RED, BLACK }

    private final String symbol;
    private final Color color;

    Suit(String symbol, Color color) {
        this.symbol = symbol;
        this.color = color;
    }

    public String symbol() {
        return symbol;
    }

    public Color color() {
        return color;
    }
}
