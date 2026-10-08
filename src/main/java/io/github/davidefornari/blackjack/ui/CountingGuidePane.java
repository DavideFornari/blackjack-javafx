package io.github.davidefornari.blackjack.ui;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

/** What each counting system on the setup form counts, how it is played, and which to pick. */
public final class CountingGuidePane extends OverlayPane {

    public CountingGuidePane() {
        super(480);
        Label title = new Label("Counting");
        title.getStyleClass().add("setup-title");

        Label tipsHeading = new Label("Tips on real betting");
        tipsHeading.getStyleClass().add("setup-heading");

        VBox text = new VBox(10,
                wrapped("Every card dealt moves the running count. A high count means the shoe left "
                        + "is rich in tens and Aces, which favours you: bet more. The table keeps the "
                        + "count for you and, for balanced systems, also shows the true count: the "
                        + "running count divided by the decks left, the number to bet on."),
                wrapped("Hi-Lo: 2-6 count +1, 7-9 count 0, tens and Aces -1. Level 1 and balanced, "
                        + "the standard count. Raise your bet as the true count climbs past +1."),
                wrapped("Red Seven: Hi-Lo plus red 7s at +1. Unbalanced, so it starts at -2 per deck "
                        + "and is read straight off the running count: raise your bet once it reaches "
                        + "0. Arnold Snyder's count, built to need no division."),
                wrapped("Zen Count: 2, 3, 7 count +1, 4-6 +2, tens -2, Aces -1. Level 2 and balanced, "
                        + "also Snyder's: weighting the cards more finely tracks the shoe more "
                        + "accurately than Hi-Lo."),
                wrapped("Omega II: 2, 3, 7 count +1, 4-6 +2, 9 -1, tens -2, 8s and Aces 0. Level 2 "
                        + "and balanced, by Bryce Carlson. Leaving the Ace out sharpens playing "
                        + "decisions; for betting, players add a separate Ace count, which this table "
                        + "doesn't show."),
                tipsHeading,
                wrapped("At a real table you keep the count yourself, in your head, at dealing speed. "
                        + "Start with Hi-Lo. Pick Red Seven if dividing by the decks left is too much: "
                        + "it gets about 80% of Hi-Lo's gain for less work. Move to Zen or Omega II "
                        + "only once Hi-Lo is automatic: a complex count played slowly or with mistakes "
                        + "earns less than a simple one played well."));

        Button closeButton = new Button("Close");
        closeButton.getStyleClass().add("primary-button");
        closeButton.setOnAction(e -> close());

        // Scrolls only when the window is shorter than the guide. A ScrollPane sizes itself from
        // its content's unwrapped (one-line) height, so its preferred height follows the text's
        // laid-out height instead.
        ScrollPane scroll = new ScrollPane(text);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.prefViewportHeightProperty().bind(text.heightProperty());
        scroll.getStyleClass().add("setup-scroll");

        setContent(title, scroll, buttonRow(closeButton));
    }

    private static Label wrapped(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        return label;
    }
}
