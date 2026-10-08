package io.github.davidefornari.blackjack.ui;

import io.github.davidefornari.blackjack.engine.BlackjackPayout;
import io.github.davidefornari.blackjack.engine.BlackjackTable;
import io.github.davidefornari.blackjack.engine.Card;
import io.github.davidefornari.blackjack.engine.CountingSystem;
import io.github.davidefornari.blackjack.engine.Dealer;
import io.github.davidefornari.blackjack.engine.GameRules;
import io.github.davidefornari.blackjack.engine.Hand;
import io.github.davidefornari.blackjack.engine.InsuranceSettlement;
import io.github.davidefornari.blackjack.engine.Player;
import io.github.davidefornari.blackjack.engine.RoundOutcome;
import io.github.davidefornari.blackjack.engine.Settlement;
import io.github.davidefornari.blackjack.engine.Shoe;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Owns the whole game session (setup -> betting -> optional insurance decision ->
 * player turn -> settlement -> next round) and keeps the JavaFX scene graph in sync
 * with the {@link BlackjackTable} engine state. There is no FXML: for a UI this size,
 * wiring the scene graph directly in Java keeps every binding visible in one place.
 */
public final class GameController {

    /** GAME_OVER: the bankroll fell under {@link #MINIMUM_BET}. CASHED_OUT: the player chose to leave. */
    private enum Phase { SETUP, BETTING, AWAITING_INSURANCE, PLAYER_TURN, ROUND_OVER, GAME_OVER, CASHED_OUT }

    private static final long DEFAULT_BANKROLL = 1000;
    /**
     * The table minimum, and the real end-of-session threshold: bets are placed in chips, so a
     * bankroll below the smallest chip can't be wagered at all even though it isn't zero.
     * Checking for zero alone left 1-4 chips looking like a live game with every chip button
     * and Deal disabled and no way out of BETTING.
     */
    private static final long MINIMUM_BET = ChipView.DENOMINATIONS.get(0);

    private final StackPane root = new StackPane();
    private final VBox setupOverlay;
    private final VBox confirmOverlay;
    private final VBox gameSettingsOverlay;
    private final VBox countingGuideOverlay;
    private final VBox insuranceOverlay;
    private final BorderPane tableLayout;

    private final HandPane dealerPane = new HandPane();
    private final HBox playerHandsBox = new HBox(24);
    private final Label messageLabel = new Label();
    private final Label bankrollLabel = new Label();
    private final Label shoeInfoLabel = new Label();

    private final Label bannerTitleLabel = new Label();
    private final Label bannerAmountLabel = new Label();
    private final VBox winLoseBanner = new VBox(4, bannerTitleLabel, bannerAmountLabel);
    private SequentialTransition bannerAnimation;

    private final List<Long> placedChips = new ArrayList<>();
    private final Map<Long, Button> chipButtonsByDenomination = new LinkedHashMap<>();
    private final StackPane betStackPane = new StackPane();
    private final Label betTotalLabel = new Label();
    private final Button clearBetButton = new Button("Clear Bet");
    private final Label betErrorLabel = new Label();
    private final Button dealButton = new Button("Deal");

    private final Button hitButton = new Button("Hit");
    private final Button standButton = new Button("Stand");
    private final Button doubleButton = new Button("Double");
    private final Button splitButton = new Button("Split");

    private final Button nextRoundButton = new Button("Next Round");
    private final Button leaveTableButton = new Button("Leave Table");
    private final Button newGameButton = new Button("New Game");

    private final Label confirmTitleLabel = new Label();
    private final Label confirmMessageLabel = new Label();
    private final Button confirmActionButton = new Button();
    private Runnable confirmAction = () -> { };

    private final TextField nameField = new TextField();
    private final TextField bankrollField = new TextField(String.valueOf(DEFAULT_BANKROLL));
    private final ComboBox<CountingSystem> countingCombo =
            new ComboBox<>(FXCollections.observableArrayList(CountingSystem.values()));
    private final CheckBox showCountCheckBox = new CheckBox("Show card count");
    private final Label setupErrorLabel = new Label();
    private final Label rulesSummaryLabel = new Label();

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

    private final Label insuranceMessageLabel = new Label();
    private final HandPane insuranceHandPreview = new HandPane();
    private final Button takeInsuranceButton = new Button("Take Insurance");
    private final Button declineInsuranceButton = new Button("No Thanks");
    private PauseTransition insuranceDelay;

    private Phase phase = Phase.SETUP;
    private BlackjackTable table;
    private Player player;
    private Map<Hand, Settlement> lastSettlements = Map.of();
    /** The chips behind the last dealt bet, re-placed for the next round when still affordable. */
    private List<Long> lastBetChips = List.of();
    private long startingBankroll;
    private boolean showCardCount;
    private CountingSystem countingSystem;
    private Stage stage;
    private GameRules pendingRules = GameRules.standard();

