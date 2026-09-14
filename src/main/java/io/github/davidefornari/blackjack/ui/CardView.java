package io.github.davidefornari.blackjack.ui;

import io.github.davidefornari.blackjack.engine.Card;
import io.github.davidefornari.blackjack.engine.Suit;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** A single playing card, rendered as styled text on a rounded panel — no image assets required. */
public final class CardView extends StackPane {

    public static final double WIDTH = 78;
    public static final double HEIGHT = 110;

    private CardView() {
        setPrefSize(WIDTH, HEIGHT);
        setMinSize(WIDTH, HEIGHT);
        setMaxSize(WIDTH, HEIGHT);
        getStyleClass().add("card");
    }

    public static CardView faceUp(Card card) {
        CardView view = new CardView();
        view.getStyleClass().add("card-face");
        view.getStyleClass().add(card.suit().color() == Suit.Color.RED ? "card-red" : "card-black");

        Label rank = new Label(card.rank().symbol());
        rank.getStyleClass().add("card-rank");
        Label suit = new Label(card.suit().symbol());
        suit.getStyleClass().add("card-suit");

        VBox box = new VBox(2, rank, suit);
        box.setAlignment(Pos.CENTER);
        view.getChildren().add(box);
        return view;
    }

    public static CardView faceDown() {
        CardView view = new CardView();
        view.getStyleClass().add("card-back");
        return view;
    }
}
