package io.github.davidefornari.blackjack.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * An in-theme popup, hidden until opened: a {@code setup-card} of content centred on a dimming
 * {@code setup-overlay}. Used in place of a stock {@link javafx.scene.control.Dialog} or
 * {@link javafx.scene.control.Alert}, whose system look, near-invisible header text and
 * OS-locale button captions didn't fit the felt table. A subclass whose content uses its own
 * fields passes no content to the constructor and calls {@link #setContent} once they exist.
 */
public class OverlayPane extends VBox {

    private final VBox card = new VBox(16);

    public OverlayPane(double maxWidth, Node... content) {
        card.getChildren().setAll(content);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(28));
        card.setMaxWidth(maxWidth);
        card.getStyleClass().add("setup-card");

        getChildren().add(card);
        setAlignment(Pos.CENTER);
        getStyleClass().add("setup-overlay");
        close();
    }

    protected final void setContent(Node... content) {
        card.getChildren().setAll(content);
    }

    /** Shows the popup; hidden, it is also out of layout. */
    public final void open() {
        setVisible(true);
        setManaged(true);
    }

    public final void close() {
        setVisible(false);
        setManaged(false);
    }

    public final boolean isOpen() {
        return isVisible();
    }

    /** A centred row of buttons, as every popup ends with. */
    static HBox buttonRow(Node... buttons) {
        HBox row = new HBox(12, buttons);
        row.setAlignment(Pos.CENTER);
        return row;
    }
}