    public GameController() {
        setupOverlay = buildSetupOverlay();
        confirmOverlay = buildConfirmOverlay();
        gameSettingsOverlay = buildGameSettingsOverlay();
        countingGuideOverlay = buildCountingGuideOverlay();
        insuranceOverlay = buildInsuranceOverlay();
        tableLayout = buildTableLayout();

        shoeInfoLabel.getStyleClass().add("count-badge");
        StackPane.setAlignment(shoeInfoLabel, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(shoeInfoLabel, new Insets(0, 16, 16, 0));

        winLoseBanner.getStyleClass().add("win-lose-banner");
        bannerTitleLabel.getStyleClass().add("win-lose-banner-title");
        bannerAmountLabel.getStyleClass().add("win-lose-banner-amount");
        winLoseBanner.setAlignment(Pos.CENTER);
        // Spans the full table width like a ribbon, but must not stretch to the StackPane's
        // full height too (Region's default max height is Double.MAX_VALUE) — clamp it to its
        // own content height so it reads as a horizontal band, not a full-screen overlay.
        winLoseBanner.setMaxHeight(Region.USE_PREF_SIZE);
        winLoseBanner.setMouseTransparent(true);
        winLoseBanner.setVisible(false);
        winLoseBanner.setOpacity(0);

        // gameSettingsOverlay and countingGuideOverlay are opened from buttons inside
        // setupOverlay, so they must come after it here to actually render on top of it.
        root.getChildren().addAll(
                tableLayout, shoeInfoLabel, winLoseBanner, confirmOverlay, insuranceOverlay,
                setupOverlay, gameSettingsOverlay, countingGuideOverlay);
        wireActions();
        refresh();
    }

    public Parent getRoot() {
        return root;
    }

    /**
     * Lets the window grow to fit content added after launch (dealt cards, wager chip
     * stacks, extra hands from a split) instead of leaving them cropped behind a fixed
     * size that inevitably goes stale as the table UI grows. Deliberately grow-only —
     * see {@link #growToFitContent()} — so the window never jumps smaller mid-round.
     * Also installs the keyboard shortcuts (see {@link #onKeyPressed}).
     */
    public void attachStage(Stage stage) {
        this.stage = stage;
        stage.addEventFilter(KeyEvent.KEY_PRESSED, this::onKeyPressed);
    }

    /** Grows (never shrinks) the window to fit the current layout, once it's actually measured. */
    private void growToFitContent() {
        if (stage == null) {
            return;
        }
        Platform.runLater(() -> {
            root.applyCss();
            root.layout();
            double neededWidth = root.prefWidth(-1);
            double neededHeight = root.prefHeight(-1);
            if (stage.getWidth() < neededWidth) {
                stage.setWidth(neededWidth);
            }
            if (stage.getHeight() < neededHeight) {
                stage.setHeight(neededHeight);
            }
        });
    }

    // ------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------

    private VBox buildSetupOverlay() {
        nameField.setPromptText("Your name");
        countingCombo.getSelectionModel().select(CountingSystem.HI_LO);
        setupErrorLabel.getStyleClass().add("error-label");
        showCountCheckBox.setSelected(false);

        rulesSummaryLabel.getStyleClass().add("rules-summary-label");
        rulesSummaryLabel.setWrapText(true);
        rulesSummaryLabel.setText(describeRules(pendingRules));

        Button gameSettingsButton = new Button("Game Settings");
        gameSettingsButton.setOnAction(e -> showGameSettingsOverlay());

        Button countingGuideButton = new Button("?");
        countingGuideButton.getStyleClass().add("setup-help-button");
        countingGuideButton.setAccessibleText("Card-counting guide");
        countingGuideButton.setOnAction(e -> show(countingGuideOverlay, true));

        Button sitDownButton = new Button("Sit Down");
        sitDownButton.getStyleClass().add("primary-button");
        sitDownButton.setOnAction(e -> onSitDown());

        VBox form = new VBox(10,
                new Label("Player name"), nameField,
                new Label("Starting bankroll"), bankrollField,
                new Label("Card-counting system"), buttonRow(countingCombo, countingGuideButton),
                showCountCheckBox,
                gameSettingsButton,
                rulesSummaryLabel,
                setupErrorLabel,
                sitDownButton);
        form.setAlignment(Pos.CENTER);
        form.setPadding(new Insets(28));
        form.setMaxWidth(320);
        form.getStyleClass().add("setup-card");

        VBox overlay = new VBox(new Label("Blackjack"), form);
        overlay.getChildren().get(0).getStyleClass().add("setup-title");
        overlay.setAlignment(Pos.CENTER);
        overlay.setSpacing(18);
        overlay.getStyleClass().add("setup-overlay");
        return overlay;
    }

    /**
     * In-theme replacement for a plain {@link javafx.scene.control.Alert} confirmation —
     * reuses the same {@code setup-overlay}/{@code setup-card} look as the setup screen
     * instead of a default-styled system dialog, which looked out of place on this table
     * and had its own contrast problems (see {@code openGameSettingsDialog()}). Shared by
     * every session-ending action; {@link #showConfirmOverlay} fills in the specifics.
     */
    private VBox buildConfirmOverlay() {
        confirmTitleLabel.getStyleClass().add("setup-title");

        confirmMessageLabel.setWrapText(true);
        confirmMessageLabel.setTextAlignment(TextAlignment.CENTER);

        confirmActionButton.getStyleClass().add("primary-button");
        confirmActionButton.setOnAction(e -> {
            show(confirmOverlay, false);
            confirmAction.run();
        });

        Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(e -> show(confirmOverlay, false));

        return overlay(340, confirmTitleLabel, confirmMessageLabel, buttonRow(cancelButton, confirmActionButton));
    }

    private void showConfirmOverlay(String title, String message, String confirmText, Runnable onConfirm) {
        confirmTitleLabel.setText(title);
        confirmMessageLabel.setText(message);
        confirmActionButton.setText(confirmText);
        confirmAction = onConfirm;
        show(confirmOverlay, true);
    }

    /** What each system on the setup form counts, how it is played, and which to pick. */
    private VBox buildCountingGuideOverlay() {
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
        closeButton.setOnAction(e -> show(countingGuideOverlay, false));

        // Scrolls only when the window is shorter than the guide. A ScrollPane sizes itself from
        // its content's unwrapped (one-line) height, so its preferred height follows the text's
        // laid-out height instead.
        ScrollPane scroll = new ScrollPane(text);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.prefViewportHeightProperty().bind(text.heightProperty());
        scroll.getStyleClass().add("setup-scroll");

        return overlay(480, title, scroll, buttonRow(closeButton));
    }

    private static Label wrapped(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        return label;
    }

    /** A hidden in-theme popup: a {@code setup-card} of {@code content}, centred on a dimming {@code setup-overlay}. */
    private static VBox overlay(double maxWidth, Node... content) {
        VBox card = new VBox(16, content);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(28));
        card.setMaxWidth(maxWidth);
        card.getStyleClass().add("setup-card");

        VBox overlay = new VBox(card);
        overlay.setAlignment(Pos.CENTER);
        overlay.getStyleClass().add("setup-overlay");
        show(overlay, false);
        return overlay;
    }

