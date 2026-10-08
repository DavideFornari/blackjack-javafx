package io.github.davidefornari.blackjack.ui;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.text.TextAlignment;

/**
 * The one shared confirmation popup; {@link #ask} fills in the specifics for each action. The
 * {@code setup-title} font truncates past ~10 characters at this card's 340px width, so keep
 * titles short ("Cash Out?" — "Leave the Table" rendered as "Leave the T...").
 */
public final class ConfirmPane extends OverlayPane {

    private final Label titleLabel = new Label();
    private final Label messageLabel = new Label();
    private final Button confirmButton = new Button();
    private Runnable onConfirm = () -> { };

    public ConfirmPane() {
        super(340);
        titleLabel.getStyleClass().add("setup-title");
        messageLabel.setWrapText(true);
        messageLabel.setTextAlignment(TextAlignment.CENTER);

        confirmButton.getStyleClass().add("primary-button");
        confirmButton.setOnAction(e -> {
            close();
            onConfirm.run();
        });
        Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(e -> close());

        setContent(titleLabel, messageLabel, buttonRow(cancelButton, confirmButton));
    }

    /** Opens the popup; {@code action} runs only if the player confirms. */
    public void ask(String title, String message, String confirmText, Runnable action) {
        titleLabel.setText(title);
        messageLabel.setText(message);
        confirmButton.setText(confirmText);
        onConfirm = action;
        open();
    }
}
