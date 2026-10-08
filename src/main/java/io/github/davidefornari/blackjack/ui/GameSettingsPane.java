package io.github.davidefornari.blackjack.ui;

import io.github.davidefornari.blackjack.engine.BlackjackPayout;
import io.github.davidefornari.blackjack.engine.GameRules;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * Edits every {@link GameRules} field. Purely UI: each control maps to a GameRules constructor
 * parameter, and Save hands the new rules to the caller. A first attempt at this panel used
 * {@code Dialog<GameRules>} and needed a caption-colour override to be readable at all.
 */
public final class GameSettingsPane extends OverlayPane {

    private final ComboBox<Integer> deckCombo =
            new ComboBox<>(FXCollections.observableArrayList(1, 2, 4, 6, 8));
    private final RadioButton standRadio = new RadioButton("Stand (S17, friendlier)");
    private final RadioButton hitRadio = new RadioButton("Hit (H17, standard casino)");
    private final CheckBox doubleAfterSplitCheck = new CheckBox("Allowed");
    private final RadioButton payout32Radio = new RadioButton("3:2 (standard)");
    private final RadioButton payout65Radio = new RadioButton("6:5 (worse for the player)");
    private final Slider penetrationSlider = new Slider(40, 80, GameRules.standard().penetrationPercent());
    private final Label penetrationValueLabel = new Label();
    private final ComboBox<Integer> maxSplitCombo =
            new ComboBox<>(FXCollections.observableArrayList(2, 3, 4));
    private final CheckBox insuranceAllowedCheck = new CheckBox("Offer insurance against a dealer Ace");
    private final Consumer<GameRules> onSave;

    public GameSettingsPane(Consumer<GameRules> onSave) {
        super(420);
        this.onSave = onSave;

        Label title = new Label("Game Settings");
        title.getStyleClass().add("setup-title");

        ToggleGroup softSeventeenGroup = new ToggleGroup();
        standRadio.setToggleGroup(softSeventeenGroup);
        hitRadio.setToggleGroup(softSeventeenGroup);

        ToggleGroup payoutGroup = new ToggleGroup();
        payout32Radio.setToggleGroup(payoutGroup);
        payout65Radio.setToggleGroup(payoutGroup);

        penetrationSlider.setMajorTickUnit(10);
        penetrationSlider.setMinorTickCount(1);
        penetrationSlider.setSnapToTicks(true);
        penetrationSlider.setShowTickMarks(true);
        penetrationSlider.valueProperty().addListener((obs, oldVal, newVal) ->
                penetrationValueLabel.setText(Math.round(newVal.doubleValue()) + "%"));

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(12);
        grid.setAlignment(Pos.CENTER);
        int row = 0;
        grid.addRow(row++, new Label("Deck count"), deckCombo);
        grid.addRow(row++, new Label("Dealer soft 17"), new VBox(4, standRadio, hitRadio));
        grid.addRow(row++, new Label("Double after split"), doubleAfterSplitCheck);
        grid.addRow(row++, new Label("Blackjack payout"), new VBox(4, payout32Radio, payout65Radio));
        grid.addRow(row++, new Label("Shoe penetration"), new HBox(8, penetrationSlider, penetrationValueLabel));
        grid.addRow(row++, new Label("Max split hands"), maxSplitCombo);
        grid.addRow(row++, new Label("Insurance"), insuranceAllowedCheck);

        Button saveButton = new Button("Save");
        saveButton.getStyleClass().add("primary-button");
        saveButton.setOnAction(e -> save());

        Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(e -> close());

        setContent(title, grid, buttonRow(cancelButton, saveButton));
    }

    /** Opens showing {@code rules}, so a prior abandoned edit never lingers into the next open. */
    public void open(GameRules rules) {
        deckCombo.getSelectionModel().select(Integer.valueOf(rules.deckCount()));
        (rules.dealerHitsSoftSeventeen() ? hitRadio : standRadio).setSelected(true);
        doubleAfterSplitCheck.setSelected(rules.doubleAfterSplitAllowed());
        (rules.blackjackPayout() == BlackjackPayout.SIX_TO_FIVE ? payout65Radio : payout32Radio).setSelected(true);
        penetrationSlider.setValue(rules.penetrationPercent());
        penetrationValueLabel.setText(rules.penetrationPercent() + "%");
        maxSplitCombo.getSelectionModel().select(Integer.valueOf(rules.maxSplitHands()));
        insuranceAllowedCheck.setSelected(rules.insuranceAllowed());
        open();
    }

    private void save() {
        onSave.accept(new GameRules(
                deckCombo.getValue(),
                (int) Math.round(penetrationSlider.getValue()),
                hitRadio.isSelected(),
                payout65Radio.isSelected() ? BlackjackPayout.SIX_TO_FIVE : BlackjackPayout.THREE_TO_TWO,
                doubleAfterSplitCheck.isSelected(),
                maxSplitCombo.getValue(),
                insuranceAllowedCheck.isSelected()));
        close();
    }
}