    private static HBox buttonRow(Node... buttons) {
        HBox row = new HBox(12, buttons);
        row.setAlignment(Pos.CENTER);
        return row;
    }

    /** Shows or hides {@code node}, taking it out of layout while hidden. */
    private static void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    /**
     * Every field here is already a {@link GameRules} constructor parameter — this overlay
     * is purely UI, no engine changes. In-theme (reuses {@code setup-overlay}/{@code setup-card},
     * same as {@link #buildConfirmOverlay()}) rather than a
     * {@link javafx.scene.control.Dialog}: a first attempt at this exact panel used
     * {@code Dialog<GameRules>} and needed a caption-color
     * override to be readable — this version sidesteps that entirely by using our own CSS.
     */
    private VBox buildGameSettingsOverlay() {
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
        saveButton.setOnAction(e -> applyGameSettings());

        Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(e -> show(gameSettingsOverlay, false));

        return overlay(420, title, grid, buttonRow(cancelButton, saveButton));
    }

    /** Resets every control from {@code pendingRules} so a prior abandoned edit never lingers into the next open. */
    private void showGameSettingsOverlay() {
        deckCombo.getSelectionModel().select(Integer.valueOf(pendingRules.deckCount()));
        (pendingRules.dealerHitsSoftSeventeen() ? hitRadio : standRadio).setSelected(true);
        doubleAfterSplitCheck.setSelected(pendingRules.doubleAfterSplitAllowed());
        (pendingRules.blackjackPayout() == BlackjackPayout.SIX_TO_FIVE ? payout65Radio : payout32Radio).setSelected(true);
        penetrationSlider.setValue(pendingRules.penetrationPercent());
        penetrationValueLabel.setText(pendingRules.penetrationPercent() + "%");
        maxSplitCombo.getSelectionModel().select(Integer.valueOf(pendingRules.maxSplitHands()));
        insuranceAllowedCheck.setSelected(pendingRules.insuranceAllowed());

        show(gameSettingsOverlay, true);
    }

    private void applyGameSettings() {
        pendingRules = new GameRules(
                deckCombo.getValue(),
                (int) Math.round(penetrationSlider.getValue()),
                hitRadio.isSelected(),
                payout65Radio.isSelected() ? BlackjackPayout.SIX_TO_FIVE : BlackjackPayout.THREE_TO_TWO,
                doubleAfterSplitCheck.isSelected(),
                maxSplitCombo.getValue(),
                insuranceAllowedCheck.isSelected());
        rulesSummaryLabel.setText(describeRules(pendingRules));
        show(gameSettingsOverlay, false);
    }

    private String describeRules(GameRules rules) {
        return rules.deckCount() + " decks, "
                + (rules.dealerHitsSoftSeventeen() ? "H17" : "S17") + ", "
                + rules.blackjackPayout().displayName() + ", DAS "
                + (rules.doubleAfterSplitAllowed() ? "on" : "off") + ", "
                + rules.penetrationPercent() + "% penetration, max "
                + rules.maxSplitHands() + " splits, insurance "
                + (rules.insuranceAllowed() ? "on" : "off");
    }

