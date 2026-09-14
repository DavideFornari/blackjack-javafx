package io.github.davidefornari.blackjack.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;

/** A reusable strip of overlapping cards with a caption above and a total below, used for both the dealer and every player hand. */
public final class HandPane extends VBox {

    private final HBox cardsRow = new HBox(-32);
    private final Label captionLabel = new Label();
    private final Label totalLabel = new Label();

    public HandPane() {
        setAlignment(Pos.CENTER);
        setSpacing(6);
        cardsRow.setAlignment(Pos.CENTER);
        captionLabel.getStyleClass().add("hand-caption");
        totalLabel.getStyleClass().add("hand-total");
        getStyleClass().add("hand-pane");
        getChildren().addAll(captionLabel, cardsRow, totalLabel);
    }

    public void setCaption(String text) {
        captionLabel.setText(text);
    }

    public void setTotalText(String text) {
        totalLabel.setText(text);
    }

    public void setCards(List<CardView> cards) {
        cardsRow.getChildren().setAll(cards);
    }

    public void setActive(boolean active) {
        getStyleClass().remove("hand-active");
        if (active) {
            getStyleClass().add("hand-active");
        }
    }
}