    /**
     * Paused mid-deal, on an Ace up-card only, when {@link GameRules#insuranceAllowed()}
     * is on — same in-theme {@code setup-overlay}/{@code setup-card} pattern as
     * {@link #buildConfirmOverlay()} rather than a stock dialog. The insurance
     * amount itself is fixed at the standard casino max (half the original wager) rather
     * than an adjustable field, to avoid a bespoke bet-amount input.
     */
    private VBox buildInsuranceOverlay() {
        Label title = new Label("Insurance?");
        title.getStyleClass().add("setup-title");

        insuranceMessageLabel.setWrapText(true);
        insuranceMessageLabel.setTextAlignment(TextAlignment.CENTER);

        // Shrunk down (vs. the full-size cards on the table behind this overlay) so it reads
        // as a reference thumbnail, not a second copy of the hand competing for attention.
        insuranceHandPreview.setScaleX(0.7);
        insuranceHandPreview.setScaleY(0.7);

        takeInsuranceButton.getStyleClass().add("primary-button");
        takeInsuranceButton.setOnAction(e -> onTakeInsurance());

        declineInsuranceButton.setOnAction(e -> onDeclineInsurance());

        return overlay(340, title, insuranceHandPreview, insuranceMessageLabel,
                buttonRow(declineInsuranceButton, takeInsuranceButton));
    }

    /** Shows the insurance popup after a short pause, so the dealt hand finishes animating onto the table first. */
    private void showInsuranceOverlayAfterDelay() {
        if (insuranceDelay != null) {
            insuranceDelay.stop();
        }
        insuranceDelay = new PauseTransition(Duration.millis(500));
        insuranceDelay.setOnFinished(e -> showInsuranceOverlay());
        insuranceDelay.play();
    }

    private void showInsuranceOverlay() {
        long cost = table.maxInsuranceBet();
        // A bet that took the whole bankroll leaves nothing to pay the side bet with, and
        // takeInsurance() throws on that — thrown inside an FX handler it only reaches stderr,
        // so the button would just look dead. Offer the decline as the only way on instead.
        boolean affordable = cost > 0 && cost <= player.bankroll();
        takeInsuranceButton.setDisable(!affordable);
        declineInsuranceButton.setText(affordable ? "No Thanks" : "Continue");
        insuranceMessageLabel.setText(affordable
                ? "The dealer is showing an Ace. Insure your hand for " + cost
                        + " chips against a dealer blackjack? It pays 2:1 if the dealer has one."
                : "The dealer is showing an Ace, but insuring this hand costs " + cost
                        + " chips and your bet left you only " + player.bankroll()
                        + ". You'll have to play it uninsured.");

        Hand hand = player.firstHand();
        List<CardView> views = new ArrayList<>();
        for (Card card : hand.cards()) {
            views.add(CardView.faceUp(card));
        }
        insuranceHandPreview.setCaption("Your Hand");
        insuranceHandPreview.setCards(views);
        insuranceHandPreview.setWager(0);
        insuranceHandPreview.setTotalText(handStatusText(hand));

        show(insuranceOverlay, true);
    }

    private void onTakeInsurance() {
        table.takeInsurance(table.maxInsuranceBet());
        show(insuranceOverlay, false);
        afterInsuranceDecision();
    }

    private void onDeclineInsurance() {
        table.declineInsurance();
        show(insuranceOverlay, false);
        afterInsuranceDecision();
    }

    /**
     * Winning insurance means the dealer had blackjack, so the round is already decided —
     * settle it and show one combined "INSURANCE WIN +netProfit" banner (main hand profit
     * folded in, e.g. bet 10 + insurance 5, dealer blackjack, plain losing hand: -10 main
     * hand + 10 insurance profit = "INSURANCE WIN +0") instead of a separate insurance
     * banner followed by the usual WIN/LOST/PUSH one. A loss or decline of insurance
     * doesn't get its own banner — just the round message text mentions it — and the round
     * proceeds exactly as it would have without insurance.
     */
    private void afterInsuranceDecision() {
        Optional<InsuranceSettlement> insurance = table.lastInsuranceSettlement();
        if (insurance.isPresent() && insurance.get().won()) {
            long mainHandProfit = settleRound();
            long insuranceProfit = insurance.get().payout() - insurance.get().amountWagered();
            showBanner("INSURANCE WIN", mainHandProfit + insuranceProfit, "win-lose-banner-win");
        } else if (table.isPlayerTurnComplete()) {
            showRoundOutcomeBanner(settleRound());
        } else {
            phase = Phase.PLAYER_TURN;
        }
        refresh();
    }

    private BorderPane buildTableLayout() {
        BorderPane layout = new BorderPane();
        layout.getStyleClass().add("table-layout");

        messageLabel.getStyleClass().add("message-label");
        playerHandsBox.setAlignment(Pos.CENTER);

        VBox center = new VBox(24, dealerPane, messageLabel, playerHandsBox);
        center.setAlignment(Pos.CENTER);
        center.setPadding(new Insets(10, 20, 10, 20));
        layout.setCenter(center);

        layout.setBottom(buildControlsArea());
        return layout;
    }

    private HBox buildChipRow() {
        HBox row = new HBox(10);
        row.getStyleClass().add("chip-rail");
        row.setAlignment(Pos.CENTER);
        double size = ChipView.DIAMETER + 10;
        for (long denomination : ChipView.DENOMINATIONS) {
            Button chip = new Button();
            chip.setGraphic(new ChipView(denomination, size));
            chip.getStyleClass().add("chip-button-round");
            chip.setPrefSize(size, size);
            chip.setMinSize(size, size);
            chip.setMaxSize(size, size);
            chip.setOnAction(e -> addChip(denomination));
            chipButtonsByDenomination.put(denomination, chip);
            row.getChildren().add(chip);
        }
        return row;
    }

    private VBox buildControlsArea() {
        HBox statusBar = new HBox(bankrollLabel);
        statusBar.setAlignment(Pos.CENTER);
        statusBar.getStyleClass().add("status-bar");

        betErrorLabel.getStyleClass().add("error-label");
        betTotalLabel.getStyleClass().add("bet-total-badge");
        clearBetButton.setOnAction(e -> onClearBet());

        betStackPane.setMinWidth(ChipView.DIAMETER + 20);

        dealButton.getStyleClass().add("primary-button");
        HBox betRow = new HBox(16, betStackPane, betTotalLabel, clearBetButton, dealButton);
        betRow.setAlignment(Pos.CENTER);

        VBox bettingBox = new VBox(10, buildChipRow(), betRow, betErrorLabel);
        bettingBox.setAlignment(Pos.CENTER);

        HBox actionRow = new HBox(10, hitButton, standButton, doubleButton, splitButton);
        actionRow.setAlignment(Pos.CENTER);

        nextRoundButton.getStyleClass().add("primary-button");
        newGameButton.getStyleClass().add("primary-button");
        // Buttons default to visible in JavaFX, and refresh() can't set their real state until
        // a table/player exist — without this they'd flash visible during SETUP, relying purely
        // on the overlay happening to cover them instead of actually being hidden.
        nextRoundButton.setVisible(false);
        leaveTableButton.setVisible(false);
        newGameButton.setVisible(false);
        HBox afterRoundRow = new HBox(10, nextRoundButton, leaveTableButton, newGameButton);
        afterRoundRow.setAlignment(Pos.CENTER);

        VBox controls = new VBox(14, statusBar, bettingBox, actionRow, afterRoundRow);
        controls.setAlignment(Pos.CENTER);
        controls.setPadding(new Insets(14, 18, 22, 18));
        controls.getStyleClass().add("controls-area");
        return controls;
    }

    private void wireActions() {
        dealButton.setOnAction(e -> onDeal());
        hitButton.setOnAction(e -> { table.hit(); afterPlayerAction(); });
        standButton.setOnAction(e -> { table.stand(); afterPlayerAction(); });
        doubleButton.setOnAction(e -> { table.doubleDown(); afterPlayerAction(); });
        splitButton.setOnAction(e -> { table.split(); afterPlayerAction(); });
        nextRoundButton.setOnAction(e -> onNextRound());
        leaveTableButton.setOnAction(e -> onLeaveTable());
        newGameButton.setOnAction(e -> onNewGame());

        dealButton.setTooltip(new Tooltip("Enter"));
        hitButton.setTooltip(new Tooltip("H"));
        standButton.setTooltip(new Tooltip("S"));
        doubleButton.setTooltip(new Tooltip("D"));
        splitButton.setTooltip(new Tooltip("P"));
        nextRoundButton.setTooltip(new Tooltip("Enter"));
    }

    /**
     * Table shortcuts: H/S/D/P for hit, stand, double, split; Enter to deal or start the next
     * round. Each just fires its button, so the button's own enabled state is the only rule.
     * A stage-level filter, so it works whatever has focus — which is also why it stands down
     * while any overlay is up, where the setup and settings text fields need those keys. Esc
     * dismisses the topmost popup that has a Close/Cancel; setup and insurance need an answer.
     */
    private void onKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.ESCAPE) {
            for (VBox popup : List.of(countingGuideOverlay, gameSettingsOverlay, confirmOverlay)) {
                if (popup.isVisible()) {
                    show(popup, false); // exactly what each popup's Close/Cancel button does
                    event.consume();
                    return;
                }
            }
        }
        boolean overlayShowing = setupOverlay.isVisible() || gameSettingsOverlay.isVisible()
                || confirmOverlay.isVisible() || insuranceOverlay.isVisible()
                || countingGuideOverlay.isVisible();
        if (overlayShowing) {
            return;
        }
        Button target = switch (event.getCode()) {
            case H -> hitButton;
            case S -> standButton;
            case D -> doubleButton;
            case P -> splitButton;
            case ENTER -> phase == Phase.ROUND_OVER ? nextRoundButton : dealButton;
            default -> null;
        };
        if (target == null) {
            return;
        }
        event.consume();
        target.fire(); // no-op while disabled, and refresh() disables every hidden button
    }

    // ------------------------------------------------------------------
    // Phase transitions
    // ------------------------------------------------------------------

    private void onSitDown() {
        Long bankroll = parsePositiveLong(bankrollField.getText());
        // Anything under one chip can't be bet, so it would seat the player straight into the
        // dead end that MINIMUM_BET exists to prevent at the other end of the session.
        if (bankroll == null || bankroll < MINIMUM_BET) {
            setupErrorLabel.setText("Enter a starting bankroll of at least " + MINIMUM_BET + " chips.");
            return;
        }
        String name = nameField.getText() == null || nameField.getText().isBlank()
                ? "Player" : nameField.getText().trim();

        player = new Player(name, bankroll);
        startingBankroll = bankroll;
        countingSystem = countingCombo.getValue();
        showCardCount = showCountCheckBox.isSelected();
        GameRules rules = pendingRules;
        Shoe shoe = new Shoe(rules.deckCount(), rules.penetrationPercent());
        table = new BlackjackTable(player, shoe, rules);

        setupErrorLabel.setText("");
        phase = Phase.BETTING;
        refresh();
    }

    private void onDeal() {
        long bet = currentBetTotal();
        if (bet <= 0) {
            betErrorLabel.setText("Place a bet first — click a chip.");
            return;
        }
        betErrorLabel.setText("");
        lastBetChips = List.copyOf(placedChips);
        table.startRound(bet);
        if (table.isInsurancePending()) {
            phase = Phase.AWAITING_INSURANCE;
            refresh();
            showInsuranceOverlayAfterDelay();
            return;
        }
        if (table.isPlayerTurnComplete()) {
            // Decided at the deal — a natural, or a dealer natural peeked on a ten up-card.
            // The banner is this call site's job too, not just afterPlayerAction()'s.
            showRoundOutcomeBanner(settleRound());
        } else {
            phase = Phase.PLAYER_TURN;
        }
        refresh();
    }

    /** Adds a chip to the current bet, ignored if it would exceed the bankroll (the button should already be disabled in that case). */
    private void addChip(long denomination) {
        if (currentBetTotal() + denomination > player.bankroll()) {
            return;
        }
        placedChips.add(denomination);
        refresh();
    }

    private void onClearBet() {
        placedChips.clear();
        refresh();
    }

    private long currentBetTotal() {
        return sum(placedChips);
    }

    private static long sum(List<Long> chips) {
        return chips.stream().mapToLong(Long::longValue).sum();
    }

    private void afterPlayerAction() {
        if (table.isPlayerTurnComplete()) {
            showRoundOutcomeBanner(settleRound());
        }
        refresh();
    }

    /**
     * Resolves the dealer's turn and every player hand, advances the phase (ROUND_OVER, or
     * GAME_OVER once what's left can't cover {@link #MINIMUM_BET}), and returns the main
     * hand(s)' total profit. Doesn't show a banner itself — callers decide that, since a win against
     * a dealer blackjack the player insured needs its profit folded into a single combined
     * "INSURANCE WIN" banner instead of its own {@link #showRoundOutcomeBanner}.
     */
    private long settleRound() {
        table.playDealerTurn();
        List<Settlement> settlements = table.settle();
        Map<Hand, Settlement> byHand = new HashMap<>();
        long totalProfit = 0;
        for (Settlement s : settlements) {
            byHand.put(s.hand(), s);
            totalProfit += s.payout() - s.hand().wager();
        }
        lastSettlements = byHand;
        phase = player.bankroll() >= MINIMUM_BET ? Phase.ROUND_OVER : Phase.GAME_OVER;
        return totalProfit;
    }

    /** Pops up "WIN +N" / "LOST -N" / "PUSH +0" for the main hand(s) — always shown, so a push still gets a result. */
    private void showRoundOutcomeBanner(long totalProfit) {
        String title = totalProfit > 0 ? "WIN" : totalProfit < 0 ? "LOST" : "PUSH";
        String styleClass = totalProfit > 0 ? "win-lose-banner-win"
                : totalProfit < 0 ? "win-lose-banner-lose" : "win-lose-banner-push";
        showBanner(title, totalProfit, styleClass);
    }

    /** Scale+fade pop-in, hold, fade-out for {@code winLoseBanner}. */
    private void showBanner(String title, long amount, String styleClass) {
        bannerTitleLabel.setText(title);
        bannerAmountLabel.setText((amount >= 0 ? "+" : "-") + Math.abs(amount));
        winLoseBanner.getStyleClass().removeAll("win-lose-banner-win", "win-lose-banner-lose", "win-lose-banner-push");
        winLoseBanner.getStyleClass().add(styleClass);

        if (bannerAnimation != null) {
            bannerAnimation.stop();
        }
        winLoseBanner.setOpacity(0);
        winLoseBanner.setScaleX(0.6);
        winLoseBanner.setScaleY(0.6);
        winLoseBanner.setVisible(true);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(220), winLoseBanner);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        ScaleTransition scaleIn = new ScaleTransition(Duration.millis(220), winLoseBanner);
        scaleIn.setFromX(0.6);
        scaleIn.setFromY(0.6);
        scaleIn.setToX(1.0);
        scaleIn.setToY(1.0);
        ParallelTransition popIn = new ParallelTransition(fadeIn, scaleIn);

        PauseTransition hold = new PauseTransition(Duration.millis(1100));

        FadeTransition fadeOut = new FadeTransition(Duration.millis(320), winLoseBanner);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> winLoseBanner.setVisible(false));

        bannerAnimation = new SequentialTransition(popIn, hold, fadeOut);
        bannerAnimation.play();
    }

    /** Re-places the last bet's chips, so repeating a bet is one click (or Enter); dropped if no longer affordable. */
    private void onNextRound() {
        lastSettlements = Map.of();
        placedChips.clear();
        if (sum(lastBetChips) <= player.bankroll()) {
            placedChips.addAll(lastBetChips);
        }
        phase = Phase.BETTING;
        refresh();
    }

    /** Confirms first — leaving ends the session, so a misclick next to "Next Round" shouldn't be able to. */
    private void onLeaveTable() {
        showConfirmOverlay("Cash Out?",
                "Cash out with " + player.bankroll() + " chips and end this session?",
                "Yes, Cash Out",
                () -> {
                    phase = Phase.CASHED_OUT;
                    refresh();
                });
    }

    /** Only offered once the session is over (out of chips, or cashed out), so there's nothing left to confirm. */
    private void onNewGame() {
        phase = Phase.SETUP;
        table = null;
        player = null;
        lastSettlements = Map.of();
        lastBetChips = List.of();
        placedChips.clear();
        refresh();
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    private void refresh() {
        boolean setupPhase = phase == Phase.SETUP;
        show(setupOverlay, setupPhase);
        // Hidden, not unmanaged: the setup form sits on bare felt (no leftovers from the last
        // session) while the table keeps its size, so the window doesn't resize around it.
        tableLayout.setVisible(!setupPhase);
        shoeInfoLabel.setVisible(!setupPhase);

        if (setupPhase) {
            return;
        }

        bankrollLabel.setText(player.name() + " — Bankroll: " + player.bankroll());
        String shoeInfo = "Shoe: " + table.cardsRemainingInShoe() + " cards left";
        if (showCardCount) {
            shoeInfo += "   |   " + countingSystem.displayName() + " count: "
                    + formatSigned(table.runningCount(countingSystem));
            if (countingSystem.isBalanced()) {
                // Locale.ROOT: an Italian locale would print "+1,5".
                shoeInfo += String.format(Locale.ROOT, " (true %+.1f)", table.trueCount(countingSystem));
            }
        }
        shoeInfoLabel.setText(shoeInfo);

        renderDealer();
        renderPlayerHands();
        renderBetStack();
        renderMessage();

        boolean betting = phase == Phase.BETTING;
        boolean playerTurn = phase == Phase.PLAYER_TURN;
        boolean roundOver = phase == Phase.ROUND_OVER;
        boolean sessionOver = phase == Phase.GAME_OVER || phase == Phase.CASHED_OUT;

        long betTotal = currentBetTotal();
        for (long denomination : ChipView.DENOMINATIONS) {
            boolean wouldExceedBankroll = betTotal + denomination > player.bankroll();
            chipButtonsByDenomination.get(denomination).setDisable(!betting || wouldExceedBankroll);
        }
        clearBetButton.setDisable(!betting || placedChips.isEmpty());
        dealButton.setDisable(!betting || betTotal <= 0);
        betErrorLabel.setVisible(betting);
        // Once dealt, each hand shows its own chip stack right under its cards — the pre-deal
        // pile in the controls area would just be a confusing, stale duplicate of that.
        show(betStackPane, betting);
        show(betTotalLabel, betting);

        hitButton.setDisable(!playerTurn);
        standButton.setDisable(!playerTurn);
        doubleButton.setDisable(!(playerTurn && table.canDouble()));
        splitButton.setDisable(!(playerTurn && table.canSplit()));

        nextRoundButton.setDisable(!roundOver);
        nextRoundButton.setVisible(roundOver);
        leaveTableButton.setDisable(!roundOver);
        leaveTableButton.setVisible(roundOver);
        newGameButton.setVisible(sessionOver);
        newGameButton.setDisable(!sessionOver);

        growToFitContent();
    }

    private void renderDealer() {
        Dealer dealer = table.dealer();
        // Between "Next Round" and the next "Deal", table.dealer() still holds last round's
        // finished hand (it only resets inside startRound()) — hide it so the table reads as empty.
        List<Card> cards = phase == Phase.BETTING ? List.of() : dealer.hand().cards();
        List<CardView> views = new ArrayList<>();
        for (int i = 0; i < cards.size(); i++) {
            boolean hidden = i == 1 && !dealer.isHoleCardRevealed();
            views.add(hidden ? CardView.faceDown() : CardView.faceUp(cards.get(i)));
        }
        dealerPane.setCaption("Dealer");
        dealerPane.setCards(views);
        dealerPane.setTotalText(phase != Phase.BETTING && dealer.isHoleCardRevealed()
                ? "Total: " + dealer.hand().total() : "");
        animateIn(views);
    }

    private void renderPlayerHands() {
        playerHandsBox.getChildren().clear();
        if (phase == Phase.BETTING) {
            return; // same reasoning as renderDealer(): don't show last round's leftover hands
        }
        List<Hand> hands = player.hands();
        for (int i = 0; i < hands.size(); i++) {
            Hand hand = hands.get(i);
            HandPane pane = new HandPane();
            pane.setCaption(hands.size() > 1 ? "Hand " + (i + 1) : "Your Hand");

            List<CardView> views = new ArrayList<>();
            for (Card card : hand.cards()) {
                views.add(CardView.faceUp(card));
            }
            pane.setCards(views);
            pane.setWager(hand.wager());
            pane.setTotalText(handStatusText(hand));
            pane.setActive(phase == Phase.PLAYER_TURN && i == table.activeHandIndex());
            playerHandsBox.getChildren().add(pane);
            animateIn(views);
        }
    }

    private void renderBetStack() {
        betStackPane.getChildren().setAll(ChipView.stack(currentBetTotal()));
        betTotalLabel.setText("Bet: " + currentBetTotal());
    }

    private String handStatusText(Hand hand) {
        Settlement settlement = lastSettlements.get(hand);
        if (settlement != null) {
            long profit = settlement.payout() - hand.wager();
            return switch (settlement.outcome()) {
                case BLACKJACK_WIN -> "Blackjack! +" + profit;
                case WIN -> "Win +" + profit;
                case PUSH -> "Push (" + hand.total() + ")";
                case LOSS -> "Lose (" + hand.total() + ")";
                case BUST -> "Bust (" + hand.total() + ")";
            };
        }
        if (hand.status() == Hand.Status.BLACKJACK) {
            return "Blackjack!";
        }
        if (hand.isBust()) {
            return "Bust (" + hand.total() + ")";
        }
        return hand.total() + (hand.isSoft() ? " (soft)" : "");
    }

    private void renderMessage() {
        if (phase == Phase.CASHED_OUT) {
            long net = player.bankroll() - startingBankroll;
            String result = net > 0 ? "up " + net : net < 0 ? "down " + -net : "breaking even";
            messageLabel.setText("Cashed out with " + player.bankroll() + " chips, " + result
                    + ". Thanks for playing, " + player.name() + ".");
        } else if (phase == Phase.GAME_OVER) {
            // GAME_OVER now also covers a non-zero bankroll too small to bet, so don't claim
            // "out of chips" when there are visibly a few left on the status line.
            messageLabel.setText(player.bankroll() > 0
                    ? "Only " + player.bankroll() + " chips left — under the " + MINIMUM_BET
                            + "-chip minimum. Thanks for playing, " + player.name() + "."
                    : "Out of chips — thanks for playing, " + player.name() + ".");
        } else if (phase == Phase.ROUND_OVER) {
            messageLabel.setText(summarizeRound());
        } else if (phase == Phase.PLAYER_TURN) {
            messageLabel.setText("Your move");
        } else if (phase == Phase.AWAITING_INSURANCE) {
            messageLabel.setText("Insurance?");
        } else if (!lastBetChips.isEmpty() && placedChips.equals(lastBetChips)) {
            messageLabel.setText("Same bet as last round — Deal, or change it");
        } else {
            messageLabel.setText("Place your bet");
        }
    }

    private String summarizeRound() {
        boolean anyWin = lastSettlements.values().stream()
                .anyMatch(s -> s.outcome() == RoundOutcome.WIN || s.outcome() == RoundOutcome.BLACKJACK_WIN);
        boolean anyLoss = lastSettlements.values().stream()
                .anyMatch(s -> s.outcome() == RoundOutcome.LOSS || s.outcome() == RoundOutcome.BUST);
        String summary;
        if (anyWin && !anyLoss) summary = "You win!";
        else if (anyLoss && !anyWin) summary = "Dealer wins";
        else if (!anyWin) summary = "Push";
        else summary = "Round over";

        return table.lastInsuranceSettlement()
                .filter(ins -> ins.amountWagered() > 0)
                .map(ins -> summary + (ins.won()
                        ? " — Insurance paid +" + (ins.payout() - ins.amountWagered())
                        : " — Insurance lost -" + ins.amountWagered()))
                .orElse(summary);
    }

    private void animateIn(List<CardView> views) {
        for (int i = 0; i < views.size(); i++) {
            views.get(i).setOpacity(0);
            FadeTransition fade = new FadeTransition(Duration.millis(180), views.get(i));
            fade.setToValue(1);
            fade.setDelay(Duration.millis(i * 90));
            fade.play();
        }
    }

    private Long parsePositiveLong(String text) {
        if (text == null) {
            return null;
        }
        try {
            long value = Long.parseLong(text.trim());
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String formatSigned(int value) {
        return value > 0 ? "+" + value : String.valueOf(value);
    }
}
